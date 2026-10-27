package com.ironvellum.app.ui.program

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.R
import com.ironvellum.app.data.Units
import com.ironvellum.app.domain.ChangeOp
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.MuscleLoad
import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.ProgramRules
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.components.IronvellumDialog
import com.ironvellum.app.data.Words

/*
 * Optimise rites. One screen with a small stack of pages (the hub, a gap's movement and rite, a heavy muscle,
 * one change opened, two movements doing one job, the days, what was applied), each reached from the one before
 * and left with Back. The page the lifter is on is saved with the screen as a short string, and so are their
 * Keep / Skip ticks, so a rotation costs nothing. The lifter's rites are only written when they press Apply or
 * Add, in one write, and Undo puts every touched rite back.
 */

// ------------------------------------------------------------------------------------------------- steps

/** The page the lifter is on. Saved as a string ([encode]), so every part must survive a round trip. */
internal sealed interface OptStep {
    data object Hub : OptStep
    data class Gap(val muscle: Muscle) : OptStep
    data class Where(val muscle: Muscle, val option: Int) : OptStep
    data class Heavy(val muscle: Muscle) : OptStep
    data class Overlap(val rite: String) : OptStep
    data object Spread : OptStep
    data class Recovery(val presetId: Long) : OptStep
    data class Detail(val parent: OptStep, val changeId: String) : OptStep

    fun encode(): String = when (this) {
        Hub -> "hub"
        is Gap -> "gap:${muscle.name}"
        is Where -> "where:${muscle.name}:$option"
        is Heavy -> "heavy:${muscle.name}"
        is Overlap -> "overlap:$rite"
        Spread -> "spread"
        is Recovery -> "recovery:$presetId"
        is Detail -> "detail:${parent.encode()}$SEP$changeId"
    }

    /** Where Back goes. */
    fun parent(): OptStep = when (this) {
        Hub -> Hub
        is Gap, is Heavy, is Overlap, Spread, is Recovery -> Hub
        is Where -> Gap(muscle)
        is Detail -> parent
    }

    companion object {
        private const val SEP = "\u0001"

        fun decode(text: String): OptStep = runCatching {
            val parts = text.split(":", limit = 3)
            when (parts[0]) {
                "gap" -> Gap(Muscle.valueOf(parts[1]))
                "where" -> Where(Muscle.valueOf(parts[1]), parts[2].toInt())
                "heavy" -> Heavy(Muscle.valueOf(parts[1]))
                "overlap" -> Overlap(text.removePrefix("overlap:"))
                "spread" -> Spread
                "recovery" -> Recovery(parts[1].toLong())
                "detail" -> {
                    val rest = text.removePrefix("detail:")
                    Detail(decode(rest.substringBefore(SEP)), rest.substringAfter(SEP))
                }
                else -> Hub
            }
        }.getOrDefault(Hub)
    }
}

/** A tick is saved as one number: the option chosen, shifted left, and a bit for Keep. */
private fun Map<String, Int>.keepOf(change: OptChange): Boolean =
    this[change.id]?.let { it and 1 == 1 } ?: (change.kind != OptKind.MOVE)

private fun Map<String, Int>.optionOf(change: OptChange): Int =
    (this[change.id]?.shr(1) ?: 0).coerceIn(0, change.options.lastIndex)

private fun Map<String, Int>.with(change: OptChange, keep: Boolean, option: Int): Map<String, Int> =
    this + (change.id to ((option shl 1) or if (keep) 1 else 0))

/** The changes a ticked list keeps, each with the option picked. */
private fun Map<String, Int>.kept(changes: List<OptChange>): List<Pair<OptChange, OptOption>> =
    changes.filter { keepOf(it) }.map { it to it.options[optionOf(it)] }

private fun OptimiseReport.allChanges(): List<OptChange> =
    heavy.flatMap { it.changes } + overlaps.flatMap { it.changes } + listOfNotNull(spread)

// ------------------------------------------------------------------------------------------------ screen

@Composable
internal fun OptimiseScreen(
    onBack: () -> Unit,
    onForgeCycle: () -> Unit,
    viewModel: OptimiseViewModel = run {
        val appContext = LocalContext.current.applicationContext
        viewModel(factory = viewModelFactory { initializer { OptimiseViewModel(ironvellumRepository(), appContext) } })
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val applied by viewModel.applied.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val errorText by viewModel.error.collectAsStateWithLifecycle()
    val error = errorText?.asString()
    var stepKey by rememberSaveable { mutableStateOf("hub") }
    var picks by rememberSaveable { mutableStateOf<Map<String, Int>>(emptyMap()) }

    val shownApplied = applied
    val current = state
    Column(Modifier.fillMaxSize()) {
        when {
            shownApplied != null -> AppliedPage(
                applied = shownApplied,
                saving = saving,
                error = error,
                onUndo = {
                    // The page that wrote the change is behind it; after Undo the lifter starts again from the hub.
                    picks = emptyMap()
                    stepKey = OptStep.Hub.encode()
                    viewModel.undo()
                },
                onDone = {
                    viewModel.dismissApplied()
                    picks = emptyMap()
                    stepKey = OptStep.Hub.encode()
                },
            )

            current is OptimiseState.Ready -> {
                ReadyPages(
                    report = current.report,
                    step = OptStep.decode(stepKey),
                    onStep = {
                        viewModel.clearError()
                        stepKey = it.encode()
                    },
                    picks = picks,
                    onPicks = { picks = it },
                    saving = saving,
                    error = error,
                    onApply = viewModel::apply,
                    onKeepAsIs = viewModel::keepAsIs,
                    onBack = onBack,
                )
            }

            else -> MessagePage(
                title = UiText.themed(R.string.optimise_title, R.string.optimise_title__t).asString(),
                body = when (current) {
                    OptimiseState.NoRites -> UiText.themed(R.string.optimise_empty_no_rites, R.string.optimise_empty_no_rites__t).asString()
                    OptimiseState.NeedsArmoury ->
                        UiText.themed(R.string.optimise_empty_needs_armoury, R.string.optimise_empty_needs_armoury__t).asString()
                    else -> UiText.themed(R.string.optimise_loading, R.string.optimise_loading__t).asString()
                },
                action = when (current) {
                    OptimiseState.NoRites, OptimiseState.NeedsArmoury ->
                        UiText.themed(R.string.optimise_forge_button, R.string.optimise_forge_button__t).asString() to onForgeCycle
                    else -> null
                },
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun MessagePage(title: String, body: String, action: Pair<String, () -> Unit>?, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(20.dp))
        ForgeHeader(title, onBack)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.InkMuted, modifier = Modifier.padding(top = 16.dp))
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            IronvellumButton(label = action.first, onClick = action.second, modifier = Modifier.heightIn(min = 48.dp))
        }
    }
}

@Composable
private fun ReadyPages(
    report: OptimiseReport,
    step: OptStep,
    onStep: (OptStep) -> Unit,
    picks: Map<String, Int>,
    onPicks: (Map<String, Int>) -> Unit,
    saving: Boolean,
    error: String?,
    onApply: (Applied, List<Pair<Long, PlannedPreset>>) -> Unit,
    onKeepAsIs: (Long, Muscle) -> Unit,
    onBack: () -> Unit,
) {
    val input = report.input
    val week = input.week
    val daysTitle = stringResource(R.string.optimise_spread_applied_title)

    // System back is the header's BACK on every page: each page names where BACK goes, and both take it.
    // The hub leaves the route and the gap sheet closes itself, so neither needs a handler here.
    val stepBack: OptStep? = when (step) {
        OptStep.Hub, is OptStep.Gap -> null
        is OptStep.Where -> OptStep.Gap(step.muscle)
        is OptStep.Detail -> step.parent
        is OptStep.Heavy, is OptStep.Overlap, OptStep.Spread, is OptStep.Recovery -> OptStep.Hub
    }
    BackHandler(enabled = stepBack != null) { stepBack?.let(onStep) }

    /** Writes [kept] as one change of the week: the rites that differ, with what they were before. */
    fun apply(title: String, changes: List<OptChange>) {
        val kept = picks.kept(changes)
        val after = resolveWeek(week, kept.map { it.second })
        val touched = week.indices.filter { after[it] != week[it] }
        if (touched.isEmpty()) return
        onApply(
            Applied(
                title = title,
                lines = kept.map { (change, option) -> change.kind.label to titleFor(change, option) },
                skipped = changes.size - kept.size,
                before = touched.map { input.ids[it] to week[it] },
                riteNames = touched.map { week[it].name },
            ),
            touched.map { input.ids[it] to after[it] },
        )
    }

    /** Writes one picked option of a recovery finding: the rites that differ, with what they were before. */
    fun applyOne(change: OptChange, option: OptOption) {
        val after = resolveWeek(week, listOf(option))
        val touched = week.indices.filter { after[it] != week[it] }
        if (touched.isEmpty()) return
        onApply(
            Applied(
                title = change.rite.orEmpty(),
                lines = listOf(change.kind.label to option.label),
                skipped = 0,
                before = touched.map { input.ids[it] to week[it] },
                riteNames = touched.map { week[it].name },
            ),
            touched.map { input.ids[it] to after[it] },
        )
    }

    when (step) {
        OptStep.Hub -> HubPage(report, onStep, onBack)
        is OptStep.Gap -> {
            HubPage(report, onStep, onBack)
            GapSheet(report, step.muscle, onStep)
        }
        is OptStep.Where -> WherePage(report, step, onStep, saving, error, onApply)
        is OptStep.Heavy -> {
            val finding = report.heavy.firstOrNull { it.muscle == step.muscle }
            if (finding == null) {
                // Eased, or no longer heavy: nothing left to show here.
                Redirect(onStep)
            } else {
                ChangesPage(
                    title = finding.muscle.label,
                    status = stringResource(R.string.optimise_heavy_label),
                    subtitle = heavyLoadText(finding.load),
                    report = report,
                    focus = finding.muscle,
                    intro = heavyReason(finding.load).first,
                    cite = heavyReason(finding.load).second.label,
                    changes = finding.changes,
                    picks = picks,
                    onPicks = onPicks,
                    saving = saving,
                    error = error,
                    emptyNote = stringResource(R.string.optimise_heavy_empty),
                    leave = stringResource(R.string.optimise_heavy_leave_button),
                    onApply = { apply(finding.muscle.label, finding.changes) },
                    onOpen = { onStep(OptStep.Detail(step, it.id)) },
                    onBack = { onStep(OptStep.Hub) },
                )
            }
        }
        is OptStep.Overlap -> {
            val rite = report.overlaps.firstOrNull { it.rite == step.rite }
            if (rite == null) {
                Redirect(onStep)
            } else {
                OverlapPage(report, rite, picks, onPicks, saving, error, { apply(rite.rite, rite.changes) }, { onStep(OptStep.Hub) })
            }
        }
        OptStep.Spread -> {
            val change = report.spread
            if (change == null) {
                Redirect(onStep)
            } else {
                ChangesPage(
                    title = stringResource(R.string.optimise_spread_title),
                    status = null,
                    subtitle = stringResource(R.string.optimise_spread_subtitle),
                    report = report,
                    focus = null,
                    intro = tightLine(report.tight.first()),
                    cite = null,
                    changes = listOf(change),
                    picks = picks,
                    onPicks = onPicks,
                    saving = saving,
                    error = error,
                    emptyNote = "",
                    leave = null,
                    onApply = { apply(daysTitle, listOf(change)) },
                    onOpen = { onStep(OptStep.Detail(step, it.id)) },
                    onBack = { onStep(OptStep.Hub) },
                )
            }
        }
        is OptStep.Recovery -> {
            val rite = report.recovery.firstOrNull { it.finding.presetId == step.presetId }
            if (rite == null) {
                // Kept as is, or no longer tired: nothing left to show here.
                Redirect(onStep)
            } else {
                RecoveryPage(
                    rite = rite,
                    saving = saving,
                    error = error,
                    onApply = { applyOne(rite.change, it) },
                    onKeep = { onKeepAsIs(rite.finding.presetId, rite.finding.muscle) },
                    onBack = { onStep(OptStep.Hub) },
                )
            }
        }
        is OptStep.Detail -> {
            val change = report.allChanges().firstOrNull { it.id == step.changeId }
            if (change == null) {
                Redirect(onStep, step.parent)
            } else {
                DetailPage(report, change, step, picks, onPicks, onStep)
            }
        }
    }
}

/** A page whose subject is gone (it was just eased, or applied) sends the lifter back, outside composition. */
@Composable
private fun Redirect(onStep: (OptStep) -> Unit, to: OptStep = OptStep.Hub) {
    LaunchedEffect(Unit) { onStep(to) }
}

// ---------------------------------------------------------------------------------------------------- hub

@Composable
private fun HubPage(report: OptimiseReport, onStep: (OptStep) -> Unit, onBack: () -> Unit) {
    val loads = report.loads
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(20.dp))
        ForgeHeader(
            UiText.themed(R.string.optimise_title, R.string.optimise_title__t).asString(),
            onBack,
            subtitle = if (report.nothing) {
                stringResource(R.string.optimise_hub_nothing)
            } else {
                UiText.plural(R.plurals.optimise_hub_findings, report.findings).asString()
            },
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (report.nothing) {
                NothingCard()
            } else {
                OptNote(UiText.themed(R.string.optimise_hub_note, R.string.optimise_hub_note__t).asString())
            }
            if (report.gaps.isNotEmpty()) {
                OptLabel(stringResource(R.string.optimise_gaps_label))
                InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp)) {
                    report.gaps.forEachIndexed { index, muscle ->
                        if (index > 0) InkDivider()
                        val load = loads[muscle]
                        val state = report.tally.states.getValue(muscle)
                        FindingRow(
                            state = state,
                            title = muscle.label,
                            subline = "${hardSetsLabel(load?.weekly ?: 0.0)} · ${state.label}",
                            onClickLabel = stringResource(R.string.optimise_gap_open_label, muscle.label.lowercase()),
                            spoken = stringResource(R.string.optimise_gap_description, muscle.label, hardSetsLabel(load?.weekly ?: 0.0), state.label),
                            onClick = { onStep(OptStep.Gap(muscle)) },
                        )
                    }
                }
            }
            if (report.heavy.isNotEmpty()) {
                OptLabel(stringResource(R.string.optimise_heavy_label))
                InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp)) {
                    report.heavy.forEachIndexed { index, finding ->
                        if (index > 0) InkDivider()
                        val sets = heavyLoadText(finding.load)
                        val changes = changesText(finding.changes.size)
                        FindingRow(
                            state = CoverageState.HEAVY,
                            title = finding.muscle.label,
                            subline = stringResource(R.string.optimise_heavy_subline, sets, CoverageState.HEAVY.label, changes),
                            onClickLabel = stringResource(R.string.optimise_heavy_open_label, finding.muscle.label.lowercase()),
                            spoken = stringResource(R.string.optimise_heavy_description, finding.muscle.label, sets, CoverageState.HEAVY.label, changes),
                            onClick = { onStep(OptStep.Heavy(finding.muscle)) },
                        )
                    }
                }
            }
            if (report.recovery.isNotEmpty()) {
                OptLabel(stringResource(R.string.optimise_recovery_label))
                InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp)) {
                    report.recovery.forEachIndexed { index, rite ->
                        if (index > 0) InkDivider()
                        val line = recoverySubline(rite)
                        FindingRow(
                            state = null,
                            title = rite.finding.rite,
                            subline = line,
                            onClickLabel = stringResource(R.string.optimise_recovery_open_label, rite.finding.rite),
                            spoken = stringResource(R.string.optimise_recovery_description, rite.finding.rite, line),
                            onClick = { onStep(OptStep.Recovery(rite.finding.presetId)) },
                        )
                    }
                }
            }
            if (report.overlaps.isNotEmpty() || report.spread != null) {
                OptLabel(stringResource(R.string.optimise_overlap_label))
                InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp)) {
                    var shown = 0
                    report.overlaps.forEach { rite ->
                        if (shown++ > 0) InkDivider()
                        val line = UiText.plural(R.plurals.optimise_overlaps_found, rite.changes.size).asString()
                        FindingRow(
                            state = null,
                            title = rite.rite,
                            subline = stringResource(R.string.optimise_overlap_subline, line),
                            onClickLabel = stringResource(R.string.optimise_overlap_open_label, rite.rite),
                            spoken = stringResource(R.string.optimise_overlap_description, rite.rite, line),
                            onClick = { onStep(OptStep.Overlap(rite.rite)) },
                        )
                    }
                    report.spread?.let { change ->
                        if (shown++ > 0) InkDivider()
                        FindingRow(
                            state = null,
                            title = stringResource(R.string.optimise_spread_title),
                            subline = tightLine(report.tight.first()),
                            onClickLabel = stringResource(R.string.optimise_spread_open_label),
                            spoken = stringResource(R.string.optimise_spread_description, change.title, tightLine(report.tight.first())),
                            onClick = { onStep(OptStep.Spread) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun changesText(n: Int): String =
    if (n == 0) stringResource(R.string.optimise_changes_none) else UiText.plural(R.plurals.optimise_changes_count, n).asString()

/** "24 hard sets a week" for a muscle past the weekly mark, else "9 hard sets in one routine". */
@Composable
private fun heavyLoadText(load: MuscleLoad): String =
    if (load.weekly > ProgramRules.HEAVY_WEEK) {
        stringResource(R.string.optimise_heavy_per_week, hardSetsLabel(load.weekly))
    } else {
        UiText.themed(R.string.optimise_heavy_per_session, R.string.optimise_heavy_per_session__t, hardSetsLabel(load.maxSession)).asString()
    }

/** The Temper v2 "nothing to change" state: a plain sentence, and what is still short. */
@Composable
private fun NothingCard() {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(
            stringResource(R.string.optimise_hub_nothing),
            style = MaterialTheme.typography.titleMedium,
            color = IronvellumColors.Ink,
            modifier = Modifier.semantics { heading() },
        )
        OptNote(UiText.themed(R.string.optimise_nothing_note, R.string.optimise_nothing_note__t).asString())
    }
}

// ------------------------------------------------------------------------------- a gap: movement, then rite

/** "Add a movement for rear delts": the three ranked movements, best match first, in the app's bottom sheet. */
@Composable
private fun GapSheet(report: OptimiseReport, muscle: Muscle, onStep: (OptStep) -> Unit) {
    val ranked = report.gapOptions[muscle]?.options.orEmpty()
    val load = report.loads[muscle]
    val state = report.tally.states[muscle] ?: CoverageState.NOT_TRAINED
    var chosen by rememberSaveable(muscle.name) { mutableIntStateOf(0) }
    val input = report.input
    val picked = ranked.getOrNull(chosen.coerceAtMost((ranked.size - 1).coerceAtLeast(0)))
    IronvellumDialog(
        onDismissRequest = { onStep(OptStep.Hub) },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CoverageMark(state, size = 20.dp)
                val sheetDescription = stringResource(
                    R.string.optimise_gap_sheet_description, muscle.label.lowercase(), state.label, hardSetsLabel(load?.weekly ?: 0.0),
                )
                Column(Modifier.semantics(mergeDescendants = true) { contentDescription = sheetDescription }) {
                    Text(muscle.label, style = MaterialTheme.typography.titleLarge, color = IronvellumColors.Ink)
                    Text(
                        "${state.label} · ${hardSetsLabel(load?.weekly ?: 0.0)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = IronvellumColors.InkMuted,
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if ((load?.weekly ?: 0.0) <= 0.0) {
                        UiText.themed(
                            R.string.optimise_gap_none_caption, R.string.optimise_gap_none_caption__t,
                            muscle.label.lowercase(), ProgramRules.COVERED_MIN.toInt(),
                        ).asString()
                    } else {
                        stringResource(R.string.optimise_gap_short_caption, ProgramRules.COVERED_MIN.toInt(), muscle.label.lowercase())
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
                if (ranked.isEmpty()) {
                    OptNote(UiText.themed(R.string.optimise_gap_no_options, R.string.optimise_gap_no_options__t).asString())
                } else {
                    Text(
                        UiText.themed(R.string.optimise_gap_ranked_heading, R.string.optimise_gap_ranked_heading__t).asString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = IronvellumColors.InkMuted,
                        modifier = Modifier.semantics { heading() },
                    )
                    RankedOptions(
                        label = stringResource(R.string.optimise_gap_ranked_description, muscle.label.lowercase()),
                        names = ranked.map { it.exercise.name },
                        reasons = ranked.map { reasonLine(it.tags) },
                        lines = ranked.map { candidate ->
                            val entry = gapEntry(input, candidate)
                            prescriptionLine(entry, entry.targetWeightKg?.let { Units.withUnit(it) })
                        },
                        chosen = chosen,
                        onChoose = { chosen = it },
                    )
                }
            }
        },
        confirmButton = {
            IronvellumButton(
                label = if (picked != null) stringResource(R.string.optimise_add_named, picked.exercise.name) else stringResource(R.string.optimise_add_button),
                onClick = { onStep(OptStep.Where(muscle, chosen.coerceAtLeast(0))) },
                enabled = picked != null,
                modifier = Modifier.heightIn(min = 48.dp),
            )
        },
        dismissButton = { IronvellumButton(label = stringResource(R.string.common_cancel), onClick = { onStep(OptStep.Hub) }, quiet = true, modifier = Modifier.heightIn(min = 48.dp)) },
    )
}

/** "Put it on": which rite the movement goes into, how many hard sets, and what it does to the week. */
@Composable
private fun WherePage(
    report: OptimiseReport,
    step: OptStep.Where,
    onStep: (OptStep) -> Unit,
    saving: Boolean,
    error: String?,
    onApply: (Applied, List<Pair<Long, PlannedPreset>>) -> Unit,
) {
    val input = report.input
    val week = input.week
    val candidate = report.gapOptions[step.muscle]?.options?.getOrNull(step.option)
    if (candidate == null) {
        Redirect(onStep)
        return
    }
    val base = remember(report, step.muscle, step.option) { gapEntry(input, candidate) }
    val choices = remember(report, step.muscle, step.option) { riteChoices(input, base) }
    var riteIndex by rememberSaveable(step.encode()) { mutableIntStateOf(choices.firstOrNull()?.index ?: 0) }
    var sets by rememberSaveable(step.encode()) { mutableIntStateOf(base.sets) }
    val entry = base.copy(sets = sets.coerceIn(SET_SANITY))
    val after = remember(report, riteIndex, entry) { withAdded(week, riteIndex, entry) }
    val rite = week.getOrNull(riteIndex)
    val choice = choices.firstOrNull { it.index == riteIndex }
    val minutes = after.getOrNull(riteIndex)?.let { ProgramRules.dayMinutes(it, input.request.focus) }
    val target = rite?.let { it.scheduledDay?.let { day -> dayLabel(day) } ?: it.name } ?: ""
    val rows = remember(report, riteIndex, entry) { impactRows(week, after, step.muscle) }
    val putOnDescription = stringResource(R.string.optimise_where_description, candidate.exercise.name)
    val addedTitle = stringResource(R.string.optimise_where_added_title, candidate.exercise.name, rite?.name.orEmpty())

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(20.dp))
            ForgeHeader(
                stringResource(R.string.optimise_add_named, candidate.exercise.name),
                onBack = { onStep(OptStep.Gap(step.muscle)) },
                subtitle = stringResource(R.string.optimise_where_subtitle, step.muscle.label, hardSetsText(base.sets)),
            )
            error?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.DangerRed, modifier = Modifier.padding(top = 8.dp)) }
            OptLabel(stringResource(R.string.optimise_where_label))
            InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                Column(Modifier.fillMaxWidth().selectableGroup().semantics { contentDescription = putOnDescription }) {
                    choices.forEachIndexed { index, c ->
                        if (index > 0) InkDivider()
                        RiteChoiceRow(c, selected = c.index == riteIndex, onSelect = { riteIndex = c.index })
                    }
                }
            }
            OptLabel(stringResource(R.string.effort_editor_sets_label))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepperButton("−", stringResource(R.string.effort_editor_sets_less_description, candidate.exercise.name), enabled = sets > SET_SANITY.first) { sets-- }
                Text(
                    "${entry.sets}",
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = IronvellumColors.Ink,
                    modifier = Modifier.width(48.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                StepperButton("+", stringResource(R.string.effort_editor_sets_more_description, candidate.exercise.name), enabled = sets < SET_SANITY.last) { sets++ }
            }
            Text(suggestedSets(entry), style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
            OptLabel(stringResource(R.string.optimise_effect_label))
            ImpactList(rows)
            if (rite != null && minutes != null) {
                Text(
                    stringResource(R.string.optimise_where_becomes, target, movementsText(rite.entries.size + 1), minutes),
                    style = MaterialTheme.typography.labelSmall,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
        }
        ForgeDock(
            primary = stringResource(R.string.optimise_where_add_button, target),
            onPrimary = {
                val index = riteIndex
                onApply(
                    Applied(
                        title = addedTitle,
                        lines = listOf(Words.text(UiText.res(R.string.optimise_kind_add)) to "${candidate.exercise.name}, ${hardSetsText(entry.sets)}"),
                        skipped = 0,
                        before = listOf(input.ids[index] to week[index]),
                        riteNames = listOf(week[index].name),
                    ),
                    listOf(input.ids[index] to after[index]),
                )
            },
            enabled = rite != null && choice != null,
            busy = saving,
            note = stringResource(R.string.optimise_where_note),
        )
    }
}

@Composable
private fun RiteChoiceRow(choice: RiteChoice, selected: Boolean, onSelect: () -> Unit) {
    val chosenText = stringResource(if (selected) R.string.optimise_option_chosen else R.string.optimise_option_not_chosen)
    val spoken = buildString {
        append("${choice.title()}, ${choice.line()}")
        choice.note?.let { append(". $it") }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .semantics(mergeDescendants = true) {
                contentDescription = spoken
                stateDescription = chosenText
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(choice.title(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = IronvellumColors.Ink)
            Text(choice.line(), style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
            choice.note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.SystemGreen) }
        }
        RadioMark(selected)
    }
}

/** A 48dp − or + for the set count. */
@Composable
private fun StepperButton(glyph: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            style = MaterialTheme.typography.titleLarge,
            color = if (enabled) IronvellumColors.Ink else IronvellumColors.Rune,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

// --------------------------------------------------------------------------- heavy muscle, spread: changes

/**
 * A list of changes to keep or skip, with what keeping them does to the week above it, and one docked
 * "Apply N changes". Heavy and the days page both read like this (the Temper v2 list).
 */
@Composable
private fun ChangesPage(
    title: String,
    status: String?,
    subtitle: String,
    report: OptimiseReport,
    focus: Muscle?,
    intro: String,
    cite: String?,
    changes: List<OptChange>,
    picks: Map<String, Int>,
    onPicks: (Map<String, Int>) -> Unit,
    saving: Boolean,
    error: String?,
    emptyNote: String,
    leave: String?,
    onApply: () -> Unit,
    onOpen: (OptChange) -> Unit,
    onBack: () -> Unit,
) {
    val week = report.input.week
    val kept = picks.kept(changes)
    val after = remember(report, picks, changes) { resolveWeek(week, kept.map { it.second }) }
    val rows = remember(report, picks, changes) { impactRows(week, after, focus) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(20.dp))
            ForgeHeader(title, onBack, subtitle = subtitle, status = status)
            Text(intro, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted, modifier = Modifier.padding(top = 8.dp))
            cite?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, fontFamily = ChakraPetch, color = IronvellumColors.InkMuted)
            }
            error?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.DangerRed, modifier = Modifier.padding(top = 8.dp)) }
            if (changes.isEmpty()) {
                OptNote(emptyNote)
            } else {
                OptLabel(stringResource(R.string.optimise_fixes_label))
                ImpactList(rows, emptyLine = stringResource(R.string.optimise_fixes_empty))
                OptLabel(stringResource(R.string.optimise_changes_label))
                changes.forEachIndexed { index, change ->
                    if (index > 0) Spacer(Modifier.height(8.dp))
                    ChangeCard(
                        number = index + 1,
                        change = change,
                        keep = picks.keepOf(change),
                        chosen = picks.optionOf(change),
                        onOpen = { onOpen(change) },
                        onKeep = { onPicks(picks.with(change, it, picks.optionOf(change))) },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        ForgeDock(
            primary = applyLabel(kept.size),
            onPrimary = onApply,
            enabled = kept.isNotEmpty(),
            busy = saving,
            note = stringResource(R.string.optimise_apply_note),
            link = leave,
            onLink = onBack,
        )
    }
}

/** "Apply", or "Apply 3 changes" once something is kept. */
@Composable
private fun applyLabel(kept: Int): String =
    if (kept == 0) stringResource(R.string.common_apply) else UiText.plural(R.plurals.optimise_apply_changes, kept).asString()

// ------------------------------------------------------------------------------------- one change, open

/** One change opened: what leaves, what comes in (ranked), what it does to the week, then Skip or Keep. */
@Composable
private fun DetailPage(
    report: OptimiseReport,
    change: OptChange,
    step: OptStep.Detail,
    picks: Map<String, Int>,
    onPicks: (Map<String, Int>) -> Unit,
    onStep: (OptStep) -> Unit,
) {
    val week = report.input.week
    val siblings = report.allChanges().filter { it.rite == change.rite || change.rite == null }
    val number = (siblings.indexOfFirst { it.id == change.id } + 1).coerceAtLeast(1)
    val chosen = picks.optionOf(change)
    val option = change.options[chosen]
    val focus = (step.parent as? OptStep.Heavy)?.muscle
    val after = remember(report, change, chosen) { resolveWeek(week, listOf(option)) }
    val rows = remember(report, change, chosen) { impactRows(week, after, focus) }
    val leaving = remember(report, change) { leavingEntry(change, week) }
    val back = { onStep(step.parent) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(20.dp))
            ForgeHeader(
                change.kind.label,
                onBack = back,
                subtitle = listOfNotNull(change.rite, stringResource(R.string.optimise_detail_position, number, siblings.size)).joinToString(" · "),
            )
            if (leaving != null) {
                OptLabel(stringResource(R.string.optimise_leaves_label))
                InkPanel(Modifier.fillMaxWidth()) {
                    Text(leaving.exerciseName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = IronvellumColors.Ink)
                    Text(movementLine(leaving), style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
                    Text(
                        movementDetail(leaving, leaving.targetWeightKg?.let { Units.withUnit(it) }),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        color = IronvellumColors.SystemGreen,
                    )
                }
            }
            if (change.options.size > 1) {
                OptLabel(stringResource(R.string.optimise_comes_in_label))
                RankedOptions(
                    label = if (leaving != null) {
                        stringResource(R.string.optimise_replacement_description, leaving.exerciseName)
                    } else {
                        stringResource(R.string.optimise_replacement_it_description)
                    },
                    names = change.options.map { it.label },
                    reasons = change.options.map { it.reason },
                    lines = change.options.map { opt -> swapLine(opt) },
                    chosen = chosen,
                    onChoose = { onPicks(picks.with(change, picks.keepOf(change), it)) },
                )
            } else {
                OptLabel(stringResource(R.string.optimise_what_changes_label))
                Text(change.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = IronvellumColors.Ink)
            }
            Text(change.detail, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted, modifier = Modifier.padding(top = 8.dp))
            OptLabel(stringResource(R.string.optimise_effect_label))
            ImpactList(rows)
            Spacer(Modifier.height(20.dp))
        }
        // Skip and Keep sit side by side in the dock; Keep is the primary action.
        val skipDescription = stringResource(R.string.optimise_skip_description)
        val keepDescription = stringResource(R.string.optimise_keep_description)
        Row(
            Modifier
                .fillMaxWidth()
                .background(IronvellumColors.VaultHigh)
                .drawBehind { drawRect(IronvellumColors.Rune, size = Size(size.width, 1.dp.toPx())) }
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IronvellumButton(
                label = stringResource(R.string.optimise_skip_button),
                onClick = {
                    onPicks(picks.with(change, false, chosen))
                    back()
                },
                quiet = true,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = skipDescription },
            )
            IronvellumButton(
                label = stringResource(R.string.optimise_keep_button),
                onClick = {
                    onPicks(picks.with(change, true, chosen))
                    back()
                },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = keepDescription },
            )
        }
    }
}

/** The movement a change takes out, as it stands now. */
private fun leavingEntry(change: OptChange, week: List<PlannedPreset>): PlannedEntry? {
    if (change.kind != OptKind.SWAP) return null
    val edit = change.options.first().edits.firstOrNull() ?: return null
    val from = (edit.op as? ChangeOp.SwapEntry)?.fromName ?: return null
    return week.getOrNull(edit.presetIndex)?.entries?.firstOrNull { it.exerciseName.equals(from, ignoreCase = true) }
}

/** What a swap option would be prescribed: "3 hard sets · ~10–15 reps · 12 kg". */
private fun swapLine(option: OptOption): String? {
    val to = (option.edits.firstOrNull()?.op as? ChangeOp.SwapEntry)?.to ?: return null
    return prescriptionLine(to, to.targetWeightKg?.let { Units.withUnit(it) })
}

// ----------------------------------------------------------------------------------------------- overlap

/** Two movements doing one job: both, the ranked replacements for the weaker, Keep or Skip, and the effect. */
@Composable
private fun OverlapPage(
    report: OptimiseReport,
    rite: OverlapRite,
    picks: Map<String, Int>,
    onPicks: (Map<String, Int>) -> Unit,
    saving: Boolean,
    error: String?,
    onApply: () -> Unit,
    onBack: () -> Unit,
) {
    val week = report.input.week
    val kept = picks.kept(rite.changes)
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(20.dp))
            ForgeHeader(
                stringResource(R.string.optimise_overlap_title, rite.rite),
                onBack,
                subtitle = UiText.plural(R.plurals.optimise_overlaps_found, rite.changes.size).asString(),
            )
            error?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.DangerRed, modifier = Modifier.padding(top = 8.dp)) }
            rite.changes.forEachIndexed { index, change ->
                val chosen = picks.optionOf(change)
                val option = change.options[chosen]
                val rows = remember(report, change, chosen) { impactRows(week, resolveWeek(week, listOf(option)), null) }
                val from = change.title.substringBefore(" → ")
                OptLabel(stringResource(R.string.optimise_overlap_pair_label))
                InkPanel(Modifier.fillMaxWidth()) {
                    change.movements.forEachIndexed { i, (name, muscles) ->
                        if (i > 0) Spacer(Modifier.height(8.dp))
                        Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = IronvellumColors.Ink)
                        if (muscles.isNotBlank()) Text(muscles, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
                    }
                }
                Text(change.detail, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted, modifier = Modifier.padding(top = 8.dp))
                OptLabel(stringResource(R.string.optimise_swap_label, from))
                if (change.options.size > 1 || option.edits.none { it.op is ChangeOp.RemoveEntry }) {
                    RankedOptions(
                        label = stringResource(R.string.optimise_replacement_description, from),
                        names = change.options.map { it.label },
                        reasons = change.options.map { it.reason },
                        lines = change.options.map { swapLine(it) },
                        chosen = chosen,
                        onChoose = { onPicks(picks.with(change, picks.keepOf(change), it)) },
                    )
                } else {
                    OptNote(UiText.themed(R.string.optimise_overlap_no_pair, R.string.optimise_overlap_no_pair__t, from).asString())
                }
                Spacer(Modifier.height(8.dp))
                InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                    KeepSkip(index + 1, picks.keepOf(change), { onPicks(picks.with(change, it, chosen)) })
                }
                OptLabel(stringResource(R.string.optimise_effect_with_label, option.label))
                ImpactList(rows)
            }
            Spacer(Modifier.height(20.dp))
        }
        ForgeDock(
            primary = applyLabel(kept.size),
            onPrimary = onApply,
            enabled = kept.isNotEmpty(),
            busy = saving,
            note = stringResource(R.string.optimise_apply_note_short),
        )
    }
}

// ----------------------------------------------------------------------------------------------- applied

/** What was written: the changes, how many were skipped, and Undo for the rites it touched. */
@Composable
private fun AppliedPage(applied: Applied, saving: Boolean, error: String?, onUndo: () -> Unit, onDone: () -> Unit) {
    // The header's BACK is Done; system back is too.
    BackHandler(onBack = onDone)
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(20.dp))
            ForgeHeader(applied.title, onBack = onDone, status = stringResource(R.string.optimise_applied_status))
            val count = applied.lines.size
            Text(
                UiText.plural(R.plurals.optimise_applied_count, count).asString(),
                style = MaterialTheme.typography.titleMedium,
                color = IronvellumColors.Ink,
                modifier = Modifier.padding(top = 16.dp).semantics { liveRegion = LiveRegionMode.Polite; heading() },
            )
            if (applied.skipped > 0) {
                Text(
                    stringResource(R.string.optimise_applied_skipped, applied.skipped),
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
            }
            error?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.DangerRed, modifier = Modifier.padding(top = 8.dp)) }
            Spacer(Modifier.height(12.dp))
            InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp)) {
                applied.lines.forEachIndexed { index, (kind, text) ->
                    if (index > 0) InkDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .semantics(mergeDescendants = true) { contentDescription = "$kind: $text" },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            kind.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = ChakraPetch,
                            color = IronvellumColors.SystemGreen,
                            modifier = Modifier.width(64.dp),
                        )
                        Text(text, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink, modifier = Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        ForgeDock(
            primary = stringResource(R.string.common_done),
            onPrimary = onDone,
            enabled = !saving,
            link = if (applied.riteNames.size == 1) {
                stringResource(R.string.optimise_applied_undo_named, applied.riteNames.single())
            } else {
                UiText.themed(R.string.optimise_applied_undo_all, R.string.optimise_applied_undo_all__t).asString()
            },
            onLink = onUndo,
        )
    }
}
