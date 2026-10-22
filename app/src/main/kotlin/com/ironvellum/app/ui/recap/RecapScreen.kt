package com.ironvellum.app.ui.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import com.ironvellum.app.domain.RecapKind
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.domain.WeeklyRecap
import com.ironvellum.app.domain.WorkoutPreset
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.PushedHeader
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.program.toPlanned
import com.ironvellum.app.ui.theme.IronvellumColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The recap and the workout the plan has for today (what an empty week offers to begin); [recap] is null until read. */
data class RecapUi(val recap: WeeklyRecap? = null, val todayRite: WorkoutPreset? = null)

class RecapViewModel(
    private val repo: Repository,
    private val weekStart: LocalDate,
) : ViewModel() {

    val ui: StateFlow<RecapUi> = combine(repo.observeHistory(), repo.observeExercises(), repo.observePresets(), repo.observeStats()) { history, exercises, presets, stats ->
        val today = LocalDate.now()
        val plan = presets.filter { it.scheduledDay != null }.map { it.toPlanned() }
        RecapUi(
            recap = WeeklyRecap.build(history, exercises.associateBy { it.id }, SetRecords.bodyweightLookup(stats), plan, weekStart, today),
            todayRite = presets.firstOrNull { it.scheduledDay == today.dayOfWeek.value },
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecapUi())

    fun begin(presetId: Long, onStarted: (Long) -> Unit) {
        viewModelScope.launch { onStarted(repo.startSessionFromPreset(presetId)) }
    }
}

/** The weekly recap of the week whose Monday is [weekEpochDay]: totals, records, muscles, streak and what is next. */
@Composable
fun RecapScreen(
    weekEpochDay: Long,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onBegin: (Long) -> Unit,
    viewModel: RecapViewModel = viewModel(
        key = "recap_$weekEpochDay",
        factory = viewModelFactory { initializer { RecapViewModel(ironvellumRepository(), LocalDate.ofEpochDay(weekEpochDay)) } },
    ),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val recap = ui.recap
    if (recap == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    RecapContent(
        recap = recap,
        todayRite = ui.todayRite,
        today = LocalDate.now(),
        onBack = onBack,
        onShare = onShare,
        onBegin = { rite -> viewModel.begin(rite, onBegin) },
    )
}

/**
 * The recap with everything handed in, so a test or a screenshot can render any week. An empty week offers to begin
 * today's workout where the others offer the share; a light week hides the comparison bars and shows what was done instead.
 */
@Composable
internal fun RecapContent(
    recap: WeeklyRecap,
    todayRite: WorkoutPreset?,
    today: LocalDate,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onBegin: (Long) -> Unit,
) {
    val empty = recap.kind == RecapKind.EMPTY
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(20.dp))
            PushedHeader(
                title = stringResource(R.string.recap_title),
                onBack = onBack,
                actions = {
                    if (!empty) {
                        IconButton(onClick = onShare) {
                            Icon(Icons.Outlined.IosShare, contentDescription = stringResource(R.string.recap_share_description), tint = IronvellumColors.Ink)
                        }
                    }
                },
            )
            Text(
                RecapText.range(recap.weekStart, recap.weekEnd),
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
                modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
            )
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HeroCard(recap)
            when (recap.kind) {
                RecapKind.EMPTY -> EmptyWeek(recap, today)
                RecapKind.LIGHT -> LightWeek(recap, today)
                RecapKind.FULL -> FullWeek(recap, today)
            }
            recap.next?.let { NextWeekCard(it) }
            Spacer(Modifier.height(8.dp))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .background(IronvellumColors.Abyss)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (empty) {
                if (todayRite != null) {
                    IronvellumButton(
                        label = UiText.res(R.string.recap_begin_button, todayRite.name).asString(),
                        onClick = { onBegin(todayRite.id) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                IronvellumButton(
                    label = stringResource(R.string.recap_share_button),
                    onClick = onShare,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
