package com.ironvellum.app.ui.dashboard

import com.ironvellum.app.R
import androidx.compose.ui.res.stringResource
import com.ironvellum.app.domain.SetRecords
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import androidx.compose.runtime.SideEffect
import com.ironvellum.app.data.Words
import com.ironvellum.app.ui.StartupGate
import com.ironvellum.app.ui.stats.trialDay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle as ComposeTextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.data.IdleSnapshot
import com.ironvellum.app.data.Repository
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.PeriodStats
import com.ironvellum.app.domain.RecapSchedule
import com.ironvellum.app.domain.WeeklyRecap
import com.ironvellum.app.ui.recap.RecapText
import com.ironvellum.app.ui.recap.ReviewTodayCard
import com.ironvellum.app.ui.recap.routeKind
import com.ironvellum.app.data.ReviewDismissals
import com.ironvellum.app.domain.PeriodReview
import com.ironvellum.app.domain.ReviewSchedule
import com.ironvellum.app.domain.ReviewSummary
import com.ironvellum.app.domain.RepMax
import com.ironvellum.app.domain.LiftRecords
import com.ironvellum.app.domain.PlayerProfile
import com.ironvellum.app.domain.Rank
import com.ironvellum.app.domain.RankBreakdown
import com.ironvellum.app.domain.SessionClock
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.Sex
import com.ironvellum.app.domain.TitleDef
import com.ironvellum.app.domain.VaultSlot
import com.ironvellum.app.domain.CrestDef
import com.ironvellum.app.domain.Crests
import com.ironvellum.app.domain.Veil
import com.ironvellum.app.domain.VeilGrant
import com.ironvellum.app.domain.Titles
import com.ironvellum.app.domain.TrainFocus
import com.ironvellum.app.domain.TrainingFocus
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.ProgramRules
import com.ironvellum.app.domain.WorkoutPreset
import com.ironvellum.app.domain.WorkoutSession
import com.ironvellum.app.domain.Xp
import com.ironvellum.app.ui.components.GatedAchievementOverlay
import com.ironvellum.app.ui.components.ExerciseInfoSheet
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.ListRow
import com.ironvellum.app.ui.components.ListRowHeight
import com.ironvellum.app.ui.components.RankSheet
import com.ironvellum.app.ui.components.Term
import com.ironvellum.app.ui.components.TermDialog
import com.ironvellum.app.ui.components.deedPages
import com.ironvellum.app.ui.components.veilGrantPages
import com.ironvellum.app.ui.components.formatDate
import com.ironvellum.app.ui.components.plural
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.domain.ArmyClass
import com.ironvellum.app.ui.components.FirstUseLine
import com.ironvellum.app.ui.launchGuarded
import com.ironvellum.app.ui.program.toPlanned
import androidx.compose.runtime.LaunchedEffect
import com.ironvellum.app.data.FatigueAdviceDismissals
import com.ironvellum.app.data.FatigueAdviceSetting
import com.ironvellum.app.data.ProgramAnswersStore
import com.ironvellum.app.data.requestFor
import com.ironvellum.app.domain.EntryAdjust
import com.ironvellum.app.domain.FatigueAdvice
import com.ironvellum.app.domain.FatigueReport
import com.ironvellum.app.domain.FatigueSwap
import com.ironvellum.app.domain.PresetEntry
import com.ironvellum.app.domain.SessionOverride
import com.ironvellum.app.ui.program.FatigueSwatch
import com.ironvellum.app.ui.program.rememberFatigueReport
import kotlinx.coroutines.Dispatchers
import com.ironvellum.app.ui.program.MusclesNowCard
import com.ironvellum.app.ui.program.MusclesView
import com.ironvellum.app.ui.program.MuscleCoverageViewModel
import com.ironvellum.app.ui.program.MuscleFatigueViewModel
import com.ironvellum.app.ui.program.rememberDismissedHeavy
import com.ironvellum.app.ui.program.weekLine
import com.ironvellum.app.ui.train.PresetsViewModel
import com.ironvellum.app.ui.train.WeekPlanRows
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.ironvellum.app.ui.components.CrestPlate
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.theme.CappedFontScale
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlin.math.roundToInt
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** Which of height and weight the scores still need. */
enum class BodyGap { HEIGHT, WEIGHT, BOTH }

/**
 * The Veil at a glance on Today: its live snapshot, the inscriptions waiting to be spent and the relic
 * setting the rate ([active], null with no relic), which the respite form shows as its centrepiece.
 */
data class VeilGlance(
    val snapshot: IdleSnapshot,
    val inscriptions: Int,
    val active: VaultSlot? = null,
    val buyPrice: Long? = null,
    /** The crest worn, with what it adds to the rate in essence an hour (0 for a perk that is not on the rate). */
    val worn: CrestDef? = null,
    val wornWorth: Double = 0.0,
)

class DashboardUi(
    val profile: PlayerProfile? = null,
    val presets: List<WorkoutPreset> = emptyList(),
    val streak: Int = 0,
    /** The focus the plan line is timed at. */
    val focus: TrainingFocus = TrainingFocus.MUSCLE,
    /** The lifter's own seconds per set; times the plan line. */
    val pace: SessionClock.Pace = SessionClock.Pace(),
    /** Weekdays (1 = Monday) whose scheduled rite was sealed this week, to the trial that sealed it. */
    val weekDone: Map<Int, WorkoutSession> = emptyMap(),
    /** The sets of each trial in [weekDone], by trial id, so a sealed card can read what was done. */
    val sealedSets: Map<Long, List<SessionSet>> = emptyMap(),
    /** The catalogue by id, for each movement's metric. */
    val exercises: Map<Long, Exercise> = emptyMap(),
    /** Lifts whose peak was set lately AND beat an earlier trial: a first trial is not a peak. */
    val newPeaks: List<RepMax.Recent> = emptyList(),
    /** Last week's recap, offered as a link from Monday through Wednesday when that week had workouts; null otherwise. */
    val lastWeek: WeeklyRecap? = null,
    /** Last month's (in January, last year's) review, offered in the first week of a month; null the rest of the time. */
    val review: ReviewSummary? = null,
    /** False only for the placeholder shown before the first read lands. */
    val loaded: Boolean = false,
)

/**
 * Weekdays of the week containing [today] whose scheduled rite is sealed this
 * week, by [TrainFocus.sealing], mapped to the trial that sealed it. Only
 * completed trials count, from the whole history, so a long cycle cannot push
 * Monday's tick out and an unsealed trial cannot light one.
 */
internal fun weekDoneDays(
    sessions: List<WorkoutSession>,
    presets: List<WorkoutPreset>,
    today: LocalDate,
    zone: ZoneId,
): Map<Int, WorkoutSession> {
    val weekStart = today.with(DayOfWeek.MONDAY).atStartOfDay(zone).toInstant().toEpochMilli()
    val sealed = presets.mapNotNull { preset ->
        val day = preset.scheduledDay ?: return@mapNotNull null
        TrainFocus.sealing(preset, sessions, weekStart)?.let { day to it }
    }.toMap()
    // An imported or free workout belongs to no rite, so it ticks the weekday it happened on: the same
    // "a workout happened that day" the Stats calendar marks (see trialDay).
    val free = sessions
        .filter { it.presetId == null && it.completedAtMs != null && it.completedAtMs!! >= weekStart }
        .sortedBy { it.completedAtMs }
        .associateBy { trialDay(it, zone).dayOfWeek.value }
    return free + sealed
}

/**
 * The latest of [trials] sealed on the calendar day [day], or null. A rite marks its own scheduled day, so a
 * Monday rite done on Thursday ticks Monday; this is how Thursday's own card can still say a trial was sealed.
 */
internal fun sealedOnDay(trials: Collection<WorkoutSession>, day: LocalDate, zone: ZoneId): WorkoutSession? =
    trials
        .filter { t -> t.completedAtMs?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == day } == true }
        .maxByOrNull { it.completedAtMs ?: 0L }

/**
 * Peaks to name on Today: set within the Ledger's fresh window, and higher than
 * an earlier trial of the same lift - the first trial of anything is its best
 * by default and is not news.
 */
internal fun newPeaks(
    history: List<Pair<WorkoutSession, List<SessionSet>>>,
    exercises: List<Exercise>,
    nowMs: Long,
    limit: Int = 2,
): List<RepMax.Recent> =
    RepMax.recent(history, exercises.associateBy { it.id }, nowMs, withinDays = LiftRecords.RECENT_DAYS, limit = limit)

/** Last week's recap when Today should link to it (see [RecapSchedule.showsOnToday]); null the rest of the week, and for a week with no workouts. */
internal fun lastWeekRecap(
    history: List<Pair<WorkoutSession, List<SessionSet>>>,
    exercises: List<Exercise>,
    bodyweightAt: (Long) -> Double,
    presets: List<WorkoutPreset>,
    today: LocalDate,
    zone: ZoneId,
): WeeklyRecap? {
    val start = WeeklyRecap.weekStartOf(today).minusDays(7)
    if (!RecapSchedule.showsOnToday(today, PeriodStats.workoutsIn(history, start, start.plusDays(7), zone))) return null
    val plan = presets.filter { it.scheduledDay != null }.map { it.toPlanned() }
    return WeeklyRecap.build(history, exercises.associateBy { it.id }, bodyweightAt, plan, start, today, zone)
}

/** The review Today offers on [today] ([ReviewSchedule.offeredOn]) with its three figures; null outside the window. */
internal fun reviewOffered(
    history: List<Pair<WorkoutSession, List<SessionSet>>>,
    exercises: List<Exercise>,
    bodyweightAt: (Long) -> Double,
    today: LocalDate,
    zone: ZoneId,
): ReviewSummary? {
    val period = ReviewSchedule.offeredOn(today, { PeriodStats.workoutsIn(history, it.from, it.to, zone) }) ?: return null
    return PeriodReview.summary(history, exercises.associateBy { it.id }, bodyweightAt, period, zone)
}

class DashboardViewModel(
    private val repo: Repository,
    /** What the lifter last told the generator; times the plan line. */
    private val savedFocus: TrainingFocus? = null,
) : ViewModel() {

    private val selectedDay = MutableStateFlow(LocalDate.now().dayOfWeek.value)

    /** The unfinished session: the day card continues it instead of starting another. */
    val live: StateFlow<WorkoutSession?> = repo.observeLiveSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The live trial's sets, for "n of m sets logged" and its rows. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val liveSets: StateFlow<List<SessionSet>> = live
        .flatMapLatest { trial -> if (trial == null) flowOf(emptyList()) else repo.observeSessionSets(trial.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val ui: StateFlow<DashboardUi> = combine(
        repo.observeProfile(),
        repo.observePresets(),
        repo.observeHistory(),
        repo.observeExercises(),
        repo.observeStats(),
    ) { profile, presets, history, exercises, stats ->
        val today = LocalDate.now()
        val bodyweightAt = SetRecords.bodyweightLookup(stats)
        val zone = ZoneId.systemDefault()
        val doneDates = history
            .map {
                Instant.ofEpochMilli(it.first.completedAtMs ?: it.first.startedAtMs)
                    .atZone(zone).toLocalDate()
            }
            .toSet()
        val weekDone = weekDoneDays(history.map { it.first }, presets, today, zone)
        val sealedIds = weekDone.values.map { it.id }.toSet()
        // One streak rule for the whole app: the Today screen used to build its
        // own day records while the deeds, the idle rate and the leaderboard
        // used Titles.trainingStreakDays, so the same lifter could read two
        // different streaks.
        DashboardUi(
            profile = profile,
            presets = presets,
            streak = Titles.trainingStreakDays(doneDates, today),
            focus = SessionClock.focusFor(savedFocus, profile?.trainingMode),
            pace = SessionClock.pace(history),
            weekDone = weekDone,
            sealedSets = history.filter { it.first.id in sealedIds }.associate { it.first.id to it.second },
            exercises = exercises.associateBy { it.id },
            newPeaks = newPeaks(history, exercises, System.currentTimeMillis()),
            lastWeek = lastWeekRecap(history, exercises, bodyweightAt, presets, today, zone),
            review = reviewOffered(history, exercises, bodyweightAt, today, zone),
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUi())

    val selected: StateFlow<Int> = selectedDay

    /** Titles awarded by startup reconciliation, still owed their celebration. */
    val pendingCelebrations: StateFlow<List<TitleDef>> = repo.pendingCelebrations

    /**
     * The reader's own bar. A deed's wording differs by sex, and the celebration
     * must not tell a woman she cleared the men's standard.
     */
    val sex: StateFlow<Sex> = repo.observeBodyProfile()
        .map { it.second }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Sex.MALE)

    private val breakdownFlow = repo.observeRankBreakdown()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /** Strength Rank band, "Unranked" without one; blank until first read. */
    val strengthRank: StateFlow<String> = breakdownFlow
        .map { it?.band ?: Rank.UNRANKED }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /** The rank written out for [RankSheet]; null while unranked. */
    val rankBreakdown: StateFlow<RankBreakdown?> = breakdownFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The Veil at a glance: its snapshot (essence, rate, echoes, relic) and the inscriptions waiting. */
    val veil: StateFlow<VeilGlance?> = combine(repo.observeIdleSnapshot(), repo.observeRolls(), repo.observeVault(), repo.observeOfferingsMade(), repo.observeCrestWorth()) { snapshot, rolls, vault, offerings, worth ->
        // The price rides the slot only when none wait and one is affordable on the banked essence.
        val price = Veil.offeringCost(offerings, snapshot.rate.effects.crest)
        val worn = Crests.byId(snapshot.rate.effects.crest.wornId)
        VeilGlance(snapshot, rolls, vault.active, buyPrice = price.takeIf { rolls == 0 && snapshot.state.essence >= it }, worn = worn, wornWorth = worn?.let { worth[it.id] } ?: 0.0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * What the body-scaled numbers still wait for. Skipping the Binding leaves
     * height and weight unset, and every score reads a dash until both exist.
     */
    val bodyGap: StateFlow<BodyGap?> = combine(repo.observeBodyProfile(), repo.observeStats()) { body, stats ->
        val noHeight = (body.first ?: 0.0) <= 0.0
        val noWeight = stats.isEmpty()
        when {
            noHeight && noWeight -> BodyGap.BOTH
            noHeight -> BodyGap.HEIGHT
            noWeight -> BodyGap.WEIGHT
            else -> null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** What the Veil paid outside a draw (milestone crests, the one-time catch-up), owed its moment. */
    val pendingVeilGrant: StateFlow<VeilGrant?> = repo.pendingVeilGrant

    /** The crest worn now, so a crest moment can offer to swap it. */
    val wornCrest: StateFlow<String?> = repo.observeEquippedFrame()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** How many of the 28 crests are held, for "10 of 28 crests" on a crest moment. */
    val crestsOwned: StateFlow<Int?> = repo.observeOwnedFrames().map<Set<String>, Int?> { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _bodyError = MutableStateFlow<UiText?>(null)

    /** Why the last body save was refused, for the sheet that asked; null while there is nothing to say. */
    val bodyError: StateFlow<UiText?> = _bodyError.asStateFlow()

    fun clearBodyError() {
        _bodyError.value = null
    }

    /**
     * Saves the figures the body sheet collected. Height goes first on purpose: addStat stamps the profile
     * height onto the weigh-in, so the other order would leave the first reading without one.
     */
    fun saveBody(heightCm: Double?, weightKg: Double?, onSaved: () -> Unit) {
        _bodyError.value = null
        viewModelScope.launch {
            runCatching {
                heightCm?.let { repo.setHeight(it) }
                weightKg?.let { repo.logTodaysWeight(it) }
            }.fold(
                onSuccess = { onSaved() },
                onFailure = { _bodyError.value = UiText.res(R.string.today_body_save_error, it.message ?: "") },
            )
        }
    }

    fun wearCrest(frameId: String) {
        viewModelScope.launchGuarded("wear crest") { repo.equipFrame(frameId) }
    }

    fun celebrationsSeen() {
        repo.clearPendingCelebrations()
        repo.clearPendingVeilGrant()
    }

    fun wearTitle(titleId: String) {
        viewModelScope.launchGuarded("wear title") { repo.equipTitle(titleId) }
    }

    fun selectDay(day: Int) {
        selectedDay.value = day
    }

    fun beginOpen(onStarted: (Long) -> Unit) {
        viewModelScope.launchGuarded("begin ${Words.openTrial.s}") { onStarted(repo.startFreeformSession(Words.openTrial.c)) }
    }

    fun beginPreset(presetId: Long, onStarted: (Long) -> Unit) {
        viewModelScope.launchGuarded("begin ${Words.rite.s}") { onStarted(repo.startSessionFromPreset(presetId)) }
    }

    /** Begins [presetId] with today's [override] (Adjust on Today); the rite itself is not edited. */
    fun beginAdjusted(presetId: Long, override: SessionOverride, onStarted: (Long) -> Unit) {
        viewModelScope.launchGuarded("begin ${Words.rite.s}") { onStarted(repo.startSessionFromPreset(presetId, override)) }
    }

    /**
     * The stand-in for each exercise in [leans], by routine entry id ([FatigueSwap.choose]); an exercise with none is
     * left out. Empty until the lifter has told the app their gear, since a stand-in must be one they can do.
     */
    suspend fun fatigueSwaps(
        context: android.content.Context,
        leans: List<FatigueAdvice.Lean>,
        routine: List<PresetEntry>,
        report: FatigueReport,
        atMs: Long,
    ): Map<Long, EntryAdjust.Swap> = withContext(Dispatchers.Default) {
        val answers = ProgramAnswersStore.get(context)
        val equipment = answers?.equipment ?: ProgramAnswersStore.equipment.value ?: return@withContext emptyMap()
        val sex = repo.observeBodyProfile().first().second
        val request = answers.requestFor(equipment, sex, ui.value.presets.size)
        val strength = repo.strengthProfile().copy(starter = repo.starterBasis(sex, request.volume))
        val catalogue = repo.observeExercises().first()
        val since = report.nowMs - RECENT_DAYS * 24L * 3_600_000L
        val recent = repo.observeHistory().first()
            .filter { (session, _) -> (session.completedAtMs ?: session.startedAtMs) >= since }
            .flatMap { (_, sets) -> sets.filter { it.done }.map { it.exerciseName.trim().lowercase() } }
            .toSet()
        leans.mapNotNull { lean ->
            FatigueSwap.choose(lean.entry, routine, report, atMs, catalogue, request, strength, recent)?.let { lean.entry.id to it }
        }.toMap()
    }

    private companion object {
        /** A lift done within this many days counts as recent when a swap is a tie. */
        const val RECENT_DAYS = 28L
    }
}

/**
 * "as of 14:05" for a sync today, "as of Sep 28" for an older one: Health
 * Connect trails the phone's own count, so the figure carries its age.
 */
internal fun stepsAsOfCaption(syncedAtMs: Long?, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): String? {
    if (syncedAtMs == null) return null
    val at = Instant.ofEpochMilli(syncedAtMs).atZone(zone)
    val pattern = if (at.toLocalDate() == today) "HH:mm" else "MMM d"
    return Words.text(UiText.res(R.string.today_steps_as_of, at.format(java.time.format.DateTimeFormatter.ofPattern(pattern, Locale.US))))
}

/** "48m", "1h 12m"; null for a trial with no sensible length (imports carry none). */
internal fun trialLength(trial: WorkoutSession): String? {
    val minutes = ((trial.completedAtMs ?: return null) - trial.startedAtMs) / 60_000
    return when {
        minutes < 1 || minutes > 24 * 60 -> null
        minutes < 60 -> "${minutes}m"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }
}

/** Trials whose SEALED stamp has already thudded in during this process: it lands once, not on every visit. */
private val stampedTrials = mutableSetOf<Long>()

/** A movement's mini bar stops growing here; a long block just reads as a long bar. */
private const val MINI_BAR_SEGMENTS = 8

/** The week rail's letters, Monday first. */
private val WEEK_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")

/** "Sat" within the week, "Oct 3" beyond it. */
private fun shortWhen(ms: Long): String =
    if (System.currentTimeMillis() - ms < 6L * 24 * 60 * 60 * 1000) formatDate(ms, "EEE") else formatDate(ms, "MMM d")

/** "Wed" for 3. */
private fun dayName(day: Int): String = DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, Locale.getDefault())

/** "Wednesday" for 3. */
private fun dayLong(day: Int): String = DayOfWeek.of(day).getDisplayName(TextStyle.FULL, Locale.getDefault())

@Composable
fun DashboardScreen(
    onStartSession: (Long) -> Unit,
    onOpenPresets: () -> Unit,
    onOpenRite: (Long) -> Unit,
    onOpenForge: () -> Unit,
    onOpenCodex: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLedger: () -> Unit,
    onOpenGarrison: () -> Unit,
    onOpenCrest: () -> Unit = {},
    onOpenWorkout: (Long) -> Unit,
    /** The header's "Add your name": straight to Settings > Profile, where the name lives. */
    onAddName: () -> Unit = onOpenSettings,
    /** A fresh peak's row opens that lift. */
    onOpenLift: (name: String, variation: String) -> Unit = { _, _ -> onOpenLedger() },
    /** The "Last week" link: the recap of the week starting on this epoch day. */
    onOpenRecap: (Long) -> Unit = {},
    /** The review card: "month" or "year", and the epoch day its period starts on. */
    onOpenReview: (String, Long) -> Unit = { _, _ -> },
    onOpenMuscles: (MusclesView) -> Unit = { onOpenPresets() },
    viewModel: DashboardViewModel =
        viewModel(factory = viewModelFactory {
            // The saved answers sit behind a Context only a composable can read;
            // they are read once, when the view model is made.
            val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
            initializer {
                DashboardViewModel(
                    ironvellumRepository(),
                    com.ironvellum.app.data.ProgramAnswersStore.get(appContext)?.focus,
                )
            }
        }),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val selectedDay by viewModel.selected.collectAsStateWithLifecycle()
    // A cached process outlives midnight: the lifter opened the app Monday
    // night, Android parked it, and Thursday's open resumed Monday's
    // selection. On each return to the foreground, follow the calendar — but
    // only when the day actually changed, so a quick app-switch preserves the
    // day she was reading.
    val launchDay = remember { LocalDate.now().dayOfWeek.value }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START &&
                LocalDate.now().dayOfWeek.value != launchDay
            ) {
                viewModel.selectDay(LocalDate.now().dayOfWeek.value)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val live by viewModel.live.collectAsStateWithLifecycle()
    val liveSets by viewModel.liveSets.collectAsStateWithLifecycle()
    val bodyGap by viewModel.bodyGap.collectAsStateWithLifecycle()
    val strengthRank by viewModel.strengthRank.collectAsStateWithLifecycle()
    val sexUnset by com.ironvellum.app.data.FirstRunPrefs.sexUnsetFlow(androidx.compose.ui.platform.LocalContext.current.applicationContext).collectAsStateWithLifecycle()
    val rankBreakdown by viewModel.rankBreakdown.collectAsStateWithLifecycle()
    val veil by viewModel.veil.collectAsStateWithLifecycle()
    // Today's cards, the Veil and the rank line have all been read: the launch screen can lift.
    if (ui.loaded && veil != null && strengthRank.isNotEmpty()) SideEffect { StartupGate.open() }

    var bodyOpen by rememberSaveable { mutableStateOf(false) }
    val bodyError by viewModel.bodyError.collectAsStateWithLifecycle()
    bodyGap?.let { gap ->
        if (bodyOpen) {
            BodyDetailsDialog(
                gap = gap,
                error = bodyError?.asString(),
                onDismiss = { bodyOpen = false; viewModel.clearBodyError() },
                onSave = { h, w -> viewModel.saveBody(h, w) { bodyOpen = false } },
            )
        }
    }

    val fatigueReport = rememberFatigueReport()
    val fatigueDismissals = remember(appContext) { FatigueAdviceDismissals(appContext) }
    val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    DashboardPanes(
        windowWidth = windowWidth,
        today = {
            TodayContent(
        ui = ui,
        selectedDay = selectedDay,
        today = LocalDate.now(),
        live = live,
        liveSets = liveSets,
        bodyGap = bodyGap,
        strengthRank = strengthRank,
        sexUnset = sexUnset,
        rankBreakdown = rankBreakdown,
        veil = veil,
        report = fatigueReport,
        fatigueDismissals = fatigueDismissals,
        actions = TodayActions(
            onStartSession = onStartSession,
            onOpenRite = onOpenRite,
            onOpenForge = onOpenForge,
            onOpenCodex = onOpenCodex,
            onOpenSettings = onOpenSettings,
            onOpenLedger = onOpenLedger,
            onOpenGarrison = onOpenGarrison,
            onOpenCrest = onOpenCrest,
            onOpenWorkout = onOpenWorkout,
            onAddBody = { bodyOpen = true },
            onAddName = onAddName,
            onOpenLift = onOpenLift,
            onOpenRecap = onOpenRecap,
            onOpenReview = onOpenReview,
            onDismissReview = { key -> ReviewDismissals.dismiss(appContext, key) },
            onSelectDay = viewModel::selectDay,
            onBeginPreset = { presetId -> viewModel.beginPreset(presetId, onStartSession) },
            onBeginOpen = { viewModel.beginOpen(onStartSession) },
            onBeginAdjusted = { presetId, override -> viewModel.beginAdjusted(presetId, override, onStartSession) },
            swapsFor = { leans, routine, report, atMs -> viewModel.fatigueSwaps(appContext, leans, routine, report, atMs) },
        ),
            )
        },
        plan = { modifier -> DashboardPlanPane(onOpenRite, onOpenPresets, onOpenMuscles, modifier) },
    )

    // Titles reconciled at startup (health data, imports) have no session to
    // celebrate in, so the moment is paid out here on the first screen.
    val owed by viewModel.pendingCelebrations.collectAsStateWithLifecycle()
    val sex by viewModel.sex.collectAsStateWithLifecycle()
    val veilGrant by viewModel.pendingVeilGrant.collectAsStateWithLifecycle()
    val wornCrest by viewModel.wornCrest.collectAsStateWithLifecycle()
    val crestsOwned by viewModel.crestsOwned.collectAsStateWithLifecycle()
    GatedAchievementOverlay(
        pages = deedPages(owed, sex) + veilGrantPages(veilGrant, crestsOwned),
        onDone = { viewModel.celebrationsSeen() },
        wornTitleId = ui.profile?.currentTitleId,
        onWear = viewModel::wearTitle,
        wornCrestId = wornCrest,
        onWearCrest = viewModel::wearCrest,
    )
}

/** The window, not the width left to Today, determines whether the companion pane is shown. */
@Composable
internal fun DashboardPanes(
    windowWidth: Dp,
    today: @Composable () -> Unit,
    plan: @Composable (Modifier) -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxSize()) { today() }
        if (windowWidth >= 840.dp) plan(Modifier.weight(1f))
    }
}

@Composable
private fun DashboardPlanPane(
    onOpenRite: (Long) -> Unit,
    onOpenPresets: () -> Unit,
    onOpenMuscles: (MusclesView) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val presetsModel: PresetsViewModel = viewModel(factory = viewModelFactory {
        initializer { PresetsViewModel(ironvellumRepository(), context) }
    })
    val plan by presetsModel.ui.collectAsStateWithLifecycle()
    val live by presetsModel.live.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val fatigueModel: MuscleFatigueViewModel = viewModel(factory = viewModelFactory {
        initializer { MuscleFatigueViewModel(ironvellumRepository()) }
    })
    val coverageModel: MuscleCoverageViewModel = viewModel(factory = viewModelFactory {
        initializer { MuscleCoverageViewModel(ironvellumRepository()) }
    })
    val fatigue by fatigueModel.ui.collectAsStateWithLifecycle()
    val coverage by coverageModel.ui.collectAsStateWithLifecycle()
    val dismissed = rememberDismissedHeavy()
    val coverageLine = remember(coverage.week, dismissed.value) { weekLine(coverage.week.tally(dismissed.value)) }
    DashboardPlanContent(plan, live != null, fatigue, if (!coverage.week.hasAnything()) null else stringResource(R.string.muscles_now_week, coverageLine), today, onOpenRite, onOpenPresets, onOpenMuscles, modifier)
}

@Composable
internal fun DashboardPlanContent(
    plan: com.ironvellum.app.ui.train.TrainUi,
    live: Boolean,
    fatigue: com.ironvellum.app.ui.program.FatigueUi,
    coverageLine: String?,
    today: LocalDate,
    onOpenRite: (Long) -> Unit,
    onOpenPresets: () -> Unit,
    onOpenMuscles: (MusclesView) -> Unit,
    modifier: Modifier = Modifier,
) {
    val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = GUTTER)) {
        Spacer(Modifier.height(16.dp))
        ListRow(
            label = UiText.themed(R.string.presets_cycle_header, R.string.presets_cycle_header__t).asString(),
            onClick = onOpenPresets,
        )
        if (plan.presets.isNotEmpty()) {
            WeekPlanRows(
                ui = plan,
                today = today.dayOfWeek.value,
                live = live,
                sealedThisWeek = { preset ->
                    plan.history.any { (session, _) -> session.presetId == preset.id && (session.completedAtMs ?: 0L) >= weekStart }
                },
                onOpenRite = onOpenRite,
            )
        }
        MusclesNowCard(
            ui = fatigue,
            coverageLine = coverageLine,
            zone = ZoneId.systemDefault(),
            onOpen = { onOpenMuscles(MusclesView.FATIGUE) },
            onOpenCoverage = { onOpenMuscles(MusclesView.COVERAGE) },
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
        )
    }
}

/** What Today's controls do. Every one defaults to nothing, so a test can render the page alone. */
internal class TodayActions(
    val onStartSession: (Long) -> Unit = {},
    val onOpenRite: (Long) -> Unit = {},
    val onOpenForge: () -> Unit = {},
    val onOpenCodex: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onOpenLedger: () -> Unit = {},
    val onOpenGarrison: () -> Unit = {},
    val onOpenCrest: () -> Unit = {},
    val onOpenWorkout: (Long) -> Unit = {},
    /** Add the missing height and/or weight: opens the body sheet. */
    val onAddBody: () -> Unit = {},
    /** The header asks for a name while the profile still has the default one. */
    val onAddName: () -> Unit = {},
    val onOpenLift: (name: String, variation: String) -> Unit = { _, _ -> },
    /** Last week's recap: the Monday of the week, as an epoch day. */
    val onOpenRecap: (Long) -> Unit = {},
    /** A month or year review: "month" or "year", and the epoch day its period starts on. */
    val onOpenReview: (String, Long) -> Unit = { _, _ -> },
    /** The review's close button: the period's key ([com.ironvellum.app.domain.ReviewPeriod.key]). */
    val onDismissReview: (String) -> Unit = {},
    val onSelectDay: (Int) -> Unit = {},
    val onBeginPreset: (Long) -> Unit = {},
    val onBeginOpen: () -> Unit = {},
    /** Adjust today: begins the rite with this workout's changes. */
    val onBeginAdjusted: (Long, SessionOverride) -> Unit = { _, _ -> },
    /** The stand-ins the adjust sheet offers, by routine entry id: the leaning exercises, the routine, the report and when it is read. */
    val swapsFor: suspend (List<FatigueAdvice.Lean>, List<PresetEntry>, FatigueReport, Long) -> Map<Long, EntryAdjust.Swap> = { _, _, _, _ -> emptyMap() },
)

/**
 * Today with everything handed in, so a test can set up any state. It does not scroll while it can
 * fit: [TodayLayout] shares the height it is given between the parts and sheds exercise rows first.
 */
@Composable
internal fun TodayContent(
    ui: DashboardUi,
    selectedDay: Int,
    today: LocalDate,
    live: WorkoutSession?,
    liveSets: List<SessionSet>,
    bodyGap: BodyGap?,
    strengthRank: String,
    rankBreakdown: RankBreakdown?,
    /** No sex chosen yet: the rank holds as "Add sex in Profile", a link to Profile. */
    sexUnset: Boolean = false,
    veil: VeilGlance?,
    actions: TodayActions,
    nowMs: Long = System.currentTimeMillis(),
    /** Whether the Veil may move; tests hand in false to keep Compose idle. */
    motion: Boolean = rememberTodayMotion(),
    /** The fatigue estimate Today's advice reads; null shows none (and every test that does not ask for it). */
    report: FatigueReport? = null,
    /** "Not today" for the advice; null when there is nowhere to keep it. */
    fatigueDismissals: FatigueAdviceDismissals? = null,
) {
    var rankOpen by rememberSaveable { mutableStateOf(false) }
    var oathOpen by rememberSaveable { mutableStateOf(false) }
    var levelOpen by rememberSaveable { mutableStateOf(false) }
    val swipe = rememberTodaySwipe(selectedDay, actions.onSelectDay, motion)
    val profile = ui.profile
    val progress = Xp.progress(profile?.totalXp ?: 0L)
    // Header: the name and what is worn are also the way to the Codex (there
    // was no other route from here to its board), then the rank link and the gear.
    val head: @Composable () -> Unit = {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .weight(1f)
                    .clickable(onClickLabel = UiText.themed(R.string.today_codex_open_label, R.string.today_codex_open_label__t).asString()) { actions.onOpenCodex() },
            ) {
                NameRow(
                    name = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val worn = veil?.worn
                            if (worn == null) {
                                HeaderCrest()
                            } else {
                                CrestPlate(
                                    worn.id,
                                    Modifier
                                        .size(32.dp)
                                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.today_crests_open_label)) { actions.onOpenCrest() },
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            // A skipped setup leaves the default name; the header asks for a real one instead of
                            // wearing "Lifter" as if it were chosen.
                            val unnamed = profile?.name?.let(Words::isDefaultName) == true
                            Text(
                                if (unnamed) stringResource(R.string.today_name_add) else profile?.name?.let(Words::displayName) ?: Words.lifter.c,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (unnamed) IronvellumColors.SystemGreen else IronvellumColors.Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = if (unnamed) {
                                    Modifier
                                        .heightIn(min = 48.dp)
                                        .wrapContentHeight(Alignment.CenterVertically)
                                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.today_name_add)) { actions.onAddName() }
                                } else {
                                    Modifier
                                },
                            )
                        }
                    },
                    // Strength Rank only: ascension names the level on the rail
                    // below, so it is never a stat beside this one.
                    rank = {
                        Row(
                            Modifier
                                .heightIn(min = 48.dp)
                                .clickable(role = Role.Button, onClickLabel = stringResource(if (sexUnset) R.string.today_sex_add_label else R.string.today_rank_show_label)) { if (sexUnset) actions.onAddName() else rankOpen = true }
                                .padding(start = 10.dp, end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                com.ironvellum.app.domain.SexHold.rank(Rank.label(strengthRank.ifEmpty { Rank.UNRANKED }), sexUnset),
                                style = MaterialTheme.typography.labelLarge,
                                color = IronvellumColors.SystemGreen,
                                maxLines = 1,
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = IronvellumColors.SystemGreen,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                )
                Titles.displayName(profile?.currentTitleId)?.let { worn ->
                    WornTitle(worn)
                }
            }
            // The settings gear: the glyph stays 22dp; the TARGET is 48dp.
            Box(
                Modifier
                    .size(48.dp)
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.today_settings_open_label)) { actions.onOpenSettings() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.today_settings_description),
                    tint = IronvellumColors.InkMuted,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        HeaderProgress(progress, ui.streak, onLevel = { levelOpen = true }, onOath = { oathOpen = true })
        // The level title stays in its own voice in both modes, so the first screen says what it is.
        if (isFirstRun(profile?.totalXp)) {
            FirstUseLine(
                "rank_titles",
                stringResource(R.string.today_first_run_rank_titles, ArmyClass.LADDER.first().title),
            )
        }
        // Seven one-letter tiles on fixed 12/24dp slots, with the selected-day tile drawn behind them: they cannot grow.
        CappedFontScale {
            WeekRail(
                selectedDay = selectedDay,
                today = today.dayOfWeek.value,
                scheduled = ui.presets.mapNotNull { it.scheduledDay }.toSet(),
                done = ui.weekDone.keys,
                onSelect = swipe.selectDay,
                dayOffset = swipe.dayOffset,
            )
        }
    }

    CompositionLocalProvider(LocalTodayMotion provides motion) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = GUTTER)) { head() }
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().then(swipe.gestures).testTag("today-day-viewport")) {
                // Key by weekday so the incoming body survives rebasing when the slide finishes.
                val pages = if (motion) -1..1 else 0..0
                for (page in pages) {
                    val day = ((if (motion) swipe.renderDay() else selectedDay) - 1 + page + 7) % 7 + 1
                    key(day) {
                        val active = !motion || page == swipe.activePage()
                        Box(
                            Modifier.fillMaxSize().then(swipe.content(page)).testTag("today-day-page-$day")
                                .then(if (active) Modifier else Modifier.clearAndSetSemantics {}),
                        ) {
                            TodayDayBody(ui, day, today, live, liveSets, bodyGap, veil, actions, nowMs, active, report, fatigueDismissals)
                        }
                    }
                }
            }
        }
    }
    if (rankOpen) RankSheet(rankBreakdown) { rankOpen = false }
    if (oathOpen) TermDialog(Term.OATH) { oathOpen = false }
    if (levelOpen) TermDialog(Term.LEVEL) { levelOpen = false }
}

/** Each adjacent day budgets its own card and rows against the same fixed viewport. */
@Composable
private fun TodayDayBody(
    ui: DashboardUi,
    selectedDay: Int,
    today: LocalDate,
    live: WorkoutSession?,
    liveSets: List<SessionSet>,
    bodyGap: BodyGap?,
    veil: VeilGlance?,
    pageActions: TodayActions,
    nowMs: Long,
    displayed: Boolean,
    report: FatigueReport?,
    dismissals: FatigueAdviceDismissals?,
) {
    val actions = if (displayed) pageActions else TodayActions()
    val selectedPreset = ui.presets.firstOrNull { it.scheduledDay == selectedDay }
    val isTodaySelected = selectedDay == today.dayOfWeek.value
    // The tick, the card and the sealed state all answer one question: was THIS
    // day's scheduled rite sealed this week. A rite taken early is not offered
    // again on its day, and a sealed past day reads as sealed.
    val sealedTrial = ui.weekDone[selectedDay]
    // The next scheduled rite after the selected day, wrapping the week.
    val nextRite = (1..7).map { (selectedDay - 1 + it) % 7 + 1 }
        .firstNotNullOfOrNull { day -> ui.presets.firstOrNull { it.scheduledDay == day } }
    val liveTrial = live
    val kind = dayKind(ui.presets.isNotEmpty(), selectedPreset, isTodaySelected, liveTrial, sealedTrial)
    val liveWork = liveSets.filter { !it.warmup }
    val liveDone = liveWork.count { it.done }
    val sealedSets = sealedTrial?.let { ui.sealedSets[it.id].orEmpty() }.orEmpty()
    // What fatigue says about this day's rite: null when nothing, the setting is off, or "not today" was said.
    val zone = remember { ZoneId.systemDefault() }
    val adviceOn = FatigueAdviceSetting.enabled
    val notToday = selectedPreset != null && dismissals?.isNotToday(today, selectedPreset.id) == true
    val advice = remember(kind, selectedPreset, ui.presets, ui.weekDone, report, selectedDay, adviceOn, notToday) {
        todayAdvice(kind, selectedPreset, ui.presets, ui.weekDone.values, report, selectedDay, zone, adviceOn, notToday)
    }
    var adjustOpen by rememberSaveable { mutableStateOf(false) }
    var adjustSwaps by remember { mutableStateOf<Map<Long, EntryAdjust.Swap>?>(null) }
    var infoRow by remember { mutableStateOf<DayRow?>(null) }
    LaunchedEffect(displayed) { if (!displayed) infoRow = null }
    val cardRows: List<DayRow> = when (kind) {
        DayKind.LIVE -> trialRows(liveSets, ui.exercises, sealed = false)
        DayKind.SEALED -> trialRows(sealedSets, ui.exercises, sealed = true)
        DayKind.BEGIN, DayKind.PLANNED -> plannedRows(selectedPreset!!.entries, advice?.tags.orEmpty())
        DayKind.RESPITE, DayKind.NO_CYCLE -> emptyList()
    }

    // The day card: the only boxed area, sized to what it holds. `rows` is where its exercise block goes.
    val cardClick: (() -> Unit)? = if (!displayed) null else when (kind) {
        DayKind.SEALED -> sealedTrial?.let { trial -> { actions.onOpenWorkout(trial.id) } }
        DayKind.LIVE -> selectedPreset?.takeIf { it.id == liveTrial?.presetId }?.let { rite -> { actions.onOpenRite(rite.id) } }
        DayKind.BEGIN, DayKind.PLANNED -> selectedPreset?.let { rite -> { actions.onOpenRite(rite.id) } }
        DayKind.RESPITE -> nextRite?.let { rite -> { actions.onOpenRite(rite.id) } }
        DayKind.NO_CYCLE -> null
    }
    // The rite's muscle focus, as the rite pages read it: sets per muscle, from the rite's own movements.
    val glyphSets = remember(selectedPreset) {
        selectedPreset?.let { ProgramRules.weeklyVolume(listOf(PlannedPreset(it.name, "", null, it.toPlanned().entries))) }.orEmpty()
    }
    val daysKept = ui.streak
    val firstRun = isFirstRun(ui.profile?.totalXp)
    val card: @Composable (rows: @Composable () -> Unit) -> Unit = { rows ->
        // A rest day ends on its 44dp "Next:" link, whose own height already pads the bottom: less is added.
        val cardPadding = if (kind == DayKind.RESPITE && nextRite != null) PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 6.dp) else PaddingValues(16.dp)
        InkPanel(Modifier.fillMaxWidth().padding(top = 8.dp), onClick = cardClick, contentPadding = cardPadding) {
            when (kind) {
                DayKind.LIVE -> {
                    val trial = liveTrial!!
                    val ofToday = selectedPreset != null && selectedPreset.id == trial.presetId
                    RiteHeader(
                        title = trial.label,
                        narrator = narratorLine(kind, liveDone, liveWork.size, daysKept),
                        meta = AnnotatedString(
                            if (ofToday) UiText.themedPlural(R.plurals.today_card_live_meta_today, R.plurals.today_card_live_meta_today__t, liveWork.size, liveDone, liveWork.size).asString()
                            else UiText.plural(R.plurals.today_card_live_meta_under_way, liveWork.size, liveDone, liveWork.size).asString(),
                        ),
                        corner = if (ofToday) ({ RiteGlyph(glyphSets, fit = true) }) else null,
                        cornerFits = true,
                    )
                    SegmentBar(liveDone, liveWork.size)
                    rows()
                    Spacer(Modifier.height(BUTTON_GAP))
                    IronvellumButton(
                        label = stringResource(R.string.today_continue_button, trial.label),
                        onClick = { actions.onStartSession(trial.id) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                DayKind.SEALED -> {
                    val trial = sealedTrial!!
                    val rite = selectedPreset!!
                    val done = sealedSets.count { it.done && !it.warmup }
                    val xpText = stringResource(R.string.session_xp_gain, trial.xpAwarded)
                    // The stamp thuds in once, the first time this process shows a rite sealed just now.
                    val thud = LocalTodayLive.current && LocalTodayMotion.current &&
                        remember(trial.id) { stampIsFresh(trial.completedAtMs, nowMs) && stampedTrials.add(trial.id) }
                    RiteHeader(
                        title = rite.name,
                        narrator = narratorLine(kind, daysKept = daysKept),
                        meta = buildAnnotatedString {
                            withStyle(SpanStyle(color = IronvellumColors.SovereignGold, fontWeight = FontWeight.SemiBold)) { append(Words.sealed.c) }
                            append(" · ${cardRows.size} ${plural(cardRows.size, "exercise", "exercises")} · $done ${plural(done, "set", "sets")}")
                            trialLength(trial)?.let { append(" · $it") }
                            append(" · ")
                            withStyle(SpanStyle(color = IronvellumColors.SovereignGold)) { append(xpText) }
                        },
                        corner = { SealedRiteGlyph(sets = glyphSets, date = formatDate(trial.completedAtMs ?: trial.startedAtMs, "EEE d MMM"), thud = thud) },
                    )
                    rows()
                    nextRite?.takeIf { it.id != rite.id }?.let { next ->
                        NextLink(next, chevron = false) { actions.onSelectDay(next.scheduledDay!!) }
                    }
                }
                DayKind.BEGIN, DayKind.PLANNED -> {
                    val rite = selectedPreset!!
                    RiteHeader(
                        title = rite.name,
                        narrator = narratorLine(kind, daysKept = daysKept),
                        meta = AnnotatedString(
                            (if (isTodaySelected) UiText.themed(R.string.today_meta_todays, R.string.today_meta_todays__t).asString() else dayLong(selectedDay)) + " · " +
                                sentencePlan(SessionClock.planLine(rite.toPlanned().entries, ui.focus, ui.pace.secondsPerSet(rite))),
                        ),
                        corner = { RiteGlyph(glyphSets, fit = true) },
                        cornerFits = true,
                    )
                    // Fatigue, above the exercise lines: the line to adjust, or on a worn-out day the panel to rest.
                    if (advice != null && report != null) {
                        when (val a = advice.advice) {
                            is FatigueAdvice.Today.Adjust -> FatigueAdviceLine(
                                title = adjustTitle(advice, a),
                                sub = adjustSub(advice, a, report, zone),
                                level = a.level,
                                onAdjust = if (kind == DayKind.BEGIN) ({ adjustOpen = true }) else null,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                            is FatigueAdvice.Today.Rest -> FatigueRestPanel(
                                title = stringResource(R.string.fatigue_rest_title),
                                sub = restSub(a, report, zone),
                                fresh = restFresh(a, report, zone),
                                doLabel = advice.other?.let { stringResource(R.string.fatigue_rest_do_button, it.name) },
                                onDo = { advice.other?.let { actions.onBeginPreset(it.id) } },
                                onRest = { dismissals?.dismissToday(today, rite.id) },
                                modifier = Modifier.padding(top = 10.dp),
                            )
                            FatigueAdvice.Today.None -> Unit
                        }
                    }
                    rows()
                    // Today is where the day's rite is begun: Train only plans it.
                    if (kind == DayKind.BEGIN) {
                        Spacer(Modifier.height(BUTTON_GAP))
                        if (advice?.advice is FatigueAdvice.Today.Rest) {
                            QuietLink(stringResource(R.string.fatigue_rest_anyway, rite.name), { actions.onBeginPreset(rite.id) })
                        } else {
                            IronvellumButton(
                                label = stringResource(R.string.today_begin_button, rite.name),
                                onClick = { actions.onBeginPreset(rite.id) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                DayKind.RESPITE -> {
                    // A rite done on a day it was not scheduled for ticks its own day; today's card still says it was sealed.
                    val sealedHere = if (isTodaySelected) sealedOnDay(ui.weekDone.values, today, ZoneId.systemDefault()) else null
                    RiteHeader(
                        title = Words.respite.c,
                        narrator = narratorLine(kind, daysKept = daysKept),
                        meta = sealedHere?.let { trial ->
                            val sealedToday = UiText.themed(R.string.today_card_sealed_today, R.string.today_card_sealed_today__t).asString()
                            val xpText = stringResource(R.string.session_xp_gain, trial.xpAwarded)
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = IronvellumColors.SovereignGold, fontWeight = FontWeight.SemiBold)) { append(sealedToday) }
                                append(" · ${trial.label} · $xpText")
                            }
                        },
                        lead = { LedgerMotif() },
                    )
                    nextRite?.let { next -> NextLink(next, chevron = true) { actions.onSelectDay(next.scheduledDay!!) } }
                }
                DayKind.NO_CYCLE -> {
                    RiteHeader(title = UiText.themed(R.string.today_no_cycle_title, R.string.today_no_cycle_title__t).asString(), narrator = narratorLine(kind), meta = null)
                    Spacer(Modifier.height(16.dp))
                    IronvellumButton(label = UiText.themed(R.string.today_no_cycle_forge_button, R.string.today_no_cycle_forge_button__t).asString(), onClick = actions.onOpenForge, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    // Plain rows under the card, divided by hairlines: no panels. Never dropped to make room.
    val peak = ui.newPeaks.firstOrNull()
    val plainRows = buildList<@Composable () -> Unit> {
        if (kind == DayKind.NO_CYCLE) {
            add {
                // Not everyone wants a plan first: a trial can be logged
                // exercise by exercise with no cycle at all.
                ListRow(
                    label = UiText.themed(R.string.today_open_trial_label, R.string.today_open_trial_label__t).asString(),
                    subline = UiText.themed(R.string.today_open_trial_subline, R.string.today_open_trial_subline__t).asString(),
                    onClick = actions.onBeginOpen,
                )
            }
        }
        // Setup can end on a day the cycle leaves free: there is still something to begin.
        val openingRite = firstCycleRite(ui.presets)
        firstBegins(kind, isTodaySelected, firstRun, openingRite != null).forEach { begin ->
            add {
                when (begin) {
                    FirstBegin.RITE -> ListRow(
                        label = stringResource(R.string.today_begin_button, openingRite!!.name),
                        subline = UiText.themed(R.string.today_first_rite_subline, R.string.today_first_rite_subline__t).asString(),
                        onClick = { actions.onBeginPreset(openingRite.id) },
                    )
                    FirstBegin.OPEN -> ListRow(
                        label = UiText.themed(R.string.today_open_trial_label, R.string.today_open_trial_label__t).asString(),
                        subline = UiText.themed(R.string.today_open_trial_subline, R.string.today_open_trial_subline__t).asString(),
                        onClick = actions.onBeginOpen,
                    )
                }
            }
        }
        // A trial under way off the card (another day selected) is one tap from here.
        if (liveTrial != null && kind != DayKind.LIVE) {
            add {
                ListRow(
                    label = stringResource(R.string.today_continue_button, liveTrial.label),
                    subline = UiText.plural(R.plurals.today_sets_logged, liveWork.size, liveDone, liveWork.size).asString(),
                    onClick = { actions.onStartSession(liveTrial.id) },
                )
            }
        }
        // One sheet takes whichever of height and weight is still missing.
        bodyGap?.let { gap ->
            add { BodyGapRow(gap, onFix = actions.onAddBody) }
        }
        if (peak != null) add { PeakRow(peak) { actions.onOpenLift(peak.name, peak.variation) } }
        ui.lastWeek?.let { recap ->
            add {
                ListRow(
                    label = stringResource(R.string.recap_today_label),
                    subline = RecapText.summary(recap).asString(),
                    onClickLabel = stringResource(R.string.recap_open_description),
                    onClick = { actions.onOpenRecap(recap.weekStart.toEpochDay()) },
                )
            }
        }
        // Read here, not in the view model: a close is Compose state, so the card goes at once.
        ui.review?.takeIf { it.period.key !in ReviewDismissals.keys }?.let { summary ->
            add {
                ReviewTodayCard(
                    summary = summary,
                    onOpen = { actions.onOpenReview(summary.period.routeKind, summary.period.from.toEpochDay()) },
                    onDismiss = { actions.onDismissReview(summary.period.key) },
                )
            }
        }
    }
    val plain: @Composable () -> Unit = {
        if (plainRows.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                plainRows.forEachIndexed { index, row ->
                    if (index > 0) InkDivider()
                    row()
                }
            }
        }
    }

    TodayLayout(
        rows = cardRows,
        onExerciseClick = { infoRow = it },
        card = card,
        plain = plain,
        displayed = displayed,
        veil = { full, grow ->
            val form = when {
                !full -> VeilForm.COMPACT
                kind == DayKind.RESPITE || kind == DayKind.NO_CYCLE -> VeilForm.HERO
                else -> VeilForm.FULL
            }
            VeilSection(veil, form, nowMs, actions.onOpenGarrison, onOpenCrest = actions.onOpenCrest, firstRun = firstRun, grow = grow)
        },
    )

    infoRow?.let { row ->
        val exercise = ui.exercises[row.exerciseId]
        if (displayed && exercise != null) {
            ExerciseInfoSheet(exercise = exercise, lastLine = null, onDismiss = { infoRow = null }, onPick = null, modifiers = row.modifiers)
        }
    }

    // The adjust sheet lives out here, not in the card: the card is composed more than once to be measured.
    val adjust = advice?.advice as? FatigueAdvice.Today.Adjust
    if (adjustOpen && displayed && adjust != null && report != null && selectedPreset != null) {
        LaunchedEffect(Unit) { adjustSwaps = actions.swapsFor(advice.leans, selectedPreset.entries, report, advice.atMs) }
        adjustSwaps?.let { swaps ->
            AdjustDialog(
                rite = selectedPreset,
                advice = advice,
                adjust = adjust,
                report = report,
                zone = zone,
                swaps = swaps,
                onBegin = { override ->
                    adjustOpen = false
                    adjustSwaps = null
                    actions.onBeginAdjusted(selectedPreset.id, override)
                },
                onNotToday = {
                    dismissals?.dismissToday(today, selectedPreset.id)
                    adjustOpen = false
                    adjustSwaps = null
                },
                onDismiss = {
                    adjustOpen = false
                    adjustSwaps = null
                },
            )
        }
    }
}

/** Exercise targets keep their full touch height even when the page is tight. */
private val LINE_HEIGHT = 48.dp
private val TIGHT_LINE = 48.dp

/** Space either side of the divider between two exercises sharing a line. */
private val PAIR_GAP = 12.dp

/** Room the check column takes in a half-width cell when the card has one (a full-width line gives it 26dp). */
private val HALF_CHECK_SLOT = 20.dp

/** The gap above the card's exercise block. */
private val ROWS_TOP_PAD = 6.dp

/** The gap between the exercise block and the card's button. */
private val BUTTON_GAP = 8.dp

/** The "+N more" line's least height. */
private val MORE_LINE = 24.dp

/** Clear of the raised Train plate, which stands 10dp above the bar. */
private val BOTTOM_CLEARANCE = 28.dp

/** Kept free under a grown Veil, on top of [BOTTOM_CLEARANCE]. */
private val GROW_MARGIN = 12.dp

/** The page's side margin; the card pads its content another 16dp inside it. */
private val GUTTER = 16.dp

/** One day body: card, plain rows, Veil (absent when probing the rest), bottom clearance. */
@Composable
private fun TodayPage(
    card: @Composable (rows: @Composable () -> Unit) -> Unit,
    plain: @Composable () -> Unit,
    rows: @Composable () -> Unit,
    veil: (@Composable () -> Unit)?,
    live: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // Only the page that is shown is live: a measuring probe must neither animate nor spend a one-off.
    CompositionLocalProvider(LocalTodayLive provides live) {
        Column(modifier.fillMaxWidth().padding(horizontal = GUTTER)) {
            card(rows)
            plain()
            veil?.invoke()
            Spacer(Modifier.height(BOTTOM_CLEARANCE))
        }
    }
}

/**
 * Lays Today out to the height it is given, never scrolling while it can fit. It measures the page
 * without its exercise rows and Veil, one row, the "+N more" line and both Veil forms, hands those
 * heights to [fitToday] and composes the page once with what fits. The probes sit in slots of their
 * own and are never placed.
 *
 * Degradation, in order: the Veil's full form (the hero on a respite day) gives way to the compact
 * one; lines fold into "+N more" (N counts exercises). Plain rows are never dropped.
 * Every Veil form carries its reserved inscriptions line, so the probes already include it.
 */
@Composable
private fun TodayLayout(
    rows: List<DayRow>,
    onExerciseClick: (DayRow) -> Unit,
    card: @Composable (rows: @Composable () -> Unit) -> Unit,
    plain: @Composable () -> Unit,
    veil: @Composable (full: Boolean, grow: Dp) -> Unit,
    displayed: Boolean,
) {
    val measurer = rememberTextMeasurer()
    val nameStyle = MaterialTheme.typography.bodyMedium
    val valueStyle = MaterialTheme.typography.labelMedium
    SubcomposeLayout(Modifier.fillMaxSize()) { constraints ->
        val width = constraints.maxWidth
        val bounded = constraints.hasBoundedHeight
        val available = if (bounded) constraints.maxHeight else Int.MAX_VALUE / 4
        val loose = Constraints(minWidth = width, maxWidth = width)
        // A probe stays composed while it is measured but is never placed, so it must expose nothing
        // to semantics: a screen reader and a test would otherwise meet every probed part twice.
        fun heightOf(slot: Any, content: @Composable () -> Unit): Int =
            subcompose(slot) { Box(Modifier.clearAndSetSemantics {}) { content() } }.sumOf { it.measure(loose).height }

        val chrome = heightOf("chrome") { TodayPage(card, plain, rows = {}, veil = null) }
        val veilCompact = heightOf("veilCompact") { Column(Modifier.fillMaxWidth().padding(horizontal = GUTTER)) { veil(false, 0.dp) } }
        val veilFull = heightOf("veilFull") { Column(Modifier.fillMaxWidth().padding(horizontal = GUTTER)) { veil(true, 0.dp) } }
        // The check column exists only when some exercise has a check to show; until then names run flush left.
        val gutter = rows.any { it.checked }
        // A row sits inside the page gutter and the card's own padding; only its content height matters here.
        val rowContent =
            if (rows.isEmpty()) 0
            else heightOf("row") { Column(Modifier.fillMaxWidth().padding(horizontal = GUTTER * 2)) { ExerciseLine(listOf(rows.first()), height = null, divider = false, gutter = gutter, onClick = {}) } }
        val moreLine =
            if (rows.isEmpty()) 0
            else heightOf("more") { Column(Modifier.fillMaxWidth().padding(horizontal = GUTTER * 2)) { MoreLine(hidden = 1, divider = false, gutter = gutter) } }
        val naturalLine = maxOf(LINE_HEIGHT.roundToPx(), rowContent)
        val tightLine = maxOf(TIGHT_LINE.roundToPx(), rowContent)
        // Two exercises share a line only when each passes the character rule AND its real text fits half the
        // card's inner width: at a large font the same names no longer fit, and the lines fall back to full width.
        val cardInner = width - (GUTTER * 4).roundToPx()
        val cellWidth = (cardInner - (PAIR_GAP * 2 + 1.dp).roundToPx()) / 2
        val lines = packLines(rows) { row ->
            cellWidth >= 48.dp.roundToPx() && fitsHalfLine(row) && halfCellNeeds(row, gutter, measurer, nameStyle, valueStyle, this) <= cellWidth
        }

        val fit = fitToday(
            TodayBudget(
                available = available,
                chrome = chrome,
                lineCount = lines.size,
                rowsTopPad = ROWS_TOP_PAD.roundToPx(),
                naturalLine = naturalLine,
                tightLine = tightLine,
                moreLine = moreLine,
                veilFull = veilFull,
                veilCompact = veilCompact,
            ),
        )
        val lineHeight = (fit?.lineHeight ?: naturalLine).toDp()
        val shown = fit?.lines ?: lines.size
        val page: @Composable (Dp, Boolean) -> Unit = { grow, live ->
            val contentModifier = if (fit == null) Modifier.verticalScroll(rememberScrollState()) else Modifier
            TodayPage(
                card = card, plain = plain,
                rows = { ExerciseBlock(lines, shown, lineHeight, hidden = hiddenExercises(lines, shown), onClick = onExerciseClick) },
                veil = { veil(fit?.fullVeil ?: true, grow) }, live = live, modifier = contentModifier,
            )
        }
        // Room the fitted page truly leaves under a full Veil, measured on the page itself, less a margin
        // above the raised Train plate; the respite centrepiece grows into it so a tall phone keeps no gap.
        val grow = if (bounded && fit?.fullVeil == true) {
            val natural = heightOf("natural") { page(0.dp, false) }
            (available - natural - GROW_MARGIN.roundToPx()).coerceAtLeast(0).toDp()
        } else 0.dp
        val body = subcompose("page") { page(grow, displayed) }.map { it.measure(if (bounded) Constraints.fixed(width, available) else loose) }
        val height = if (bounded) available else body.maxOf { it.height }
        layout(width, height) {
            body.forEach { it.place(0, 0) }
        }
    }
}

/** Today's label and workout status stay fixed while the selected-day tile follows a swipe. */
@Composable
private fun WeekRail(
    selectedDay: Int,
    today: Int,
    scheduled: Set<Int>,
    done: Set<Int>,
    onSelect: (Int) -> Unit,
    dayOffset: () -> Float,
) {
    var width by remember { mutableIntStateOf(0) }
    val tileWidth = with(LocalDensity.current) { (width.toDp() / 7 - 8.dp).coerceAtLeast(0.dp) }
    val shape = MaterialTheme.shapes.medium
    Box(Modifier.fillMaxWidth().clipToBounds().onSizeChanged { width = it.width }) {
        // Draw behind the fixed letters/statuses; duplicate past the edges for Sunday/Monday.
        for (wrap in -1..1) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset {
                        val position = ((selectedDay - 1 + dayOffset()) % 7f + 7f) % 7f
                        IntOffset(((position + 0.5f + wrap * 7f) * width / 7f - tileWidth.toPx() / 2).roundToInt(), 0)
                    }
                    .size(width = tileWidth, height = 48.dp)
                    .clip(shape)
                    .background(IronvellumColors.AccentTint)
                    .inkBorder(IronvellumColors.AccentTintBorder, shape)
                    .then(if (wrap == 0) Modifier.testTag("today-week-indicator") else Modifier),
            ) {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).background(IronvellumColors.Emerald))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            WEEK_LETTERS.forEachIndexed { index, letter ->
                val day = index + 1
                val isDone = day in done
                val isSelected = day == selectedDay
                val isToday = day == today
                val status = when {
                    isDone -> UiText.themed(R.string.today_week_status_sealed, R.string.today_week_status_sealed__t).asString()
                    day in scheduled -> UiText.themed(R.string.today_week_status_scheduled, R.string.today_week_status_scheduled__t).asString()
                    else -> UiText.themed(R.string.today_week_status_none, R.string.today_week_status_none__t).asString()
                }
                val dayDescription = dayLong(day).let { if (isDone) stringResource(R.string.today_week_day_done, it) else it }
                val stateText = if (isToday) stringResource(R.string.today_week_status_today, status) else status
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 60.dp)
                        .clickable(role = Role.Tab) { onSelect(day) }
                        .semantics(mergeDescendants = true) {
                            selected = isSelected
                            contentDescription = dayDescription
                            stateDescription = stateText
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // All three slots are reserved even when empty: no reflow between states.
                    Box(Modifier.height(12.dp), contentAlignment = Alignment.Center) {
                        if (isToday) Text(
                            stringResource(R.string.today_week_label_today),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            lineHeight = 12.sp,
                            color = IronvellumColors.Ink,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                    }
                    Box(Modifier.height(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            letter,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected || day in scheduled || isDone) IronvellumColors.Ink else IronvellumColors.InkMuted,
                        )
                    }
                    Box(Modifier.height(24.dp), contentAlignment = Alignment.Center) {
                        if (isDone) SealMark(18.dp)
                        else if (day in scheduled) {
                            Canvas(Modifier.size(10.dp).clearAndSetSemantics {}) {
                                val diamond = Path().apply {
                                    moveTo(size.width / 2, 1.dp.toPx())
                                    lineTo(size.width - 1.dp.toPx(), size.height / 2)
                                    lineTo(size.width / 2, size.height - 1.dp.toPx())
                                    lineTo(1.dp.toPx(), size.height / 2)
                                    close()
                                }
                                drawPath(diamond, IronvellumColors.Ink, style = Stroke(1.dp.toPx()))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One straight segment per set, [done] of [total] filled, with 3dp between. */
@Composable
private fun SegmentBar(done: Int, total: Int) {
    if (total <= 0) return
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(total) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(3.dp)
                    .background(if (index < done) IronvellumColors.Emerald else IronvellumColors.Rune),
            )
        }
    }
}

/**
 * The day's movements as [lines] (one exercise or two per line): the first [shown] lines, each [lineHeight]
 * tall with its hairline inside that height, then a muted "+N more" line for the [hidden] exercises on the
 * rest. Nothing at all when there is nothing to show.
 */
@Composable
private fun ExerciseBlock(lines: List<List<DayRow>>, shown: Int, lineHeight: Dp, hidden: Int, onClick: (DayRow) -> Unit) {
    if (shown == 0 && hidden == 0) return
    val gutter = lines.any { line -> line.any { it.checked } }
    Column(Modifier.fillMaxWidth().padding(top = ROWS_TOP_PAD).testTag("today-rows")) {
        lines.take(shown).forEachIndexed { index, line -> ExerciseLine(line, lineHeight, divider = index > 0, gutter = gutter, onClick = onClick) }
        if (hidden > 0) MoreLine(hidden, divider = shown > 0, gutter = gutter)
    }
}

/** One line of the card: a single exercise at full width, or two in half-width cells with a divider between. [height] null measures its content. */
@Composable
private fun ExerciseLine(line: List<DayRow>, height: Dp?, divider: Boolean, gutter: Boolean, onClick: (DayRow) -> Unit) {
    Box(Modifier.fillMaxWidth().then(if (height != null) Modifier.height(height) else Modifier)) {
        if (divider) InkDivider(Modifier.align(Alignment.TopStart))
        Row(Modifier.fillMaxWidth().align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            if (line.size == 1) {
                ExerciseCell(line[0], checkSlot = if (gutter) 26.dp else 0.dp, Modifier.weight(1f), onClick)
            } else {
                ExerciseCell(line[0], checkSlot = if (gutter) HALF_CHECK_SLOT else 0.dp, Modifier.weight(1f).padding(end = PAIR_GAP), onClick)
                Box(Modifier.width(1.dp).height(20.dp).background(IronvellumColors.Rune))
                ExerciseCell(line[1], checkSlot = if (gutter) HALF_CHECK_SLOT else 0.dp, Modifier.weight(1f).padding(start = PAIR_GAP), onClick)
            }
        }
    }
}

/** What a [row] needs in a half-width cell: its check, name, mini bar and sets text, so a pair only forms when both fit. */
private fun halfCellNeeds(row: DayRow, gutter: Boolean, measurer: TextMeasurer, nameStyle: ComposeTextStyle, valueStyle: ComposeTextStyle, density: Density): Int = with(density) {
    fun textWidth(text: String, style: ComposeTextStyle) = measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
    var needs = textWidth(row.name, nameStyle) + 8.dp.roundToPx() + textWidth(row.value, valueStyle)
    if (gutter) needs += HALF_CHECK_SLOT.roundToPx()
    if (row.partly) {
        val segments = row.total.coerceAtMost(MINI_BAR_SEGMENTS)
        needs += 8.dp.roundToPx() + (segments * 8 + (segments - 1) * 2).dp.roundToPx()
    }
    needs
}

/**
 * One movement: a check when done, its name, a mini bar when part way, its sets. Name then sets, in that order,
 * so the card's merged reading stays one exercise after another whether a line holds one exercise or two.
 */
@Composable
private fun ExerciseCell(row: DayRow, checkSlot: Dp, modifier: Modifier, onClick: (DayRow) -> Unit) {
    Row(modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { onClick(row) }, verticalAlignment = Alignment.CenterVertically) {
        if (checkSlot > 0.dp) {
            Box(Modifier.width(checkSlot)) {
                if (row.checked) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = IronvellumColors.Emerald,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Text(
            row.name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (row.checked) IronvellumColors.InkMuted else IronvellumColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (row.partly) {
            Row(Modifier.padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(row.total.coerceAtMost(MINI_BAR_SEGMENTS)) { segment ->
                    Box(
                        Modifier
                            .size(width = 8.dp, height = 3.dp)
                            .background(if (segment < row.done) IronvellumColors.Emerald else IronvellumColors.Rune),
                    )
                }
            }
        }
        row.fatigue?.let { tag ->
            Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                FatigueSwatch(tag.level)
                Spacer(Modifier.width(4.dp))
                Text(tag.muscle.label, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.Caution, maxLines = 1)
            }
        }
        Text(
            row.value,
            style = MaterialTheme.typography.labelMedium,
            color = IronvellumColors.InkMuted,
            maxLines = 1,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** "+3 more": the exercises that did not fit; the card's own tap opens the rite or trial that lists them. */
@Composable
private fun MoreLine(hidden: Int, divider: Boolean, gutter: Boolean) {
    Box(Modifier.fillMaxWidth().heightIn(min = MORE_LINE), contentAlignment = Alignment.CenterStart) {
        if (divider) InkDivider(Modifier.align(Alignment.TopStart))
        Text(
            UiText.res(R.string.today_more_rows, hidden).asString(),
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
            maxLines = 1,
            modifier = Modifier.padding(start = if (gutter) 26.dp else 0.dp),
        )
    }
}

/** "Next: Full Body A · Fri", a quiet link that shows that day. */
@Composable
private fun NextLink(next: WorkoutPreset, chevron: Boolean, onClick: () -> Unit) {
    val day = next.scheduledDay!!
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.today_next_show_label, dayName(day)), onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(R.string.today_next_link, next.name, dayName(day)),
            style = MaterialTheme.typography.bodyMedium,
            color = IronvellumColors.SystemGreen,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (chevron) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = IronvellumColors.SystemGreen,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/**
 * A fresh peak as one 52dp plain row: the lift, "New peak" in gold over when and how far it rose, and
 * the set that did it. Opens the lift.
 */
@Composable
private fun PeakRow(peak: RepMax.Recent, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.today_peak_open_label, peak.label), onClick = onOpen)
            .heightIn(min = ListRowHeight)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                peak.label,
                style = MaterialTheme.typography.bodyMedium,
                color = IronvellumColors.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val newPeak = UiText.themed(R.string.today_peak_new, R.string.today_peak_new__t).asString()
            val detail = stringResource(R.string.today_peak_detail, peak.hit.kind.label, shortWhen(peak.atMs), peakGainText(peak))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = IronvellumColors.SovereignGold)) { append(newPeak) }
                    append(" · $detail")
                },
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            peakSetText(peak),
            style = MaterialTheme.typography.labelMedium,
            color = IronvellumColors.Ink,
            maxLines = 1,
        )
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = IronvellumColors.InkMuted)
    }
}

/** One 52dp row: what the scores wait for, and a green link to where it is added. */
@Composable
private fun BodyGapRow(gap: BodyGap, onFix: () -> Unit) {
    val (label, subline) = when (gap) {
        BodyGap.BOTH -> stringResource(R.string.today_body_gap_both_label) to stringResource(R.string.today_body_gap_scores_subline)
        BodyGap.HEIGHT -> stringResource(R.string.today_body_gap_height_label) to stringResource(R.string.today_body_gap_height_subline)
        BodyGap.WEIGHT -> stringResource(R.string.today_body_gap_weight_label) to stringResource(R.string.today_body_gap_scores_subline)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ListRowHeight)
            .clickable(role = Role.Button, onClick = onFix)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.Straighten, contentDescription = null, tint = IronvellumColors.InkMuted, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink)
            Text(subline, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
        }
        Text(
            stringResource(R.string.today_body_gap_add_button),
            style = MaterialTheme.typography.labelMedium,
            color = IronvellumColors.SystemGreen,
            maxLines = 1,
        )
    }
}
