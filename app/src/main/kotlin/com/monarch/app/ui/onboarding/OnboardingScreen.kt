package com.monarch.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.monarch.app.data.Repository
import com.monarch.app.data.Seed
import com.monarch.app.domain.BodyLimits
import com.monarch.app.domain.EquipmentAccess
import com.monarch.app.domain.Exercise
import com.monarch.app.domain.PlannedEntry
import com.monarch.app.domain.PlannedPreset
import com.monarch.app.domain.RoutineBuilder
import com.monarch.app.domain.RoutinePlan
import com.monarch.app.domain.Sex
import com.monarch.app.domain.TrainingFocus
import com.monarch.app.ui.components.CrestMark
import com.monarch.app.ui.components.InkRail
import com.monarch.app.ui.components.InkSegmented
import com.monarch.app.ui.components.MonarchButton
import com.monarch.app.ui.components.SystemWindow
import com.monarch.app.ui.components.formatBodyValue
import com.monarch.app.ui.monarchRepository
import com.monarch.app.ui.theme.ChakraPetch
import com.monarch.app.ui.theme.MonarchColors
import com.monarch.app.ui.theme.MonarchTracking
import com.monarch.app.ui.theme.inkBorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val repo: Repository,
) : ViewModel() {

    /** In-memory only: a skip must not need a new persisted flag to hold for this process. */
    private val _dismissed = MutableStateFlow(false)
    val dismissed: StateFlow<Boolean> = _dismissed.asStateFlow()

    /**
     * True from the moment the hunter leaves the profile step. The gate's own
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

    /** The full movement list, feeding RoutineBuilder. Empty until Room emits. */
    val catalogue: StateFlow<List<Exercise>> =
        repo.observeExercises().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The plan on the review step; null until first generated. */
    private val _plan = MutableStateFlow<RoutinePlan?>(null)
    val plan: StateFlow<RoutinePlan?> = _plan.asStateFlow()

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
    private var planKey: Triple<Int, EquipmentAccess, TrainingFocus>? = null

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
                repo.addStat(weightKg, null)
            }.onFailure {
                _applyError.value = "Could not save your profile: ${it.message}"
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
        equipment: EquipmentAccess,
        focus: TrainingFocus,
        catalogue: List<Exercise>,
        force: Boolean = false,
    ) {
        val key = Triple(daysPerWeek, equipment, focus)
        if (force || _plan.value == null || _isStarter.value || planKey != key) {
            _plan.value = RoutineBuilder.plan(daysPerWeek, equipment, focus, catalogue)
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
     * replace; on refusal nothing was written, so the hunter stays on the
     * review step with the reason on screen. Dismissing afterwards releases
     * the gate the same way a skip does - the profile height, once saved, is
     * the durable half of that signal.
     */
    fun acceptRoutine() {
        val plan = _plan.value ?: return
        viewModelScope.launch {
            runCatching {
                if (_isStarter.value) repo.applyStarterTemplate() else repo.applyRoutine(plan)
            }.fold(
                onSuccess = {
                    _applyError.value = null
                    _dismissed.value = true
                },
                onFailure = {
                    _applyError.value = "Could not save the routine: ${it.message}. Nothing was written."
                },
            )
        }
    }

    fun skip() {
        _dismissed.value = true
    }
}

private val DAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

private fun dayLabel(scheduledDay: Int): String =
    DAY_NAMES.getOrNull(scheduledDay - 1) ?: "Unscheduled"

private fun entryScheme(entry: PlannedEntry): String = buildString {
    append(entry.sets)
    append(" x ")
    append(entry.reps)
    entry.targetWeightKg?.let {
        append(" @ ")
        append(formatBodyValue(it))
        append(" kg")
    }
}

/** "your name, height and a first weigh-in" - a list a person reads, not "a, b, and c". */
private fun joinHuman(items: List<String>): String = when (items.size) {
    0 -> ""
    1 -> items[0]
    else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
}

/**
 * First-run setup, walked in three windows: who you are, how you train, and a
 * proposed week built from the real catalogue that she can review and change
 * before accepting. Every number this app shows a new hunter is body-scaled -
 * strength scores need a bodyweight, BMI and FFMI need a height, the Navy
 * estimator needs a sex - which is why the profile comes first.
 *
 * The half-automatic part is the proposal: RoutineBuilder proposes, she
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
    viewModel: OnboardingViewModel = viewModel(
        factory = viewModelFactory { initializer { OnboardingViewModel(monarchRepository()) } },
    ),
) {
    var step by rememberSaveable { mutableIntStateOf(0) }

    // Step 1 answers.
    var name by rememberSaveable { mutableStateOf("") }
    var sex by rememberSaveable { mutableStateOf(Sex.MALE) }
    var heightInput by rememberSaveable { mutableStateOf("") }
    var weightInput by rememberSaveable { mutableStateOf("") }

    // Step 2 answers.
    var daysPerWeek by rememberSaveable { mutableIntStateOf(3) }
    var equipment by rememberSaveable { mutableStateOf(EquipmentAccess.BODYWEIGHT) }
    var focus by rememberSaveable { mutableStateOf(TrainingFocus.GENERAL) }

    val applyError by viewModel.applyError.collectAsStateWithLifecycle()
    val plan by viewModel.plan.collectAsStateWithLifecycle()

    // The same bounds the repository enforces, checked here so the footer can
    // name what is missing instead of leaving a dead button to explain itself.
    val missing = buildList {
        if (name.trim().isEmpty()) add("your name")
        if (!BodyLimits.validHeight(heightInput.toDoubleOrNull())) add("height")
        if (!BodyLimits.validWeight(weightInput.toDoubleOrNull())) add("a first weigh-in")
    }
    val profileValid = missing.isEmpty()

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(MonarchColors.Abyss, Color(0xFF111110)))),
    ) {
        // The app's own crest, faint, anchoring the space the old layout left
        // dead. Decorative: not announced, not interactive.
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.BottomCenter) {
            CrestMark(
                "monarch",
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .alpha(0.07f),
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            StepHeader(step = step, onSkip = viewModel::skip)
            Spacer(Modifier.height(20.dp))

            Box(Modifier.weight(1f)) {
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
                    )
                    1 -> TrainingStep(
                        daysPerWeek = daysPerWeek,
                        onDays = { daysPerWeek = it },
                        equipment = equipment,
                        onEquipment = { equipment = it },
                        focus = focus,
                        onFocus = { focus = it },
                    )
                    else -> ProposalStep(
                        viewModel = viewModel,
                        daysPerWeek = daysPerWeek,
                        equipment = equipment,
                        focus = focus,
                    )
                }
            }

            StepFooter(
                step = step,
                missing = missing,
                profileValid = profileValid,
                planReady = plan != null,
                applyError = applyError,
                onContinueProfile = {
                    // Only reachable when profileValid, so the !! is safe -
                    // the same bounds the repository enforces, checked first.
                    viewModel.saveProfile(name, sex, heightInput.toDoubleOrNull()!!, weightInput.toDoubleOrNull()!!)
                    step = 1
                },
                onBack = { step -= 1 },
                onForward = { step = 2 },
                onAccept = viewModel::acceptRoutine,
            )
        }
    }
}

/** Per-step title and one line of prose. "WELCOME, HUNTER" on every step was a bug, not a header. */
private fun stepTitle(step: Int): String = when (step) {
    0 -> "WHO YOU ARE"
    1 -> "HOW YOU TRAIN"
    else -> "YOUR PROPOSED WEEK"
}

private fun stepProse(step: Int): String = when (step) {
    0 -> "Three facts scale every number this app shows you."
    1 -> "Three answers, and the forge proposes a week."
    else -> "Built from your answers. Change anything before you take it."
}

/** Rune numerals, the skill tree's own counting: I, II, III. Reads as a quiet crest row, not a progress bar. */
@Composable
private fun StepRunes(step: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clearAndSetSemantics { contentDescription = "Step ${step + 1} of 3" },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf("I", "II", "III").forEachIndexed { index, rune ->
            Text(
                rune,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = when {
                    index == step -> MonarchColors.EmeraldBright
                    index < step -> MonarchColors.SystemGreen
                    else -> MonarchColors.Bracket
                },
                letterSpacing = MonarchTracking.InlineLabel,
            )
        }
    }
}

/**
 * The header band: where she is (runes + ink rail), what this step is, and the
 * skip affordance, which carries its consequence in its own description
 * instead of a three-line caveat under the form.
 */
@Composable
private fun StepHeader(step: Int, onSkip: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepRunes(step, Modifier.weight(1f))
            Box(
                Modifier
                    .heightIn(min = 40.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable(onClick = onSkip)
                    .semantics {
                        role = Role.Button
                        contentDescription =
                            "Skip setup - set your profile and routine later in Settings or the Stats log"
                    }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "SKIP",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = ChakraPetch,
                    color = MonarchColors.SystemGreen,
                    letterSpacing = MonarchTracking.InlineLabel,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        // The filled portion grows with her progress; the rail is the same
        // brushed stroke the dashboard and the Codex use.
        InkRail(fraction = (step + 1f) / 3f, seed = step + 1, height = 4.dp)
        Spacer(Modifier.height(22.dp))
        Text(
            stepTitle(step),
            style = MaterialTheme.typography.titleLarge,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = MonarchColors.EmeraldBright,
            letterSpacing = MonarchTracking.ScreenTitle,
            modifier = Modifier.semantics { contentDescription = stepTitle(step).lowercase() },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stepProse(step),
            style = MaterialTheme.typography.bodySmall,
            color = MonarchColors.InkMuted,
        )
    }
}

/**
 * Actions pinned at the bottom, where a thumb lands. The disabled state
 * speaks: when the primary cannot proceed, the missing pieces are named
 * inline, quiet, right above the button.
 */
@Composable
private fun StepFooter(
    step: Int,
    missing: List<String>,
    profileValid: Boolean,
    planReady: Boolean,
    applyError: String?,
    onContinueProfile: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onAccept: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        if (applyError != null) {
            Text(
                applyError,
                style = MaterialTheme.typography.labelSmall,
                color = MonarchColors.InkMuted,
            )
            Spacer(Modifier.height(8.dp))
        }
        when (step) {
            0 -> {
                if (!profileValid) {
                    Text(
                        "Add ${joinHuman(missing)} to continue.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MonarchColors.InkMuted,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                MonarchButton(
                    label = "Continue",
                    onClick = onContinueProfile,
                    enabled = profileValid,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            1 -> Row(verticalAlignment = Alignment.CenterVertically) {
                MonarchButton(label = "Back", onClick = onBack, quiet = true)
                Spacer(Modifier.width(10.dp))
                MonarchButton(
                    label = "Continue",
                    onClick = onForward,
                    modifier = Modifier.weight(1f),
                )
            }
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                MonarchButton(label = "Back", onClick = onBack, quiet = true)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    if (!planReady) {
                        Text(
                            "Still consulting the catalogue...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MonarchColors.InkMuted,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    // Accepting the routine is the earned moment: the one gold
                    // button in the flow.
                    MonarchButton(
                        label = "Take this routine",
                        onClick = onAccept,
                        enabled = planReady,
                        gold = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * Step 1 - who you are. Unchanged in substance from the single-window days:
 * name, sex, height and the first weigh-in. Saved at Continue, not at the
 * end of the flow, so a hunter lost at step 2 still carries her numbers.
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
) {
    // One scrolling mechanism per step: a Column in verticalScroll, never a
    // lazy list nested inside - that pairing crashes at runtime in this repo.
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SystemWindow(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                shape = MaterialTheme.shapes.small,
                value = name,
                onValueChange = { onName(it.take(24)) },
                label = { Text("Claim your name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    shape = MaterialTheme.shapes.small,
                    value = heightInput,
                    onValueChange = { onHeight(it.filter { c -> c.isDigit() || c == '.' }.take(6)) },
                    label = { Text("Height (cm)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    shape = MaterialTheme.shapes.small,
                    value = weightInput,
                    onValueChange = { onWeight(it.filter { c -> c.isDigit() || c == '.' }.take(6)) },
                    label = { Text("Weight (kg)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(14.dp))
            FieldLabel("SEX - PICKS THE BODY-FAT FORMULA")
            InkSegmented(
                options = Sex.entries.map { it to it.name },
                selected = sex,
                onPick = onSex,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Strength scores are body-scaled against your weight; height powers BMI and FFMI.",
                style = MaterialTheme.typography.labelSmall,
                color = MonarchColors.InkMuted,
            )
        }
    }
}

/**
 * Step 2 - how you train. Plain language on every control: a stranger does
 * not know what SKILL means, and an enum name is never user-facing text.
 * Three small panels instead of one dense card, so each question reads on
 * its own.
 */
@Composable
private fun TrainingStep(
    daysPerWeek: Int,
    onDays: (Int) -> Unit,
    equipment: EquipmentAccess,
    onEquipment: (EquipmentAccess) -> Unit,
    focus: TrainingFocus,
    onFocus: (TrainingFocus) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SystemWindow(Modifier.fillMaxWidth()) {
            FieldLabel("DAYS PER WEEK - BE HONEST, THE WEEK IS BUILT TO FIT")
            Spacer(Modifier.height(8.dp))
            InkSegmented(
                options = (2..6).map { it to it.toString() },
                selected = daysPerWeek,
                onPick = onDays,
            )
        }
        SystemWindow(Modifier.fillMaxWidth()) {
            FieldLabel("WHAT YOU HAVE ACCESS TO")
            Spacer(Modifier.height(8.dp))
            InkSegmented(
                options = listOf(
                    EquipmentAccess.BODYWEIGHT to "No gear",
                    EquipmentAccess.HOME_WEIGHTS to "Home gym",
                    EquipmentAccess.FULL_GYM to "Full gym",
                ),
                selected = equipment,
                onPick = onEquipment,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when (equipment) {
                    EquipmentAccess.BODYWEIGHT ->
                        "Calisthenics: pull-up bars, rings and the floor."
                    EquipmentAccess.HOME_WEIGHTS ->
                        "A bar or some dumbbells at home. Loaded work, room to grow."
                    EquipmentAccess.FULL_GYM ->
                        "Barbells, machines and cables. The whole catalogue opens up."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MonarchColors.InkMuted,
            )
        }
        SystemWindow(Modifier.fillMaxWidth()) {
            FieldLabel("WHAT YOU ARE CHASING")
            Spacer(Modifier.height(8.dp))
            InkSegmented(
                options = listOf(
                    TrainingFocus.STRENGTH to "Power",
                    TrainingFocus.MUSCLE to "Muscle",
                    TrainingFocus.SKILL to "Skills",
                    TrainingFocus.GENERAL to "All-round",
                ),
                selected = focus,
                onPick = onFocus,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when (focus) {
                    TrainingFocus.STRENGTH ->
                        "Low reps, heavy moves. Bigger numbers on the main lifts."
                    TrainingFocus.MUSCLE ->
                        "Higher volume, moderate load. Size comes before bragging rights."
                    TrainingFocus.SKILL ->
                        "Handstands, levers, the planche. Practice over pump."
                    TrainingFocus.GENERAL ->
                        "A balanced mix: a bit stronger, a bit bigger, nothing neglected."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MonarchColors.InkMuted,
            )
        }
    }
}

/** A small on-field caption in the app's HUD voice. */
@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = ChakraPetch,
        color = MonarchColors.InkMuted,
        letterSpacing = MonarchTracking.InlineLabel,
        modifier = Modifier.semantics { contentDescription = text },
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
    equipment: EquipmentAccess,
    focus: TrainingFocus,
) {
    val catalogue by viewModel.catalogue.collectAsStateWithLifecycle()
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    val isStarter by viewModel.isStarter.collectAsStateWithLifecycle()

    // Rebuild only when an upstream choice actually changed (the guard lives
    // in the view model). Waiting for a non-empty catalogue means the first
    // generation is never run against a half-loaded Room list.
    LaunchedEffect(daysPerWeek, equipment, focus, catalogue) {
        if (catalogue.isNotEmpty()) {
            viewModel.ensurePlan(daysPerWeek, equipment, focus, catalogue)
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

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val current = plan
        if (current == null) {
            SystemWindow(Modifier.fillMaxWidth()) {
                Text(
                    "Consulting the catalogue...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MonarchColors.InkMuted,
                )
            }
        } else {
            current.presets.forEachIndexed { presetIndex, preset ->
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
                )
            }
            if (current.presets.isEmpty()) {
                Text(
                    "Nothing left - go back and rebuild, or take the starter week instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MonarchColors.InkMuted,
                )
            }
        }

        if (!isStarter) {
            MonarchButton(
                label = "Use the starter week instead",
                onClick = viewModel::previewStarter,
                quiet = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            MonarchButton(
                label = "Build from my answers instead",
                onClick = {
                    if (catalogue.isNotEmpty()) {
                        viewModel.ensurePlan(daysPerWeek, equipment, focus, catalogue, force = true)
                    }
                },
                quiet = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(2.dp))
    }
}

/**
 * One proposed training day. The day and its focus lead, in the app's crest
 * voice; a brush hairline separates the plan from its movement list.
 */
@Composable
private fun ProposedDay(
    preset: PlannedPreset,
    editable: Boolean,
    onSets: (entryIndex: Int, delta: Int) -> Unit,
    onReps: (entryIndex: Int, delta: Int) -> Unit,
    onRemove: (entryIndex: Int) -> Unit,
) {
    // The ink identity is hand-drawn: a geometric RoundedCornerShape here
    // reads as a foreign rectangle, which is why InkCoverageTest fails the
    // build on one.
    val dayShape = MaterialTheme.shapes.extraSmall
    Column(
        Modifier
            .fillMaxWidth()
            .clip(dayShape)
            .background(MonarchColors.Vault)
            .inkBorder(MonarchColors.Rune, dayShape, 1.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            dayLabel(preset.scheduledDay).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = MonarchColors.EmeraldBright,
            letterSpacing = MonarchTracking.InlineLabel,
        )
        Text(
            preset.name.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = MonarchColors.SystemGreen,
            letterSpacing = MonarchTracking.InlineLabel,
        )
        Spacer(Modifier.height(8.dp))
        preset.entries.forEachIndexed { entryIndex, entry ->
            ProposedEntryRow(
                entry = entry,
                dayName = dayLabel(preset.scheduledDay),
                editable = editable,
                onSets = { delta -> onSets(entryIndex, delta) },
                onReps = { delta -> onReps(entryIndex, delta) },
                onRemove = { onRemove(entryIndex) },
            )
        }
    }
}

/**
 * One proposed movement. Name and scheme are the content; the edit cluster
 * sits beneath, captioned, so the plan is what the eye lands on and the
 * glyphs read as annotation. The steppers are 32dp tappable boxes - well
 * over the 24dp accessibility floor - and each announces its purpose by
 * name, because a bare "-" tells a screen reader nothing.
 */
@Composable
private fun ProposedEntryRow(
    entry: PlannedEntry,
    dayName: String,
    editable: Boolean,
    onSets: (delta: Int) -> Unit,
    onReps: (delta: Int) -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                entry.exerciseName,
                style = MaterialTheme.typography.bodyMedium,
                color = MonarchColors.Ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                entryScheme(entry),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = MonarchColors.SystemGreen,
                letterSpacing = MonarchTracking.InlineLabel,
            )
        }
        if (editable) {
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "SETS",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = MonarchColors.InkMuted,
                    modifier = Modifier.clearAndSetSemantics {},
                )
                TapPad("-", "Fewer sets for ${entry.exerciseName}") { onSets(-1) }
                TapPad("+", "More sets for ${entry.exerciseName}") { onSets(1) }
                Spacer(Modifier.width(10.dp))
                Text(
                    "REPS",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = MonarchColors.InkMuted,
                    modifier = Modifier.clearAndSetSemantics {},
                )
                TapPad("-", "Fewer reps for ${entry.exerciseName}") { onReps(-1) }
                TapPad("+", "More reps for ${entry.exerciseName}") { onReps(1) }
                Spacer(Modifier.weight(1f))
                TapPad("x", "Remove ${entry.exerciseName} from $dayName") { onRemove() }
            }
        }
    }
}

/**
 * The small tap target used by the proposal editor. Not MonarchButton: five
 * steppers per movement row need a control that stays narrow while clearing
 * the 24dp touch floor, which the 32dp box guarantees on both axes.
 */
@Composable
private fun TapPad(
    label: String,
    description: String,
    onClick: () -> Unit,
) {
    val shape = MaterialTheme.shapes.extraSmall
    Box(
        Modifier
            .clip(shape)
            .background(MonarchColors.VaultHigh)
            .clickable(onClick = onClick)
            .heightIn(min = 32.dp)
            .widthIn(min = 32.dp)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            color = MonarchColors.SystemGreen,
            modifier = Modifier.semantics { contentDescription = description },
        )
    }
}
