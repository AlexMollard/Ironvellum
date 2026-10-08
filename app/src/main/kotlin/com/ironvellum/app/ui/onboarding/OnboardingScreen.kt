package com.ironvellum.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.data.FirstRunPrefs
import com.ironvellum.app.data.ProgramAnswers
import com.ironvellum.app.data.ProgramAnswersStore
import com.ironvellum.app.data.Repository
import com.ironvellum.app.data.Seed
import com.ironvellum.app.domain.BodyLimits
import com.ironvellum.app.domain.Equipment
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.FirstRun
import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.VolumeLevel
import com.ironvellum.app.domain.ProgramGenerator
import com.ironvellum.app.domain.ProgramRequest
import com.ironvellum.app.domain.StrengthProfile
import com.ironvellum.app.domain.RoutinePlan
import com.ironvellum.app.domain.Sex
import com.ironvellum.app.domain.TrainingFocus
import com.ironvellum.app.domain.TrainingMode
import com.ironvellum.app.domain.TrainingSplit
import com.ironvellum.app.domain.DecimalInput
import com.ironvellum.app.ui.components.DockedActionBar
import com.ironvellum.app.ui.components.InkChip
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkSegmented
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.formatBodyValue
import com.ironvellum.app.ui.components.decimalKeyboard
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.program.CoveragePreviewScreen
import com.ironvellum.app.ui.program.CoverageSummaryRow
import com.ironvellum.app.ui.program.OptionalEquipmentSaver
import com.ironvellum.app.ui.program.GearPicker
import com.ironvellum.app.ui.program.ProposedDay
import com.ironvellum.app.ui.program.SourcesPanel
import com.ironvellum.app.ui.program.planTexts
import com.ironvellum.app.ui.program.planNotes
import com.ironvellum.app.ui.program.SplitPicker
import com.ironvellum.app.ui.program.splitCaption
import com.ironvellum.app.ui.program.volumeCaption
import com.ironvellum.app.ui.settings.SettingsConfirmDialog
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.theme.ironvellumFieldColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.content.Context
import androidx.core.content.edit
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A plan with no exercises would, taken, replace every existing rite with
 * nothing and then dismiss setup for good. Only a plan that writes something
 * may be taken.
 */
internal fun RoutinePlan.isTakeable(): Boolean = presets.any { it.entries.isNotEmpty() }

class OnboardingViewModel(
    private val repo: Repository,
    private val appContext: Context,
) : ViewModel() {

    private val prefs = appContext.getSharedPreferences("onboarding", Context.MODE_PRIVATE)

    /**
     * Persisted: the comment that called this in-memory argued a skip "must
     * not need a persisted flag" - but the gate's other signal is the null
     * height, and a lifter who skips never writes one. In-memory meant every
     * cold start re-gated her, for the life of the app, until she either
     * completed the profile or skipped again forever.
     */
    private val _dismissed = MutableStateFlow(prefs.getBoolean(KEY_DISMISSED, false))
    val dismissed: StateFlow<Boolean> = _dismissed.asStateFlow()

    /** The Welcome screen stands in front of step 1 until it has been left once, for good. */
    private val _welcomeSeen = MutableStateFlow(FirstRunPrefs.welcomeSeen(appContext))
    val welcomeSeen: StateFlow<Boolean> = _welcomeSeen.asStateFlow()

    fun leaveWelcome() {
        _welcomeSeen.value = true
        FirstRunPrefs.setWelcomeSeen(appContext)
    }

    /**
     * A restore writes the height before it writes anything else, which would drop the gate on its own
     * before the lifter has seen what came back. Hold it open until [restoreFinished] has looked.
     */
    fun restoreStarted() {
        _flowActive.value = true
    }

    /** A restore that brought a body profile and rites has finished setup; anything less leaves setup open. */
    fun restoreFinished() {
        viewModelScope.launch {
            val height = repo.observeBodyProfile().first().first
            val rites = repo.observePresets().first().size
            if (FirstRun.restoreCompletesSetup(height, rites)) {
                leaveWelcome()
                skip()
            }
        }
    }

    /**
     * True from the moment the lifter leaves the profile step. The gate's own
     * signal is the profile height, and saving that height at step 1 would
     * otherwise close the gate and strand her before the training questions
     * and the proposed week. This keeps the flow open for the rest of the
     * process only; a death mid-flow after a saved height reopens on an
     * already-working app.
     */
    private val _flowActive = MutableStateFlow(false)

    /**
     * "Never set up" is the profile's own null height (Entities: "Null = never
     * set"): a fresh install has it, everyone who ever saved a height does not.
     * Null until Room's first emission, so neither gate branch flashes before
     * the truth arrives.
     */
    val needsSetup: StateFlow<Boolean?> =
        combine(repo.observeBodyProfile(), _dismissed, _flowActive) { body, dismissed, active ->
            (body.first == null || active) && !dismissed
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The full movement list, feeding ProgramGenerator. Empty until Room emits. */
    val catalogue: StateFlow<List<Exercise>> =
        repo.observeExercises().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The plan on the review step; null until first generated. */
    private val _plan = MutableStateFlow<RoutinePlan?>(null)
    val plan: StateFlow<RoutinePlan?> = _plan.asStateFlow()

    /** How many rites already exist; taking a cycle replaces them, so the screen asks first. */
    val existingRites: StateFlow<Int> = repo.observePresets()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** True while the reviewed plan is the owner's calisthenics week, whose accept path is applyStarterTemplate. */
    private val _isStarter = MutableStateFlow(false)
    val isStarter: StateFlow<Boolean> = _isStarter.asStateFlow()

    /** Set when a write is refused; shown on the step that made it. Nothing was written. */
    private val _applyError = MutableStateFlow<String?>(null)
    val applyError: StateFlow<String?> = _applyError.asStateFlow()

    /**
     * The choices the current plan was built from. A plain field, not state:
     * only touched from the main thread, and its job is to answer "did a
     * choice actually change", not to be rendered.
     */
    private var planKey: PlanKey? = null

    private data class PlanKey(
        val daysPerWeek: Int,
        val split: TrainingSplit,
        val equipment: Equipment,
        val focus: TrainingFocus,
        val tier: VolumeLevel,
        val sex: Sex,
    )

    /**
     * Height is written BEFORE the weigh-in on purpose: addStat stamps the
     * profile height onto the stat row, so the wrong order would land the very
     * first reading heightless with BMI stuck at the dash.
     */
    fun saveProfile(name: String, sex: Sex, heightCm: Double, weightKg: Double) {
        _flowActive.value = true
        viewModelScope.launch {
            runCatching {
                if (name.trim().isNotEmpty()) repo.rename(name)
                repo.setSex(sex)
                repo.setHeight(heightCm)
                repo.logTodaysWeight(weightKg)
            }.onFailure {
                _applyError.value = "Could not save your details" + (it.message?.let { m -> ": $m" } ?: "")
            }
        }
    }

    /**
     * Regenerates the proposal only when a choice actually changed (or there
     * is nothing yet, or she is returning from the starter template).
     * Returning to the review step after hand-editing entries therefore keeps
     * the edits: same key, same plan.
     */
    fun ensurePlan(
        daysPerWeek: Int,
        split: TrainingSplit,
        equipment: Equipment,
        focus: TrainingFocus,
        tier: VolumeLevel,
        sex: Sex,
        catalogue: List<Exercise>,
        force: Boolean = false,
    ) {
        val key = PlanKey(daysPerWeek, split, equipment, focus, tier, sex)
        if (force || _plan.value == null || _isStarter.value || planKey != key) {
            // RoutineBuilder is gone: one generator everywhere, and the first
            // run now uses the same evidence-backed engine as Train ->
            // NEW PRESET. No history exists yet, so the strength profile the
            // loads would come from is honestly empty.
            _plan.value = ProgramGenerator.week(
                ProgramRequest(
                    focus = focus,
                    volume = tier,
                    equipment = equipment,
                    daysPerWeek = daysPerWeek,
                    sex = sex,
                    split = split,
                ),
                catalogue,
                StrengthProfile(),
            )
            _isStarter.value = false
            planKey = key
            _applyError.value = null
        }
    }

    /** Swaps the review for the owner's calisthenics week, shown read-only (see ProposalStep). */
    fun previewStarter() {
        if (!_isStarter.value) {
            _plan.value = Seed.starterPlan()
            _isStarter.value = true
        }
    }

    /** Applies a hand edit to sets, reps or membership made on the review step. */
    fun replacePlan(plan: RoutinePlan) {
        if (!_isStarter.value) _plan.value = plan
    }

    /**
     * Accept writes the whole week through the repository's transactional
     * replace; on refusal nothing was written, so the lifter stays on the
     * review step with the reason on screen. Dismissing afterwards releases
     * the gate the same way a skip does - the profile height, once saved, is
     * the durable half of that signal.
     *
     * On the generated path her answers are remembered (Weekly Coverage and
     * the next builder visit read them back) and the progression mode follows
     * the goal, so a hypertrophy plan never runs on the strength engine.
     */
    fun acceptRoutine(
        tier: VolumeLevel,
        focus: TrainingFocus,
        equipment: Equipment,
        daysPerWeek: Int,
        split: TrainingSplit,
    ) {
        val plan = _plan.value ?: return
        if (!plan.isTakeable()) {
            _applyError.value = "This cycle has no exercises. Rebuild it from your answers first. Nothing was written."
            return
        }
        val generated = !_isStarter.value
        viewModelScope.launch {
            runCatching {
                if (_isStarter.value) repo.applyStarterTemplate() else repo.applyRoutine(plan)
                // Every focus the question offers must land in the profile, or
                // Settings' TRAINING MODE silently shows the STRENGTH default
                // for Skills and Mixed. Skill practice is low-rep and
                // load-driven, so it follows the strength engine; a mixed goal
                // suits double progression (reps first, then load).
                when (focus) {
                    TrainingFocus.STRENGTH, TrainingFocus.SKILL ->
                        repo.setTrainingMode(TrainingMode.STRENGTH)
                    TrainingFocus.MUSCLE, TrainingFocus.GENERAL ->
                        repo.setTrainingMode(TrainingMode.HYPERTROPHY)
                }
                if (generated) {
                    ProgramAnswersStore.save(
                        appContext,
                        ProgramAnswers(tier, focus, equipment, daysPerWeek, emptySet(), split),
                    )
                }
            }.fold(
                onSuccess = {
                    _applyError.value = null
                    _dismissed.value = true
                    prefs.edit { putBoolean(KEY_DISMISSED, true) }
                },
                onFailure = {
                    _applyError.value = "Could not save the cycle" +
                        (it.message?.let { m -> ": $m" } ?: "") + ". Nothing was written."
                },
            )
        }
    }

    fun skip() {
        _dismissed.value = true
        prefs.edit { putBoolean(KEY_DISMISSED, true) }
    }

    companion object {
        private const val KEY_DISMISSED = "dismissed"
    }
}


/** "your name, height and a first weigh-in" - a list a person reads, not "a, b, and c". */
private fun joinHuman(items: List<String>): String = when (items.size) {
    0 -> ""
    1 -> items[0]
    else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
}

/**
 * What sits above Continue on step 1: what is still missing, and what is typed but out of range. An
 * out-of-range figure is named as such, never as missing, with the range the repository enforces.
 */
internal fun profileHint(nameMissing: Boolean, height: BodyLimits.Reading, weight: BodyLimits.Reading): String? {
    val missing = buildList {
        if (nameMissing) add("a name")
        if (height == BodyLimits.Reading.EMPTY) add("height")
        if (weight == BodyLimits.Reading.EMPTY) add("weight")
    }
    val outOfRange = buildList {
        if (height == BodyLimits.Reading.OUT_OF_RANGE) add(heightRangeLine())
        if (weight == BodyLimits.Reading.OUT_OF_RANGE) add(weightRangeLine())
    }
    return when {
        missing.isEmpty() && outOfRange.isEmpty() -> null
        outOfRange.isEmpty() -> "Add ${joinHuman(missing)} to continue."
        missing.isEmpty() -> outOfRange.joinToString(" ")
        else -> "Add ${joinHuman(missing)}. ${outOfRange.joinToString(" ")}"
    }
}

private fun heightRangeLine() = "Height must be ${BodyLimits.say(BodyLimits.HEIGHT_CM)} cm."

private fun weightRangeLine() = "Weight must be ${BodyLimits.say(BodyLimits.WEIGHT_KG)} kg."

/**
 * First-run setup, walked in three windows: who you are, how you train, and a
 * proposed week built from the real catalogue that she can review and change
 * before accepting. Every number this app shows a new lifter is body-scaled -
 * strength scores need a bodyweight, BMI and FFMI need a height, the Navy
 * estimator needs a sex - which is why the profile comes first.
 *
 * The half-automatic part is the proposal: ProgramGenerator proposes, she
 * disposes. The owner's own calisthenics week is offered as an alternative
 * template instead of being forced on every fresh install.
 *
 * All step answers live HERE in rememberSaveable, not inside the step
 * composables: a step leaving the composition as she advances would otherwise
 * drop its state, and "back must not lose answers" is a hard requirement.
 *
 * Structure: one scaffold owns the screen - a header band (rune progress
 * rail, the step's own title, one line of prose), the step's content taking
 * every remaining pixel, and the actions pinned at the bottom where a thumb
 * lands. Prose was cut to one line a step; the old three-line skip caveat is
 * folded into the skip control's own accessibility description.
 */
@Composable
fun OnboardingScreen(
    appContext: Context = LocalContext.current,
    viewModel: OnboardingViewModel = viewModel(
        factory = viewModelFactory { initializer {
                    val app = appContext.applicationContext
                    OnboardingViewModel(ironvellumRepository(), app)
                    } },
    ),
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val welcomeSeen by viewModel.welcomeSeen.collectAsStateWithLifecycle()
    if (!welcomeSeen) {
        WelcomeScreen(
            onBegin = viewModel::leaveWelcome,
            onRestoreStarted = viewModel::restoreStarted,
            onRestoreFinished = viewModel::restoreFinished,
        )
        return
    }

    // Step 1 answers.
    var name by rememberSaveable { mutableStateOf("") }
    var sex by rememberSaveable { mutableStateOf(Sex.MALE) }
    var heightInput by rememberSaveable { mutableStateOf("") }
    var weightInput by rememberSaveable { mutableStateOf("") }

    // Step 2 answers.
    var daysPerWeek by rememberSaveable { mutableIntStateOf(3) }
    var split by rememberSaveable { mutableStateOf(TrainingSplit.FULL_BODY) }
    // No default: the armoury steers every cycle, so the lifter answers it.
    var equipment by rememberSaveable(stateSaver = OptionalEquipmentSaver) { mutableStateOf<Equipment?>(null) }
    var focus by rememberSaveable { mutableStateOf(TrainingFocus.GENERAL) }
    var tier by rememberSaveable { mutableStateOf(VolumeLevel.LOW) }

    val applyError by viewModel.applyError.collectAsStateWithLifecycle()
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    val existingRites by viewModel.existingRites.collectAsStateWithLifecycle()
    var confirmReplace by remember { mutableStateOf(false) }
    val takeCycle = {
        equipment?.let { viewModel.acceptRoutine(tier, focus, it, daysPerWeek, split) }
        Unit
    }
    if (confirmReplace) {
        SettingsConfirmDialog(
            title = "Replace your rites?",
            text = "Taking this cycle replaces your $existingRites existing " +
                "${if (existingRites == 1) "rite" else "rites"}. Your Chronicle is kept. This cannot be undone.",
            confirmLabel = "Replace",
            dismissLabel = "Keep my rites",
            danger = true,
            onConfirm = {
                confirmReplace = false
                takeCycle()
            },
            onDismiss = { confirmReplace = false },
        )
    }

    // The same bounds the repository enforces, checked here so the footer can
    // name what is missing instead of leaving a dead button to explain itself.
    val heightReading = BodyLimits.readHeight(heightInput)
    val weightReading = BodyLimits.readWeight(weightInput)
    val profileValid = name.trim().isNotEmpty() &&
        heightReading == BodyLimits.Reading.VALID && weightReading == BodyLimits.Reading.VALID
    val profileHint = profileHint(name.trim().isEmpty(), heightReading, weightReading)

    // Hoisted so the page keeps its place while the coverage page is up.
    val pageScroll = rememberScrollState()
    var coverageOpen by rememberSaveable { mutableStateOf(false) }
    val coverageOf = plan?.presets
    if (coverageOpen && step == 2 && coverageOf != null) {
        BackHandler { coverageOpen = false }
        Column(
            Modifier
                .fillMaxSize()
                .background(IronvellumColors.Abyss)
                .statusBarsPadding(),
        ) {
            CoveragePreviewScreen(coverageOf, tier, focus, emptySet()) { coverageOpen = false }
        }
        return
    }

    // Deliberately not saved: a rotation should not restore a keyboard, and the
    // back contract below only cares about focus as it stands right now.
    var fieldFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // Back belongs to the flow, not the task stack: with a field focused it
    // puts the keyboard away, past the first step it walks a step back, and
    // only on step one untouched does it leave the app - which is what back
    // means on a first screen. Without this, one press during a weigh-in
    // dismissed the keyboard AND killed the app.
    BackHandler(enabled = fieldFocused || step > 0) {
        if (fieldFocused) {
            focusManager.clearFocus()
        } else {
            step -= 1
        }
    }

    // Flat Abyss, the app's own surface: the bar and Skip on top, the page
    // taking every remaining pixel and scrolling when it does not fit (the
    // keyboard up, a large font, landscape), and the action bar docked below
    // it so Continue is always in reach.
    Column(
        Modifier
            .fillMaxSize()
            .background(IronvellumColors.Abyss)
            .imePadding()
            .statusBarsPadding(),
    ) {
        StepBar(step = step, onBack = { step -= 1 }, onSkip = viewModel::skip)

        // A step is a plain Column inside this one scroller: never a lazy list
        // or a second scroller nested in it - that pairing crashes at runtime
        // in this repo.
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(pageScroll)
                .padding(horizontal = 16.dp),
        ) {
            StepHeader(step = step)
            Box(
                Modifier
                    .fillMaxWidth()
                    .focusGroup()
                    .onFocusChanged { fieldFocused = it.hasFocus },
            ) {
                when (step) {
                    0 -> ProfileStep(
                        name = name,
                        onName = { name = it },
                        sex = sex,
                        onSex = { sex = it },
                        heightInput = heightInput,
                        onHeight = { heightInput = it },
                        weightInput = weightInput,
                        onWeight = { weightInput = it },
                        heightReading = heightReading,
                        weightReading = weightReading,
                    )
                    1 -> TrainingStep(
                        split = split,
                        daysPerWeek = daysPerWeek,
                        onSplit = { s, d -> split = s; daysPerWeek = d },
                        equipment = equipment,
                        onEquipment = { equipment = it },
                        focus = focus,
                        onFocus = { focus = it },
                        tier = tier,
                        onTier = { tier = it },
                    )
                    else -> ProposalStep(
                        viewModel = viewModel,
                        daysPerWeek = daysPerWeek,
                        split = split,
                        // Step 3 is reachable only once the armoury is answered.
                        equipment = equipment ?: Equipment.NOTHING,
                        focus = focus,
                        tier = tier,
                        sex = sex,
                        onOpenCoverage = { coverageOpen = true },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        StepNotes(
            step = step,
            profileHint = profileHint,
            planReady = plan?.isTakeable() == true,
            planEmpty = plan != null && plan?.isTakeable() != true,
            armouryPicked = equipment != null,
            applyError = applyError,
        )
        // Taking the cycle is the one primary of the last step, emerald like
        // every other screen's: gold is for what was earned, not for accepting.
        when (step) {
            0 -> DockedActionBar(
                primary = "Continue",
                onPrimary = {
                    // Only reachable when profileValid, so the !! is safe -
                    // the same bounds the repository enforces, checked first.
                    viewModel.saveProfile(name, sex, DecimalInput.parse(heightInput)!!, DecimalInput.parse(weightInput)!!)
                    step = 1
                },
                primaryEnabled = profileValid,
                reserveLink = false,
            )
            1 -> DockedActionBar(
                primary = "Continue",
                onPrimary = { step = 2 },
                primaryEnabled = equipment != null,
                reserveLink = false,
            )
            else -> DockedActionBar(
                primary = "Take this cycle",
                onPrimary = {
                    // Taking a cycle replaces every existing rite: never silently.
                    if (existingRites > 0) confirmReplace = true else takeCycle()
                },
                primaryEnabled = plan?.isTakeable() == true,
                reserveLink = false,
            )
        }
    }
}

/** Why Continue is off on the training step until the armoury is answered. */
private const val ARMOURY_REQUIRED_CAPTION = "Pick your armoury to continue. Nothing means bodyweight only."

/** Per-step title and one line of prose. "Welcome, lifter" on every step was a bug, not a header. */
private fun stepTitle(step: Int): String = when (step) {
    0 -> "Who you are"
    1 -> "How you train"
    else -> "Your cycle"
}

private fun stepProse(step: Int): String = when (step) {
    0 -> "Your sex, height and weight scale every number Ironvellum shows you."
    1 -> "Answer four things and the Forge builds you a cycle."
    else -> "Built from your answers. Tap a day to open it, an exercise to adjust it."
}

/**
 * The top bar: Back (past the first step) and Skip as outlined chips. Skip's
 * consequence is on the quiet line below the bar and again in its own
 * accessibility description.
 */
@Composable
private fun StepBar(step: Int, onBack: () -> Unit, onSkip: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (step > 0) InkChip(label = "BACK", description = "Back to ${stepTitle(step - 1).lowercase()}", onClick = onBack)
        Spacer(Modifier.weight(1f))
        InkChip(
            label = "SKIP",
            description = "Skip setup. Set your details and cycle later in Settings or the Ledger",
            onClick = onSkip,
        )
    }
}

/**
 * The page head: the skip caveat, one three-segment bar for where she is, the
 * step's own title and one line of prose.
 */
@Composable
private fun StepHeader(step: Int) {
    Column(Modifier.fillMaxWidth()) {
        // Sighted people get the cost of skipping too, not only TalkBack.
        Text(
            "Skipping leaves your scores blank and builds no cycle. Add your body in Settings and build a cycle in Train any time.",
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {
                    contentDescription = "Step ${step + 1} of 3"
                    progressBarRangeInfo = ProgressBarRangeInfo(step + 1f, 1f..3f, 1)
                },
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            repeat(3) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .background(if (index <= step) IronvellumColors.Emerald else IronvellumColors.Rune),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stepTitle(step),
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = IronvellumColors.Ink,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stepProse(step),
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
        )
        Spacer(Modifier.height(14.dp))
    }
}

/**
 * What sits between the scrolling page and the docked bar: the refused-write
 * error in DangerRed and, when the primary cannot proceed, the missing pieces
 * named quietly right above it. Nothing is drawn when there is nothing to say.
 */
@Composable
private fun StepNotes(
    step: Int,
    profileHint: String?,
    planReady: Boolean,
    planEmpty: Boolean,
    armouryPicked: Boolean,
    applyError: String?,
) {
    val hint = when {
        step == 0 -> profileHint
        step == 1 && !armouryPicked -> ARMOURY_REQUIRED_CAPTION
        step >= 2 && !planReady ->
            if (planEmpty) "Add exercises back or rebuild to take a cycle." else "Still consulting the catalogue…"
        else -> null
    }
    if (applyError == null && hint == null) return
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (applyError != null) {
            Text(applyError, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.DangerRed)
        }
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
        }
    }
}

/**
 * Step 1 - who you are. Unchanged in substance from the single-window days:
 * name, sex, height and the first weigh-in. Saved at Continue, not at the
 * end of the flow, so a lifter lost at step 2 still carries her numbers.
 */
@Composable
private fun ProfileStep(
    name: String,
    onName: (String) -> Unit,
    sex: Sex,
    onSex: (Sex) -> Unit,
    heightInput: String,
    onHeight: (String) -> Unit,
    weightInput: String,
    onWeight: (String) -> Unit,
    heightReading: BodyLimits.Reading,
    weightReading: BodyLimits.Reading,
) {
    // A figure is only called out of range once the lifter has left its field: "1" and "17" on the way
    // to "175" are out of range too.
    var heightLeft by rememberSaveable { mutableStateOf(false) }
    var weightLeft by rememberSaveable { mutableStateOf(false) }
    val heightOff = heightLeft && heightReading == BodyLimits.Reading.OUT_OF_RANGE
    val weightOff = weightLeft && weightReading == BodyLimits.Reading.OUT_OF_RANGE
    // The screen scrolls as a whole (see OnboardingScreen), so a step is a plain
    // Column: never a lazy list or a second scroller nested inside - that
    // pairing crashes at runtime in this repo.
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            shape = MaterialTheme.shapes.small,
            colors = ironvellumFieldColors(),
            value = name,
            onValueChange = { onName(it.take(24)) },
            label = { Text("Your name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                shape = MaterialTheme.shapes.small,
                colors = ironvellumFieldColors(),
                value = heightInput,
                onValueChange = { onHeight(DecimalInput.sanitize(it, maxDecimals = 1, maxLength = 5)) },
                label = { Text("Height (cm)") },
                singleLine = true,
                isError = heightOff,
                keyboardOptions = decimalKeyboard(ImeAction.Next),
                modifier = Modifier.weight(1f).onFieldLeft { heightLeft = true },
            )
            OutlinedTextField(
                shape = MaterialTheme.shapes.small,
                colors = ironvellumFieldColors(),
                value = weightInput,
                onValueChange = { onWeight(DecimalInput.sanitize(it, maxDecimals = 1, maxLength = 5)) },
                label = { Text("Weight (kg)") },
                singleLine = true,
                isError = weightOff,
                keyboardOptions = decimalKeyboard(ImeAction.Done),
                modifier = Modifier.weight(1f).onFieldLeft { weightLeft = true },
            )
        }
        // The limits the repository enforces, said before they bite; a figure outside them says so.
        val ranges = listOfNotNull(
            heightRangeLine().takeIf { heightOff },
            weightRangeLine().takeIf { weightOff },
        )
        Text(
            if (ranges.isEmpty()) {
                "Height ${BodyLimits.say(BodyLimits.HEIGHT_CM)} cm. Weight ${BodyLimits.say(BodyLimits.WEIGHT_KG)} kg."
            } else {
                ranges.joinToString(" ")
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (ranges.isEmpty()) IronvellumColors.InkMuted else IronvellumColors.DangerRed,
            modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.height(14.dp))
        FieldLabel("Sex")
        InkSegmented(
            options = Sex.entries.map { it to it.name.lowercase().replaceFirstChar(Char::titlecase) },
            selected = sex,
            onPick = onSex,
        )
    }
}

/** Runs [onLeft] when the field it modifies loses focus after having had it. */
private fun Modifier.onFieldLeft(onLeft: () -> Unit): Modifier = composed {
    var had by remember { mutableStateOf(false) }
    onFocusChanged {
        if (it.isFocused) had = true else if (had) onLeft()
    }
}

/**
 * Step 2 - how you train. Plain language on every control: a stranger does
 * not know what SKILL means, and an enum name is never user-facing text.
 * Four stacked questions, each a muted label, its control and one caption.
 * The split leads: it is the choice lifters think in; volume is the dose on
 * top of it.
 */
@Composable
private fun TrainingStep(
    split: TrainingSplit,
    daysPerWeek: Int,
    onSplit: (TrainingSplit, Int) -> Unit,
    equipment: Equipment?,
    onEquipment: (Equipment) -> Unit,
    focus: TrainingFocus,
    onFocus: (TrainingFocus) -> Unit,
    tier: VolumeLevel,
    onTier: (VolumeLevel) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        FieldLabel("How you divide the week")
        SplitPicker(split = split, days = daysPerWeek, onPick = onSplit)
        FieldCaption(splitCaption(split, daysPerWeek))

        Spacer(Modifier.height(12.dp))
        FieldLabel("Weekly volume")
        InkSegmented(
            options = VolumeLevel.entries.map { it to it.label },
            selected = tier,
            onPick = onTier,
        )
        FieldCaption(volumeCaption(tier, focus))

        Spacer(Modifier.height(12.dp))
        FieldLabel("Your armoury")
        GearPicker(equipment = equipment, onChange = onEquipment)

        Spacer(Modifier.height(12.dp))
        FieldLabel("What you are chasing")
        InkSegmented(
            options = listOf(
                TrainingFocus.STRENGTH to "Power",
                TrainingFocus.MUSCLE to "Muscle",
                TrainingFocus.SKILL to "Techniques",
                // "All-round" clipped to "All-roun" at 360dp: four equal
                // segments leave ~72dp each. Keep every label short.
                TrainingFocus.GENERAL to "Mixed",
            ),
            selected = focus,
            onPick = onFocus,
        )
        FieldCaption(
            when (focus) {
                TrainingFocus.STRENGTH ->
                    "Low reps, heavy load. Bigger numbers on the main exercises."
                TrainingFocus.MUSCLE ->
                    "Higher volume, moderate load. Size comes before bragging rights."
                TrainingFocus.SKILL ->
                    "Handstands, levers, the planche. Practice over pump."
                TrainingFocus.GENERAL ->
                    "A balanced mix: a bit stronger, a bit bigger, nothing neglected."
            },
        )
    }
}

/** A question's label: sentence case, InkMuted. */
@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = IronvellumColors.InkMuted,
        modifier = Modifier
            .padding(bottom = 4.dp)
            .semantics { contentDescription = text },
    )
}

/** The one line under a control saying what the pick does. */
@Composable
private fun FieldCaption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = IronvellumColors.InkMuted,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/**
 * Step 3 - the proposed week, shown in full: every day, every movement, sets
 * and reps. Generated plans are editable (sets, reps, removal); the owner's
 * calisthenics template is shown read-only because accepting it goes through
 * applyStarterTemplate, which restores the hand-written preset notes that a
 * round-trip through the plan would silently drop. Changing a choice upstream
 * rebuilds the plan; coming back without changing one keeps the edits.
 *
 * This is the payoff step, so the day cards lead with the training plan -
 * day and focus legible at a glance - and the edit glyphs sit beneath each
 * movement as clearly secondary.
 */
@Composable
private fun ProposalStep(
    viewModel: OnboardingViewModel,
    daysPerWeek: Int,
    split: TrainingSplit,
    equipment: Equipment,
    focus: TrainingFocus,
    tier: VolumeLevel,
    sex: Sex,
    onOpenCoverage: () -> Unit,
) {
    val catalogue by viewModel.catalogue.collectAsStateWithLifecycle()
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    val isStarter by viewModel.isStarter.collectAsStateWithLifecycle()

    // Rebuild only when an upstream choice actually changed (the guard lives
    // in the view model). Waiting for a non-empty catalogue means the first
    // generation is never run against a half-loaded Room list.
    LaunchedEffect(daysPerWeek, split, equipment, focus, tier, sex, catalogue) {
        if (catalogue.isNotEmpty()) {
            viewModel.ensurePlan(daysPerWeek, split, equipment, focus, tier, sex, catalogue)
        }
    }

    fun editEntry(presetIndex: Int, entryIndex: Int, transform: (PlannedEntry) -> PlannedEntry) {
        val current = plan ?: return
        val preset = current.presets[presetIndex]
        val entries = preset.entries.toMutableList()
        entries[entryIndex] = transform(entries[entryIndex])
        viewModel.replacePlan(
            current.copy(
                presets = current.presets.toMutableList()
                    .also { it[presetIndex] = preset.copy(entries = entries) },
            ),
        )
    }

    fun removeEntry(presetIndex: Int, entryIndex: Int) {
        val current = plan ?: return
        val preset = current.presets[presetIndex]
        val entries = preset.entries.filterIndexed { i, _ -> i != entryIndex }
        val presets = if (entries.isEmpty()) {
            current.presets.filterIndexed { i, _ -> i != presetIndex }
        } else {
            current.presets.toMutableList().also { it[presetIndex] = preset.copy(entries = entries) }
        }
        viewModel.replacePlan(current.copy(presets = presets))
    }

    Column(Modifier.fillMaxWidth()) {
        val current = plan
        if (current == null) {
            InkPanel(Modifier.fillMaxWidth()) {
                Text(
                    "Consulting the catalogue…",
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
            }
        } else {
            current.presets.forEachIndexed { presetIndex, preset ->
                if (presetIndex > 0) InkDivider()
                ProposedDay(
                    preset = preset,
                    editable = !isStarter,
                    onSets = { entryIndex, delta ->
                        editEntry(presetIndex, entryIndex) {
                            it.copy(sets = (it.sets + delta).coerceIn(1, 10))
                        }
                    },
                    onReps = { entryIndex, delta ->
                        editEntry(presetIndex, entryIndex) {
                            it.copy(reps = (it.reps + delta).coerceIn(1, 30))
                        }
                    },
                    onRemove = { entryIndex -> removeEntry(presetIndex, entryIndex) },
                    showNote = false,
                    folded = true,
                    startOpen = presetIndex == 0,
                )
            }
            if (current.presets.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                // The same coverage row as the Forge, with no priorities picked at this point.
                CoverageSummaryRow(current.presets, tier, focus, emptySet(), onOpenCoverage)
            }
            if (current.presets.isEmpty()) {
                Text(
                    "Nothing left. Rebuild from your answers, or take the starter cycle instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
                if (!isStarter) {
                    IronvellumButton(
                        label = "Rebuild from my answers",
                        onClick = {
                            if (catalogue.isNotEmpty()) {
                                viewModel.ensurePlan(daysPerWeek, split, equipment, focus, tier, sex, catalogue, force = true)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            // The rest guidance and any shortfall, said once for the routine
            // rather than under every day, with the progression rule the goal
            // picked beside it.
            val notes = planNotes(current) + when (focus) {
                TrainingFocus.STRENGTH -> listOf("Strength goal: load goes up when you hit your reps.")
                TrainingFocus.MUSCLE -> listOf("Muscle goal: reps go up first, then load.")
                else -> emptyList()
            }
            notes.forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        if (!isStarter) {
            IronvellumButton(
                label = "Use the starter cycle",
                onClick = viewModel::previewStarter,
                quiet = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            IronvellumButton(
                label = "Forge from my answers",
                onClick = {
                    if (catalogue.isNotEmpty()) {
                        viewModel.ensurePlan(daysPerWeek, split, equipment, focus, tier, sex, catalogue, force = true)
                    }
                },
                quiet = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        current?.let { SourcesPanel(planTexts(it.presets, it.note)) }
        Spacer(Modifier.height(2.dp))
    }
}
