package com.ironvellum.app.ui.recap

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.R
import com.ironvellum.app.data.Repository
import com.ironvellum.app.domain.SetRecords
import com.ironvellum.app.domain.PeriodReview
import com.ironvellum.app.domain.ReviewKind
import com.ironvellum.app.domain.ReviewPeriod
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.PushedHeader
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.theme.IronvellumColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** The review once it is read, or null before. */
data class ReviewUi(val review: PeriodReview? = null)

class ReviewViewModel(
    private val repo: Repository,
    val period: ReviewPeriod,
) : ViewModel() {

    val ui: StateFlow<ReviewUi> = combine(repo.observeHistory(), repo.observeExercises(), repo.observeSkillPractices(), repo.observeStats()) { history, exercises, practices, stats ->
        ReviewUi(PeriodReview.build(history, exercises.associateBy { it.id }, SetRecords.bodyweightLookup(stats), practices, period, LocalDate.now()))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewUi())
}

/** The period a route names: [kind] is "month" or "year", [startEpochDay] any day inside it. */
internal fun reviewPeriodOf(kind: String, startEpochDay: Long): ReviewPeriod =
    ReviewPeriod.of(if (kind == "year") ReviewKind.YEAR else ReviewKind.MONTH, LocalDate.ofEpochDay(startEpochDay))

internal val ReviewPeriod.routeKind: String get() = if (kind == ReviewKind.YEAR) "year" else "month"

/** The review of the month or year [kind] starts on [startEpochDay]: totals, best week, lifts, muscles, days, skills. */
@Composable
fun ReviewScreen(
    kind: String,
    startEpochDay: Long,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onOpenWeek: (Long) -> Unit,
    viewModel: ReviewViewModel = viewModel(
        key = "review_$kind$startEpochDay",
        factory = viewModelFactory { initializer { ReviewViewModel(ironvellumRepository(), reviewPeriodOf(kind, startEpochDay)) } },
    ),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val review = ui.review
    if (review == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    ReviewContent(review, onBack = onBack, onShare = onShare, onOpenWeek = onOpenWeek)
}

/** The review with everything handed in, so a test or a screenshot can render any period. */
@Composable
internal fun ReviewContent(
    review: PeriodReview,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onOpenWeek: (Long) -> Unit,
    scroll: ScrollState = rememberScrollState(),
) {
    val locale = LocalConfiguration.current.locales[0]
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(20.dp))
            PushedHeader(
                title = stringResource(ReviewText.titleRes(review.period)),
                onBack = onBack,
                actions = {
                    IconButton(onClick = onShare) {
                        Icon(Icons.Outlined.IosShare, contentDescription = stringResource(R.string.review_share_description), tint = IronvellumColors.Ink)
                    }
                },
            )
            Text(
                ReviewText.subtitle(review, locale),
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
                modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
            )
            Positions(scroll)
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ReviewHero(review)
            ReviewBestWeek(review, onOpenWeek)
            ReviewLifts(review)
            ReviewMuscles(review)
            ReviewVolume(review)
            ReviewDays(review)
            ReviewSkills(review)
            ReviewWeeks(review, onOpenWeek)
            Spacer(Modifier.height(8.dp))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .background(IronvellumColors.Abyss)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            IronvellumButton(
                label = stringResource(R.string.review_share_button, ReviewText.short(review.period, locale)),
                onClick = onShare,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Three marks under the header that fill as the page is read: the first at the top, the second through the middle, all three
 * at the end. Decoration only (the scroll bar is the real control), so a screen reader skips it.
 */
@Composable
private fun Positions(scroll: ScrollState) {
    val reached by remember {
        derivedStateOf { if (scroll.maxValue <= 0) 2 else minOf(2, (scroll.value * 3f / scroll.maxValue).toInt()) }
    }
    Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(3.dp)
                    .background(if (index <= reached) IronvellumColors.Emerald else IronvellumColors.Rune),
            )
        }
    }
}
