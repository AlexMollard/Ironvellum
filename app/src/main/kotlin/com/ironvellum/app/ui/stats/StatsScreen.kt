package com.ironvellum.app.ui.stats

import com.ironvellum.app.domain.fmt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.wrapContentHeight
import com.ironvellum.app.ui.components.LedgerContrast
import com.ironvellum.app.ui.components.LedgerSpace
import com.ironvellum.app.ui.components.rememberZoneId
import com.ironvellum.app.domain.MeasurementEntry
import com.ironvellum.app.domain.Measurements
import kotlinx.coroutines.flow.Flow
import com.ironvellum.app.ui.components.IronvellumButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.viewModelScope
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.components.InkListRow
import androidx.compose.material.icons.outlined.History
import com.ironvellum.app.ui.components.InkTabbedPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.TrendChart
import com.ironvellum.app.ui.components.BarChart
import com.ironvellum.app.ui.components.PanelLabel
import com.ironvellum.app.ui.components.StatSize
import com.ironvellum.app.ui.components.StatValue
import com.ironvellum.app.domain.LedgerRange
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.style.TextOverflow
import com.ironvellum.app.ui.components.formatDate
import com.ironvellum.app.ui.components.formatBodyValue
import com.ironvellum.app.ui.components.plural
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.data.Repository
import com.ironvellum.app.domain.BodyLimits
import com.ironvellum.app.domain.BodyStats
import com.ironvellum.app.domain.StatEntry
import com.ironvellum.app.domain.WorkoutSession
import com.ironvellum.app.domain.HealthDay
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.Energy
import com.ironvellum.app.domain.EnergyConfidence
import com.ironvellum.app.domain.EnergyEstimate
import com.ironvellum.app.domain.MeasurementSite
import com.ironvellum.app.domain.STEP_GOAL
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.theme.inkStroke
import com.ironvellum.app.ui.theme.IronvellumColors
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import com.ironvellum.app.ui.theme.IronvellumTracking
import kotlinx.coroutines.flow.stateIn
import com.ironvellum.app.ui.launchGuarded
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import com.ironvellum.app.domain.Sex
import com.ironvellum.app.domain.LiftRecord
import com.ironvellum.app.domain.LiftRecords
import androidx.compose.foundation.layout.width
import com.ironvellum.app.ui.components.InfoAction
import com.ironvellum.app.ui.components.InfoChip
import com.ironvellum.app.ui.components.InfoFigure
import com.ironvellum.app.ui.components.InfoFigures
import com.ironvellum.app.ui.components.InfoSheet
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.domain.Band
import com.ironvellum.app.domain.BandTable
import com.ironvellum.app.domain.BandTone
import com.ironvellum.app.domain.Bands
import com.ironvellum.app.domain.Ledger
import com.ironvellum.app.domain.DecimalInput
import com.ironvellum.app.ui.dashboard.stepsAsOfCaption
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import com.ironvellum.app.R
import java.util.Locale
import com.ironvellum.app.ui.components.Term
import com.ironvellum.app.ui.components.TermInfo

/** Band tones to theme tokens: gold stays for earned things, so no band is gold. */
private fun toneColor(tone: BandTone): Color = when (tone) {
    BandTone.LOW -> LedgerContrast.Graphic
    BandTone.OK -> IronvellumColors.SystemGreen
    BandTone.GOOD -> IronvellumColors.Emerald
    BandTone.STRONG -> IronvellumColors.EmeraldBright
    BandTone.WARN -> IronvellumColors.InkMuted
    BandTone.DANGER -> IronvellumColors.DangerRed
}

data class StatsUi(
    /** False until the first real emission: the screen draws nothing rather than flash its empty state. */
    val loaded: Boolean = false,
    val stats: List<StatEntry> = emptyList(),
    val completedDates: Set<LocalDate> = emptySet(),
    val scheduledDays: Set<Int> = emptySet(),
    val sessions: List<WorkoutSession> = emptyList(),
    val healthDays: List<HealthDay> = emptyList(),
    val exercises: Map<Long, Exercise> = emptyMap(),
    /** Sets per session id, so energy estimates can work the real logged work. */
    val sessionSets: Map<Long, List<SessionSet>> = emptyMap(),
    /** Profile-owned height (Settings); null until the lifter sets it once. */
    val profileHeight: Double? = null,
    /** Profile sex: picks the FFMI band table. */
    val sex: Sex = Sex.MALE,
    /** When Health Connect last returned days this process; null until it has. */
    val healthSyncedAtMs: Long? = null,
    val measurements: List<MeasurementEntry> = emptyList(),
)

class StatsViewModel(private val repo: Repository) : ViewModel() {
    // Not a StateFlow: a stateIn with an empty initial value made `ui` emit a
    // "loaded" empty screen before Room had answered.
    private val log: Flow<Triple<List<StatEntry>, List<Pair<WorkoutSession, List<SessionSet>>>, List<HealthDay>>> =
        combine(
            repo.observeStats(),
            repo.observeHistory(),
            repo.observeHealthDays(),
        ) { stats, history, healthDays ->
            Triple(stats, history, healthDays)
        }
    val ui: StateFlow<StatsUi> = combine(
        log,
        repo.observePresets(),
        repo.observeExercises(),
        repo.observeBodyProfile(),
        combine(repo.observeHealthSyncedAt(), repo.observeMeasurements()) { syncedAt, tape -> syncedAt to tape },
    ) { (stats, history, healthDays), presets, exercises, bodyProfile, (syncedAt, tape) ->
        StatsUi(
            loaded = true,
            stats = stats,
            scheduledDays = presets.mapNotNull { it.scheduledDay }.toSet(),
            sessions = history.map { it.first }.sortedBy { it.startedAtMs },
            healthDays = healthDays,
            exercises = exercises.associateBy { it.id },
            sessionSets = history.associate { it.first.id to it.second },
            profileHeight = bodyProfile.first,
            sex = bodyProfile.second,
            healthSyncedAtMs = syncedAt,
            measurements = tape,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUi())

    fun deleteStat(id: Long) {
        viewModelScope.launch { repo.deleteStat(id) }
    }

    fun restoreStat(stat: StatEntry) {
        viewModelScope.launchGuarded("restore reading") { repo.restoreStat(stat) }
    }

    fun addStat(weightKg: Double, bodyFatPct: Double?) {
        viewModelScope.launchGuarded("log reading") { repo.addStat(weightKg, bodyFatPct) }
    }

    fun syncHealthHistory(days: Int = 90) {
        viewModelScope.launch { repo.syncHealthHistory(days) }
    }
}

/**
 * BODY / TRAINING / DAILY. DAILY, not ACTIVITY: the Train screen's workout history
 * was called the "Activity Log", so one word named both a step count and a
 * training record. BODY, not FRAME: the tab holds the body.
 */
private enum class StatsTab(val label: String) { BODY("BODY"), TRAINING("TRAINING"), DAILY("DAILY") }

/** Panes inside the Ledger. They replace the tabs and answer to Back, so they need no routes. */
private enum class LedgerPage { MAIN, HISTORY, TAPE }

@Composable
fun StatsScreen(
    // No default: a defaulted no-op lets a forgotten nav wiring compile clean,
    // which is how five screens ended up unreachable earlier.
    onOpenMeasurement: (MeasurementSite) -> Unit,
    onOpenLog: () -> Unit,
    onOpenWorkout: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: StatsViewModel =
        viewModel(factory = viewModelFactory { initializer { StatsViewModel(ironvellumRepository()) } }),
) {
    val stream by viewModel.ui.collectAsStateWithLifecycle()
    val zone = rememberZoneId()
    // Sealed days are bucketed here, with the zone the screen is showing, not in
    // the ViewModel with whatever zone was current at the last Room emission.
    val ui = remember(stream, zone) {
        stream.copy(completedDates = stream.sessions.map { trialDay(it, zone) }.toSet())
    }
    // Everything the lifter chose survives rotation and process death: the
    // dialog, the tab, the pane, the range, the month and each tab's scroll.
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var drill by rememberSaveable { mutableStateOf<String?>(null) }
    // Saveable, so it carries the open tab through rotation and process death.
    val pager = rememberPagerState { StatsTab.entries.size }
    val scope = rememberCoroutineScope()
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }
    var rangeIndex by rememberSaveable { mutableIntStateOf(0) }
    var monthsBack by rememberSaveable { mutableIntStateOf(0) }
    var dailyRangeIndex by rememberSaveable { mutableIntStateOf(1) }
    val page = LedgerPage.entries[pageIndex]
    val range = LedgerRange.entries[rangeIndex]
    val bodyScroll = rememberScrollState()
    val trainingScroll = rememberScrollState()
    val dailyScroll = rememberScrollState()
    var lastDeleted by remember { mutableStateOf<StatEntry?>(null) }

    val today = rememberToday(zone)
    // Health Connect trails the watch: pull the last fortnight whenever the Ledger opens.
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.syncHealthHistory(14) }
    val latest = ui.stats.firstOrNull()
    val ffmiReading = remember(ui.stats, ui.profileHeight) { Ledger.latestFfmi(ui.stats, ui.profileHeight) }

    BackHandler(enabled = page != LedgerPage.MAIN) { pageIndex = 0 }

    when (page) {
        LedgerPage.HISTORY -> WeightHistoryPage(
            stats = ui.stats,
            profileHeight = ui.profileHeight,
            lastDeleted = lastDeleted,
            onDelete = {
                lastDeleted = it
                viewModel.deleteStat(it.id)
            },
            onUndo = {
                lastDeleted?.let(viewModel::restoreStat)
                lastDeleted = null
            },
            onUndoExpired = { lastDeleted = null },
            onBack = { pageIndex = 0 },
        )
        LedgerPage.TAPE -> TapePage(
            entries = ui.measurements,
            onOpenSite = onOpenMeasurement,
            onBack = { pageIndex = 0 },
        )
        LedgerPage.MAIN -> Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth().padding(start = LedgerSpace.Gutter, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "THE LEDGER",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                    letterSpacing = IronvellumTracking.ScreenTitle,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                Text(
                    "+ WEIGHT",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.SystemGreen,
                    letterSpacing = IronvellumTracking.InlineLabel,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraSmall)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) { showAdd = true }
                        .heightIn(min = LedgerSpace.Target)
                        .padding(horizontal = 12.dp)
                        .wrapContentHeight(),
                )
                IconButton(onClick = onOpenLog) {
                    Icon(
                        Icons.Outlined.History,
                        contentDescription = "Full chronicle",
                        tint = IronvellumColors.SystemGreen,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            // Pages run edge to edge under the strip, each with its own hoisted scroll.
            InkTabbedPager(
                labels = StatsTab.entries.map { it.label },
                state = pager,
                modifier = Modifier.weight(1f),
                tabsModifier = Modifier.padding(horizontal = LedgerSpace.Gutter),
            ) { index ->
                // Nothing is drawn until Room has answered: an empty state shown for
                // a frame reads as "you have no data".
                if (!ui.loaded) return@InkTabbedPager
                when (StatsTab.entries[index]) {
                    StatsTab.BODY -> BodyTab(
                        ui = ui,
                        today = today,
                        zone = zone,
                        scroll = bodyScroll,
                        range = range,
                        onRange = { rangeIndex = it.ordinal },
                        onDrill = { drill = it },
                        onLogWeight = { showAdd = true },
                        onOpenTraining = { scope.launch { pager.animateScrollToPage(StatsTab.TRAINING.ordinal) } },
                        onOpenTape = { pageIndex = LedgerPage.TAPE.ordinal },
                        onOpenDaily = { scope.launch { pager.animateScrollToPage(StatsTab.DAILY.ordinal) } },
                        onOpenHistory = { pageIndex = LedgerPage.HISTORY.ordinal },
                    )
                    StatsTab.TRAINING -> TrainingTab(
                        ui = ui,
                        today = today,
                        month = YearMonth.from(today).minusMonths(monthsBack.toLong()),
                        onMonth = { monthsBack = (monthsBack - it).coerceAtLeast(0) },
                        scroll = trainingScroll,
                        onOpenLog = onOpenLog,
                        onOpenWorkout = onOpenWorkout,
                    )
                    StatsTab.DAILY -> ActivityTab(
                        days = ui.healthDays,
                        today = today,
                        syncedAtMs = ui.healthSyncedAtMs,
                        profileHeight = ui.profileHeight,
                        stats = ui.stats,
                        sessions = ui.sessions,
                        sessionSets = ui.sessionSets,
                        exercises = ui.exercises,
                        onOpenSettings = onOpenSettings,
                        scroll = dailyScroll,
                        rangeIndex = dailyRangeIndex,
                        onRange = { dailyRangeIndex = it },
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddStatDialog(
            initialWeight = latest?.weightKg?.let { formatBodyValue(it) } ?: "",
            heightCm = ui.profileHeight,
            sex = ui.sex,
            measurements = remember(ui.measurements) {
                Measurements.latest(ui.measurements).mapValues { it.value.valueCm }
            },
            onDismiss = { showAdd = false },
            onOpenSettings = {
                showAdd = false
                onOpenSettings()
            },
            onConfirm = { weight, bf ->
                viewModel.addStat(weight, bf)
                showAdd = false
            },
        )
    }

    drill?.let { metric ->
        // Kept as (reading, value) pairs so the scrub readout can name each day.
        val readings = ui.stats.sortedBy { it.takenAtMs }.mapNotNull {
            val v = when (metric) {
                "BMI" -> Ledger.bmiOf(it, ui.profileHeight)
                else -> Ledger.ffmiOf(it, ui.profileHeight)
            }
            v?.let { value -> it to value }
        }
        val series = readings.map { it.second }
        val current = series.lastOrNull()
        StatDrillSheet(
            metric = metric,
            table = if (metric == "BMI") Bands.BMI else Bands.ffmi(ui.sex),
            series = series,
            seriesDates = readings.map { formatDate(it.first.takenAtMs, "d MMM") },
            current = current,
            asOf = if (metric == "BMI") null else ffmiReading?.let { formatDate(it.takenAtMs, "d MMM yyyy") },
            onDismiss = { drill = null },
        )
    }
}

/**
 * Each lift's best estimated one-rep max with its trend. The number is the
 * Epley estimate of the marked load (a pull-up's ADDED kilos), so it is
 * comparable with itself over time, not with a bodyweight-inclusive table.
 * Gold appears only as the PEAK tag on a peak set in the last fortnight.
 */
@Composable
private fun LiftRecordsPanel(records: List<LiftRecord>, nowMs: Long) {
    InkPanel(Modifier.fillMaxWidth()) {
        PanelLabel("LIFT PEAKS")
        Text(
            "Best estimated 1RM of the marked load, in kg. Epley, up to 12 reps.",
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
        )
        if (records.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Log weighted sets of 1 to 12 reps to build the board.",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
        }
        records.forEachIndexed { i, record ->
            if (i > 0) InkDivider()
            LiftRecordRow(record, fresh = LiftRecords.isFresh(record, nowMs))
        }
    }
}

@Composable
private fun LiftRecordRow(record: LiftRecord, fresh: Boolean) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = LedgerSpace.Target).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                record.name,
                style = MaterialTheme.typography.bodyMedium,
                color = IronvellumColors.InkMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    record.deltaKg?.let { "${Ledger.signed(it, "kg")} / 90d" } ?: "no 90d history yet",
                    style = MaterialTheme.typography.labelSmall,
                    color = IronvellumColors.InkMuted,
                )
                if (fresh) {
                    Text(
                        "PEAK",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        color = IronvellumColors.SovereignGold,
                        letterSpacing = IronvellumTracking.InlineLabel,
                    )
                }
            }
        }
        Box(Modifier.width(88.dp)) {
            TrendChart(
                record.series,
                IronvellumColors.Emerald,
                fromZero = false,
                modifier = Modifier.fillMaxWidth().height(32.dp),
                description = "${record.name} best estimated one-rep max by trial, " +
                    "${record.series.size} ${plural(record.series.size, "trial", "trials")}",
                recordMarker = false,
                scrub = false,
            )
        }
        StatValue(
            "%.1f".format(Locale.US, record.bestE1rmKg),
            size = StatSize.Inline,
            unit = "kg",
        )
    }
}

/**
 * TRAINING: the calendar leads (the owner opens this tab to see whether he
 * trained), then the records board, then the per-trial strength line, then the
 * door to the full chronicle. One column, panels [LedgerSpace.Panel] apart.
 */
@Composable
private fun TrainingTab(
    ui: StatsUi,
    today: LocalDate,
    month: YearMonth,
    onMonth: (Int) -> Unit,
    scroll: androidx.compose.foundation.ScrollState,
    onOpenLog: () -> Unit,
    onOpenWorkout: (Long) -> Unit,
) {
    val nowMs = remember(today) { System.currentTimeMillis() }
    val records = remember(ui.sessions, ui.sessionSets, ui.exercises, today) {
        LiftRecords.board(ui.sessions, ui.sessionSets, ui.exercises, nowMs)
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = LedgerSpace.Gutter),
        verticalArrangement = Arrangement.spacedBy(LedgerSpace.Panel),
    ) {
        Spacer(Modifier.height(LedgerSpace.Panel))
        TrainingCalendar(
            sessions = ui.sessions,
            completedDates = ui.completedDates,
            scheduledDays = ui.scheduledDays,
            month = month,
            today = today,
            onMonth = onMonth,
            onOpenTrial = onOpenWorkout,
        )

        if (ui.sessions.isNotEmpty()) LiftRecordsPanel(records, nowMs)

        InkPanel(Modifier.fillMaxWidth()) {
            PanelLabel("STRENGTH SCORE PER TRIAL")
            // 0 means "not scored" (no bodyweight existed yet), not a collapse
            // in strength - plotting it dropped the line to the floor.
            val scored = ui.sessions.filter { it.strengthScore > 0 }
            val scores = scored.map { it.strengthScore.toDouble() }
            if (scores.size >= 2) {
                Spacer(Modifier.height(8.dp))
                fun day(i: Int) = formatDate(scored[i].completedAtMs ?: scored[i].startedAtMs, "d MMM")
                TrendChart(
                    scores,
                    IronvellumColors.Emerald,
                    fromZero = false,
                    startLabel = day(0),
                    endLabel = day(scored.lastIndex),
                    valueText = { "%.0f strength score".fmt(it) },
                    dateText = ::day,
                )
                ChartCaption(
                    "Best ${scores.max().toInt()} · ${scores.size} ${plural(scores.size, "trial", "trials")} · scaled to bodyweight",
                )
            } else if (scores.size == 1) {
                ChartCaption("Best ${scores.first().toInt()} · one more scored trial draws the line.")
            } else if (ui.sessions.isEmpty()) {
                ChartCaption("Seal a trial to draw this line.")
            } else {
                ChartCaption("Log bodyweight to score these trials.")
            }
        }

        // The owner's instinct is that his workout history lives under the
        // Ledger. It lives under Train, so put the door here too.
        InkPanel(Modifier.fillMaxWidth()) {
            InkListRow(
                label = "Full chronicle",
                value = null,
                supporting = "every sealed trial",
                onClick = onOpenLog,
            )
        }
        Spacer(Modifier.height(LedgerSpace.Section))
    }
}

@Composable
private fun StatDrillSheet(
    metric: String,
    table: BandTable,
    series: List<Double>,
    seriesDates: List<String>,
    current: Double?,
    asOf: String?,
    onDismiss: () -> Unit,
) {
    val bands = table.bands
    val category = current?.let { Bands.categoryOf(table, it) }

    InfoSheet(
        title = metric,
        subtitle = "The Ledger rates your frame",
        onDismiss = onDismiss,
        chips = listOfNotNull(category?.let { InfoChip(it, IronvellumColors.SystemGreen) }),
        summary = current?.let { value ->
            {
                InfoFigures(listOf(InfoFigure("YOUR READING", formatBodyValue(value))))
                Spacer(Modifier.height(10.dp))
                BandBar(
                    value = value,
                    bands = bands,
                    scaleMax = table.scaleMax,
                    description = "$metric ${formatBodyValue(value)}, $category, on a scale of " +
                        bands.indices.joinToString("; ") { "${bands[it].label} ${Bands.rangeText(table, it)}" },
                )
            }
        },
        actions = listOf(InfoAction("Close", onDismiss, quiet = true)),
    ) {
        if (current == null) {
            text(null, "This page is still blank. Log readings to fill it.", IronvellumColors.InkMuted)
            return@InfoSheet
        }
        section("TREND") {
            if (series.size >= 2) {
                TrendChart(
                    series,
                    IronvellumColors.Emerald,
                    fromZero = false,
                    recordMarker = false,
                    valueText = { "$metric ${formatBodyValue(it)}" },
                    dateText = { seriesDates[it] },
                )
                ChartCaption("${series.size} readings in the Ledger")
            } else {
                ChartCaption("Two readings draw the line.")
            }
        }
        rows("BANDS", bands.indices.map { i -> bands[i].label to Bands.rangeText(table, i) }, collapseAfter = 6)
        val about = listOfNotNull(
            asOf?.let { "From your reading of $it, the newest with body fat." },
            table.note,
        ).joinToString(" ")
        if (about.isNotEmpty()) text("ABOUT THIS RATING", about, IronvellumColors.InkMuted)
    }
}

@Composable
private fun BandBar(value: Double, bands: List<Band>, scaleMax: Double, description: String) {
    Canvas(Modifier.fillMaxWidth().height(18.dp).semantics { contentDescription = description }) {
        var low = 0.0
        bands.forEach { band ->
            val start = (low / scaleMax * size.width).toFloat()
            val end = (band.upTo.coerceAtMost(scaleMax) / scaleMax * size.width).toFloat()
            drawRect(color = toneColor(band.tone), topLeft = Offset(start, 0f), size = androidx.compose.ui.geometry.Size(end - start, size.height))
            low = band.upTo.coerceAtMost(scaleMax)
        }
        val markerX = (value / scaleMax * size.width).toFloat().coerceIn(0f, size.width)
        // Where the reading falls on the scale: struck by hand, not ruled.
        inkStroke(
            from = Offset(markerX, -6f),
            to = Offset(markerX, size.height + 6f),
            color = IronvellumColors.Ink,
            widthPx = 3.dp.toPx(),
            seed = markerX.toInt(),
            taperEnds = false,
        )
    }
}

@Composable
private fun ChartCaption(text: String) {
    Spacer(Modifier.height(4.dp))
    Text(text, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
}


/** Keep digits and one separator; a comma is accepted and read as a point. */
private fun decimalInput(raw: String): String = DecimalInput.sanitize(raw, maxDecimals = 1, maxLength = 6)

@Composable
private fun AddStatDialog(
    initialWeight: String,
    heightCm: Double?,
    sex: Sex,
    measurements: Map<MeasurementSite, Double>,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onConfirm: (Double, Double?) -> Unit,
) {
    // Saveable so a half-entered weigh-in survives rotation/process death; the
    // prefills are initial values only — restored user input wins over them.
    val weight = rememberSaveable { mutableStateOf(initialWeight) }
    val bodyFat = rememberSaveable { mutableStateOf("") }
    val bfValue = Ledger.parseDecimal(bodyFat.value)
    // Bounded, not merely positive: a typo'd body fat of 500 used to reach
    // Katch-McArdle and show a negative resting burn as fact.
    val validWeight = BodyLimits.validWeight(Ledger.parseDecimal(weight.value))
    // Typed text that does not read as a number is invalid, never "no body fat".
    val validBodyFat = Ledger.bodyFatTextValid(bodyFat.value)

    // Estimator state: prefill the tapes from the lifter's latest measurements.
    var showEstimator by rememberSaveable { mutableStateOf(false) }
    val neck = rememberSaveable { mutableStateOf(measurements[MeasurementSite.NECK]?.let { formatBodyValue(it) } ?: "") }
    val waist = rememberSaveable { mutableStateOf(measurements[MeasurementSite.WAIST]?.let { formatBodyValue(it) } ?: "") }
    val hips = rememberSaveable { mutableStateOf(measurements[MeasurementSite.HIPS]?.let { formatBodyValue(it) } ?: "") }
    val estimate = if (showEstimator) BodyStats.estimateBodyFatNavy(
        sex, heightCm ?: 0.0,
        Ledger.parseDecimal(neck.value) ?: 0.0,
        Ledger.parseDecimal(waist.value) ?: 0.0,
        Ledger.parseDecimal(hips.value),
    ) else null

    Dialog(onDismissRequest = onDismiss) {
        InkPanel(Modifier.fillMaxWidth(), accent = IronvellumColors.Emerald) {
            // Scrollable: with the estimator open and the keyboard up the
            // buttons used to sit below the fold.
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "LOG A READING",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = ChakraPetch,
                    letterSpacing = IronvellumTracking.InlineLabel,
                    color = IronvellumColors.Emerald,
                )
                // A rejected figure used to do nothing at all: LOG IT greyed
                // out with no reason given, so a mistyped weight read as a
                // broken button. Say which bound was missed, and only once
                // something has actually been typed.
                OutlinedTextField(
                    shape = MaterialTheme.shapes.small,
                    value = weight.value,
                    onValueChange = { weight.value = decimalInput(it) },
                    label = { Text("Weight (kg)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    isError = weight.value.isNotBlank() && !validWeight,
                    supportingText = if (weight.value.isNotBlank() && !validWeight) {
                        { Text("Enter a weight between ${BodyLimits.WEIGHT_KG.start.toInt()} and ${BodyLimits.WEIGHT_KG.endInclusive.toInt()} kg.") }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    shape = MaterialTheme.shapes.small,
                    value = bodyFat.value,
                    onValueChange = { bodyFat.value = decimalInput(it) },
                    label = { Text("Body fat % — optional") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    // The buttons can sit under the keyboard: its own Done key logs the reading.
                    keyboardActions = KeyboardActions(
                        onDone = { if (validWeight && validBodyFat) onConfirm(Ledger.parseDecimal(weight.value) ?: 0.0, bfValue) },
                    ),
                    isError = bodyFat.value.isNotBlank() && !validBodyFat,
                    supportingText = if (bodyFat.value.isNotBlank() && !validBodyFat) {
                        { Text("Enter a body fat between ${BodyLimits.BODY_FAT_PCT.start.toInt()} and ${BodyLimits.BODY_FAT_PCT.endInclusive.toInt()}%, or leave it blank.") }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (heightCm == null || heightCm <= 0.0) {
                    // BMI/FFMI need it, but logging must not demand it every
                    // time. The hint is the button: SETTINGS was otherwise two
                    // tabs and a gear away from this dialog.
                    Text(
                        "Set your height once in SETTINGS to unlock BMI and FFMI.",
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.SystemGreen,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.extraSmall)
                            .clickable(onClick = onOpenSettings)
                            .heightIn(min = LedgerSpace.Target)
                            .wrapContentHeight(),
                    )
                }
                IronvellumButton(
                    label = if (showEstimator) "Hide estimator" else "Estimate for me",
                    onClick = { showEstimator = !showEstimator },
                    quiet = true,
                )
                if (showEstimator) {
                    // US Navy circumference method, pre-filled from the latest
                    // measurements the lifter already logged.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Navy tape method from your neck, waist" +
                                if (sex == Sex.FEMALE) ", hips and height." else " and height.",
                            style = MaterialTheme.typography.bodySmall,
                            color = IronvellumColors.InkMuted,
                            modifier = Modifier.weight(1f),
                        )
                        TermInfo(Term.NAVY_TAPE)
                    }
                    listOf("NECK (cm)" to neck, "WAIST (cm)" to waist).forEach { (label, field) ->
                        OutlinedTextField(
                            shape = MaterialTheme.shapes.small,
                            value = field.value,
                            onValueChange = { field.value = decimalInput(it) },
                            label = { Text(label) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (sex == Sex.FEMALE) {
                        OutlinedTextField(
                            shape = MaterialTheme.shapes.small,
                            value = hips.value,
                            onValueChange = { hips.value = decimalInput(it) },
                            label = { Text("HIPS (cm)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            estimate?.let {
                                if (Ledger.bodyFatTextValid(it.toInt().toString())) {
                                    "~${it.toInt()}% BODY FAT (estimate)"
                                } else {
                                    "Estimate out of range, check the tapes"
                                }
                            } ?: "Tapes not complete",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = ChakraPetch,
                            color = if (estimate != null) IronvellumColors.EmeraldBright else IronvellumColors.InkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IronvellumButton(
                            label = "Use",
                            // An estimate outside the allowed range (Navy can go negative) cannot be used.
                            enabled = estimate != null && Ledger.bodyFatTextValid(estimate.toInt().toString()),
                            onClick = {
                                estimate?.let {
                                    bodyFat.value = it.toInt().toString()
                                    showEstimator = false
                                }
                            },
                            quiet = true,
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    IronvellumButton(label = "Cancel", onClick = onDismiss, modifier = Modifier.weight(1f), quiet = true)
                    IronvellumButton(
                        label = "LOG IT",
                        onClick = { onConfirm(Ledger.parseDecimal(weight.value) ?: 0.0, bfValue) },
                        enabled = validWeight && validBodyFat,
                        gold = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}


/** Today's date, refreshed on resume and at midnight so the windows below never go stale. */
@Composable
private fun rememberToday(zone: ZoneId): LocalDate {
    var today by remember { mutableStateOf(LocalDate.now(zone)) }
    androidx.compose.runtime.LaunchedEffect(today, zone) {
        today = LocalDate.now(zone)
        val untilMidnight = java.time.Duration.between(
            java.time.ZonedDateTime.now(zone),
            today.plusDays(1).atStartOfDay(zone),
        ).toMillis()
        kotlinx.coroutines.delay(untilMidnight.coerceAtLeast(1_000L) + 1_000L)
        today = LocalDate.now(zone)
    }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner, zone) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) today = LocalDate.now(zone)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return today
}
