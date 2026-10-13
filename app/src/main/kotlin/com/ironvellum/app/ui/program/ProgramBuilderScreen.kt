package com.ironvellum.app.ui.program

import com.ironvellum.app.ui.components.PushedHeader
import androidx.compose.foundation.layout.width
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.ironvellum.app.ui.components.IronvellumDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.data.ProgramAnswers
import com.ironvellum.app.data.ProgramAnswersStore
import com.ironvellum.app.data.Repository
import com.ironvellum.app.domain.Equipment
import com.ironvellum.app.domain.Evidence
import com.ironvellum.app.domain.VolumeLevel
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.Improvement
import com.ironvellum.app.domain.MuscleArea
import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.ProgramGenerator
import com.ironvellum.app.domain.ProgramRequest
import com.ironvellum.app.domain.ProgramRules
import com.ironvellum.app.domain.ProgramTemplate
import com.ironvellum.app.domain.ProgramTemplates
import com.ironvellum.app.domain.RoutinePlan
import com.ironvellum.app.domain.SessionKind
import com.ironvellum.app.domain.Sex
import com.ironvellum.app.domain.StrengthProfile
import com.ironvellum.app.domain.TrainingFocus
import com.ironvellum.app.domain.TrainingMode
import com.ironvellum.app.domain.WorkoutPreset
import com.ironvellum.app.domain.TrainingSplit
import com.ironvellum.app.ui.components.DockedActionBar
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkSegmented
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.ListRow
import com.ironvellum.app.ui.components.ListRowHeight
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.settings.SettingsSummaries
import com.ironvellum.app.data.Units
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The one generator, surfaced from Train -> NEW PRESET: pick a hand-authored
 * template, generate a whole week, generate a single workout, or take an
 * existing preset and improve it with a before/after in hand. Modes arrive as
 * the [mode] route argument; the questions are shared and the actions differ.
 */
class ProgramBuilderViewModel(
    private val repo: Repository,
    val mode: String,
    private val presetId: Long?,
    private val appContext: Context,
) : ViewModel() {

    val catalogue: StateFlow<List<Exercise>> =
        repo.observeExercises().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val presets: StateFlow<List<WorkoutPreset>> =
        repo.observePresets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** False until Room's first emission, so "still loading" and "she has no rites" are not the same thing. */
    val presetsLoaded: StateFlow<Boolean> =
        repo.observePresets().map { true }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val sex: StateFlow<Sex> = repo.observeBodyProfile()
        .map { it.second }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Sex.MALE)

    /** The profile's own training mode, the default for the goal question. */
    val trainingMode: StateFlow<TrainingMode?> = repo.observeProfile()
        .map { it?.trainingMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _strength = MutableStateFlow(StrengthProfile())

    /** Her logged lifts, once read: a plan built before they arrive is rebuilt with them. */
    val strength: StateFlow<StrengthProfile> = _strength.asStateFlow()
    private val _firstSessionEpochDay = MutableStateFlow<Long?>(null)

    /** Whether any completed session exists - what the tier caption quotes. */
    val hasHistory = MutableStateFlow(false)

    /** Years of logged sessions, rounded to one decimal, for the tier caption. */
    val historyYears = MutableStateFlow<Double?>(null)

    // Answers. The screen renders them; the view model owns them so the
    // generated plan survives rotation the same way the editor's does.
    val focus = MutableStateFlow(TrainingFocus.GENERAL)
    val tier = MutableStateFlow(VolumeLevel.LOW)
    /** Null until the lifter has picked (or Settings / onboarding saved) an armoury; no cycle is built on a guess. */
    val equipment = MutableStateFlow<Equipment?>(null)
    val daysPerWeek = MutableStateFlow(4)
    val split = MutableStateFlow(TrainingSplit.UPPER_LOWER)
    val priorities = MutableStateFlow<Set<MuscleArea>>(emptySet())
    val compoundOnly = MutableStateFlow(false)
    val maxExercises = MutableStateFlow(ProgramRules.DEFAULT_MAX_EXERCISES)
    /** Minutes one rite may take: 45, 60, 75 or 90. Every generator call reads it. */
    val sessionMinutes = MutableStateFlow(ProgramRules.DEFAULT_SESSION_MINUTES)
    val sessionKind = MutableStateFlow(SessionKind.AUTO)
    val sessionDay = MutableStateFlow<Int?>(1)

    /** True once she has picked a day; the first-free-day default must not override her. */
    private var sessionDayTouched = false

    fun setSessionDay(day: Int?) {
        sessionDayTouched = true
        sessionDay.value = day
    }
    val selectedTemplateId = MutableStateFlow<String?>(null)
    val selectedPresetId = MutableStateFlow<Long?>(presetId)

    private val _plan = MutableStateFlow<RoutinePlan?>(null)
    val plan: StateFlow<RoutinePlan?> = _plan.asStateFlow()

    /** The proposal a failed generation cleared, kept only so the page can show it greyed out. Never applied. */
    private val _previousPlan = MutableStateFlow<RoutinePlan?>(null)
    val previousPlan: StateFlow<RoutinePlan?> = _previousPlan.asStateFlow()

    /** The week generator; a seam so a test can make it refuse. */
    internal var weekGenerator: (ProgramRequest, List<Exercise>, StrengthProfile) -> RoutinePlan = ProgramGenerator::week

    private val _improvement = MutableStateFlow<com.ironvellum.app.domain.Improvement?>(null)
    val improvement: StateFlow<com.ironvellum.app.domain.Improvement?> = _improvement.asStateFlow()

    /** The rite [improvement] was computed for: applying it to any other would write one rite's changes onto another. */
    private var improvementFor: Long? = null

    /** True while a save is in flight; a second tap is ignored and the buttons are disabled. */
    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    /** The answers the first proposal was built from; what leaving would lose is measured against it. */
    val baseline = MutableStateFlow<List<Any?>?>(null)

    /** The first proposal as generated, to tell her day edits from the plan she was given. */
    val generatedPlan = MutableStateFlow<RoutinePlan?>(null)

    /** Everything the last generation was built from; the same inputs again must not overwrite her edits. */
    private var lastKey: List<Any?>? = null

    /** Set when a generation or write is refused, or a write landed but its bookkeeping did not. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** True once she has picked a tier herself; the suggestion must not override her. */
    private var tierTouched = false

    /** Same guard for the goal question: the profile default must not overwrite her pick. */
    private var focusTouched = false

    fun setFocus(value: TrainingFocus) {
        focusTouched = true
        focus.value = value
    }

    /** Screen-visible read of the touch guard. */
    fun focusTouched(): Boolean = focusTouched

    init {
        // Her last generator answers come first: the Weekly Coverage bug was
        // exactly this screen guessing from history and the profile mode
        // while she had just answered STANDARD / MUSCLE. Applying a saved
        // answer counts as a deliberate choice, so the suggestVolume /
        // trainingMode fallbacks must not override it.
        val savedAnswers = ProgramAnswersStore.get(appContext)
        // Gear set in Settings counts even before the generator was ever run.
        ProgramAnswersStore.equipment.value?.let { equipment.value = it }
        savedAnswers?.let { saved ->
            if (!tierTouched) {
                tierTouched = true
                tier.value = saved.volume
            }
            if (!focusTouched) {
                focusTouched = true
                focus.value = saved.focus
            }
            equipment.value = saved.equipment
            daysPerWeek.value = saved.daysPerWeek
            split.value = saved.split
            priorities.value = saved.priorities
            compoundOnly.value = saved.compoundOnly
            maxExercises.value = saved.maxExercises
            sessionMinutes.value = saved.sessionMinutes
        }
        // Templates exist only for strength and hypertrophy; a saved or
        // default Skills / Mixed goal would match no template.
        if (mode == "template" && focus.value != TrainingFocus.STRENGTH && focus.value != TrainingFocus.MUSCLE) {
            focus.value = TrainingFocus.MUSCLE
        }
        viewModelScope.launch {
            runCatching {
                _firstSessionEpochDay.value = repo.firstSessionEpochDay()
                hasHistory.value = _firstSessionEpochDay.value != null
                historyYears.value = _firstSessionEpochDay.value?.let { first ->
                    ((LocalDate.now().toEpochDay() - first) / 365.25 * 10).toLong() / 10.0
                }
                _strength.value = repo.strengthProfile()
            }.onFailure { _error.value = "Could not read your Chronicle: ${it.message}" }
            // The suggestion lands once, from real history; after she touches
            // the control it is hers, and a later load cannot re-suggest.
            if (!tierTouched) {
                tier.value = ProgramRules.suggestVolume(
                    _firstSessionEpochDay.value,
                    LocalDate.now().toEpochDay(),
                )
            }
        }
        // One workout lands on the first weekday her routine leaves free,
        // not always Monday (unscheduled when every day is taken).
        if (mode == "session") {
            viewModelScope.launch {
                runCatching { repo.observePresets().first() }.onSuccess { existing ->
                    if (!sessionDayTouched) sessionDay.value = firstFreeWeekday(existing.mapNotNull { it.scheduledDay }.toSet())
                }
            }
        }
    }

    fun setTier(value: VolumeLevel) {
        tierTouched = true
        tier.value = value
    }

    fun togglePriority(area: MuscleArea) {
        val current = priorities.value
        priorities.value = when {
            area in current -> current - area
            // Prioritising everything prioritises nothing; the screen explains.
            current.size >= 3 -> current
            else -> current + area
        }
    }

    private fun request(): ProgramRequest = ProgramRequest(
        focus = focus.value,
        volume = tier.value,
        equipment = checkNotNull(equipment.value) { "armoury not picked" },
        daysPerWeek = daysPerWeek.value,
        priorities = priorities.value,
        sex = sex.value,
        split = split.value,
        compoundOnly = compoundOnly.value,
        maxExercises = maxExercises.value,
        sessionMinutes = sessionMinutes.value,
    )

    fun setSplit(value: TrainingSplit, days: Int) {
        split.value = value
        daysPerWeek.value = days
    }

    private fun planned(existing: List<WorkoutPreset>): List<PlannedPreset> = existing.map { it.toPlanned() }

    /** Read from the model so the settled template and rite picks are in it. */
    fun answersNow(): List<Any?> = listOf(
        focus.value, tier.value, equipment.value, daysPerWeek.value,
        split.value, priorities.value, compoundOnly.value,
        maxExercises.value, sessionKind.value, sessionDay.value,
        selectedTemplateId.value, selectedPresetId.value, sessionMinutes.value,
    )

    private class Generated(val plan: RoutinePlan?, val improvement: Improvement? = null, val improvementFor: Long? = null)

    /** Nothing can be shown, so nothing stale may be saved either. */
    private fun clearProposal() {
        _plan.value = null
        _improvement.value = null
        improvementFor = null
        lastKey = null
    }

    /**
     * Runs the generator for this screen's mode. Pure domain work on the
     * catalogue already in memory. It rebuilds only when an input actually
     * changed: the screen calls it on every recomposition trigger, including a
     * rotation, and the same inputs must not overwrite her hand edits. A
     * refusal lands in [error] and the board is cleared, so the save buttons
     * cannot write a plan built from answers she has since changed.
     */
    fun generate() {
        val cat = catalogue.value
        if (cat.isEmpty()) return clearProposal()
        // The screen asks for the armoury and shows no preview until it is set.
        val gear = equipment.value ?: return clearProposal()
        // A preset picker that is still loading is not "nothing to improve":
        // wait for Room's first emission instead of flashing an error.
        if (mode == "improve" && presets.value.isEmpty()) return clearProposal()
        // Picks are settled first, so the key below already holds them.
        if (mode == "template") {
            val templates = matchingTemplates()
            selectedTemplateId.value =
                (templates.firstOrNull { it.id == selectedTemplateId.value } ?: templates.firstOrNull())?.id
        }
        if (mode == "improve") {
            // No route argument and no pick yet: improve the first preset
            // rather than demanding a choice she can see below.
            val sel = selectedPresetId.value ?: presetId ?: presets.value.firstOrNull()?.id
            if (sel != selectedPresetId.value) selectedPresetId.value = sel
        }
        val key = listOf(
            answersNow(), cat, sex.value, _strength.value,
            // Only these modes read the saved rites; a week or pattern does not care that she saved one.
            if (mode == "session" || mode == "improve") presets.value else null,
        )
        if (key == lastKey) return
        lastKey = key
        runCatching {
            when (mode) {
                "template" -> {
                    val chosen = matchingTemplates().firstOrNull { it.id == selectedTemplateId.value }
                    Generated(
                        chosen?.let {
                            ProgramTemplates.build(
                                it, tier.value, gear, cat, _strength.value, compoundOnly.value, maxExercises.value, sessionMinutes.value,
                            )
                        },
                    )
                }
                "session" -> {
                    val week = planned(presets.value)
                    val preset = ProgramGenerator.session(
                        request(), sessionKind.value, sessionDay.value, week, cat, _strength.value,
                    )
                    // Null is a message for her, not a silent blank. For
                    // "what my week is missing" it means nothing is missing -
                    // unless compound & skill only is what emptied it, which
                    // the same request without the flag tells apart.
                    requireNotNull(preset) {
                        val isolationWouldFill = compoundOnly.value && ProgramGenerator.session(
                            request().copy(compoundOnly = false), sessionKind.value, sessionDay.value,
                            week, cat, _strength.value,
                        ) != null
                        when {
                            isolationWouldFill ->
                                "Only isolation work would fill the gaps. Turn off Compound & technique only, under More options, to add it."
                            sessionKind.value == SessionKind.AUTO ->
                                "Your cycle already reaches the target on every muscle. " +
                                    "Pick Full body, Upper or Lower for an extra rite."
                            else -> "The catalogue cannot fill this rite with your armoury."
                        }
                    }
                    Generated(RoutinePlan(listOf(preset)))
                }
                "improve" -> {
                    val all = planned(presets.value)
                    val ids = presets.value.map { it.id }
                    val sel = selectedPresetId.value
                    val index = ids.indexOf(sel)
                    val target = all.getOrNull(index)
                    if (target == null) {
                        // Thrown, not set: the fold below owns the error line,
                        // and a returned null would clear it.
                        throw IllegalArgumentException("Pick a rite to temper.")
                    } else {
                        val rest = all.filterIndexed { i, _ -> i != index }
                        Generated(
                            plan = null,
                            improvement = ProgramGenerator.improve(target, rest, request(), cat, _strength.value),
                            improvementFor = sel,
                        )
                    }
                }
                else -> Generated(weekGenerator(request(), cat, _strength.value))
            }
        }.fold(
            onSuccess = { result ->
                _error.value = null
                _previousPlan.value = null
                _plan.value = result.plan
                _improvement.value = result.improvement
                improvementFor = result.improvementFor
            },
            onFailure = {
                // Whatever was shown was built from answers that no longer stand. A copy stays for DISPLAY
                // only ([previousPlan]); nothing that applies a proposal reads it.
                _plan.value?.let { _previousPlan.value = it }
                _plan.value = null
                _improvement.value = null
                improvementFor = null
                _error.value = "The Forge failed: ${it.message}"
            },
        )
        generatedPlan.value = _plan.value
        if (baseline.value == null && (_plan.value != null || _improvement.value != null)) baseline.value = answersNow()
    }

    /** Try again after a refusal: the same answers, generated afresh. */
    fun retry() {
        lastKey = null
        generate()
    }

    /** Every hand-authored template for the goal, whatever its split: picking one picks the split. */
    fun matchingTemplates(): List<ProgramTemplate> =
        ProgramTemplates.ALL.filter { it.focus == focus.value }.sortedBy { it.days.size }

    /** Edits made on the previewed days. */
    fun replacePlan(plan: RoutinePlan) {
        _plan.value = plan
    }

    fun editEntry(presetIndex: Int, entryIndex: Int, transform: (PlannedEntry) -> PlannedEntry) {
        _plan.value?.withEntryEdited(presetIndex, entryIndex, transform)?.let(::replacePlan)
    }

    /** [exercise] standing in for the movement at the index: same sets, a fresh prescription for the new lift. */
    fun swapEntry(presetIndex: Int, entryIndex: Int, exercise: Exercise) {
        editEntry(presetIndex, entryIndex) { old -> newEntryFor(exercise, old.sets) }
    }

    /** [exercise] appended to the rite at [presetIndex]. */
    fun addEntry(presetIndex: Int, exercise: Exercise) {
        val plan = _plan.value ?: return
        val rite = plan.presets.getOrNull(presetIndex) ?: return
        val entries = rite.entries + newEntryFor(exercise, null)
        replacePlan(plan.copy(presets = plan.presets.toMutableList().also { it[presetIndex] = rite.copy(entries = entries) }))
    }

    private fun newEntryFor(exercise: Exercise, sets: Int?): PlannedEntry = newPlannedEntry(
        exercise, sets, tier.value, focus.value, equipment.value ?: Equipment.NOTHING, _strength.value,
    )

    fun removeEntry(presetIndex: Int, entryIndex: Int) {
        _plan.value?.withEntryRemoved(presetIndex, entryIndex)?.let(::replacePlan)
    }

    // Actions. Every one reports failure on screen; silence is the bug.

    /** Remembered whenever generator output is applied; Weekly Coverage and
     *  the next builder visit read the SAME volume and goal she answered with. */
    private fun rememberAnswers() {
        val gear = equipment.value ?: return
        ProgramAnswersStore.save(
            appContext,
            ProgramAnswers(
                tier.value, focus.value, gear, daysPerWeek.value, priorities.value, split.value,
                compoundOnly.value, maxExercises.value, sessionMinutes.value,
            ),
        )
    }

    /** True when saving would switch the profile's progression mode ([alignProgressionMode]). */
    fun switchesProgression(): Boolean =
        mode in setOf("week", "template") && progressionModeFor(focus.value) != null

    fun progressionLine(): String = when (focus.value) {
        TrainingFocus.STRENGTH -> "Strength goal: load goes up when you hit your reps."
        else -> "Muscle goal: reps go up first, then load."
    }

    /**
     * One save at a time: a second tap while one is in flight is ignored, never a second insert. [write] is
     * the database write alone (all or nothing), so its failure honestly says nothing was written; [after] is
     * the bookkeeping that follows a write that already landed, and its failure must not claim otherwise.
     */
    private fun save(
        failure: String,
        onDone: () -> Unit,
        after: suspend () -> Unit = {},
        write: suspend () -> Unit,
    ) {
        if (!_saving.compareAndSet(false, true)) return
        viewModelScope.launch {
            var landed = false
            try {
                val written = runCatching { write() }
                if (written.isFailure) {
                    _error.value = "$failure: ${written.exceptionOrNull()?.message}. Nothing was written."
                    return@launch
                }
                landed = true
                val bookkeeping = runCatching { after() }
                _error.value = bookkeeping.exceptionOrNull()?.let {
                    "Saved, but your answers could not be remembered: ${it.message}"
                }
                onDone()
            } finally {
                // A landed save stays locked: [onDone] leaves the screen, and a tap before that frame must not save twice.
                if (!landed) _saving.value = false
            }
        }
    }

    fun addPresets(onDone: () -> Unit) {
        val plan = _plan.value ?: return
        if (_saving.value) return
        // Adding keeps her cycle, so the new week must not land on days it already uses.
        val taken = presets.value.mapNotNull { it.scheduledDay }.toSet()
        val placed = plan.avoidingDays(taken)
        if (placed == null) {
            val needed = plan.presets.count { it.scheduledDay != null }
            _error.value = "Your cycle leaves ${7 - taken.size} free ${if (7 - taken.size == 1) "day" else "days"} " +
                "and this week needs $needed. Free some days, or replace your cycle instead."
            return
        }
        val focusNow = focus.value
        save("Could not add the rites", onDone, after = { rememberAnswers(); repo.alignProgressionMode(focusNow) }) {
            repo.applyRoutine(placed, replaceExisting = false)
        }
    }

    fun replacePresets(onDone: () -> Unit) {
        val plan = _plan.value ?: return
        val focusNow = focus.value
        save("Could not replace the cycle", onDone, after = { rememberAnswers(); repo.alignProgressionMode(focusNow) }) {
            repo.applyRoutine(plan, replaceExisting = true)
        }
    }

    fun addSession(onDone: () -> Unit) {
        val preset = _plan.value?.presets?.firstOrNull() ?: return
        save("Could not save the rite", onDone, after = { rememberAnswers() }) {
            repo.savePlannedPreset(null, preset)
        }
    }

    fun applyImprovement(onDone: () -> Unit) {
        val improvement = _improvement.value ?: return
        val id = selectedPresetId.value ?: presetId
        if (improvementFor != id) {
            _error.value = "That preview was for another rite. Wait for this one to load."
            return
        }
        save("Could not apply the changes", onDone, after = { rememberAnswers() }) {
            repo.savePlannedPreset(id, improvement.after)
        }
    }
}

/**
 * The cycle a proposal makes, for the coverage row and the page behind it. A generated rite, week or pattern
 * is judged against the cycle it joins (adding is the primary action, and never replaces the rite already
 * on that day), so the saved rites count too, as the generator saw them. A tempered rite stands in for the
 * original; the rest of the week feeds the read. Weekly Coverage reads only the scheduled rites of a cycle
 * ([scheduledRoutine]), so this does as well, and the two cannot disagree about the same cycle.
 */
internal fun proposalCoveragePresets(
    mode: String,
    proposed: List<PlannedPreset>?,
    improved: PlannedPreset?,
    saved: List<WorkoutPreset>,
    improvingId: Long?,
): List<PlannedPreset> = scheduledRoutine(
    when (mode) {
        "improve" -> listOfNotNull(improved) + saved.filter { it.id != improvingId }.map { it.toPlanned() }
        // A week or pattern is added to the saved cycle by default (the primary action), so it joins it.
        else -> proposed.orEmpty() + saved.map { it.toPlanned() }
    },
) { it.scheduledDay }

/** The planned form of a stored preset, keeping modifiers and loads. */
internal fun WorkoutPreset.toPlanned(): PlannedPreset = PlannedPreset(
    name = name,
    note = note,
    scheduledDay = scheduledDay,
    entries = entries.map {
        PlannedEntry(
            exerciseName = it.exerciseName,
            sets = it.targetSets,
            reps = it.targetReps,
            targetWeightKg = it.targetWeightKg,
            modifiers = it.modifiers,
        )
    },
)

/** The first ISO weekday (1 = Monday) not in [taken], or null (unscheduled) when all seven are. */
internal fun firstFreeWeekday(taken: Set<Int>): Int? = (1..7).firstOrNull { it !in taken }

private val DAY_PICKS = listOf(
    1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu",
    5 to "Fri", 6 to "Sat", 7 to "Sun", null to "—",
)

@Composable
fun ProgramBuilderScreen(
    mode: String,
    presetId: Long?,
    onDone: () -> Unit,
    viewModel: ProgramBuilderViewModel =
        viewModel(
            key = "program_builder_${mode}_$presetId",
            factory = viewModelFactory {
                val appContext = LocalContext.current.applicationContext
                initializer { ProgramBuilderViewModel(ironvellumRepository(), mode, presetId, appContext) }
            },
        ),
) {
    val catalogue by viewModel.catalogue.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val presetsLoaded by viewModel.presetsLoaded.collectAsStateWithLifecycle()
    val strength by viewModel.strength.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val baseline by viewModel.baseline.collectAsStateWithLifecycle()
    val generated by viewModel.generatedPlan.collectAsStateWithLifecycle()
    val sex by viewModel.sex.collectAsStateWithLifecycle()
    val profileMode by viewModel.trainingMode.collectAsStateWithLifecycle()
    val focus by viewModel.focus.collectAsStateWithLifecycle()
    val tier by viewModel.tier.collectAsStateWithLifecycle()
    val equipment by viewModel.equipment.collectAsStateWithLifecycle()
    val daysPerWeek by viewModel.daysPerWeek.collectAsStateWithLifecycle()
    val split by viewModel.split.collectAsStateWithLifecycle()
    val priorities by viewModel.priorities.collectAsStateWithLifecycle()
    val compoundOnly by viewModel.compoundOnly.collectAsStateWithLifecycle()
    val maxExercises by viewModel.maxExercises.collectAsStateWithLifecycle()
    val sessionMinutes by viewModel.sessionMinutes.collectAsStateWithLifecycle()
    val sessionKind by viewModel.sessionKind.collectAsStateWithLifecycle()
    val sessionDay by viewModel.sessionDay.collectAsStateWithLifecycle()
    val templateId by viewModel.selectedTemplateId.collectAsStateWithLifecycle()
    val selectedPresetId by viewModel.selectedPresetId.collectAsStateWithLifecycle()
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    val improvement by viewModel.improvement.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val hasHistory by viewModel.hasHistory.collectAsStateWithLifecycle()
    val historyYears by viewModel.historyYears.collectAsStateWithLifecycle()

    // The goal question defaults from the profile's own training mode - the
    // setting she already made - until she picks otherwise. The touch guard
    // stops a late Room emission overwriting a pick she already made.
    LaunchedEffect(profileMode) {
        if (viewModel.focusTouched()) return@LaunchedEffect
        when (profileMode) {
            TrainingMode.STRENGTH -> viewModel.focus.value = TrainingFocus.STRENGTH
            TrainingMode.HYPERTROPHY -> viewModel.focus.value = TrainingFocus.MUSCLE
            null -> {}
        }
    }

    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmReplace by remember { mutableStateOf(false) }
    var armouryOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var coverageOpen by rememberSaveable { mutableStateOf(false) }
    // Hoisted so the form keeps its place while the coverage page is up.
    val formScroll = rememberScrollState()
    // The baseline and the first proposal live in the view model, with the plan they measure: a rotation
    // re-runs the effect below, and a screen-local copy would forget there was anything to discard.

    // Any answer change rebuilds, and generate() itself skips a call whose inputs did not change (a rotation
    // re-runs this effect), so hand edits survive. The tier suggestion has settled by the time the
    // catalogue is non-empty, so this cannot thrash.
    LaunchedEffect(mode, catalogue, presets, strength, focus, tier, equipment, daysPerWeek, split, priorities, compoundOnly, maxExercises, sessionMinutes, sessionKind, sessionDay, templateId, selectedPresetId, sex) {
        viewModel.generate()
    }

    // Forge a cycle is the three-step form, the proposal and the day page; every other mode keeps its one page.
    if (mode == "week") {
        ForgeCycleScreen(viewModel, onDone)
        return
    }

    // Something to lose: answers moved off the first proposal's, or its days edited.
    val answers = viewModel.answersNow()
    val dirty = baseline != null && (answers != baseline || plan != generated)
    BackHandler(enabled = dirty) { confirmDiscard = true }

    // The cycle the proposal makes, which the coverage row counts and the coverage page maps.
    val coveragePresets = remember(mode, plan, improvement, presets, selectedPresetId, presetId) {
        proposalCoveragePresets(mode, plan?.presets, improvement?.after, presets, selectedPresetId ?: presetId)
    }
    if (coverageOpen) {
        BackHandler { coverageOpen = false }
        CoveragePreviewScreen(coveragePresets) { coverageOpen = false }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(formScroll)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(20.dp))
            PushedHeader(
                when (mode) {
                    "template" -> "From a pattern"
                    "week" -> "Forge a cycle"
                    "session" -> "Forge a rite"
                    else -> "Temper a rite"
                },
                onBack = { if (dirty) confirmDiscard = true else onDone() },
            )
            Spacer(Modifier.height(12.dp))

            Question("Goal") {
                InkSegmented(
                    // Templates are hand-authored for strength and hypertrophy
                    // only; offering Skills / Mixed here composed questions
                    // that matched no template.
                    options = if (mode == "template") {
                        listOf(
                            TrainingFocus.STRENGTH to "Power",
                            TrainingFocus.MUSCLE to "Muscle",
                        )
                    } else {
                        listOf(
                            TrainingFocus.STRENGTH to "Power",
                            TrainingFocus.MUSCLE to "Muscle",
                            TrainingFocus.SKILL to "Techniques",
                            TrainingFocus.GENERAL to "Mixed",
                        )
                    },
                    selected = focus,
                    onPick = viewModel::setFocus,
                )
                Spacer(Modifier.height(6.dp))
                Caption(
                    when (focus) {
                        TrainingFocus.STRENGTH -> "Low reps, heavy load. Bigger numbers on the main exercises."
                        TrainingFocus.MUSCLE -> "Higher volume, moderate load. Size comes before bragging rights."
                        TrainingFocus.SKILL -> "Handstands, levers, the planche. Practice over pump."
                        TrainingFocus.GENERAL -> "A balanced mix: a bit stronger, a bit bigger, nothing neglected."
                    },
                )
            }

            Question("Starting sets") {
                InkSegmented(
                    options = VolumeLevel.entries.map { it to it.label },
                    selected = tier,
                    onPick = viewModel::setTier,
                )
                Spacer(Modifier.height(6.dp))
                Caption(startingSetsCaption(tier))
                Spacer(Modifier.height(4.dp))
                Caption(volumeSuggestion(hasHistory, historyYears))
            }

            // Two answers most lifters never change, each a row that opens a sheet.
            Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                InkDivider()
                ListRow(
                    label = "Armoury",
                    value = SettingsSummaries.armoury(equipment),
                    onClickLabel = "Choose your armoury",
                    onClick = { armouryOpen = true },
                )
                InkDivider()
                ListRow(
                    label = "More options",
                    value = (if (compoundOnly) "Compound · " else "") + "$sessionMinutes min · $maxExercises per rite",
                    onClickLabel = "Rite length, compound only and exercises per rite",
                    onClick = { moreOpen = true },
                )
                InkDivider()
            }

            if (mode != "template") {
                Question("Priorities · up to 3") {
                    MuscleAreaChips(
                        selectedAreas = priorities,
                        onToggle = viewModel::togglePriority,
                    )
                }
            }

            if (mode == "session") {
                Question("What kind of rite") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SessionKind.entries.chunked(2).forEach { chunk ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                chunk.forEach { kind ->
                                    PickCell(
                                        label = kind.label,
                                        selected = sessionKind == kind,
                                        modifier = Modifier.weight(1f),
                                        onClick = { viewModel.sessionKind.value = kind },
                                    )
                                }
                                if (chunk.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Caption("\"What my week is missing\" reads the rest of your cycle and fills the gap.")
                }
                Question("Place it on") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        DAY_PICKS.chunked(4).forEach { chunk ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                chunk.forEach { (day, label) ->
                                    PickCell(
                                        label = label,
                                        selected = sessionDay == day,
                                        modifier = Modifier.weight(1f),
                                        description = label.takeIf { day != null } ?: "No day in the cycle",
                                        onClick = { viewModel.setSessionDay(day) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (mode == "template") {
                SectionHeader("Patterns")
                val templates = viewModel.matchingTemplates()
                if (templates.isEmpty()) {
                    Caption("No hand-written pattern for this goal — forge a cycle instead.")
                }
                templates.forEachIndexed { index, template ->
                    if (index > 0) InkDivider()
                    ChoiceRow(
                        title = template.name,
                        subline = "${template.split.label} · ${template.days.size} days",
                        detail = template.summary,
                        selected = template.id == templateId,
                        onClick = { viewModel.selectedTemplateId.value = template.id },
                    )
                }
                Spacer(Modifier.height(6.dp))
                Caption("Adapted to your armoury and weekly volume.")
            }

            if (mode == "improve") {
                SectionHeader("Which rite")
                presets.forEachIndexed { index, preset ->
                    if (index > 0) InkDivider()
                    ChoiceRow(
                        title = preset.name,
                        trailing = dayLabel(preset.scheduledDay),
                        selected = preset.id == (selectedPresetId ?: presetId),
                        onClick = { viewModel.selectedPresetId.value = preset.id },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Caption(Evidence.split(ProgramRules.SEX_NOTE).first)

            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    error!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = IronvellumColors.DangerRed,
                )
            }

            SectionHeader(
                when (mode) {
                    "session" -> "Proposed rite"
                    "improve" -> "Preview"
                    else -> "Proposed cycle"
                },
            )

            if (mode == "improve") {
                val current = improvement
                if (equipment == null) {
                    Caption(ARMOURY_PICK_CAPTION)
                } else if (current == null) {
                    Caption(
                        when {
                            // Room has answered and there is nothing to read: say so instead of waiting forever.
                            presetsLoaded && presets.isEmpty() ->
                                "You have no rites to temper yet. Forge a cycle or a rite first."
                            error != null -> "Nothing to show yet."
                            else -> "Reading your rite against the evidence…"
                        },
                    )
                } else {
                    // The rest of the week feeds the volume read but is NOT
                    // listed: on a five-day split it buried the diff under every
                    // other session's full movement list.
                    val others = presets.filter { it.id != (selectedPresetId ?: presetId) }
                    val volume = ProgramRules.weeklyVolume(coveragePresets)
                    BeforeAfter(
                        current,
                        stillShort = ProgramRules.TRACKED.filter { (volume[it] ?: 0.0) < ProgramRules.COVERED_MIN - 1e-9 },
                    )
                    Spacer(Modifier.height(10.dp))
                    if (others.isNotEmpty()) {
                        val noun = if (others.size == 1) "rite" else "rites"
                        Caption("Weekly volume includes your ${others.size} other $noun.")
                        Spacer(Modifier.height(6.dp))
                    }
                    CoverageChip(remember(coveragePresets) { coverageTally(coveragePresets) }, onOpen = { coverageOpen = true })
                }
            } else {
                val current = plan
                if (current == null || current.presets.isEmpty()) {
                    Caption(
                        when {
                            equipment == null -> ARMOURY_PICK_CAPTION
                            catalogue.isEmpty() -> "Consulting the catalogue…"
                            else -> "Nothing to show yet."
                        },
                    )
                } else {
                    current.presets.forEachIndexed { presetIndex, preset ->
                        if (presetIndex > 0) InkDivider()
                        ProposedDay(
                            preset = preset,
                            editable = true,
                            equipment = equipment,
                            folded = true,
                            onSets = { entryIndex, delta ->
                                viewModel.editEntry(presetIndex, entryIndex) { it.withSetsDelta(delta) }
                            },
                            onLoad = { entryIndex, delta ->
                                viewModel.editEntry(presetIndex, entryIndex) { entry ->
                                    val cap = equipment?.let { ProgramGenerator.dumbbellCapKg(entry.exerciseName, it) }
                                    entry.withLoadDelta(delta, loadStepKg(Units.current), cap)
                                }
                            },
                            onRemove = { entryIndex -> viewModel.removeEntry(presetIndex, entryIndex) },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    CoverageChip(remember(coveragePresets) { coverageTally(coveragePresets) }, onOpen = { coverageOpen = true })
                    if (presets.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        val noun = if (presets.size == 1) "rite" else "rites"
                        Caption("Weekly volume includes your ${presets.size} existing $noun.")
                    }
                    // Routine-wide advice, said once for the whole plan.
                    Evidence.split(current.note).first.trim().takeIf { it.isNotBlank() }?.let { note ->
                        Spacer(Modifier.height(10.dp))
                        Caption(note)
                    }
                    // One quiet line, so the progression switch is stated
                    // where she acts, not discovered in Settings later.
                    if (viewModel.switchesProgression()) {
                        Spacer(Modifier.height(6.dp))
                        Caption(viewModel.progressionLine())
                    }
                }
            }

            // Every paper the screen leans on, once, below the proposal.
            SourcesPanel(
                listOf(ProgramRules.SEX_NOTE) + when (mode) {
                    "improve" -> improvement?.let { planTexts(listOf(it.after)) + it.changes.map { c -> c.detail } }.orEmpty()
                    else -> plan?.let { planTexts(it.presets, it.note) }.orEmpty()
                },
            )
            Spacer(Modifier.height(24.dp))
        }

        // The action is docked, so it never scrolls away from the proposal it applies. Nothing is docked
        // while there is nothing to apply: the page already says why.
        when (mode) {
            "template" -> if (plan?.presets?.isNotEmpty() == true) {
                // With nothing written yet, add and replace are the same
                // act, and "delete your 0 rites" was a scare for nobody.
                DockedActionBar(
                    primary = if (presets.isEmpty()) "Take this cycle" else "Add to my cycle",
                    onPrimary = { viewModel.addPresets(onDone) },
                    primaryEnabled = !saving,
                    link = if (presets.isNotEmpty()) "Replace my cycle" else null,
                    linkOn = !saving,
                    linkColor = IronvellumColors.DangerRed,
                    onLink = { confirmReplace = true },
                )
            }
            "session" -> if (plan != null) {
                DockedActionBar(primary = "Add this rite", onPrimary = { viewModel.addSession(onDone) }, primaryEnabled = !saving)
            }
            else -> {
                if (improvement?.changes?.isEmpty() == true) {
                    // Nothing to apply: a disabled Apply beside "Keep
                    // original" offered a choice that did not exist.
                    DockedActionBar(primary = "Done", onPrimary = onDone)
                } else if (improvement != null) {
                    DockedActionBar(
                        primary = "Apply changes",
                        onPrimary = { viewModel.applyImprovement(onDone) },
                        primaryEnabled = !saving,
                        link = "Keep original",
                        onLink = onDone,
                    )
                }
            }
        }
    }

    if (armouryOpen) {
        IronvellumDialog(
            onDismissRequest = { armouryOpen = false },
            title = { Text("Armoury") },
            text = { GearPicker(equipment = equipment, onChange = { viewModel.equipment.value = it }) },
            confirmButton = { IronvellumButton(label = "Done", onClick = { armouryOpen = false }) },
        )
    }

    if (moreOpen) {
        IronvellumDialog(
            onDismissRequest = { moreOpen = false },
            title = { Text("More options") },
            text = {
                MoreOptionsBody(
                    tier = tier,
                    onTier = viewModel::setTier,
                    compoundOnly = compoundOnly,
                    onCompound = { viewModel.compoundOnly.value = it },
                    maxExercises = maxExercises,
                    onMaxExercises = { viewModel.maxExercises.value = it },
                    showTier = false,
                    sessionMinutes = sessionMinutes,
                    onSessionMinutes = { viewModel.sessionMinutes.value = it },
                )
            },
            confirmButton = { IronvellumButton(label = "Done", onClick = { moreOpen = false }) },
        )
    }

    if (confirmDiscard) {
        IronvellumDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard your changes?") },
            text = { Text("Your answers and any edits to the proposal will be lost.") },
            confirmButton = {
                IronvellumButton(
                    label = "Discard",
                    onClick = {
                        confirmDiscard = false
                        onDone()
                    },
                    danger = true,
                )
            },
            dismissButton = {
                IronvellumButton(label = "Keep editing", onClick = { confirmDiscard = false }, quiet = true)
            },
        )
    }

    if (confirmReplace) {
        IronvellumDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("Replace your cycle?") },
            text = {
                Text(
                    "Your ${presets.size} current ${if (presets.size == 1) "rite" else "rites"} will be deleted " +
                        "and the forged cycle takes their place. Your Chronicle is untouched." +
                        if (viewModel.switchesProgression()) " " + viewModel.progressionLine() else "",
                )
            },
            confirmButton = {
                IronvellumButton(
                    label = "Replace",
                    onClick = {
                        confirmReplace = false
                        viewModel.replacePresets(onDone)
                    },
                    danger = true,
                )
            },
            dismissButton = {
                IronvellumButton(label = "Keep mine", onClick = { confirmReplace = false }, quiet = true)
            },
        )
    }
}

private fun volumeSuggestion(hasHistory: Boolean, years: Double?): String {
    // Selection-independent on purpose: a saved answer can preselect
    // Standard, and "starts low" would then contradict it.
    if (!hasHistory || years == null) return "The Chronicle is blank: pick what you can recover from."
    return "Suggested from ${historySpan(years)} of training."
}

/** "3 weeks", "5 months", "1.4 years" - under a year, "0 years" read as no history at all. */
private fun historySpan(years: Double): String {
    val days = (years * 365.25).toInt()
    fun plural(n: Int, unit: String) = "$n $unit${if (n == 1) "" else "s"}"
    return when {
        days < 14 -> plural(days.coerceAtLeast(1), "day")
        days < 61 -> plural(days / 7, "week")
        years < 1.0 -> plural((days / 30.44).toInt(), "month")
        else -> "${trimYears(years)} year${if (years == 1.0) "" else "s"}"
    }
}

/** One decimal, no trailing .0 - "1.4 years", never "1.4000001 years". */
private fun trimYears(value: Double): String {
    val rounded = (value * 10).toLong() / 10.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
}

private const val ARMOURY_PICK_CAPTION =
    "Pick your armoury to see a preview. Nothing means bodyweight only."

/** A question on the page surface: a small muted label, then its control. */
@Composable
private fun Question(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.padding(bottom = 6.dp).semantics { heading() },
        )
        content()
    }
}

/**
 * One choice in a list of pattern or rite options: the title over its detail, an optional trailing
 * word, and an Emerald check on the one chosen. Rows are divided by the caller, not boxed.
 */
@Composable
private fun ChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    subline: String? = null,
    detail: String? = null,
    trailing: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ListRowHeight)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink)
            if (subline != null) {
                Text(subline, style = MaterialTheme.typography.labelMedium, color = IronvellumColors.InkMuted)
            }
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
            }
        }
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.labelMedium, color = IronvellumColors.InkMuted)
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = IronvellumColors.Emerald, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = IronvellumColors.InkMuted,
    )
}

/** Multi-select muscle-area chips: the chosen ones carry a check. */
@Composable
private fun MuscleAreaChips(
    selectedAreas: Set<MuscleArea>,
    onToggle: (MuscleArea) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        MuscleArea.entries.forEach { area ->
            // Checkbox role: the check alone is invisible to a screen reader.
            PickCell(
                label = area.label,
                selected = area in selectedAreas,
                role = Role.Checkbox,
                onClick = { onToggle(area) },
            )
        }
    }
}
