package com.ironvellum.app.ui.program

import androidx.annotation.StringRes
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
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.R
import com.ironvellum.app.data.DismissedHeavyStore
import com.ironvellum.app.data.Repository
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.MuscleArea
import com.ironvellum.app.domain.MuscleMap
import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.ProgramRules
import com.ironvellum.app.domain.TrainingFocus
import com.ironvellum.app.domain.TrainingMode
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.domain.VolumeLevel
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.InkTabbedPager
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.ListRow
import com.ironvellum.app.ui.components.ListRowHeight
import com.ironvellum.app.ui.components.NavChip
import com.ironvellum.app.ui.components.PushedHeader
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.DotShape
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Weekly muscle coverage on one map: this Monday-to-Sunday week, what is done (hard sets already logged) and what
 * the plan's remaining days still hold. The sets are counted by [HardSets] (logged) and [ProgramRules.coverage]
 * (planned), one rule each, combined in [WeekCoverage].
 */

data class CoverageUi(
    val week: WeekCoverage = WeekCoverage(),
    /** The plan's remaining days as presets: what the rest of the week trains. */
    val plannedPresets: List<PlannedPreset> = emptyList(),
    /** This week's hard sets so far, as one pseudo-rite, so a lit muscle can say what it was done with. */
    val donePresets: List<PlannedPreset> = emptyList(),
    /** Hard sets, done or planned, whose movement has no MuscleMap profile, so the map cannot count them. */
    val unattributedSets: Int = 0,
    val hasAnyPreset: Boolean = false,
    /** Saved rites with no day, which the week leaves out ([uncountedRoutine]): id and name. */
    val uncountedRites: List<Pair<Long, String>> = emptyList(),
)

/** Sets whose exercise has no MuscleMap profile, so the heat map cannot count them. */
internal fun unattributedSets(presets: List<PlannedPreset>): Int =
    presets.sumOf { p -> p.entries.sumOf { e -> if (MuscleMap.profile(e.exerciseName) == null) e.sets else 0 } }

/** What the page reads for a cycle that is not saved yet: the builder's proposal, with nothing done. */
internal fun previewCoverage(presets: List<PlannedPreset>): CoverageUi = CoverageUi(
    week = plannedOnlyWeek(presets),
    plannedPresets = presets,
    unattributedSets = unattributedSets(presets),
    hasAnyPreset = presets.isNotEmpty(),
)

/**
 * Heavy flags the lifter has dismissed, kept per muscle on the device ([DismissedHeavyStore]). A dismissed muscle
 * reads as plain covered, which it still is.
 */
@Composable
internal fun rememberDismissedHeavy(): MutableState<Set<Muscle>> {
    val context = LocalContext.current
    val store = remember { DismissedHeavyStore(context.applicationContext) }
    val state = remember { mutableStateOf(store.muscles) }
    return remember {
        object : MutableState<Set<Muscle>> by state {
            override var value: Set<Muscle>
                get() = state.value
                set(next) {
                    state.value = next
                    store.muscles = next
                }
        }
    }
}

class MuscleCoverageViewModel(
    private val repo: Repository,
    /** The clock the week is cut from; a test pins it to a Monday so the plan's whole week is still to come. */
    private val nowMs: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    val ui: StateFlow<CoverageUi> = combine(
        repo.observePresets(),
        repo.observeHistory(),
        repo.observeExercises(),
    ) { presets, history, exercises ->
        val now = nowMs()
        val zone = ZoneId.systemDefault()
        val byId = exercises.associateBy { it.id }
        val (from, to) = weekBounds(now, zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).dayOfWeek.value
        val remaining = remainingRites(presets, history, today, from)
        val done = doneInWeek(history, byId, from, to)
        val doneRite = doneRite(history, byId, from, to)
        CoverageUi(
            week = weekCoverage(done.sets, done.maxSession, remaining),
            plannedPresets = remaining,
            donePresets = doneRite,
            unattributedSets = unattributedSets(remaining) + unattributedSets(doneRite),
            hasAnyPreset = presets.isNotEmpty(),
            uncountedRites = uncountedRoutine(presets) { it.scheduledDay }.map { it.id to it.name },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CoverageUi())
}

/** The coverage content on its own page: a pushed header over [MuscleCoverageContent]. */
@Composable
fun MuscleCoverageScreen(
    onBack: () -> Unit,
    onGenerateSession: () -> Unit,
    onOpenRite: (Long) -> Unit = {},
    onOpenRites: () -> Unit = {},
    onOptimise: () -> Unit = {},
    viewModel: MuscleCoverageViewModel =
        viewModel(factory = viewModelFactory {
            initializer { MuscleCoverageViewModel(ironvellumRepository()) }
        }),
) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(20.dp))
            PushedHeader(stringResource(R.string.coverage_screen_title), onBack)
            Spacer(Modifier.height(12.dp))
        }
        MuscleCoverageContent(onGenerateSession, onOpenRite, onOpenRites, onOptimise, viewModel, Modifier.weight(1f))
    }
}

/**
 * Coverage with no screen chrome: the one week map, its rows and the dock. Muscles hosts it under its
 * Fatigue | Coverage tab row, which is the screen's only one.
 */
@Composable
internal fun MuscleCoverageContent(
    onGenerateSession: () -> Unit,
    onOpenRite: (Long) -> Unit = {},
    onOpenRites: () -> Unit = {},
    onOptimise: () -> Unit = {},
    viewModel: MuscleCoverageViewModel =
        viewModel(factory = viewModelFactory {
            initializer { MuscleCoverageViewModel(ironvellumRepository()) }
        }),
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var openExercise by rememberSaveable { mutableStateOf<String?>(null) }
    val dismissed = rememberDismissedHeavy()
    val lit = rememberSaveable(key = "lit-week") { mutableStateOf<Muscle?>(null) }
    // The dock reads the same week the page does, so it is worked out here and not inside the page.
    val tally = remember(ui.week, dismissed.value) { ui.week.tally(dismissed.value) }
    val empty = !ui.week.hasAnything()

    Column(modifier.fillMaxSize()) {
        CoveragePage(
            ui = ui,
            lit = lit,
            openExercise = openExercise,
            onOpenExercise = { openExercise = it },
            dismissed = dismissed.value,
            onDismiss = { dismissed.value = dismissed.value + it },
            onOpenUncounted = {
                // One rite goes straight to it; several go to the list that holds them all.
                ui.uncountedRites.singleOrNull()?.let { (id, _) -> onOpenRite(id) } ?: onOpenRites()
            },
            modifier = Modifier.weight(1f),
        )
        val actions = dockActions(tally, planned = true, hasRites = ui.hasAnyPreset)
        if (!empty && actions.any) {
            CoverageDock(
                headline = coverageHeadline(tally),
                onForge = onGenerateSession.takeIf { actions.forge },
                onOptimise = onOptimise.takeIf { actions.optimise },
            )
        }
    }
}

/**
 * Coverage of the cycle the builder is proposing, before it is saved: the page's own content ([CoveragePage]) fed
 * the proposal with nothing done, so it reads Planned, Short and Heavy. No Forge dock: the lifter is already forging.
 */
@Composable
internal fun CoveragePreviewScreen(
    presets: List<PlannedPreset>,
    onBack: () -> Unit,
) {
    val ui = remember(presets) { previewCoverage(presets) }
    val dismissed = rememberDismissedHeavy()
    val tally = remember(ui, dismissed.value) { ui.week.tally(dismissed.value) }
    val lit = rememberSaveable(key = "lit-preview") { mutableStateOf<Muscle?>(null) }
    var openExercise by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(20.dp))
            PushedHeader(UiText.themed(R.string.coverage_preview_title, R.string.coverage_preview_title__t).asString(), onBack)
            Spacer(Modifier.height(8.dp))
            Text(
                coverageHeadline(tally),
                style = MaterialTheme.typography.titleMedium,
                color = IronvellumColors.Ink,
            )
            Text(
                stringResource(R.string.coverage_hint_caption),
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
            Spacer(Modifier.height(8.dp))
        }
        Box(Modifier.weight(1f)) {
            CoveragePage(
                ui = ui,
                lit = lit,
                openExercise = openExercise,
                onOpenExercise = { openExercise = it },
                dismissed = dismissed.value,
                onDismiss = { dismissed.value = dismissed.value + it },
            )
        }
    }
}

/**
 * What the page is short of, and the ways to act on it: forge a rite that fills a gap, or optimise the rites
 * already there. With both, Forge is the one filled button (the page's primary action) and Optimise sits beside
 * it as a quiet one; each shows only when it has something to do.
 */
@Composable
private fun CoverageDock(headline: String, onForge: (() -> Unit)?, onOptimise: (() -> Unit)?) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(IronvellumColors.VaultHigh)
            .drawBehind { drawRect(IronvellumColors.Rune, size = Size(size.width, 1.dp.toPx())) }
            .navigationBarsPadding()
            .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text(
                headline,
                style = MaterialTheme.typography.titleMedium,
                color = IronvellumColors.Ink,
            )
            Text(
                stringResource(R.string.coverage_hint_caption),
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            if (onOptimise != null) {
                IronvellumButton(
                    label = UiText.themed(R.string.coverage_dock_optimise_button, R.string.coverage_dock_optimise_button__t).asString(),
                    onClick = onOptimise,
                    quiet = onForge != null,
                    modifier = Modifier.weight(1f),
                )
            }
            if (onForge != null) {
                IronvellumButton(
                    label = UiText.themed(R.string.coverage_dock_forge_button, R.string.coverage_dock_forge_button__t).asString(),
                    onClick = onForge,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The week's page: its own scroll, the one map, the rows that need a look and the folded covered ones. */
@Composable
internal fun CoveragePage(
    ui: CoverageUi,
    lit: MutableState<Muscle?>,
    openExercise: String?,
    onOpenExercise: (String?) -> Unit,
    dismissed: Set<Muscle>,
    onDismiss: (Muscle) -> Unit,
    onOpenUncounted: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val week = ui.week
    val states = remember(week, dismissed) { week.states(dismissed) }
    val tally = remember(week, dismissed) { week.tally(dismissed) }
    val presets = remember(ui) { ui.plannedPresets + ui.donePresets }
    // Each exercise once, planned then done, keyed with its modifiers because
    // a deficit push-up works the chest differently from a flat one.
    val exercisesInView = remember(presets) {
        presets.flatMap { it.entries }
            .map { it.exerciseName to it.modifiers }
            .distinct()
            .mapNotNull { (name, modifiers) -> MuscleMap.profile(name, modifiers)?.let { Triple(name, modifiers, it.muscles) } }
    }
    val empty = !week.hasAnything()
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var coveredOpen by rememberSaveable { mutableStateOf(false) }
    var byExerciseOpen by rememberSaveable { mutableStateOf(false) }
    var whyOpen by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp),
    ) {
        if (empty) {
            Text(
                UiText.themed(R.string.coverage_empty_cycle, R.string.coverage_empty_cycle__t).asString(),
                style = MaterialTheme.typography.bodyMedium,
                color = IronvellumColors.InkMuted,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(20.dp))
            return@Column
        }

        CoverageMap(
            week = week,
            dismissed = dismissed,
            selection = lit,
            unattributed = ui.unattributedSets,
        )
        Text(
            stringResource(R.string.coverage_week_caption),
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        if (ui.uncountedRites.isNotEmpty()) {
            UncountedRitesLine(ui.uncountedRites.size, onOpenUncounted)
        }
        Spacer(Modifier.height(12.dp))

        // The one place a lit muscle narrows anything: the list gives way to the rites and exercises that
        // train it, and clearing brings it back.
        val litMuscle = lit.value
        if (litMuscle != null) {
            if (states[litMuscle] == WeekState.HEAVY) {
                HeavyNote(litMuscle, week.of(litMuscle).load) { onDismiss(litMuscle) }
            }
            MuscleFilter(
                muscle = litMuscle,
                rites = remember(presets, litMuscle) { ritesTraining(presets, litMuscle) },
                byRite = true,
                onClear = { lit.value = null },
            )
        } else {
            // A row does what tapping the muscle on the figure does, so the page scrolls up to show it lit.
            val pick = { muscle: Muscle ->
                lit.value = muscle
                scope.launch { scroll.animateScrollTo(0) }
                Unit
            }
            // The muscles that need a look come first (heavy, short, on track); the done ones fold away.
            val rows = remember(week, states) { weekRows(week, states) }
            InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp)) {
                rows.needing.forEachIndexed { index, muscle ->
                    if (index > 0) InkDivider()
                    WeekRow(muscle, week.of(muscle), states.getValue(muscle)) { pick(muscle) }
                }
                if (rows.needing.isEmpty()) {
                    Text(
                        stringResource(R.string.coverage_covered_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = IronvellumColors.Ink,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
                if (rows.covered.isNotEmpty()) {
                    if (rows.needing.isNotEmpty()) InkDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = ListRowHeight)
                            .clickable(
                                role = Role.Button,
                                onClickLabel = stringResource(
                                    if (coveredOpen) R.string.coverage_covered_hide_hint else R.string.coverage_covered_show_hint,
                                ),
                                onClick = { coveredOpen = !coveredOpen },
                            )
                            .semantics { stateDescription = Words.text(UiText.res(if (coveredOpen) R.string.a11y_state_expanded else R.string.a11y_state_collapsed)) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        WeekSwatch(WeekState.DONE, size = 20.dp)
                        Text(
                            UiText.plural(R.plurals.coverage_week_covered_count, rows.covered.size).asString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = IronvellumColors.Ink,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (coveredOpen) "⌃" else "›",
                            style = MaterialTheme.typography.titleMedium,
                            color = IronvellumColors.InkMuted,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                    }
                    if (coveredOpen) {
                        rows.covered.forEach { muscle ->
                            InkDivider()
                            WeekRow(muscle, week.of(muscle), WeekState.DONE) { pick(muscle) }
                        }
                    }
                }
            }

            if (exercisesInView.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                InkDivider()
                ListRow(
                    label = stringResource(R.string.coverage_by_exercise_label),
                    value = if (byExerciseOpen) stringResource(R.string.coverage_by_exercise_hide_value) else null,
                    onClickLabel = stringResource(
                        if (byExerciseOpen) R.string.coverage_by_exercise_hide_hint else R.string.coverage_by_exercise_show_hint,
                    ),
                    onClick = { byExerciseOpen = !byExerciseOpen },
                )
                InkDivider()
                if (byExerciseOpen) {
                    Spacer(Modifier.height(8.dp))
                    InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp)) {
                        exercisesInView.forEachIndexed { index, (name, modifiers, shares) ->
                            if (index > 0) InkDivider()
                            val key = "$name|$modifiers"
                            ExerciseRow(
                                name = name,
                                modifiers = modifiers,
                                shares = shares,
                                open = openExercise == key,
                                onToggle = { onOpenExercise(if (openExercise == key) null else key) },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Disclosure(stringResource(R.string.coverage_why_title), whyOpen) { whyOpen = !whyOpen }
            if (whyOpen) {
                CoverageExplainer(
                    tally = tally,
                    onSeeMuscles = {
                        whyOpen = false
                        scope.launch { scroll.animateScrollTo(0) }
                    },
                    onDone = { whyOpen = false },
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

/**
 * A quiet line for rites the page cannot count because they have no day. It is a link: one rite opens that
 * rite, several open the list. The words carry the meaning; nothing about it is a warning colour.
 */
@Composable
private fun UncountedRitesLine(count: Int, onOpen: () -> Unit) {
    val line = uncountedRitesLine(count)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(
                role = Role.Button,
                onClickLabel = UiText.themedPlural(R.plurals.coverage_uncounted_open_hint, R.plurals.coverage_uncounted_open_hint__t, count).asString(),
                onClick = onOpen,
            )
            .semantics(mergeDescendants = true) { contentDescription = line },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            line,
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.weight(1f),
        )
        Text(
            "›",
            style = MaterialTheme.typography.titleMedium,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/** One exercise's credit to a muscle: its name, MAIN or ASSIST, and sets times share. */
@Composable
private fun CreditRow(credit: ProgramRules.MuscleCredit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                credit.exerciseName,
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (credit.modifiers.isNotBlank()) {
                Text(
                    credit.modifiers.split(",").joinToString(" · ") { it.trim() },
                    style = MaterialTheme.typography.labelSmall,
                    color = IronvellumColors.InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            shareLevel(credit.share).label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = if (shareLevel(credit.share) == ShareLevel.MAIN) IronvellumColors.Ink else IronvellumColors.InkMuted,
            letterSpacing = IronvellumTracking.InlineLabel,
        )
        Text(
            "${credit.sets} × ${trimSets(credit.share)} = ${trimSets(credit.credited)}",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            textAlign = TextAlign.End,
            modifier = Modifier.width(96.dp),
        )
    }
}

/**
 * What a lit muscle narrows the page to: every rite that trains it with its sets, and under each the
 * exercises behind that number. The LAST 7 DAYS page has no rites (its week is one pooled list), so there
 * [byRite] is false and the exercises stand alone.
 */
@Composable
private fun MuscleFilter(
    muscle: Muscle,
    rites: List<RiteContribution>,
    byRite: Boolean,
    onClear: () -> Unit,
) {
    // The way back sits beside the heading, in reach the moment the page narrows, not past a long list.
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        SectionHeader(stringResource(R.string.coverage_filter_title, muscle.label), Modifier.weight(1f))
        NavChip(stringResource(R.string.common_clear).uppercase(), Icons.Filled.Close, onClick = onClear, modifier = Modifier.padding(bottom = 4.dp))
    }
    if (rites.isEmpty()) {
        Text(
            stringResource(R.string.coverage_filter_empty),
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
        )
    } else if (byRite) {
        rites.forEach { rite ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    rite.rite,
                    style = MaterialTheme.typography.bodyMedium,
                    color = IronvellumColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    riteSetsLabel(rite.credited),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.SystemGreen,
                    letterSpacing = IronvellumTracking.InlineLabel,
                )
            }
            Column(Modifier.fillMaxWidth().padding(start = 12.dp)) {
                rite.exercises.forEach { CreditRow(it) }
            }
        }
    } else {
        rites.flatMap { it.exercises }.sortedByDescending { it.credited }.forEach { CreditRow(it) }
    }
}

/**
 * One exercise in the view and its main muscles; tapped open, the figure and
 * the full MAIN / ASSIST lists from [ExerciseMuscles].
 */
@Composable
private fun ExerciseRow(
    name: String,
    modifiers: String,
    shares: Map<Muscle, Double>,
    open: Boolean,
    onToggle: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = ListRowHeight)
                .clickable(
                    onClickLabel = stringResource(
                        if (open) R.string.coverage_exercise_hide_hint else R.string.coverage_exercise_show_hint,
                        name,
                    ),
                    role = Role.Button,
                    onClick = onToggle,
                )
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = IronvellumColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (modifiers.isNotBlank()) {
                    Text(
                        modifiers.split(",").joinToString(" · ") { it.trim() },
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.InkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                musclesAt(shares, ShareLevel.MAIN).joinToString(" · ") { it.label },
                style = MaterialTheme.typography.labelMedium,
                color = IronvellumColors.InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = IronvellumColors.InkMuted,
                modifier = Modifier.size(20.dp),
            )
        }
        if (open) {
            ExerciseMuscles(shares, name, Modifier.fillMaxWidth().padding(bottom = 10.dp))
        }
    }
}

/** One decimal, trimmed of a trailing .0 - "14" and "14.5", never "14.0000". */
internal fun trimSets(value: Double): String {
    val rounded = (value * 10).toLong() / 10.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
}
