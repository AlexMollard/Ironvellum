package com.ironvellum.app.ui.program

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.data.Units
import com.ironvellum.app.domain.ChangeOp
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.ProgramRules
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.components.IronvellumDialog

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
    data class Detail(val parent: OptStep, val changeId: String) : OptStep

    fun encode(): String = when (this) {
        Hub -> "hub"
        is Gap -> "gap:${muscle.name}"
        is Where -> "where:${muscle.name}:$option"
        is Heavy -> "heavy:${muscle.name}"
        is Overlap -> "overlap:$rite"
        Spread -> "spread"
        is Detail -> "detail:${parent.encode()}$SEP$changeId"
    }

    /** Where Back goes. */
    fun parent(): OptStep = when (this) {
        Hub -> Hub
        is Gap, is Heavy, is Overlap, Spread -> Hub
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
    val error by viewModel.error.collectAsStateWithLifecycle()
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
                    onBack = onBack,
                )
            }

            else -> MessagePage(
                title = "Optimise rites",
                body = when (current) {
                    OptimiseState.NoRites -> "No rites to optimise yet. Forge a cycle first, then come back and tighten it."
                    OptimiseState.NeedsArmoury -> "Optimise ranks movements against your armoury. Choose it in the Forge, then come back."
                    else -> "Reading your rites…"
                },
                action = when (current) {
                    OptimiseState.NoRites, OptimiseState.NeedsArmoury -> "Forge a cycle" to onForgeCycle
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
    onBack: () -> Unit,
) {
    val input = report.input
    val week = input.week

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
                    status = "Heavy",
                    subtitle = if (finding.load.weekly > ProgramRules.HEAVY_WEEK) {
                        "${hardSetsLabel(finding.load.weekly)} a week"
                    } else {
                        "${hardSetsLabel(finding.load.maxSession)} in one rite"
                    },
                    report = report,
                    focus = finding.muscle,
                    intro = heavyReason(finding.load).first,
                    cite = heavyReason(finding.load).second.label,
                    changes = finding.changes,
                    picks = picks,
                    onPicks = onPicks,
                    saving = saving,
                    error = error,
                    emptyNote = "No change eases this without leaving another muscle short. The flag is a hint, so leaving it is fine.",
                    leave = "Leave it, I want the volume",
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
                    title = "Spread your days",
                    status = null,
                    subtitle = "Recovery of 48 hours or more",
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
                    onApply = { apply("Days", listOf(change)) },
                    onOpen = { onStep(OptStep.Detail(step, it.id)) },
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
            "Optimise rites",
            onBack,
            subtitle = if (report.nothing) "Nothing to change" else "${report.findings} ${if (report.findings == 1) "thing" else "things"} to look at",
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (report.nothing) {
                NothingCard()
            } else {
                OptNote("Suggestions change the rites you already have. Nothing is written until you apply it.")
            }
            if (report.gaps.isNotEmpty()) {
                OptLabel("Gaps")
                InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp)) {
                    report.gaps.forEachIndexed { index, muscle ->
                        if (index > 0) InkDivider()
                        val load = loads[muscle]
                        val state = report.tally.states.getValue(muscle)
                        FindingRow(
                            state = state,
                            title = muscle.label,
                            subline = "${hardSetsLabel(load?.weekly ?: 0.0)} · ${state.label}",
                            onClickLabel = "Add a movement for ${muscle.label.lowercase()}",
                            spoken = "Add a movement for ${muscle.label}: ${hardSetsLabel(load?.weekly ?: 0.0)}, ${state.label}",
                            onClick = { onStep(OptStep.Gap(muscle)) },
                        )
                    }
                }
            }
            if (report.heavy.isNotEmpty()) {
                OptLabel("Heavy")
                InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp)) {
                    report.heavy.forEachIndexed { index, finding ->
                        if (index > 0) InkDivider()
                        val weekly = finding.load.weekly > ProgramRules.HEAVY_WEEK
                        val sets = if (weekly) "${hardSetsLabel(finding.load.weekly)} a week" else "${hardSetsLabel(finding.load.maxSession)} in one rite"
                        FindingRow(
                            state = CoverageState.HEAVY,
                            title = finding.muscle.label,
                            subline = "$sets · Heavy · ${changesText(finding.changes.size)}",
                            onClickLabel = "Ease ${finding.muscle.label.lowercase()}",
                            spoken = "Ease ${finding.muscle.label}: $sets, Heavy, ${changesText(finding.changes.size)}",
                            onClick = { onStep(OptStep.Heavy(finding.muscle)) },
                        )
                    }
                }
            }
            if (report.overlaps.isNotEmpty() || report.spread != null) {
                OptLabel("Overlap")
                InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp)) {
                    var shown = 0
                    report.overlaps.forEach { rite ->
                        if (shown++ > 0) InkDivider()
                        val line = "${rite.changes.size} ${if (rite.changes.size == 1) "overlap" else "overlaps"} found"
                        FindingRow(
                            state = null,
                            title = rite.rite,
                            subline = "Two movements doing one job · $line",
                            onClickLabel = "Open the overlaps in ${rite.rite}",
                            spoken = "${rite.rite}: two movements doing one job, $line",
                            onClick = { onStep(OptStep.Overlap(rite.rite)) },
                        )
                    }
                    report.spread?.let { change ->
                        if (shown++ > 0) InkDivider()
                        FindingRow(
                            state = null,
                            title = "Spread your days",
                            subline = tightLine(report.tight.first()),
                            onClickLabel = "Open the day spacing",
                            spoken = "Spread your days: ${change.title}. ${tightLine(report.tight.first())}",
                            onClick = { onStep(OptStep.Spread) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

private fun changesText(n: Int): String = when (n) {
    0 -> "no safe change"
    1 -> "1 change"
    else -> "$n changes"
}

/** The Temper v2 "nothing to change" state: a plain sentence, and what is still short. */
@Composable
private fun NothingCard() {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(
            "Nothing to change",
            style = MaterialTheme.typography.titleMedium,
            color = IronvellumColors.Ink,
            modifier = Modifier.semantics { heading() },
        )
        OptNote("Your rites cover every muscle without running any heavy, and no two movements do one job.")
    }
}

// ------------------------------------------------------------------------------- a gap: movement, then rite

/** "Add a movement for rear delts": the three ranked movements, best match first, in the app's bottom sheet. */
@Composable
private fun GapSheet(report: OptimiseReport, muscle: Muscle, onStep: (OptStep) -> Unit) {
    val ranked = report.gapOptions[muscle]?.options.orEmpty()
    val load = report.loads[muscle]
    val state = report.tally.states[muscle] ?: CoverageState.NOT_TRAINED
    var chosen by rememberSaveable(muscle.name) { mutableStateOf(0) }
    val input = report.input
    val picked = ranked.getOrNull(chosen.coerceAtMost((ranked.size - 1).coerceAtLeast(0)))
    IronvellumDialog(
        onDismissRequest = { onStep(OptStep.Hub) },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CoverageMark(state, size = 20.dp)
                Column(Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "Add a movement for ${muscle.label.lowercase()}. ${state.label}, ${hardSetsLabel(load?.weekly ?: 0.0)}"
                }) {
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
                        "Nothing in your cycle trains ${muscle.label.lowercase()} directly. ${ProgramRules.COVERED_MIN.toInt()} or more hard sets a week gets it covered."
                    } else {
                        "${ProgramRules.COVERED_MIN.toInt()} or more hard sets a week gets ${muscle.label.lowercase()} covered."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
                if (ranked.isEmpty()) {
                    OptNote("Nothing in your armoury trains it. Another armoury, or a rite forged for it, would.")
                } else {
                    Text(
                        "Fits your armoury · ranked best first",
                        style = MaterialTheme.typography.labelMedium,
                        color = IronvellumColors.InkMuted,
                        modifier = Modifier.semantics { heading() },
                    )
                    RankedOptions(
                        label = "Movement to add for ${muscle.label.lowercase()}, ranked best to worst",
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
                label = if (picked != null) "Add ${picked.exercise.name}" else "Add",
                onClick = { onStep(OptStep.Where(muscle, chosen.coerceAtLeast(0))) },
                enabled = picked != null,
                modifier = Modifier.heightIn(min = 48.dp),
            )
        },
        dismissButton = { IronvellumButton(label = "Cancel", onClick = { onStep(OptStep.Hub) }, quiet = true, modifier = Modifier.heightIn(min = 48.dp)) },
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
    var riteIndex by rememberSaveable(step.encode()) { mutableStateOf(choices.firstOrNull()?.index ?: 0) }
    var sets by rememberSaveable(step.encode()) { mutableStateOf(base.sets) }
    val entry = base.copy(sets = sets.coerceIn(SET_SANITY))
    val after = remember(report, riteIndex, entry) { withAdded(week, riteIndex, entry) }
    val rite = week.getOrNull(riteIndex)
    val choice = choices.firstOrNull { it.index == riteIndex }
    val minutes = after.getOrNull(riteIndex)?.let { ProgramRules.dayMinutes(it, input.request.focus) }
    val target = rite?.let { it.scheduledDay?.let { day -> dayLabel(day) } ?: it.name } ?: ""
    val rows = remember(report, riteIndex, entry) { impactRows(week, after, step.muscle) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(20.dp))
            ForgeHeader(
                "Add ${candidate.exercise.name}",
                onBack = { onStep(OptStep.Gap(step.muscle)) },
                subtitle = "${step.muscle.label} · ${hardSetsText(base.sets)} suggested",
            )
            error?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.DangerRed, modifier = Modifier.padding(top = 8.dp)) }
            OptLabel("Put it on")
            InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                Column(Modifier.fillMaxWidth().selectableGroup().semantics { contentDescription = "Put ${candidate.exercise.name} on" }) {
                    choices.forEachIndexed { index, c ->
                        if (index > 0) InkDivider()
                        RiteChoiceRow(c, selected = c.index == riteIndex, onSelect = { riteIndex = c.index })
                    }
                }
            }
            OptLabel("Hard sets")
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepperButton("−", "Fewer hard sets for ${candidate.exercise.name}", enabled = sets > SET_SANITY.first) { sets-- }
                Text(
                    "${entry.sets}",
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = IronvellumColors.Ink,
                    modifier = Modifier.width(48.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                StepperButton("+", "More hard sets for ${candidate.exercise.name}", enabled = sets < SET_SANITY.last) { sets++ }
            }
            Text(suggestedSets(entry), style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
            OptLabel("Effect on your week")
            ImpactList(rows)
            if (rite != null && minutes != null) {
                Text(
                    "$target becomes ${movementsText(rite.entries.size + 1)} · ~$minutes min.",
                    style = MaterialTheme.typography.labelSmall,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
        }
        ForgeDock(
            primary = "Add to $target",
            onPrimary = {
                val index = riteIndex
                onApply(
                    Applied(
                        title = "Added ${candidate.exercise.name} to ${rite?.name.orEmpty()}",
                        lines = listOf("Add" to "${candidate.exercise.name}, ${hardSetsText(entry.sets)}"),
                        skipped = 0,
                        before = listOf(input.ids[index] to week[index]),
                        riteNames = listOf(week[index].name),
                    ),
                    listOf(input.ids[index] to after[index]),
                )
            },
            enabled = rite != null && choice != null,
            busy = saving,
            note = "A suggestion only. Move or remove it later.",
        )
    }
}

@Composable
private fun RiteChoiceRow(choice: RiteChoice, selected: Boolean, onSelect: () -> Unit) {
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
                stateDescription = if (selected) "Chosen" else "Not chosen"
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
                OptLabel("What it fixes")
                ImpactList(rows, emptyLine = "Keep a change to see what it does.")
                OptLabel("Changes")
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
            primary = if (kept.isEmpty()) "Apply" else "Apply ${changeCount(kept.size)}",
            onPrimary = onApply,
            enabled = kept.isNotEmpty(),
            busy = saving,
            note = "Nothing is written until you apply. Skipped changes stay as they are.",
            link = leave,
            onLink = onBack,
        )
    }
}

private fun changeCount(n: Int): String = "$n ${if (n == 1) "change" else "changes"}"

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
                subtitle = listOfNotNull(change.rite, "change $number of ${siblings.size}").joinToString(" · "),
            )
            if (leaving != null) {
                OptLabel("Leaves")
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
                OptLabel("Comes in · ranked best first")
                RankedOptions(
                    label = "Replacement for ${leaving?.exerciseName ?: "it"}, ranked best to worst",
                    names = change.options.map { it.label },
                    reasons = change.options.map { it.reason },
                    lines = change.options.map { opt -> swapLine(opt) },
                    chosen = chosen,
                    onChoose = { onPicks(picks.with(change, picks.keepOf(change), it)) },
                )
            } else {
                OptLabel("What changes")
                Text(change.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = IronvellumColors.Ink)
            }
            Text(change.detail, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted, modifier = Modifier.padding(top = 8.dp))
            OptLabel("Effect on your week")
            ImpactList(rows)
            Spacer(Modifier.height(20.dp))
        }
        // Skip and Keep sit side by side in the dock; Keep is the primary action.
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
                label = "Skip",
                onClick = {
                    onPicks(picks.with(change, false, chosen))
                    back()
                },
                quiet = true,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = "Skip this change" },
            )
            IronvellumButton(
                label = "Keep",
                onClick = {
                    onPicks(picks.with(change, true, chosen))
                    back()
                },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = "Keep this change" },
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
                "Optimise ${rite.rite}",
                onBack,
                subtitle = "${rite.changes.size} ${if (rite.changes.size == 1) "overlap" else "overlaps"} found",
            )
            error?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.DangerRed, modifier = Modifier.padding(top = 8.dp)) }
            rite.changes.forEachIndexed { index, change ->
                val chosen = picks.optionOf(change)
                val option = change.options[chosen]
                val rows = remember(report, change, chosen) { impactRows(week, resolveWeek(week, listOf(option)), null) }
                val from = change.title.substringBefore(" → ")
                OptLabel("Two movements, one job")
                InkPanel(Modifier.fillMaxWidth()) {
                    change.movements.forEachIndexed { i, (name, muscles) ->
                        if (i > 0) Spacer(Modifier.height(8.dp))
                        Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = IronvellumColors.Ink)
                        if (muscles.isNotBlank()) Text(muscles, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
                    }
                }
                Text(change.detail, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted, modifier = Modifier.padding(top = 8.dp))
                OptLabel("Swap $from for · ranked best first")
                if (change.options.size > 1 || !option.label.startsWith("Remove")) {
                    RankedOptions(
                        label = "Replacement for $from, ranked best to worst",
                        names = change.options.map { it.label },
                        reasons = change.options.map { it.reason },
                        lines = change.options.map { swapLine(it) },
                        chosen = chosen,
                        onChoose = { onPicks(picks.with(change, picks.keepOf(change), it)) },
                    )
                } else {
                    OptNote("Nothing in your armoury makes a better pair, so the change removes $from.")
                }
                Spacer(Modifier.height(8.dp))
                InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                    KeepSkip(index + 1, picks.keepOf(change), { onPicks(picks.with(change, it, chosen)) })
                }
                OptLabel("Effect with ${option.label}")
                ImpactList(rows)
            }
            Spacer(Modifier.height(20.dp))
        }
        ForgeDock(
            primary = if (kept.isEmpty()) "Apply" else "Apply ${changeCount(kept.size)}",
            onPrimary = onApply,
            enabled = kept.isNotEmpty(),
            busy = saving,
            note = "Nothing is written until you apply.",
        )
    }
}

// ----------------------------------------------------------------------------------------------- applied

/** What was written: the changes, how many were skipped, and Undo for the rites it touched. */
@Composable
private fun AppliedPage(applied: Applied, saving: Boolean, error: String?, onUndo: () -> Unit, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(20.dp))
            ForgeHeader(applied.title, onBack = onDone, status = "Applied")
            val count = applied.lines.size
            Text(
                "$count ${if (count == 1) "change" else "changes"} applied",
                style = MaterialTheme.typography.titleMedium,
                color = IronvellumColors.Ink,
                modifier = Modifier.padding(top = 16.dp).semantics { liveRegion = LiveRegionMode.Polite; heading() },
            )
            if (applied.skipped > 0) {
                Text(
                    "${applied.skipped} skipped, left as it was.",
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
            primary = "Done",
            onPrimary = onDone,
            enabled = !saving,
            link = if (applied.riteNames.size == 1) "Undo, put ${applied.riteNames.single()} back" else "Undo, put the rites back",
            onLink = onUndo,
        )
    }
}
