package com.monarch.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.monarch.app.ui.theme.InkCircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import com.monarch.app.ui.components.MonarchButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
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
import com.monarch.app.ui.components.SectionHeader
import com.monarch.app.ui.components.InkSegmented
import com.monarch.app.ui.components.SystemWindow
import com.monarch.app.ui.components.TrendChart
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.style.TextOverflow
import com.monarch.app.ui.components.formatDate
import com.monarch.app.ui.monarchRepository
import com.monarch.app.data.Repository
import com.monarch.app.domain.BodyStats
import com.monarch.app.domain.StatEntry
import com.monarch.app.domain.WorkoutSession
import com.monarch.app.domain.HealthDay
import com.monarch.app.domain.Exercise
import com.monarch.app.domain.SessionSet
import com.monarch.app.domain.Energy
import com.monarch.app.domain.EnergyConfidence
import com.monarch.app.domain.EnergyEstimate
import com.monarch.app.domain.MeasurementSite
import com.monarch.app.domain.STEP_GOAL
import com.monarch.app.ui.monarchRepository
import com.monarch.app.ui.theme.ChakraPetch
import com.monarch.app.ui.theme.inkStroke
import com.monarch.app.ui.theme.MonarchColors
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import com.monarch.app.ui.theme.MonarchTracking
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import com.monarch.app.domain.Sex
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import com.monarch.app.R
import java.util.Locale

// Genuinely unique chart/band colours that have no MonarchColors token — kept in one
// place so they aren't scattered; everything else must reference MonarchColors.
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
    /** Profile-owned height (Settings); null until the hunter sets it once. */
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
        viewModelScope.launch { repo.addStat(weightKg, bodyFatPct) }
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

private enum class StatsTab(val label: String) { BODY("BODY"), TRAINING("TRAINING"), ACTIVITY("ACTIVITY") }

@Composable
fun StatsScreen(
    // No default: a defaulted no-op lets a forgotten nav wiring compile clean,
    // which is how five screens ended up unreachable earlier.
    onOpenMeasurement: (MeasurementSite) -> Unit,
    viewModel: StatsViewModel =
        viewModel(factory = viewModelFactory { initializer { StatsViewModel(monarchRepository()) } }),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
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
            color = MonarchColors.InkMuted,
            letterSpacing = MonarchTracking.ScreenTitle,
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
                    SystemWindow(
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
                                color = MonarchColors.SystemGreen,
                                letterSpacing = MonarchTracking.InlineLabel,
                            )
                            Text("\u203A", color = MonarchColors.SystemGreen)
                        }
                    }
                    SystemWindow(
                        Modifier.weight(1f),
                        onClick = if (latest == null) null else ({ drill = "FFMI" }),
                    ) {
                        MetricLabel("FFMI")
                        val ffmi = latest?.let { s -> s.bodyFatPct?.let { BodyStats.ffmi(s.weightKg, s.heightCm, it) } }
                        MetricValue(
                            ffmi?.toString() ?: "—",
                            ffmi?.let { BodyStats.ffmiCategory(it) } ?: "log body fat %",
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "DETAIL",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = ChakraPetch,
                                color = MonarchColors.SystemGreen,
                                letterSpacing = MonarchTracking.InlineLabel,
                            )
                            Text("\u203A", color = MonarchColors.SystemGreen)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                SystemWindow(Modifier.fillMaxWidth()) {
                    MetricLabel("WEIGHT")
                    val weights = ui.stats.sortedBy { it.takenAtMs }.map { it.weightKg }
                    MetricValueBig(latest?.weightKg?.toString() ?: "—", "kg")
                    if (weights.size >= 2) {
                        Spacer(Modifier.height(8.dp))
                        TrendChart(weights, MonarchColors.SovereignGold, fromZero = false)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            ChartCaption("min ${weights.min()} kg")
                            ChartCaption("max ${weights.max()} kg")
                        }
                    } else {
                        ChartCaption("Two readings unlock the trend line.")
                    }
                }
                Spacer(Modifier.height(10.dp))
                SystemWindow(Modifier.fillMaxWidth()) {
                    MetricLabel("BMI HISTORY")
                    val bmis = ui.stats.sortedBy { it.takenAtMs }.mapNotNull { BodyStats.bmi(it.weightKg, it.heightCm) }
                    if (bmis.size >= 2) {
                        TrendChart(bmis, MonarchColors.SystemGreen, fromZero = false)
                        ChartCaption("Latest ${bmis.last()} — ${BodyStats.bmiCategory(bmis.last())}")
                    } else {
                        ChartCaption("Two readings unlock the line.")
                    }
                }
                Spacer(Modifier.height(14.dp))
                MonarchButton(
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
                            "No readings yet. The System knows nothing of your vessel.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MonarchColors.InkMuted,
                        )
                    }
                }
                ui.stats.forEach { stat ->
                    SystemWindow(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
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
                                        append("${stat.weightKg} kg")
                                        // 0.0 is the heightless sentinel, never a real height.
                                        if (stat.heightCm > 0.0) append(" · ${stat.heightCm} cm")
                                        stat.bodyFatPct?.let { append(" · $it% bf") }
                                        bmi?.let { append(" · BMI $it") }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MonarchColors.SystemGreen,
                                )
                            }
                            IconButton(onClick = { viewModel.deleteStat(stat.id) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete reading", tint = MonarchColors.InkMuted)
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
                SystemWindow(Modifier.fillMaxWidth()) {
                    MetricLabel("CUMULATIVE XP")
                    val cumulative = runningXp(ui.sessions)
                    if (cumulative.size >= 2) {
                        TrendChart(cumulative, MonarchColors.SystemGreen)
                        ChartCaption(
                            "${ui.sessions.sumOf { it.xpAwarded }} XP across ${ui.sessions.size} campaigns",
                        )
                    } else {
                        ChartCaption("Complete workouts to draw the line.")
                    }
                }
                Spacer(Modifier.height(10.dp))
                SystemWindow(Modifier.fillMaxWidth()) {
                    MetricLabel("STRENGTH PER CAMPAIGN")
                    val scores = ui.sessions.map { it.strengthScore.toDouble() }
                    if (scores.any { it > 0.0 }) {
                        TrendChart(scores)
                        ChartCaption(
                            "Best ${scores.max().toInt()} · ${scores.size} campaigns · body-scaled (heavier hunters must move more)",
                        )
                    } else {
                        ChartCaption("Body-scaled score — log bodyweight, then conquer campaigns.")
                    }
                }
                Spacer(Modifier.height(10.dp))
                SystemWindow(Modifier.fillMaxWidth()) {
                    val xpSeries = ui.sessions.map { it.xpAwarded.toDouble() }
                    TrendChart(xpSeries, MonarchColors.SystemGreen)
                    ChartCaption("${xpSeries.size} campaigns · bar height = XP of that campaign")
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
                        color = MonarchColors.InkMuted,
                        letterSpacing = MonarchTracking.SectionHeader,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "←",
                            style = MaterialTheme.typography.titleMedium,
                            color = MonarchColors.SystemGreen,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.extraSmall)
                                .clickable { month = month.minusMonths(1) }
                                .padding(horizontal = 10.dp, vertical = 2.dp),
                        )
                        Text(
                            "${month.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${month.year}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MonarchColors.Ink,
                        )
                        Text(
                            "→",
                            style = MaterialTheme.typography.titleMedium,
                            color = MonarchColors.SystemGreen,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.extraSmall)
                                .clickable { month = month.plusMonths(1) }
                                .padding(horizontal = 10.dp, vertical = 2.dp),
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
            initialWeight = latest?.weightKg?.toString() ?: "",
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
            Band(25.0, "Healthy", MonarchColors.Emerald),
            Band(30.0, "Overweight", MonarchColors.SovereignGold),
            Band(40.0, "Obese", BandObese),
        )
    } else {
        listOf(
            Band(18.0, "Below average", BandNeutral),
            Band(20.0, "Average (active male)", MonarchColors.SystemGreen),
            Band(22.0, "Above average (1-3 yrs)", MonarchColors.Emerald),
            Band(24.0, "Excellent (3-5 yrs)", MonarchColors.SovereignGold),
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
                    color = MonarchColors.SovereignGold,
                )
                Text(
                    "The System rates your vessel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MonarchColors.InkMuted,
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
                        color = MonarchColors.SovereignGold,
                    )
                    Text(
                        category ?: "",
                        style = MaterialTheme.typography.labelLarge,
                        color = MonarchColors.SystemGreen,
                    )
                    Spacer(Modifier.height(10.dp))
                    BandBar(value = current, bands = bands, scaleMax = scaleMax)
                    Spacer(Modifier.height(12.dp))
                    if (series.size >= 2) {
                        TrendChart(series, MonarchColors.SystemGreen, fromZero = false)
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
                            color = MonarchColors.InkMuted,
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
        color = MonarchColors.SystemGreen,
        letterSpacing = MonarchTracking.InlineLabel,
    )
}

@Composable
private fun MetricValue(value: String, hint: String) {
    Text(
        value,
        style = MaterialTheme.typography.headlineMedium,
        fontFamily = ChakraPetch,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        color = MonarchColors.SovereignGold,
    )
    Text(hint, style = MaterialTheme.typography.labelSmall, color = MonarchColors.InkMuted)
}

@Composable
private fun MetricValueBig(value: String, unit: String) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            value,
            style = MaterialTheme.typography.displaySmall,
            fontFamily = ChakraPetch,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = MonarchColors.SovereignGold,
        )
        Text(unit, style = MaterialTheme.typography.titleMedium, color = MonarchColors.InkMuted)
    }
}

@Composable
private fun ChartCaption(text: String) {
    Spacer(Modifier.height(4.dp))
    Text(text, style = MaterialTheme.typography.labelSmall, color = MonarchColors.InkMuted)
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
                    color = MonarchColors.InkMuted,
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
                                                Brush.verticalGradient(listOf(MonarchColors.EmeraldBright, CalendarConquered))
                                            } else {
                                                Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                                            },
                                        )
                                        .border(
                                            width = if (isToday) 1.5.dp else 0.dp,
                                            color = MonarchColors.SovereignGold,
                                            shape = InkCircleShape(7),
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        date.dayOfMonth.toString(),
                                        color = when {
                                            completed -> OnEmeraldInk
                                            isToday -> MonarchColors.SovereignGold
                                            else -> MonarchColors.Ink
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
                                                scheduled -> MonarchColors.SystemGreen
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
            CalendarLegend(MonarchColors.SovereignGold, "today")
            CalendarLegend(MonarchColors.SystemGreen, "scheduled")
        }
    }
}

@Composable
private fun CalendarLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).clip(InkCircleShape(7)).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MonarchColors.InkMuted)
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
    val weight = remember { mutableStateOf(initialWeight) }
    val bodyFat = remember { mutableStateOf("") }
    val bfValue = bodyFat.value.toDoubleOrNull()
    val validWeight = weight.value.toDoubleOrNull()?.let { it > 0.0 } == true

    // Estimator state: prefill the tapes from the hunter's latest measurements.
    var showEstimator by remember { mutableStateOf(false) }
    val neck = remember { mutableStateOf(measurements[MeasurementSite.NECK]?.toString() ?: "") }
    val waist = remember { mutableStateOf(measurements[MeasurementSite.WAIST]?.toString() ?: "") }
    val hips = remember { mutableStateOf(measurements[MeasurementSite.HIPS]?.toString() ?: "") }
    val estimate = if (showEstimator) BodyStats.estimateBodyFatNavy(
        sex, heightCm ?: 0.0,
        neck.value.toDoubleOrNull() ?: 0.0,
        waist.value.toDoubleOrNull() ?: 0.0,
        hips.value.toDoubleOrNull(),
    ) else null

    Dialog(onDismissRequest = onDismiss) {
        SystemWindow(Modifier.fillMaxWidth(), accent = MonarchColors.Emerald) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "LOG BODY READING",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = ChakraPetch,
                    letterSpacing = MonarchTracking.InlineLabel,
                    color = MonarchColors.Emerald,
                )
                OutlinedTextField(
                    shape = MaterialTheme.shapes.small,
                    value = weight.value,
                    onValueChange = { weight.value = it },
                    label = { Text("Weight (kg)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    shape = MaterialTheme.shapes.small,
                    value = bodyFat.value,
                    onValueChange = { bodyFat.value = it },
                    label = { Text("Body fat % — optional") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (heightCm == null || heightCm <= 0.0) {
                    // BMI/FFMI need it, but logging must not demand it every time.
                    Text(
                        "Set your height once in SETTINGS to unlock BMI and FFMI.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MonarchColors.InkMuted,
                    )
                }
                TextButton(onClick = { showEstimator = !showEstimator }) {
                    Text(
                        if (showEstimator) "HIDE ESTIMATOR" else "ESTIMATE FOR ME",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        letterSpacing = MonarchTracking.InlineLabel,
                    )
                }
                if (showEstimator) {
                    // US Navy circumference method, pre-filled from the latest
                    // measurements the hunter already logged.
                    Text(
                        "Navy tape method from your neck, waist" +
                            if (sex == Sex.FEMALE) " and hip measurements." else " and measurements.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MonarchColors.InkMuted,
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
                            color = if (estimate != null) MonarchColors.EmeraldBright else MonarchColors.InkMuted,
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
                    MonarchButton(
                        label = "LOG IT",
                        onClick = { onConfirm(weight.value.toDoubleOrNull() ?: 0.0, bfValue) },
                        enabled = validWeight,
                        gold = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}


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
            SystemWindow(Modifier.fillMaxWidth()) {
                MetricLabel("ACTIVITY")
                Spacer(Modifier.height(6.dp))
                Text(
                    "No activity synced yet — connect Health Connect in System.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MonarchColors.InkMuted,
                )
            }
            Spacer(Modifier.height(24.dp))
            return
        }

        val today = LocalDate.now()
        val byDate = days.associateBy { it.date }
        val sorted = days.sortedBy { it.date }
        val last7 = sorted.filter { it.date > today.minusDays(7) }
        val avg7 = if (last7.isEmpty()) 0 else last7.sumOf { it.steps } / last7.size
        val bestDay = days.maxBy { it.steps }
        val lifetime = days.sumOf { it.steps }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActivityTile("TODAY'S STEPS", fmtInt(byDate[today]?.steps ?: 0), today.toString(), Modifier.weight(1f))
            ActivityTile("7-DAY AVERAGE", fmtInt(avg7), "steps per day", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActivityTile("BEST DAY", fmtInt(bestDay.steps), bestDay.date.toString(), Modifier.weight(1f))
            ActivityTile("LIFETIME", fmtInt(lifetime), "${days.size} days tracked", Modifier.weight(1f))
        }

        Spacer(Modifier.height(14.dp))
        SystemWindow(Modifier.fillMaxWidth()) {
            MetricLabel("STEPS — LAST 14 DAYS")
            val window = sorted.takeLast(14)
            TrendChart(window.map { it.steps.toDouble() }, goal = STEP_GOAL.toDouble())
            val hits = window.count { it.steps >= STEP_GOAL }
            ChartCaption("${window.size} days · $hits of ${window.size} hit the ${fmtInt(STEP_GOAL)} goal")
        }

        Spacer(Modifier.height(10.dp))
        SystemWindow(Modifier.fillMaxWidth()) {
            MetricLabel("DISTANCE (KM) — LAST 30 DAYS")
            val km = sorted.takeLast(30).map { it.distanceKm }
            if (km.count { it > 0.0 } >= 2) {
                TrendChart(km, MonarchColors.SystemGreen)
                ChartCaption("best ${"%.1f".format(km.max())} km · total ${"%.0f".format(km.sum())} km")
            } else {
                ChartCaption("Distance appears once Health Connect reports it.")
            }
        }

        EnergySection(sorted.takeLast(14), latest, sessions, sessionSets, exercises)
        Spacer(Modifier.height(14.dp))
        SectionHeader("Active calories — last 7 days")
        val kcal7 = last7
        if (kcal7.isEmpty()) {
            Text(
                "No active calories recorded yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MonarchColors.InkMuted,
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
        val sleep7 = sorted.filter { it.sleepMinutes > 0 }.takeLast(7)
        if (sleep7.isEmpty()) {
            Text(
                "No sleep recorded yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MonarchColors.InkMuted,
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
    SystemWindow(Modifier.fillMaxWidth()) {
        MetricLabel("BURN TODAY")
        if (todayBurn != null) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    fmtInt(todayBurn.kcal),
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.Bold,
                    color = MonarchColors.SovereignGold,
                )
                Text(
                    "kcal · ${confidenceWord(todayBurn.confidence)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = ChakraPetch,
                    color = MonarchColors.InkMuted,
                    letterSpacing = MonarchTracking.InlineLabel,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        } else {
            MetricValue("—", "no steps or sessions logged today")
        }

        Spacer(Modifier.height(10.dp))
        MetricLabel("DAILY BURN — LAST 14 DAYS")
        TrendChart(burns.map { it?.kcal?.toDouble() ?: 0.0 })
        Spacer(Modifier.height(6.dp))
        EnergyLegend()
        val measuredCount = window.count { it.activeKcal > 0 }
        ChartCaption(
            if (measuredCount == 0) {
                "All 14 days are MET estimates — Health Connect has not reported active calories."
            } else {
                "$measuredCount of ${window.size} days are Health Connect measurements (solid line, green). " +
                    "Estimates are never added on top of a measured day."
            }
        )

        val missingPrompts = buildList {
            if (latest?.weightKg == null) add("Log your bodyweight to estimate activity burn.")
            if (latest?.heightCm == null) add("Log your height to estimate steps when distance is missing.")
        }
        missingPrompts.forEach { prompt ->
            Spacer(Modifier.height(6.dp))
            Text(
                prompt,
                style = MaterialTheme.typography.bodySmall,
                color = MonarchColors.InkMuted,
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
                    color = MonarchColors.SystemGreen,
                )
                Text(
                    "kcal/day at rest · ${confidenceWord(resting.confidence)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = ChakraPetch,
                    color = MonarchColors.InkMuted,
                    letterSpacing = MonarchTracking.InlineLabel,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            ChartCaption(resting.basis)
        } else {
            Text(
                "Log body fat to estimate resting burn.",
                style = MaterialTheme.typography.bodySmall,
                color = MonarchColors.InkMuted,
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
            Text("\u25CF", color = MonarchColors.Emerald, style = MaterialTheme.typography.labelSmall)
            Text(
                "measured — Health Connect",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = MonarchColors.InkMuted,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("\u25CB", color = MonarchColors.InkMuted, style = MaterialTheme.typography.labelSmall)
            Text(
                "estimated — MET model",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = MonarchColors.InkMuted,
            )
        }
    }
}


private fun fmtInt(v: Int): String = String.format(Locale.getDefault(), "%,d", v)

private fun fmtSleep(minutes: Int): String = "${minutes / 60}h ${minutes % 60}m"

@Composable
private fun ActivityTile(label: String, value: String, hint: String, modifier: Modifier = Modifier) {
    SystemWindow(modifier) {
        MetricLabel(label)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = MonarchColors.SovereignGold,
        )
        Text(hint, style = MaterialTheme.typography.labelSmall, color = MonarchColors.InkMuted)
    }
}

@Composable
private fun MetricRow(label: String, value: String, best: Boolean) {
    SystemWindow(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = ChakraPetch,
                color = MonarchColors.Ink,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = ChakraPetch,
                color = if (best) MonarchColors.SovereignGold else MonarchColors.SystemGreen,
            )
        }
    }
}

