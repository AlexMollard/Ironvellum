package com.ironvellum.app.ui.recap

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.R
import com.ironvellum.app.domain.PeriodReview
import com.ironvellum.app.ui.ironvellumRepository

/** The share screen of a month or year in review: the weekly recap's, on the review's card. */
@Composable
fun ReviewShareScreen(
    kind: String,
    startEpochDay: Long,
    onBack: () -> Unit,
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
    ReviewShareContent(review, onBack)
}

@Composable
internal fun ReviewShareContent(review: PeriodReview, onBack: () -> Unit) {
    ShareCardContent(
        title = stringResource(R.string.review_share_title),
        cardKey = review,
        saveName = stringResource(R.string.review_share_save_name, review.period.key),
        chooserTitle = stringResource(R.string.review_share_chooser_title),
        fileName = stringResource(R.string.review_share_file_name),
        recordsLabel = stringResource(R.string.review_share_lifts_label),
        recordsCaption = stringResource(R.string.review_share_lifts_caption),
        streakCaption = stringResource(R.string.review_share_streak_caption),
        onBack = onBack,
        render = { context, options, palette -> ReviewCard.render(context, review, options, palette) },
    )
}
