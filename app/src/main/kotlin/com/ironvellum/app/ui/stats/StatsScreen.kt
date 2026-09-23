package com.ironvellum.app.ui.stats

import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import com.ironvellum.app.ui.theme.InkCircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import com.ironvellum.app.ui.components.IronvellumButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewModelScope
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.components.NavChip
import androidx.compose.material.icons.outlined.History
import com.ironvellum.app.ui.components.InkSegmented
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.TrendChart
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
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import com.ironvellum.app.domain.Sex
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import com.ironvellum.app.R
import java.util.Locale

// Genuinely unique chart/band colours that have no IronvellumColors token — kept in one
// place so they aren't scattered; everything else must reference IronvellumColors.
private val CalendarConquered = Color(0xFF10B981)
private val OnEmeraldInk = Color(0xFF06251B)
private val BandNeutral = Color(0xFF5C6B63)
private val BandObese = Color(0xFFEF5350)
private val BandCeiling = Color(0xFFF59E0B)

data class StatsUi(
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
)

class StatsViewModel(private val repo: Repository) : ViewModel() {
    // Five flows exceed combine's arity-4 convenience overload, so the history
    // group is combined first and joined with the exercise catalogue after.
    val log: StateFlow<Triple<List<StatEntry>, List<Pair<WorkoutSession, List<SessionSet>>>, List<HealthDay>>> =
        combine(
            repo.observeStats(),
            repo.observeHistory(),
            repo.observeHealthDays(),
        ) { stats, history, healthDays ->
            Triple(stats, history, healthDays)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Triple(emptyList(), emptyList(), emptyList()))
    val ui: StateFlow<StatsUi> = combine(
        log,
        repo.observePresets(),
        repo.observeExercises(),
        repo.observeBodyProfile(),
    ) { (stats, history, healthDays), presets, exercises, bodyProfile ->
        StatsUi(
            stats = stats,
            completedDates = history.map {
                Instant.ofEpochMilli(it.first.completedAtMs ?: it.first.startedAtMs)
                    .atZone(ZoneId.systemDefault()).toLocalDate()
            }.toSet(),
            scheduledDays = presets.mapNotNull { it.scheduledDay }.toSet(),
            sessions = history.map { it.first }.sortedBy { it.startedAtMs },
            healthDays = healthDays,
            exercises = exercises.associateBy { it.id },
            sessionSets = history.associate { it.first.id to it.second },
            profileHeight = bodyProfile.first,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUi())

    fun deleteStat(id: Long) {
        viewModelScope.launch { repo.deleteStat(id) }
    }

    fun addStat(weightKg: Double, bodyFatPct: Double?) {
        viewModelScope.launchGuarded("log reading") { repo.addStat(weightKg, bodyFatPct) }
    }

    /** Profile sex; feeds the body-fat estimator's formula choice. */
    val sex: StateFlow<Sex> = repo.observeBodyProfile()
        .map { it.second }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Sex.MALE)

    /** Latest measured circumference per site, for the estimator prefill. */
    val latestMeasurements: StateFlow<Map<MeasurementSite, Double>> = repo.observeMeasurements()
        .map { entries ->
            entries.sortedBy { it.takenAtMs }.associate { it.site to it.valueCm }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun syncHealthHistory(days: Int = 90) {
        viewModelScope.launch { repo.syncHealthHistory(days) }
    }
}

/**
 * DAILY, not ACTIVITY: the Train screen's workout history was called the
 * "Activity Log", so one word named both a step count and a training record
 * and the owner kept opening this tab looking for his workouts.
 */
private enum class StatsTab(val label: String) { BODY("BODY"), TRAINING("TRAINING"), DAILY("DAILY") }

@Composable
fun StatsScreen(
    // No default: a defaulted no-op lets a forgotten nav wiring compile clean,
    // which is how five screens ended up unreachable earlier.
    onOpenMeasurement: (MeasurementSite) -> Unit,
    onOpenLog: () -> Unit,
    viewModel: StatsViewModel =
        viewModel(factory = viewModelFactory { initializer { StatsViewModel(ironvellumRepository()) } }),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    // Saveable: a rotation mid-add used to slam the dialog shut and drop the weigh-in.
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var drill by remember { mutableStateOf<String?>(null) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var tab by remember { mutableStateOf(StatsTab.BODY) }
    val latest = ui.stats.firstOrNull()

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(20.dp))
        Text(
            "STATUS WINDOW",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            letterSpacing = IronvellumTracking.ScreenTitle,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(12.dp))

        // Segmented control
        InkSegmented(
            options = StatsTab.entries.map { it to it.label },
            selected = tab,
            onPick = { tab = it },
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        if (tab == StatsTab.BODY) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InkPanel(
                        Modifier.weight(1f),
                        onClick = if (latest == null) null else ({ drill = "BMI" }),
                    ) {
                        MetricLabel("BMI")
                        val bmi = latest?.let { BodyStats.bmi(it.weightKg, it.heightCm) }
                        MetricValue(
                            bmi?.toString() ?: "—",
                            bmi?.let { BodyStats.bmiCategory(it) }
                                ?: "set height in Settings",
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "DETAIL",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = ChakraPetch,
                                color = IronvellumColors.SystemGreen,
                                letterSpacing = IronvellumTracking.InlineLabel,
                            )
                            Text("\u203A", color = IronvellumColors.SystemGreen)
                        }
                    }
                    InkPanel(
                        Modifier.weight(1f),
                        onClick = if (latest == null) null else ({ drill = "FFMI" }),
                    ) {
                        MetricLabel("FFMI")
                        val ffmi = latest?.let { s -> s.bodyFatPct?.let { BodyStats.ffmi(s.weightKg, s.heightCm, it) } }
                        MetricValue(
                            ffmi?.toString() ?: "—",
                            // ffmi is also null when height is unset — a lifter
                            // who already logs body fat must not be told to log it.
                            ffmi?.let { BodyStats.ffmiCategory(it) }
                                ?: if ((latest?.heightCm ?: 0.0) <= 0.0) "Set your height once in SETTINGS" else "log body fat %",
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "DETAIL",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = ChakraPetch,
                                color = IronvellumColors.SystemGreen,
                                letterSpacing = IronvellumTracking.InlineLabel,
                            )
                            Text("\u203A", color = IronvellumColors.SystemGreen)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                InkPanel(Modifier.fillMaxWidth()) {
                    MetricLabel("WEIGHT")
                    // Derived once per data change rather than on every
                    // recomposition. The saving is algorithmic, not measured: on a
                    // software-rendered emulator identical scroll sweeps vary by
                    // ±11 points of janky frames, which is too noisy to attribute
                    // anything to. A real-device measurement is still owed.
                    val weights = remember(ui.stats) {
                        ui.stats.sortedBy { it.takenAtMs }.map { it.weightKg }
                    }
                    MetricValueBig(latest?.weightKg?.let { formatBodyValue(it) } ?: "—", "kg")
                    if (weights.size >= 2) {
                        Spacer(Modifier.height(8.dp))
                        TrendChart(weights, IronvellumColors.SovereignGold, fromZero = false)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            ChartCaption("min ${formatBodyValue(weights.min())} kg")
                            ChartCaption("max ${formatBodyValue(weights.max())} kg")
                        }
                    } else {
                        ChartCaption("Two readings unlock the trend line.")
                    }
                }
                Spacer(Modifier.height(10.dp))
                InkPanel(Modifier.fillMaxWidth()) {
                    MetricLabel("BMI HISTORY")
                    val bmis = remember(ui.stats) {
                        ui.stats.sortedBy { it.takenAtMs }
                            .mapNotNull { BodyStats.bmi(it.weightKg, it.heightCm) }
                    }
                    // Was: a second "readings unlock the trend" hint below —
                    // WEIGHT card above already says it; the big "—" value from
                    // MetricValueBig stands in until a trend exists.
                    if (bmis.size >= 2) {
                        TrendChart(bmis, IronvellumColors.SystemGreen, fromZero = false)
                        ChartCaption("Latest ${bmis.last()} — ${BodyStats.bmiCategory(bmis.last())}")
                    } else {
                        // Muted dash placeholder, same treatment as the empty
                        // measurement tiles.
                        Text(
                            "—",
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = ChakraPetch,
                            color = IronvellumColors.InkMuted,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                IronvellumButton(
                    "Log weight / body fat",
                    onClick = { showAdd = true },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                // Circumferences sit beside weight/BMI/FFMI: same body tab,
                // same never-leaves-the-device rule.
                MeasurementsPanel(onOpenSite = onOpenMeasurement)
                SectionHeader("Readings")
                if (ui.stats.isEmpty()) {
                    // Empty-state art drawn for this screen and never wired in.
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.art_empty_stats),
                            contentDescription = null,
                            // Explicit size for the same reason as the rest-day
                            // art, and 213dp against a 71-unit viewport matches
                            // its ~9dp rendered stroke exactly.
                            modifier = Modifier
                                .size(213.dp)
                                .alpha(0.6f),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "No readings yet. The Ledger knows nothing of your vessel.",
                            style = MaterialTheme.typography.bodySmall,
                            color = IronvellumColors.InkMuted,
                        )
                    }
                }
                // Latest readings only: this section sits in a plain scrolling
                // Column, so every row listed is composed whether it is on
                // screen or not, and someone who weighs in daily reaches
                // thousands. The charts above already carry the whole history,
                // and the metric detail screens carry it per metric.
                ui.stats.take(READING_ROWS).forEach { stat ->
                    InkPanel(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    formatDate(stat.takenAtMs, "MMM d, yyyy · HH:mm"),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                val bmi = BodyStats.bmi(stat.weightKg, stat.heightCm)
                                Text(
                                    buildString {
                                        append("${formatBodyValue(stat.weightKg)} kg")
                                        // 0.0 is the heightless sentinel, never a real height.
                                        if (stat.heightCm > 0.0) append(" · ${formatBodyValue(stat.heightCm)} cm")
                                        stat.bodyFatPct?.let { append(" · ${formatBodyValue(it)}% bf") }
                                        bmi?.let { append(" · BMI $it") }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = IronvellumColors.SystemGreen,
                                )
                            }
                        // One tap on the trash icon used to erase the weigh-in
                        // outright; it feeds the strength score's weight
                        // interpolation. Arm first, match the WorkoutLog row.
                        var armed by remember { mutableStateOf(false) }
                        if (armed) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "KEEP",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = ChakraPetch,
                                    color = IronvellumColors.InkMuted,
                                    letterSpacing = IronvellumTracking.InlineLabel,
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.extraSmall)
                                        .clickable { armed = false }
                                        .heightIn(min = 24.dp)
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                        .wrapContentHeight(),
                                )
                                Text(
                                    "DELETE",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = ChakraPetch,
                                    color = IronvellumColors.DangerRed,
                                    letterSpacing = IronvellumTracking.InlineLabel,
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.extraSmall)
                                        .clickable {
                                            armed = false
                                            viewModel.deleteStat(stat.id)
                                        }
                                        .heightIn(min = 24.dp)
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                        .wrapContentHeight(),
                                )
                            }
                        } else {
                            IconButton(onClick = { armed = true }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete reading", tint = IronvellumColors.InkMuted)
                            }
                        }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        } else if (tab == StatsTab.TRAINING) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.height(16.dp))
                // The owner's instinct is that his workout history lives under
                // Stats. It lives under Train, so put the door here too rather
                // than expect him to re-learn the map.
                NavChip(
                    label = "FULL WORKOUT LOG",
                    icon = Icons.Outlined.History,
                    onClick = onOpenLog,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                // Nothing logged yet: one line, not three cards each explaining
                // in its own words that it has no data. The third card here was
                // an UNLABELLED chart of per-campaign XP - the same numbers the
                // cumulative line already draws - captioned "0 campaigns".
                if (ui.sessions.isEmpty()) {
                    InkPanel(Modifier.fillMaxWidth()) {
                        MetricLabel("TRAINING")
                        ChartCaption("Conquer a campaign to draw these lines.")
                    }
                } else {
                    InkPanel(Modifier.fillMaxWidth()) {
                        MetricLabel("CUMULATIVE XP")
                        val cumulative = remember(ui.sessions) { runningXp(ui.sessions) }
                        val totalXp = remember(ui.sessions) { ui.sessions.sumOf { it.xpAwarded } }
                        if (cumulative.size >= 2) {
                            TrendChart(cumulative, IronvellumColors.SystemGreen)
                            ChartCaption("$totalXp XP across ${plural(ui.sessions.size, "campaign", "campaigns")}")
                        } else {
                            ChartCaption("One more campaign draws the line.")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    InkPanel(Modifier.fillMaxWidth()) {
                        MetricLabel("STRENGTH PER CAMPAIGN")
                        // 0 means "not scored" (no bodyweight existed yet), not a
                        // collapse in strength — plotting it dropped the line to
                        // the floor. And TrendChart draws no line below 2 points.
                        val scores = ui.sessions.map { it.strengthScore.toDouble() }.filter { it > 0.0 }
                        if (scores.size >= 2) {
                            TrendChart(scores)
                            ChartCaption(
                                "Best ${scores.max().toInt()} · ${plural(scores.size, "campaign", "campaigns")} · body-scaled (heavier lifters must move more)",
                            )
                        } else if (scores.size == 1) {
                            // Scored, just not plottable yet. The old copy said
                            // "log bodyweight" at a lifter who plainly had.
                            ChartCaption(
                                "Best ${scores.first().toInt()} · one more scored campaign draws the line.",
                            )
                        } else {
                            ChartCaption("Log bodyweight to score these campaigns.")
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "TRAINING CALENDAR",
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = ChakraPetch,
                        color = IronvellumColors.InkMuted,
                        letterSpacing = IronvellumTracking.SectionHeader,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "←",
                            style = MaterialTheme.typography.titleMedium,
                            color = IronvellumColors.SystemGreen,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.extraSmall)
                                .clickable { month = month.minusMonths(1) }
                                // "←" is announced as a character, which tells a
                                // screen-reader user nothing about what it does.
                                .semantics { contentDescription = "Previous month" }
                                .padding(horizontal = 10.dp, vertical = 2.dp),
                        )
                        Text(
                            "${month.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${month.year}",
                            style = MaterialTheme.typography.titleSmall,
                            color = IronvellumColors.Ink,
                        )
                        // Unbounded, the arrow paged into empty future months
                        // forever; the calendar stops at the current month.
                        val canAdvance = month < YearMonth.now()
                        Text(
                            "→",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (canAdvance) IronvellumColors.SystemGreen else IronvellumColors.InkMuted,
                            modifier = (if (canAdvance) {
                                Modifier
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .clickable { month = month.plusMonths(1) }
                                    .semantics { contentDescription = "Next month" }
                            } else {
                                Modifier.semantics { contentDescription = "Next month — already at the current month" }
                            }).padding(horizontal = 10.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                CalendarGrid(month, ui.completedDates, ui.scheduledDays)
                Spacer(Modifier.height(24.dp))
            }
        } else {
            ActivityTab(
                days = ui.healthDays,
                latest = ui.stats.firstOrNull(),
                sessions = ui.sessions,
                sessionSets = ui.sessionSets,
                exercises = ui.exercises,
            )
        }
    }

    if (showAdd) {
        AddStatDialog(
            initialWeight = latest?.weightKg?.let { formatBodyValue(it) } ?: "",
            heightCm = ui.profileHeight,
            sex = viewModel.sex.collectAsStateWithLifecycle().value,
            measurements = viewModel.latestMeasurements.collectAsStateWithLifecycle().value,
            onDismiss = { showAdd = false },
            onConfirm = { weight, bf ->
                viewModel.addStat(weight, bf)
                showAdd = false
            },
        )
    }

    drill?.let { metric ->
        val series = ui.stats.sortedBy { it.takenAtMs }.mapNotNull {
            when (metric) {
                "BMI" -> BodyStats.bmi(it.weightKg, it.heightCm)
                else -> it.bodyFatPct?.let { bf -> BodyStats.ffmi(it.weightKg, it.heightCm, bf) }
            }
        }
        val current = series.lastOrNull()
        StatDrillDialog(
            metric = metric,
            series = series,
            current = current,
            onDismiss = { drill = null },
        )
    }
}

private fun runningXp(sessions: List<WorkoutSession>): List<Double> {
    var total = 0
    return sessions.map { total += it.xpAwarded; total.toDouble() }
}

private data class Band(val upTo: Double, val label: String, val color: Color)

@Composable
private fun StatDrillDialog(
    metric: String,
    series: List<Double>,
    current: Double?,
    onDismiss: () -> Unit,
) {
    val isBmi = metric == "BMI"
    val bands: List<Band> = if (isBmi) {
        listOf(
            Band(18.5, "Underweight", BandNeutral),
            Band(25.0, "Healthy", IronvellumColors.Emerald),
            Band(30.0, "Overweight", IronvellumColors.SovereignGold),
            Band(40.0, "Obese", BandObese),
        )
    } else {
        listOf(
            Band(18.0, "Below average", BandNeutral),
            Band(20.0, "Average (active male)", IronvellumColors.SystemGreen),
            Band(22.0, "Above average (1-3 yrs)", IronvellumColors.Emerald),
            Band(24.0, "Excellent (3-5 yrs)", IronvellumColors.SovereignGold),
            Band(26.0, "Natural ceiling (~25)", BandCeiling),
        )
    }
    val scaleMax = bands.last().upTo


    val category = current?.let { v ->
        bands.firstOrNull { v < it.upTo }?.label ?: bands.last().label
    }

    AlertDialog(
        // Material's dialog container is a 28dp rounded rect - the most
        // obviously stock surface in the app. Give it the ink shape.
        shape = MaterialTheme.shapes.medium,
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    "$metric — vessel rating",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.SovereignGold,
                )
                Text(
                    "The Ledger rates your vessel",
                    style = MaterialTheme.typography.labelSmall,
                    color = IronvellumColors.InkMuted,
                )
            }
        },
        text = {
            Column {
                if (current == null) {
                    Text(
                        "Not enough data. Log readings to open this window.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        current.toString(),
                        style = MaterialTheme.typography.displaySmall,
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        color = IronvellumColors.SovereignGold,
                    )
                    Text(
                        category ?: "",
                        style = MaterialTheme.typography.labelLarge,
                        color = IronvellumColors.SystemGreen,
                    )
                    Spacer(Modifier.height(10.dp))
                    BandBar(value = current, bands = bands, scaleMax = scaleMax)
                    Spacer(Modifier.height(12.dp))
                    if (series.size >= 2) {
                        TrendChart(series, IronvellumColors.SystemGreen, fromZero = false)
                        ChartCaption("${series.size} readings on record")
                    } else {
                        ChartCaption("Two readings unlock the trend line.")
                    }
                    Spacer(Modifier.height(8.dp))
                    bands.forEachIndexed { i, band ->
                        val lower = if (i == 0) 0.0 else bands[i - 1].upTo
                        Text(
                            "${band.label}: ${if (i == 0) "below" else "$lower –"}${band.upTo}",
                            style = MaterialTheme.typography.labelSmall,
                            color = IronvellumColors.InkMuted,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun BandBar(value: Double, bands: List<Band>, scaleMax: Double) {
    Canvas(Modifier.fillMaxWidth().height(18.dp)) {
        var low = 0.0
        bands.forEach { band ->
            val start = (low / scaleMax * size.width).toFloat()
            val end = (band.upTo / scaleMax * size.width).toFloat()
            drawRect(color = band.color, topLeft = Offset(start, 0f), size = androidx.compose.ui.geometry.Size(end - start, size.height))
            low = band.upTo
        }
        val markerX = (value / scaleMax * size.width).toFloat().coerceIn(0f, size.width)
        // Where the reading falls on the scale: struck by hand, not ruled.
        inkStroke(
            from = Offset(markerX, -6f),
            to = Offset(markerX, size.height + 6f),
            color = Color.White,
            widthPx = 3.dp.toPx(),
            seed = markerX.toInt(),
            taperEnds = false,
        )
    }
}

@Composable
private fun MetricLabel(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        fontFamily = ChakraPetch,
        color = IronvellumColors.SystemGreen,
        letterSpacing = IronvellumTracking.InlineLabel,
    )
}

@Composable
private fun MetricValue(value: String, hint: String) {
    Text(
        value,
        style = MaterialTheme.typography.headlineMedium,
        fontFamily = ChakraPetch,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        color = IronvellumColors.SovereignGold,
    )
    Text(hint, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
}

@Composable
private fun MetricValueBig(value: String, unit: String) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            value,
            style = MaterialTheme.typography.displaySmall,
            fontFamily = ChakraPetch,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = IronvellumColors.SovereignGold,
        )
        Text(unit, style = MaterialTheme.typography.titleMedium, color = IronvellumColors.InkMuted)
    }
}

@Composable
private fun ChartCaption(text: String) {
    Spacer(Modifier.height(4.dp))
    Text(text, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
}

private val WEEKDAYS = listOf("M", "T", "W", "T", "F", "S", "S")

@Composable
private fun CalendarGrid(
    month: YearMonth,
    completedDates: Set<LocalDate>,
    scheduledDays: Set<Int>,
) {
    val today = LocalDate.now()
    val firstDay = month.atDay(1)
    val leadingBlanks = firstDay.dayOfWeek.value - 1
    val cells: List<LocalDate?> = List(leadingBlanks) { null } +
        (1..month.lengthOfMonth()).map { month.atDay(it) }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            WEEKDAYS.forEach { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        val paddedCells = cells + List((7 - cells.size % 7) % 7) { null }
        paddedCells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                week.forEach { date ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (date == null) {
                            Spacer(Modifier.height(36.dp))
                        } else {
                            val completed = date in completedDates
                            val scheduled = date.dayOfWeek.value in scheduledDays
                            val isToday = date == today
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier
                                        .size(32.dp)
                                        .clip(InkCircleShape(7))
                                        .background(
                                            if (completed) {
                                                Brush.verticalGradient(listOf(IronvellumColors.EmeraldBright, CalendarConquered))
                                            } else {
                                                Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                                            },
                                        )
                                        .inkBorder(
                                            IronvellumColors.SovereignGold,
                                            InkCircleShape(7),
                                            if (isToday) 1.5.dp else 0.dp,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        date.dayOfMonth.toString(),
                                        color = when {
                                            completed -> OnEmeraldInk
                                            isToday -> IronvellumColors.SovereignGold
                                            else -> IronvellumColors.Ink
                                        },
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Box(
                                    Modifier
                                        .size(4.dp)
                                        .clip(InkCircleShape(7))
                                        .background(
                                            when {
                                                completed -> CalendarConquered
                                                scheduled -> IronvellumColors.SystemGreen
                                                else -> Color.Transparent
                                            },
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CalendarLegend(CalendarConquered, "conquered")
            CalendarLegend(IronvellumColors.SovereignGold, "today")
            CalendarLegend(IronvellumColors.SystemGreen, "scheduled")
        }
    }
}

@Composable
private fun CalendarLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).clip(InkCircleShape(7)).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
    }
}

@Composable
private fun AddStatDialog(
    initialWeight: String,
    heightCm: Double?,
    sex: Sex,
    measurements: Map<MeasurementSite, Double>,
    onDismiss: () -> Unit,
    onConfirm: (Double, Double?) -> Unit,
) {
    // Saveable so a half-entered weigh-in survives rotation/process death; the
    // prefills are initial values only — restored user input wins over them.
    val weight = rememberSaveable { mutableStateOf(initialWeight) }
    val bodyFat = rememberSaveable { mutableStateOf("") }
    val bfValue = bodyFat.value.toDoubleOrNull()
    // Bounded, not merely positive: a typo'd body fat of 500 used to reach
    // Katch-McArdle and show a negative resting burn as fact.
    val validWeight = BodyLimits.validWeight(weight.value.toDoubleOrNull())
    val validBodyFat = bodyFat.value.isBlank() && bfValue == null || BodyLimits.validBodyFat(bfValue)

    // Estimator state: prefill the tapes from the lifter's latest measurements.
    var showEstimator by rememberSaveable { mutableStateOf(false) }
    val neck = rememberSaveable { mutableStateOf(measurements[MeasurementSite.NECK]?.let { formatBodyValue(it) } ?: "") }
    val waist = rememberSaveable { mutableStateOf(measurements[MeasurementSite.WAIST]?.let { formatBodyValue(it) } ?: "") }
    val hips = rememberSaveable { mutableStateOf(measurements[MeasurementSite.HIPS]?.let { formatBodyValue(it) } ?: "") }
    val estimate = if (showEstimator) BodyStats.estimateBodyFatNavy(
        sex, heightCm ?: 0.0,
        neck.value.toDoubleOrNull() ?: 0.0,
        waist.value.toDoubleOrNull() ?: 0.0,
        hips.value.toDoubleOrNull(),
    ) else null

    Dialog(onDismissRequest = onDismiss) {
        InkPanel(Modifier.fillMaxWidth(), accent = IronvellumColors.Emerald) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "LOG BODY READING",
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
                    onValueChange = { weight.value = it },
                    label = { Text("Weight (kg)") },
                    singleLine = true,
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
                    onValueChange = { bodyFat.value = it },
                    label = { Text("Body fat % — optional") },
                    singleLine = true,
                    isError = bodyFat.value.isNotBlank() && !validBodyFat,
                    supportingText = if (bodyFat.value.isNotBlank() && !validBodyFat) {
                        { Text("Enter a body fat between ${BodyLimits.BODY_FAT_PCT.start.toInt()} and ${BodyLimits.BODY_FAT_PCT.endInclusive.toInt()}%, or leave it blank.") }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (heightCm == null || heightCm <= 0.0) {
                    // BMI/FFMI need it, but logging must not demand it every time.
                    Text(
                        "Set your height once in SETTINGS to unlock BMI and FFMI.",
                        style = MaterialTheme.typography.labelSmall,
                        color = IronvellumColors.InkMuted,
                    )
                }
                TextButton(onClick = { showEstimator = !showEstimator }) {
                    Text(
                        if (showEstimator) "HIDE ESTIMATOR" else "ESTIMATE FOR ME",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        letterSpacing = IronvellumTracking.InlineLabel,
                    )
                }
                if (showEstimator) {
                    // US Navy circumference method, pre-filled from the latest
                    // measurements the lifter already logged.
                    Text(
                        "Navy tape method from your neck, waist" +
                            if (sex == Sex.FEMALE) " and hip measurements." else " and measurements.",
                        style = MaterialTheme.typography.labelSmall,
                        color = IronvellumColors.InkMuted,
                    )
                    listOf("NECK (cm)" to neck, "WAIST (cm)" to waist).forEach { (label, field) ->
                        OutlinedTextField(
                            shape = MaterialTheme.shapes.small,
                            value = field.value,
                            onValueChange = { field.value = it },
                            label = { Text(label) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (sex == Sex.FEMALE) {
                        OutlinedTextField(
                            shape = MaterialTheme.shapes.small,
                            value = hips.value,
                            onValueChange = { hips.value = it },
                            label = { Text("HIPS (cm)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            estimate?.let { "~$it% BODY FAT" } ?: "Tapes not complete",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = ChakraPetch,
                            color = if (estimate != null) IronvellumColors.EmeraldBright else IronvellumColors.InkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            enabled = estimate != null,
                            onClick = {
                                estimate?.let {
                                    bodyFat.value = it.toString()
                                    showEstimator = false
                                }
                            },
                        ) { Text("USE") }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    IronvellumButton(
                        label = "LOG IT",
                        onClick = { onConfirm(weight.value.toDoubleOrNull() ?: 0.0, bfValue) },
                        enabled = validWeight && validBodyFat,
                        gold = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}


/** Step aggregates derived once per data change rather than per frame. */
private data class StepsDerived(
    val byDate: Map<java.time.LocalDate, com.ironvellum.app.domain.HealthDay>,
    val sorted: List<com.ironvellum.app.domain.HealthDay>,
    val last7: List<com.ironvellum.app.domain.HealthDay>,
    val avg7: Int,
    val bestDay: com.ironvellum.app.domain.HealthDay,
    val lifetime: Int,
)

@Composable
private fun ActivityTab(
    days: List<HealthDay>,
    latest: StatEntry?,
    sessions: List<WorkoutSession>,
    sessionSets: Map<Long, List<SessionSet>>,
    exercises: Map<Long, Exercise>,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        if (days.isEmpty()) {
            InkPanel(Modifier.fillMaxWidth()) {
                MetricLabel("DAILY")
                Spacer(Modifier.height(6.dp))
                Text(
                    "No activity synced yet — connect Health Connect in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
            }
            Spacer(Modifier.height(24.dp))
            return
        }

        // One derivation per data change. Every line below walks the whole
        // health history, and a scroll recomposes this section continuously.
        val today = LocalDate.now()
        val derived = remember(days) {
            val sorted = days.sortedBy { it.date }
            val last7 = sorted.filter { it.date > today.minusDays(7) }
            StepsDerived(
                byDate = days.associateBy { it.date },
                sorted = sorted,
                last7 = last7,
                // Divided by 7, not last7.size: gap days have no HealthDay row,
                // so averaging over days-with-data inflated the mean every time
                // a sync day was missed — and the tile says "7-DAY AVERAGE".
                avg7 = if (last7.isEmpty()) 0 else last7.sumOf { it.steps } / 7,
                bestDay = days.maxBy { it.steps },
                lifetime = days.sumOf { it.steps },
            )
        }
        val byDate = derived.byDate
        val sorted = derived.sorted
        val last7 = derived.last7
        val avg7 = derived.avg7
        val bestDay = derived.bestDay
        val lifetime = derived.lifetime

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActivityTile("TODAY'S STEPS", fmtInt(byDate[today]?.steps ?: 0), today.toString(), Modifier.weight(1f))
            // The owner must see why the number is low — a missed sync shrinks it.
            ActivityTile(
                "7-DAY AVERAGE",
                fmtInt(avg7),
                if (last7.size >= 7) "steps per day" else "steps per day · ${last7.size} of 7 tracked",
                Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActivityTile("BEST DAY", fmtInt(bestDay.steps), bestDay.date.toString(), Modifier.weight(1f))
            ActivityTile("LIFETIME", fmtInt(lifetime), "${days.size} days tracked", Modifier.weight(1f))
        }

        Spacer(Modifier.height(14.dp))
        InkPanel(Modifier.fillMaxWidth()) {
            MetricLabel("STEPS — LAST 14 DAYS")
            val window = lastDays(sorted, today, 14)
            TrendChart(window.map { it.steps.toDouble() }, goal = STEP_GOAL.toDouble())
            val hits = window.count { it.steps >= STEP_GOAL }
            // window.size is TRACKED days inside the period, not 14 — gap days
            // have no HealthDay row, so the period and the coverage are stated
            // separately ("3 of 14 days tracked · 2 hit the 10,000 goal").
            ChartCaption("${window.size} of 14 days tracked · $hits hit the ${fmtInt(STEP_GOAL)} goal")
        }

        Spacer(Modifier.height(10.dp))
        InkPanel(Modifier.fillMaxWidth()) {
            MetricLabel("DISTANCE (KM) — LAST 30 DAYS")
            val window30 = lastDays(sorted, today, 30)
            val km = window30.map { it.distanceKm }
            if (km.count { it > 0.0 } >= 2) {
                TrendChart(km, IronvellumColors.SystemGreen)
                ChartCaption(
                    "best ${"%.1f".format(km.max())} km · total ${"%.0f".format(km.sum())} km · " +
                        "${window30.size} of 30 days tracked",
                )
            } else {
                ChartCaption("Distance appears once Health Connect reports it.")
            }
        }

        EnergySection(lastDays(sorted, today, 14), latest, sessions, sessionSets, exercises)
        Spacer(Modifier.height(14.dp))
        SectionHeader("Active calories — last 7 days")
        val kcal7 = last7
        if (kcal7.isEmpty()) {
            Text(
                "No active calories recorded yet.",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
        } else {
            kcal7.reversed().forEach { day ->
                val burn = dayBurn(day, sessionsOn(day.date, sessions, sessionSets), exercises, latest)
                val measured = day.activeKcal > 0
                val value = if (measured) "${fmtInt(day.activeKcal)} kcal" else burn?.let { "${fmtInt(it.kcal)} kcal (est.)" } ?: "—"
                MetricRow(
                    day.date.toString() + if (measured) "" else " · estimated",
                    value,
                    measured && day.activeKcal == kcal7.maxOf { it.activeKcal },
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        SectionHeader("Sleep — last 7 nights")
        // Calendar-day bound first: takeLast(7) over days-with-sleep silently
        // stretched "7 nights" across weeks of gaps.
        val sleep7 = sorted.filter { it.date > today.minusDays(7) && it.sleepMinutes > 0 }
        if (sleep7.isEmpty()) {
            Text(
                "No sleep recorded yet.",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
        } else {
            val bestSleep = sleep7.maxOf { it.sleepMinutes }
            sleep7.reversed().forEach { day ->
                MetricRow(day.date.toString(), fmtSleep(day.sleepMinutes), day.sleepMinutes == bestSleep)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Calendar-day window, not entry-count: days with no Health Connect signal have
 * no row at all, so takeLast(N) over the sorted list silently stretched every
 * "last N days" window across weeks of gaps.
 */
private fun lastDays(sorted: List<HealthDay>, today: LocalDate, n: Long): List<HealthDay> =
    sorted.filter { it.date > today.minusDays(n) }

/** Sessions completed on [date], with their logged sets. */
private fun sessionsOn(
    date: LocalDate,
    sessions: List<WorkoutSession>,
    sessionSets: Map<Long, List<SessionSet>>,
): List<Pair<WorkoutSession, List<SessionSet>>> = sessions
    .filter {
        Instant.ofEpochMilli(it.completedAtMs ?: it.startedAtMs)
            .atZone(ZoneId.systemDefault()).toLocalDate() == date
    }
    .map { it to sessionSets[it.id].orEmpty() }

/**
 * One day's burn. Measured Health Connect active calories win outright — the
 * estimate for that day is never added on top of them (rule: never double-count).
 */
private fun dayBurn(
    day: HealthDay,
    sessionsThatDay: List<Pair<WorkoutSession, List<SessionSet>>>,
    exercises: Map<Long, Exercise>,
    latest: StatEntry?,
): EnergyEstimate? {
    val stepsEst = if (day.steps > 0) {
        Energy.stepsKcal(day.steps, day.distanceKm.takeIf { it > 0.0 }, latest?.weightKg, latest?.heightCm)
    } else {
        null
    }
    val sessionEsts = sessionsThatDay.mapNotNull { (session, sets) ->
        val minutes = session.completedAtMs?.let { ((it - session.startedAtMs) / 60_000L).toInt().coerceAtLeast(0) }
        Energy.sessionKcal(sets, exercises, latest?.weightKg, minutes)
    }
    return Energy.dayKcal(day.activeKcal.takeIf { it > 0 }, stepsEst, sessionEsts)
}

@Composable
private fun EnergySection(
    window: List<HealthDay>,
    latest: StatEntry?,
    sessions: List<WorkoutSession>,
    sessionSets: Map<Long, List<SessionSet>>,
    exercises: Map<Long, Exercise>,
) {
    val burns = window.map { dayBurn(it, sessionsOn(it.date, sessions, sessionSets), exercises, latest) }
    val todayBurn = burns.lastOrNull()
    val resting = Energy.restingKcalPerDay(latest?.weightKg, latest?.bodyFatPct)

    Spacer(Modifier.height(14.dp))
    SectionHeader("Energy burn — last 14 days")
    InkPanel(Modifier.fillMaxWidth()) {
        MetricLabel("BURN TODAY")
        if (todayBurn != null) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    fmtInt(todayBurn.kcal),
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.Bold,
                    color = IronvellumColors.SovereignGold,
                )
                Text(
                    "kcal · ${confidenceWord(todayBurn.confidence)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                    letterSpacing = IronvellumTracking.InlineLabel,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        } else {
            MetricValue("—", "no steps or sessions logged today")
        }

        Spacer(Modifier.height(10.dp))
        MetricLabel("DAILY BURN — LAST 14 DAYS")
        // A null estimate is "not estimable" (usually just a missing
        // bodyweight), not 0 kcal — plotting zeros drew confident zero-burn
        // days. TrendChart cannot draw gaps, so only estimable days are
        // plotted and the caption states the real coverage.
        val charted = window.zip(burns).mapNotNull { (day, burn) -> burn?.let { day.date to it.kcal } }
        if (charted.size >= 2) {
            TrendChart(charted.map { it.second.toDouble() })
            Spacer(Modifier.height(6.dp))
            EnergyLegend()
            val measuredCount = window.count { it.activeKcal > 0 }
            val measuredLine =
                if (measuredCount == 0) {
                    "all MET estimates — Health Connect has not reported active calories."
                } else {
                    "$measuredCount of ${window.size} days are Health Connect measurements. " +
                        "Estimates are never added on top of a measured day."
                }
            ChartCaption("${charted.size} of ${window.size} days estimable · $measuredLine")
        } else {
            ChartCaption("Burn is estimable on ${charted.size} of ${window.size} days — log bodyweight or connect Health Connect to estimate more.")
        }

        val missingPrompts = buildList {
            // heightCm is a non-null Double using 0.0 as the "never set"
            // sentinel, so `heightCm == null` was dead code: the prompt stayed
            // hidden for exactly the lifter who needed it.
            if (latest == null) add("Log your bodyweight to estimate activity burn.")
            if (latest == null || latest.heightCm <= 0.0) {
                add("Log your height to estimate steps when distance is missing.")
            }
        }
        missingPrompts.forEach { prompt ->
            Spacer(Modifier.height(6.dp))
            Text(
                prompt,
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
        }

        // Resting rate: Katch-McArdle needs lean mass, so it only exists with body fat.
        Spacer(Modifier.height(12.dp))
        MetricLabel("RESTING BURN (KATCH-MCARDLE)")
        if (resting != null) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    fmtInt(resting.kcal),
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.Bold,
                    color = IronvellumColors.SystemGreen,
                )
                Text(
                    "kcal/day at rest · ${confidenceWord(resting.confidence)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                    letterSpacing = IronvellumTracking.InlineLabel,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            ChartCaption(resting.basis)
        } else {
            Text(
                "Log body fat to estimate resting burn.",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
        }
    }
}

private fun confidenceWord(confidence: EnergyConfidence): String = when (confidence) {
    EnergyConfidence.MEASURED -> "measured"
    EnergyConfidence.ESTIMATED -> "estimated"
    EnergyConfidence.COARSE -> "rough estimate"
}

@Composable
private fun EnergyLegend() {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("\u25CF", color = IronvellumColors.Emerald, style = MaterialTheme.typography.labelSmall)
            Text(
                "measured — Health Connect",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = IronvellumColors.InkMuted,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("\u25CB", color = IronvellumColors.InkMuted, style = MaterialTheme.typography.labelSmall)
            Text(
                "estimated — MET model",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = IronvellumColors.InkMuted,
            )
        }
    }
}


private fun fmtInt(v: Int): String = String.format(Locale.getDefault(), "%,d", v)

private fun fmtSleep(minutes: Int): String = "${minutes / 60}h ${minutes % 60}m"

@Composable
private fun ActivityTile(label: String, value: String, hint: String, modifier: Modifier = Modifier) {
    InkPanel(modifier) {
        MetricLabel(label)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = IronvellumColors.SovereignGold,
        )
        Text(hint, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
    }
}

@Composable
private fun MetricRow(label: String, value: String, best: Boolean) {
    InkPanel(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = ChakraPetch,
                color = IronvellumColors.Ink,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = ChakraPetch,
                color = if (best) IronvellumColors.SovereignGold else IronvellumColors.SystemGreen,
            )
        }
    }
}

/** The charts carry the whole history; this list is the recent detail. */
private const val READING_ROWS = 12
