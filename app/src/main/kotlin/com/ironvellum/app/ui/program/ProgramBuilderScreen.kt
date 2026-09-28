package com.ironvellum.app.ui.program

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
import com.ironvellum.app.domain.EquipmentAccess
import com.ironvellum.app.domain.ExperienceTier
import com.ironvellum.app.domain.Exercise
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
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.InkSegmented
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import com.ironvellum.app.ui.theme.inkBorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

    val sex: StateFlow<Sex> = repo.observeBodyProfile()
        .map { it.second }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Sex.MALE)

    /** The profile's own training mode, the default for the goal question. */
    val trainingMode: StateFlow<TrainingMode?> = repo.observeProfile()
        .map { it?.trainingMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _strength = MutableStateFlow(StrengthProfile())
    private val _firstSessionEpochDay = MutableStateFlow<Long?>(null)

    /** Whether any completed session exists - what the tier caption quotes. */
    val hasHistory = MutableStateFlow(false)

    /** Years of logged sessions, rounded to one decimal, for the tier caption. */
    val historyYears = MutableStateFlow<Double?>(null)

    // Answers. The screen renders them; the view model owns them so the
    // generated plan survives rotation the same way the editor's does.
    val focus = MutableStateFlow(TrainingFocus.GENERAL)
    val tier = MutableStateFlow(ExperienceTier.BEGINNER)
    val equipment = MutableStateFlow(EquipmentAccess.FULL_GYM)
    val daysPerWeek = MutableStateFlow(4)
    val priorities = MutableStateFlow<Set<MuscleArea>>(emptySet())
    val sessionKind = MutableStateFlow(SessionKind.AUTO)
    val sessionDay = MutableStateFlow<Int?>(1)
    val selectedTemplateId = MutableStateFlow<String?>(null)
    val selectedPresetId = MutableStateFlow<Long?>(presetId)

    private val _plan = MutableStateFlow<RoutinePlan?>(null)
    val plan: StateFlow<RoutinePlan?> = _plan.asStateFlow()

    private val _improvement = MutableStateFlow<com.ironvellum.app.domain.Improvement?>(null)
    val improvement: StateFlow<com.ironvellum.app.domain.Improvement?> = _improvement.asStateFlow()

    /** Set when a generation or write is refused; nothing was written. */
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
        // while she had just answered INTERMEDIATE / MUSCLE. Applying a saved
        // answer counts as a deliberate choice, so the suggestTier /
        // trainingMode fallbacks must not override it.
        ProgramAnswersStore.get(appContext)?.let { saved ->
            if (!tierTouched) {
                tierTouched = true
                tier.value = saved.tier
            }
            if (!focusTouched) {
                focusTouched = true
                focus.value = saved.focus
            }
            equipment.value = saved.equipment
            daysPerWeek.value = saved.daysPerWeek
            priorities.value = saved.priorities
        }
        // Templates exist only for strength and hypertrophy; a saved or
        // default Skills / All-round goal would match no template.
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
            }.onFailure { _error.value = "Could not read your training history: ${it.message}" }
            // The suggestion lands once, from real history; after she touches
            // the control it is hers, and a later load cannot re-suggest.
            if (!tierTouched) {
                tier.value = ProgramRules.suggestTier(
                    _firstSessionEpochDay.value,
                    LocalDate.now().toEpochDay(),
                )
            }
        }
    }

    fun setTier(value: ExperienceTier) {
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
        tier = tier.value,
        equipment = equipment.value,
        daysPerWeek = daysPerWeek.value,
        priorities = priorities.value,
        sex = sex.value,
    )

    private fun planned(existing: List<WorkoutPreset>): List<PlannedPreset> = existing.map { it.toPlanned() }

    /**
     * Runs the generator for this screen's mode. Pure domain work on the
     * catalogue already in memory; a refusal lands in [error] and the board
     * keeps whatever was last shown.
     */
    fun generate() {
        val cat = catalogue.value
        if (cat.isEmpty()) return
        // A preset picker that is still loading is not "nothing to improve":
        // wait for Room's first emission instead of flashing an error.
        if (mode == "improve" && presets.value.isEmpty()) return
        runCatching {
            when (mode) {
                "template" -> {
                    val templates = matchingTemplates()
                    val chosen = templates.firstOrNull { it.id == selectedTemplateId.value } ?: templates.firstOrNull()
                    selectedTemplateId.value = chosen?.id
                    chosen?.let { ProgramTemplates.build(it, equipment.value, cat, _strength.value) }
                }
                "session" -> {
                    val week = planned(presets.value)
                    val preset = ProgramGenerator.session(
                        request(), sessionKind.value, sessionDay.value, week, cat, _strength.value,
                    )
                    // Null is a message for her, not a silent blank. For
                    // "what my week is missing" it means nothing is missing.
                    requireNotNull(preset) {
                        if (sessionKind.value == SessionKind.AUTO) {
                            "Your week already reaches the target on every muscle. " +
                                "Pick Full body, Upper or Lower for an extra session."
                        } else {
                            "The catalogue cannot fill this workout with your equipment."
                        }
                    }
                    RoutinePlan(listOf(preset))
                }
                "improve" -> {
                    val all = planned(presets.value)
                    val ids = presets.value.map { it.id }
                    // No route argument and no pick yet: improve the first
                    // preset rather than demanding a choice she can see below.
                    val sel = selectedPresetId.value ?: presetId ?: ids.firstOrNull()
                    if (sel != selectedPresetId.value) selectedPresetId.value = sel
                    val index = ids.indexOf(sel)
                    val target = all.getOrNull(index)
                    if (target == null) {
                        // Thrown, not set: the fold below owns the error line,
                        // and a returned null would clear it.
                        throw IllegalArgumentException("Pick a preset to improve.")
                    } else {
                        val rest = all.filterIndexed { i, _ -> i != index }
                        _improvement.value = ProgramGenerator.improve(
                            target, rest, request(), cat, _strength.value,
                        )
                        null
                    }
                }
                else -> ProgramGenerator.week(request(), cat, _strength.value)
            }
        }.fold(
            onSuccess = { result ->
                _error.value = null
                if (result != null) _plan.value = result
            },
            onFailure = { _error.value = "Could not build the plan: ${it.message}" },
        )
    }

    /** Templates for the chosen tier and goal - the six hand-authored programs. */
    fun matchingTemplates(): List<ProgramTemplate> =
        ProgramTemplates.ALL.filter { it.tier == tier.value && it.focus == focus.value }

    /** Edits made on the previewed days. */
    fun replacePlan(plan: RoutinePlan) {
        _plan.value = plan
    }

    fun editEntry(presetIndex: Int, entryIndex: Int, transform: (PlannedEntry) -> PlannedEntry) {
        val current = _plan.value ?: return
        val preset = current.presets[presetIndex]
        val entries = preset.entries.toMutableList()
        entries[entryIndex] = transform(entries[entryIndex])
        replacePlan(
            current.copy(
                presets = current.presets.toMutableList()
                    .also { it[presetIndex] = preset.copy(entries = entries) },
            ),
        )
    }

    fun removeEntry(presetIndex: Int, entryIndex: Int) {
        val current = _plan.value ?: return
        val preset = current.presets[presetIndex]
        val entries = preset.entries.filterIndexed { i, _ -> i != entryIndex }
        val presetsOut = if (entries.isEmpty()) {
            current.presets.filterIndexed { i, _ -> i != presetIndex }
        } else {
            current.presets.toMutableList().also { it[presetIndex] = preset.copy(entries = entries) }
        }
        replacePlan(current.copy(presets = presetsOut))
    }

    // Actions. Every one reports failure on screen; silence is the bug.

    /** Remembered whenever generator output is applied; Weekly Coverage and
     *  the next builder visit read the SAME tier and goal she answered with. */
    private fun rememberAnswers() {
        ProgramAnswersStore.save(
            appContext,
            ProgramAnswers(tier.value, focus.value, equipment.value, daysPerWeek.value, priorities.value),
        )
    }

    /** The progression engine must follow the goal: a hypertrophy plan left on
     *  the strength mode progressed by the rep band, never by double
     *  progression. GENERAL and SKILL leave the profile mode alone. */
    private suspend fun alignProgressionMode() {
        when (focus.value) {
            TrainingFocus.STRENGTH -> repo.setTrainingMode(TrainingMode.STRENGTH)
            TrainingFocus.MUSCLE -> repo.setTrainingMode(TrainingMode.HYPERTROPHY)
            else -> {}
        }
    }

    /** True when [alignProgressionMode] would switch the profile's mode. */
    fun switchesProgression(): Boolean =
        mode in setOf("week", "template") &&
            (focus.value == TrainingFocus.STRENGTH || focus.value == TrainingFocus.MUSCLE)

    fun progressionLine(): String = when (focus.value) {
        TrainingFocus.STRENGTH -> "Progression switched to linear progression for strength."
        else -> "Progression switched to double progression for hypertrophy."
    }

    fun addPresets(onDone: () -> Unit) {
        val plan = _plan.value ?: return
        viewModelScope.launch {
            runCatching {
                repo.applyRoutine(plan, replaceExisting = false)
                rememberAnswers()
                alignProgressionMode()
            }.fold(
                onSuccess = { _error.value = null; onDone() },
                onFailure = { _error.value = "Could not add the presets: ${it.message}. Nothing was written." },
            )
        }
    }

    fun replacePresets(onDone: () -> Unit) {
        val plan = _plan.value ?: return
        viewModelScope.launch {
            runCatching {
                repo.applyRoutine(plan, replaceExisting = true)
                rememberAnswers()
                alignProgressionMode()
            }.fold(
                onSuccess = { _error.value = null; onDone() },
                onFailure = { _error.value = "Could not replace the presets: ${it.message}. Nothing was written." },
            )
        }
    }

    fun addSession(onDone: () -> Unit) {
        val preset = _plan.value?.presets?.firstOrNull() ?: return
        viewModelScope.launch {
            runCatching {
                repo.savePlannedPreset(null, preset)
                rememberAnswers()
            }.fold(
                onSuccess = { _error.value = null; onDone() },
                onFailure = { _error.value = "Could not save the workout: ${it.message}. Nothing was written." },
            )
        }
    }

    fun applyImprovement(onDone: () -> Unit) {
        val improvement = _improvement.value ?: return
        val id = selectedPresetId.value ?: presetId
        viewModelScope.launch {
            runCatching {
                repo.savePlannedPreset(id, improvement.after)
                rememberAnswers()
            }.fold(
                onSuccess = { _error.value = null; onDone() },
                onFailure = { _error.value = "Could not apply the changes: ${it.message}. Nothing was written." },
            )
        }
    }
}

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
    val sex by viewModel.sex.collectAsStateWithLifecycle()
    val profileMode by viewModel.trainingMode.collectAsStateWithLifecycle()
    val focus by viewModel.focus.collectAsStateWithLifecycle()
    val tier by viewModel.tier.collectAsStateWithLifecycle()
    val equipment by viewModel.equipment.collectAsStateWithLifecycle()
    val daysPerWeek by viewModel.daysPerWeek.collectAsStateWithLifecycle()
    val priorities by viewModel.priorities.collectAsStateWithLifecycle()
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

    // Any answer change rebuilds; the tier suggestion has settled by the time
    // the catalogue is non-empty, so this cannot thrash.
    LaunchedEffect(mode, catalogue, presets, focus, tier, equipment, daysPerWeek, priorities, sessionKind, sessionDay, templateId, selectedPresetId, sex) {
        viewModel.generate()
    }

    var confirmReplace by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when (mode) {
                    "template" -> "FROM A TEMPLATE"
                    "week" -> "GENERATE A WEEK"
                    "session" -> "GENERATE ONE WORKOUT"
                    else -> "IMPROVE A PRESET"
                },
                style = MaterialTheme.typography.labelLarge,
                fontFamily = ChakraPetch,
                color = IronvellumColors.SystemGreen,
                letterSpacing = IronvellumTracking.InlineLabel,
            )
            // The same BACK chip the Weekly Coverage / Movement Records
            // screens use; system back alone was undiscoverable.
            Text(
                "BACK",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = IronvellumColors.InkMuted,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .inkBorder(IronvellumColors.Rune, MaterialTheme.shapes.extraSmall, 1.dp)
                    .clickable { onDone() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(12.dp))

        QuestionPanel("WHAT YOU ARE CHASING") {
            InkSegmented(
                // Templates are hand-authored for strength and hypertrophy
                // only; offering Skills / All-round here composed questions
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
                        TrainingFocus.SKILL to "Skills",
                        TrainingFocus.GENERAL to "All-round",
                    )
                },
                selected = focus,
                onPick = viewModel::setFocus,
            )
            Spacer(Modifier.height(8.dp))
            Caption(
                when (focus) {
                    TrainingFocus.STRENGTH -> "Low reps, heavy moves. Bigger numbers on the main lifts."
                    TrainingFocus.MUSCLE -> "Higher volume, moderate load. Size comes before bragging rights."
                    TrainingFocus.SKILL -> "Handstands, levers, the planche. Practice over pump."
                    TrainingFocus.GENERAL -> "A balanced mix: a bit stronger, a bit bigger, nothing neglected."
                },
            )
        }

        QuestionPanel("TRAINING AGE") {
            InkSegmented(
                options = ExperienceTier.entries.map { it to it.label },
                selected = tier,
                onPick = viewModel::setTier,
            )
            Spacer(Modifier.height(8.dp))
            Caption(tierCaption(hasHistory, historyYears))
        }

        QuestionPanel("WHAT YOU HAVE ACCESS TO") {
            InkSegmented(
                options = listOf(
                    EquipmentAccess.BODYWEIGHT to "No gear",
                    EquipmentAccess.HOME_WEIGHTS to "Home gym",
                    EquipmentAccess.FULL_GYM to "Full gym",
                ),
                selected = equipment,
                onPick = { viewModel.equipment.value = it },
            )
        }

        // Templates fix their own days and doses, so these two questions
        // compose nothing in template mode and are hidden there.
        if (mode == "week") {
            QuestionPanel("DAYS PER WEEK") {
                InkSegmented(
                    options = (1..6).map { it to it.toString() },
                    selected = daysPerWeek,
                    onPick = { viewModel.daysPerWeek.value = it },
                )
            }
        }

        if (mode != "template") {
            QuestionPanel("MUSCLES TO PRIORITISE - PICK UP TO 3") {
                MuscleAreaChips(
                    selectedAreas = priorities,
                    onToggle = viewModel::togglePriority,
                )
                Spacer(Modifier.height(8.dp))
                Caption("Prioritising everything prioritises nothing. Three is the ceiling.")
            }
        }

        if (mode == "session") {
            QuestionPanel("WHAT KIND OF WORKOUT") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SessionKind.entries.chunked(2).forEach { chunk ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
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
                Spacer(Modifier.height(8.dp))
                Caption("\"What my week is missing\" reads the rest of your week and fills the gap.")
            }
            QuestionPanel("SCHEDULE IT ON") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    DAY_PICKS.chunked(4).forEach { chunk ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            chunk.forEach { (day, label) ->
                                PickCell(
                                    label = label,
                                    selected = sessionDay == day,
                                    modifier = Modifier.weight(1f),
                                    description = label.takeIf { day != null } ?: "No scheduled day",
                                    onClick = { viewModel.sessionDay.value = day },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (mode == "template") {
            SectionHeader("Templates")
            val templates = viewModel.matchingTemplates()
            if (templates.isEmpty()) {
                Caption("No hand-authored template sits on this tier and goal - generate a week instead.")
            }
            templates.forEach { template ->
                val selected = template.id == templateId
                InkPanel(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    accent = if (selected) IronvellumColors.SovereignGold else IronvellumColors.Rune,
                    onClick = { viewModel.selectedTemplateId.value = template.id },
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            template.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = ChakraPetch,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) IronvellumColors.SovereignGold else IronvellumColors.Ink,
                        )
                        Text(
                            "${template.days.size} days/week",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = ChakraPetch,
                            color = IronvellumColors.InkMuted,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        template.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.InkMuted,
                    )
                }
            }
            Caption("Written for a full gym; adapted to what you have.")
        }

        if (mode == "improve") {
            SectionHeader("Which preset")
            presets.forEach { preset ->
                val selected = preset.id == (selectedPresetId ?: presetId)
                InkPanel(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    accent = if (selected) IronvellumColors.SovereignGold else IronvellumColors.Rune,
                    onClick = { viewModel.selectedPresetId.value = preset.id },
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(preset.name, style = MaterialTheme.typography.titleSmall, color = IronvellumColors.Ink)
                        Text(
                            dayLabel(preset.scheduledDay),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = ChakraPetch,
                            color = IronvellumColors.InkMuted,
                        )
                    }
                }
            }
        }

        // Sex enters the request on purpose, and the preview says what it does
        // and does not change, citing the evidence - quietly, once.
        Spacer(Modifier.height(8.dp))
        Caption("Profile sex: ${if (sex == Sex.MALE) "male" else "female"}. ${ProgramRules.SEX_NOTE}")

        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                error!!,
                style = MaterialTheme.typography.labelSmall,
                color = IronvellumColors.SovereignGold,
            )
        }

        SectionHeader("Preview")

        if (mode == "improve") {
            val current = improvement
            if (current == null) {
                Caption("Reading your preset against the evidence...")
            } else {
                // The rest of the week feeds the volume read but is NOT
                // listed: on a five-day split it buried the diff under every
                // other session's full movement list.
                val others = presets.filter { it.id != (selectedPresetId ?: presetId) }
                val week = listOf(current.after) + others.map { it.toPlanned() }
                val volume = ProgramRules.weeklyVolume(week)
                val floor = ProgramRules.weeklySetTarget(tier, focus).start
                BeforeAfter(current, stillShort = ProgramRules.TRACKED.filter { (volume[it] ?: 0.0) < floor })
                Spacer(Modifier.height(10.dp))
                if (others.isNotEmpty()) {
                    val noun = if (others.size == 1) "preset" else "presets"
                    Caption("Weekly volume includes your ${others.size} other $noun.")
                }
                WeeklyVolumePanel(week, tier, focus)
            }
        } else {
            val current = plan
            if (current == null || current.presets.isEmpty()) {
                Caption(if (catalogue.isEmpty()) "Consulting the catalogue..." else "Nothing to show yet.")
            } else {
                current.presets.forEachIndexed { presetIndex, preset ->
                    ProposedDay(
                        preset = preset,
                        editable = true,
                        onSets = { entryIndex, delta ->
                            viewModel.editEntry(presetIndex, entryIndex) {
                                it.copy(sets = (it.sets + delta).coerceIn(1, 10))
                            }
                        },
                        onReps = { entryIndex, delta ->
                            viewModel.editEntry(presetIndex, entryIndex) {
                                it.copy(reps = (it.reps + delta).coerceIn(1, 30))
                            }
                        },
                        onRemove = { entryIndex -> viewModel.removeEntry(presetIndex, entryIndex) },
                    )
                    Spacer(Modifier.height(10.dp))
                }
                // A single generated workout is judged against the week it
                // joins, so the volume panel reads the whole board. Adding
                // never replaces the preset already on that day, so that day
                // counts too, the same as the generator saw it.
                val volumePresets =
                    if (mode == "session") current.presets + presets.map { it.toPlanned() }
                    else current.presets
                WeeklyVolumePanel(volumePresets, tier, focus)
                if (mode == "session" && presets.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    val noun = if (presets.size == 1) "preset" else "presets"
                    Caption("Weekly volume includes your ${presets.size} existing $noun.")
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        when (mode) {
            "week", "template" -> {
                Column(Modifier.fillMaxWidth()) {
                    IronvellumButton(
                        label = "Add to my presets",
                        onClick = { viewModel.addPresets(onDone) },
                        enabled = plan != null && plan!!.presets.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    IronvellumButton(
                        label = "Replace my presets",
                        onClick = { confirmReplace = true },
                        enabled = plan != null && plan!!.presets.isNotEmpty(),
                        quiet = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // One quiet line, so the progression switch is stated
                    // where she acts, not discovered in Settings later.
                    if (viewModel.switchesProgression()) {
                        Spacer(Modifier.height(6.dp))
                        Caption(viewModel.progressionLine())
                    }
                }
            }
            "session" -> {
                IronvellumButton(
                    label = "Add this workout",
                    onClick = { viewModel.addSession(onDone) },
                    enabled = plan != null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            else -> {
                if (improvement?.changes?.isEmpty() == true) {
                    // Nothing to apply: a disabled Apply beside "Keep
                    // original" offered a choice that did not exist.
                    IronvellumButton(label = "Done", onClick = onDone, modifier = Modifier.fillMaxWidth())
                } else {
                    IronvellumButton(
                        label = "Apply changes",
                        onClick = { viewModel.applyImprovement(onDone) },
                        enabled = improvement != null,
                        gold = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    IronvellumButton(
                        label = "Keep original",
                        onClick = onDone,
                        quiet = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmReplace) {
        AlertDialog(
            // Material's dialog container is a 28dp rounded rect - the most
            // obviously stock surface in the app. Give it the ink shape.
            shape = MaterialTheme.shapes.medium,
            onDismissRequest = { confirmReplace = false },
            title = { Text("Replace your presets?") },
            text = {
                Text(
                    "All ${presets.size} of your current presets will be deleted and the generated " +
                        "week takes their place. Your workout history is untouched - completed sessions stay." +
                        if (viewModel.switchesProgression()) " " + viewModel.progressionLine() else "",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReplace = false
                    viewModel.replacePresets(onDone)
                }) { Text("Replace", color = IronvellumColors.DangerRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReplace = false }) { Text("Keep mine") }
            },
        )
    }
}

private fun tierCaption(hasHistory: Boolean, years: Double?): String {
    // The caption names where the suggestion came from; a lifter with no
    // history must not be told she has "1.4 years of logged sessions".
    val logged = years?.let { "${trimYears(it)} year${if (it == 1.0) "" else "s"}" }
    return when {
        // Selection-independent on purpose: a saved answer can preselect
        // Intermediate, and "starts at beginner volume" then contradicted it.
        !hasHistory -> "No logged sessions yet to suggest a level from - pick where you honestly are."
        else -> "Suggested from $logged of logged sessions."
    }
}

/** One decimal, no trailing .0 - "1.4 years", never "1.4000001 years". */
private fun trimYears(value: Double): String {
    val rounded = (value * 10).toLong() / 10.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
}

/** A question in the app's HUD voice, one panel to itself, like onboarding's. */
@Composable
private fun QuestionPanel(label: String, content: @Composable () -> Unit) {
    InkPanel(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            letterSpacing = IronvellumTracking.InlineLabel,
        )
        Spacer(Modifier.height(8.dp))
        content()
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

/** Multi-select muscle-area chips; selection is both a fill and a brighter edge. */
@Composable
private fun MuscleAreaChips(
    selectedAreas: Set<MuscleArea>,
    onToggle: (MuscleArea) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MuscleArea.entries.chunked(3).forEach { chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                chunk.forEach { area ->
                    val on = area in selectedAreas
                    val shape = MaterialTheme.shapes.extraSmall
                    Text(
                        area.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = ChakraPetch,
                        color = if (on) IronvellumColors.Abyss else IronvellumColors.InkMuted,
                        modifier = Modifier
                            .weight(1f)
                            .clip(shape)
                            .background(if (on) IronvellumColors.SystemGreen else IronvellumColors.VaultHigh)
                            .inkBorder(if (on) IronvellumColors.EmeraldBright else IronvellumColors.Rune, shape, 1.dp)
                            .clickable { onToggle(area) }
                            .padding(horizontal = 8.dp, vertical = 10.dp)
                            // Fill alone is invisible to a screen reader; the
                            // checkbox role and state say what the colour shows.
                            .semantics {
                                role = Role.Checkbox
                                selected = on
                            },
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                if (chunk.size < 3) repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** One of the small drawn day/kind cells, in the preset editor's own style. */
@Composable
private fun PickCell(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    description: String? = null,
    onClick: () -> Unit,
) {
    val shape = MaterialTheme.shapes.small
    OutlinedButton(
        shape = shape,
        onClick = onClick,
        // The unscheduled option is drawn as "—", which a screen reader
        // announces as a dash. Say what it means (same rule as the editor).
        modifier = modifier.then(
            if (description != null) Modifier.semantics { contentDescription = description } else Modifier,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) IronvellumColors.SovereignGold else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
