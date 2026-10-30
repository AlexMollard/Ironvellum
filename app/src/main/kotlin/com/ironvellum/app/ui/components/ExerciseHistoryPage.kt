package com.ironvellum.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.R
import com.ironvellum.app.data.Repository
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.ExerciseLog
import com.ironvellum.app.domain.ExerciseLogCalculator
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.ExerciseLogBlock
import com.ironvellum.app.domain.ExerciseLogSession
import com.ironvellum.app.domain.ExerciseLogSet
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/**
 * Opens a past workout by id, supplied by the nav host. Null where there is no route to it (a preview, a test host),
 * and then the history rows are plain text rather than links.
 */
val LocalOpenWorkout = compositionLocalOf<((Long) -> Unit)?> { null }

/**
 * Loads one movement's log off the main thread; the sheet is only ever opened on one exercise, so the VM is keyed by it.
 * The log shows ONE variation (the incline and the deficit push-up differ by kilos, so one line through both only
 * jumps): the lifter's pick, else the variation of the most recent block, else every variation when there is one.
 */
internal class ExerciseHistoryViewModel(repo: Repository, exercise: Exercise) : ViewModel() {
    private val picked = MutableStateFlow<String?>(null)

    val log: StateFlow<ExerciseLog?> = combine(repo.observeExerciseLog(exercise), picked) { inputs, pick ->
        val all = ExerciseLogCalculator.build(exercise, inputs.rows, inputs.bodyweightAt)
        val shown = when {
            all.variations.size < 2 || pick == ExerciseLog.ALL -> null
            pick != null && all.variations.any { it.key == pick } -> pick
            else -> all.defaultVariation
        }
        if (shown == null) all else ExerciseLogCalculator.build(exercise, inputs.rows, inputs.bodyweightAt, shown)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Shows [variation] (a [com.ironvellum.app.domain.Variation.key]), or every one for [ExerciseLog.ALL]. */
    fun pick(variation: String) {
        picked.value = variation
    }
}

/** The HISTORY page of the exercise sheet: summary, trend, then past workouts newest first. */
internal fun InfoSheetScope.historyFacts(exercise: Exercise, onDismiss: () -> Unit) {
    section(null) { ExerciseHistoryPage(exercise, onDismiss) }
}

@Composable
private fun ColumnScope.ExerciseHistoryPage(exercise: Exercise, onDismiss: () -> Unit) {
    val vm: ExerciseHistoryViewModel = viewModel(
        key = "exercise-log-${exercise.id}",
        factory = viewModelFactory { initializer { ExerciseHistoryViewModel(ironvellumRepository(), exercise) } },
    )
    val log by vm.log.collectAsStateWithLifecycle()
    var shown by rememberSaveable { mutableIntStateOf(RECENT_ROWS) }
    val open = LocalOpenWorkout.current
    // Nothing until Room has answered: an empty state for a frame would read as "never logged".
    val current = log ?: return
    if (current.isEmpty) {
        Text(
            stringResource(R.string.infosheet_history_empty_title),
            style = MaterialTheme.typography.titleSmall,
            color = IronvellumColors.Ink,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            Words.text(UiText.themed(R.string.infosheet_history_empty_hint, R.string.infosheet_history_empty_hint__t)),
            style = MaterialTheme.typography.bodyMedium,
            color = IronvellumColors.InkMuted,
        )
        return
    }
    val dateFormat = stringResource(R.string.explorer_date_day_month_year)
    if (current.variations.size >= 2) {
        VariationChips(current, vm::pick)
        Spacer(Modifier.height(8.dp))
    }
    HistorySummary(exercise, current, dateFormat)
    if (current.bodyMissing) {
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.infosheet_history_body_missing),
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
        )
    }
    if (current.trend.size >= 2) {
        Spacer(Modifier.height(10.dp))
        HistoryTrend(current, dateFormat)
    }
    Spacer(Modifier.height(10.dp))
    PanelLabel(Words.text(UiText.themed(R.string.infosheet_history_list_label, R.string.infosheet_history_list_label__t)))
    Spacer(Modifier.height(2.dp))
    current.visible(shown).forEach { session ->
        HistoryRow(exercise, session, dateFormat, open?.let { go -> { onDismiss(); go(session.sessionId) } })
    }
    if (current.sessions.size > shown) {
        SeeAllRow(current.sessions.size) { shown = Int.MAX_VALUE }
    }
}

/** The past workouts a page shows before "See all": enough to read the trend, few enough to fit the page. */
private const val RECENT_ROWS = 4

/**
 * One panel, the best set as its lead (with the best estimated 1RM beside it for a weighted lift), then how many
 * times and when last, inline. Every label keeps its words; each figure is one spoken line.
 */
@Composable
private fun HistorySummary(exercise: Exercise, log: ExerciseLog, dateFormat: String) {
    val hold = exercise.metric == ExerciseMetric.HOLD
    val judged = exercise.metric == ExerciseMetric.REPS || hold
    val lead = if (judged) log.best?.let { best ->
        stringResource(if (hold) R.string.infosheet_history_best_hold_label else R.string.infosheet_history_best_set_label) to bestSetText(exercise, best)
    } else null
    val e1rm = log.bestE1rmKg?.let { stringResource(R.string.infosheet_history_best_e1rm_label) to e1rmText(it, log.addsToBody, 1) }
    val done = stringResource(R.string.infosheet_history_done_label) to log.sessions.size.toString()
    val last = log.lastAtMs?.let { stringResource(R.string.infosheet_history_last_label) to formatDate(it, dateFormat) }
    InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(10.dp)) {
        when {
            lead != null && e1rm != null -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LeadFigure(lead, Modifier.weight(1f))
                    LeadFigure(e1rm, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                InkDivider()
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    InlineFigure(done, Modifier.weight(1f))
                    if (last != null) InlineFigure(last, Modifier.weight(1f))
                }
            }
            lead != null -> {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LeadFigure(lead, Modifier.weight(1f))
                    Box(Modifier.width(1.dp).fillMaxHeight().background(IronvellumColors.Rune))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
                        InlineFigure(done, Modifier.fillMaxWidth())
                        if (last != null) InlineFigure(last, Modifier.fillMaxWidth())
                    }
                }
            }
            else -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LeadFigure(done, Modifier.weight(1f))
                    if (last != null) LeadFigure(last, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LeadFigure(figure: Pair<String, String>, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        PanelLabel(figure.first)
        StatValue(figure.second, size = StatSize.Tile)
    }
}

@Composable
private fun InlineFigure(figure: Pair<String, String>, modifier: Modifier) {
    Row(modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        PanelLabel(figure.first, Modifier.padding(end = 8.dp), )
        Spacer(Modifier.weight(1f))
        Text(
            figure.second,
            style = MaterialTheme.typography.titleSmall,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = IronvellumColors.Ink,
        )
    }
}

/** One chip per variation the lifter has used, after "All"; the shown one is marked. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VariationChips(log: ExerciseLog, onPick: (String) -> Unit) {
    val plain = stringResource(R.string.common_variation_standard)
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterChip(stringResource(R.string.infosheet_history_variation_all), log.selected == null) { onPick(ExerciseLog.ALL) }
        log.variations.forEach { v ->
            FilterChip(v.label(plain), log.selected == v.key) { onPick(v.key) }
        }
    }
}

private fun bestSetText(exercise: Exercise, set: ExerciseLogSet): String =
    blockLine(exercise, ExerciseLogBlock(0, "", listOf(set), null, false)).text

@Composable
private fun HistoryTrend(log: ExerciseLog, dateFormat: String) {
    val hold = log.metric == ExerciseMetric.HOLD
    val title = when {
        log.byE1rm -> UiText.themed(R.string.infosheet_history_trend_e1rm, R.string.infosheet_history_trend_e1rm__t)
        hold -> UiText.themed(R.string.infosheet_history_trend_hold, R.string.infosheet_history_trend_hold__t)
        else -> UiText.themed(R.string.infosheet_history_trend_reps, R.string.infosheet_history_trend_reps__t)
    }
    val context = LocalContext.current
    val values = log.trend.map { it.value }
    InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(10.dp)) {
    PanelLabel(Words.text(title))
    TrendChart(
        values,
        fromZero = false,
        modifier = Modifier.fillMaxWidth().height(TREND_HEIGHT).padding(top = 4.dp),
        startLabel = formatDate(log.trend.first().atMs, dateFormat),
        endLabel = formatDate(log.trend.last().atMs, dateFormat),
        valueText = {
            when {
                log.byE1rm -> e1rmText(it, log.addsToBody, 1)
                hold -> UiText.res(R.string.explorer_set_hold, it.toInt()).asString(context)
                else -> it.toInt().toString()
            }
        },
        // The top and bottom of the line, with their unit, on the left edge: "+46 kg" over "+20 kg".
        axisText = {
            when {
                log.byE1rm -> e1rmText(it, log.addsToBody, 0)
                hold -> UiText.res(R.string.explorer_set_hold, it.toInt()).asString(context)
                else -> UiText.plural(R.plurals.victory_totals_reps, it.toInt(), it.toInt().toString()).asString(context)
            }
        },
        dateText = { formatDate(log.trend[it].atMs, dateFormat) },
        atMs = log.trend.map { it.atMs },
        surface = IronvellumColors.Vault,
        smooth = false,
    )
    }
}

private val TREND_HEIGHT = 80.dp

@Composable
private fun HistoryRow(exercise: Exercise, session: ExerciseLogSession, dateFormat: String, onOpen: (() -> Unit)?) {
    val date = formatDate(session.atMs, dateFormat)
    val recordTag = Words.peak.u
    val recordSpoken = Words.text(UiText.themed(R.string.infosheet_history_record_description, R.string.infosheet_history_record_description__t))
    val lines = session.blocks.map { block ->
        val mods = modifierText(block.modifiers)
        val sets = blockLine(exercise, block).text
        buildString {
            if (mods.isNotEmpty()) append(mods).append(": ")
            append(sets)
            if (block.record) append(", ").append(recordSpoken)
        }
    }
    val spoken = stringResource(R.string.infosheet_history_row_description, date, session.label, lines.joinToString(". "))
    val openLabel = Words.text(UiText.themed(R.string.infosheet_history_open_label, R.string.infosheet_history_open_label__t))
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = LedgerSpace.Target)
            .then(if (onOpen != null) Modifier.clickable(role = Role.Button, onClickLabel = openLabel, onClick = onOpen) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .padding(vertical = 5.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                date,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = ChakraPetch,
                color = IronvellumColors.InkMuted,
            )
            if (session.label.isNotBlank()) {
                Text(
                    " · ${session.label}",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
        session.blocks.forEach { block ->
            val mods = modifierText(block.modifiers)
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(blockLine(exercise, block), style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink)
                    if (mods.isNotEmpty()) {
                        Text(mods, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
                    }
                }
                if (block.record) {
                    Text(
                        recordTag,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        color = IronvellumColors.SovereignGold,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
    InkDivider()
}

/** "See all 24 workouts ›": opens the rest of the list in place. A 48dp target. */
@Composable
private fun SeeAllRow(count: Int, onClick: () -> Unit) {
    val label = Words.text(UiText.themedPlural(R.plurals.infosheet_history_see_all, R.plurals.infosheet_history_see_all__t, count))
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = LedgerSpace.Target)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            color = IronvellumColors.SystemGreen,
            modifier = Modifier.weight(1f),
        )
        Text("›", style = MaterialTheme.typography.titleMedium, color = IronvellumColors.InkMuted, modifier = Modifier.clearAndSetSemantics {})
    }
}
