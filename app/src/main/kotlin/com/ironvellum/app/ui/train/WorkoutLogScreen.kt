package com.ironvellum.app.ui.train

import com.ironvellum.app.ui.components.PushedHeader
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.data.Repository
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.MovementDifficulty
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.WorkoutSession
import com.ironvellum.app.domain.fmt
import com.ironvellum.app.ui.components.plural
import com.ironvellum.app.ui.components.metricTotals
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.formatBodyValue
import com.ironvellum.app.ui.components.formatDate
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.theme.IronvellumTracking
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

data class WorkoutLogUi(
    val history: List<Pair<WorkoutSession, List<SessionSet>>> = emptyList(),
    /** Catalogue by id, so a hold's seconds are never totalled as reps. */
    val exercises: Map<Long, Exercise> = emptyMap(),
)

class WorkoutLogViewModel(private val repo: Repository) : ViewModel() {
    val ui: StateFlow<WorkoutLogUi> = combine(
        repo.observeHistory(),
        repo.observeExercises(),
    ) { history, exercises -> WorkoutLogUi(history, exercises.associateBy { it.id }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutLogUi())

    /** The list flow above re-emits from the database, so the row simply leaves. */
    fun delete(sessionId: Long) {
        viewModelScope.launch { repo.deleteWorkout(sessionId) }
    }
}

/**
 * The complete training chronicle: every completed session, grouped by month,
 * with a lifetime ledger on top so the log reads as a campaign record rather
 * than a flat dump of rows.
 */
@Composable
fun WorkoutLogScreen(
    onOpenWorkout: (sessionId: Long) -> Unit,
    onBack: () -> Unit,
    viewModel: WorkoutLogViewModel =
        viewModel(factory = viewModelFactory { initializer { WorkoutLogViewModel(ironvellumRepository()) } }),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    // LazyColumn, not a scrolling Column: this screen shows EVERY completed
    // session, so a plain Column composes a row per workout whether it is on
    // screen or not — a thousand of them after a few years of training.
    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        item {
        Spacer(Modifier.height(20.dp))
        PushedHeader("CHRONICLE", onBack)
        Spacer(Modifier.height(12.dp))

        }

        if (ui.history.isEmpty()) {
            item {
            InkPanel(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "THE CHRONICLE IS BLANK",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        color = IronvellumColors.Ink,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Seal a trial and it is written here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = IronvellumColors.InkMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
            }
        } else {
            item {
                LifetimeLedger(ui.history, ui.exercises)
                Spacer(Modifier.height(4.dp))
            }

            // Month groups, newest first: observeHistory is already newest-first,
            // so grouping preserves order and a long history stays navigable.
            val zone = ZoneId.systemDefault()
            val byMonth = ui.history.groupBy { (session, _) ->
                val ms = session.completedAtMs ?: session.startedAtMs
                YearMonth.from(Instant.ofEpochMilli(ms).atZone(zone))
            }
            for ((month, entries) in byMonth) {
                item(key = "month-$month") {
                    SectionHeader("${month.year} · ${month.month.name}")
                }
                items(entries, key = { (session, _) -> session.id }) { (session, sets) ->
                    LogRow(
                        session = session,
                        sets = sets,
                        exercises = ui.exercises,
                        onClick = { onOpenWorkout(session.id) },
                        onDelete = { viewModel.delete(session.id) },
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

@Composable
private fun LifetimeLedger(
    history: List<Pair<WorkoutSession, List<SessionSet>>>,
    exercises: Map<Long, Exercise>,
) {
    val sessions = history.map { it.first }
    // Only sets a lifter actually conquered. A session carries its whole
    // prescription, so a week planned at fourteen sets and finished at four
    // was crediting the lifetime record with all fourteen and a hundred reps
    // she never performed - while the row below it, the share card and the XP
    // she was paid all counted the four. This figure now agrees with them.
    val doneSets = history.flatMap { it.second }.filter { it.done }
    // Seconds held are not repetitions, and a climb's attempts or a run's
    // kilometres are neither. Each figure is totalled only over sets that
    // share its unit; a metric a mixed history never logged shows nothing.
    val totals = metricTotals(doneSets) { set -> figureMetric(set, exercises) }
    val totalXp = sessions.sumOf { it.xpAwarded }

    InkPanel(Modifier.fillMaxWidth()) {
        Text(
            "CHRONICLE TOTALS",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = IronvellumColors.SovereignGold,
            letterSpacing = IronvellumTracking.InlineLabel,
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            // Counts get explicit plural forms — appending "s" rendered "1 WORKOUTs".
            LedgerStat("${sessions.size}", plural(sessions.size, "TRIAL", "TRIALS"))
            LedgerStat("${doneSets.size}", plural(doneSets.size, "SET", "SETS"))
            LedgerStat("%,d".fmt(totals.reps), plural(totals.reps, "REP", "REPS"))
            if (totals.heldSeconds > 0) LedgerStat("%,d".fmt(totals.heldSeconds), "SEC HELD")
            if (totals.attempts > 0) LedgerStat("%,d".fmt(totals.attempts), plural(totals.attempts, "ATTEMPT", "ATTEMPTS"))
            // One slot for timed and distance work: both same-unit totals, and
            // either may be absent from a history that never logged them.
            if (totals.km > 0 || totals.secondsWorked > 0) {
                val kmPart = if (totals.km > 0) "${formatBodyValue(totals.km)} KM" else null
                val minPart = if (totals.secondsWorked > 0) "${(totals.secondsWorked + 59) / 60} MIN" else null
                LedgerStat(listOfNotNull(kmPart, minPart).joinToString(" · "), "KM · MIN")
            }
            LedgerStat("%,d".fmt(totalXp), "XP")
        }
        // Strength per trial is drawn once, on the Ledger's Training tab.
    }
}

@Composable
private fun LedgerStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = IronvellumColors.Ink,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            letterSpacing = IronvellumTracking.InlineLabel,
        )
    }
}

@Composable
private fun LogRow(
    session: WorkoutSession,
    sets: List<SessionSet>,
    exercises: Map<Long, Exercise>,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val doneSets = sets.filter { it.done }
    // Same-unit figures only: a climb's attempts and a run's kilometres must
    // not pass through the rep counter on their way onto this line.
    val totals = metricTotals(doneSets) { set -> figureMetric(set, exercises) }
    // Inline confirm, same treatment as DELETE MY CLOUD ACCOUNT: the destructive
    // step says what it does and asks once more before it does it. Row-local,
    // so arming one entry never arms another.
    var armed by remember { mutableStateOf(false) }
    InkPanel(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    // A named session reads as its own entry; the preset label
                    // is the fallback identity.
                    session.title.ifBlank { session.label },
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.SemiBold,
                    color = IronvellumColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    formatDate(session.completedAtMs ?: session.startedAtMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = IronvellumColors.InkMuted,
                )
            }
            ScorePill("+${session.xpAwarded}", "XP", IronvellumColors.Emerald)
            // A zero strength score is no score (no bodyweight logged, or an
            // activity-only trial): a gold "0 STR" read as a failing grade.
            if (session.strengthScore > 0) {
                Spacer(Modifier.width(8.dp))
                ScorePill("%,d".fmt(session.strengthScore), "STR", IronvellumColors.SovereignGold)
            }
            // Annotation glyphs so annotated sessions are findable at a glance
            // without opening each one.
            if (session.note.isNotBlank()) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = "Has a shared note",
                    tint = IronvellumColors.Emerald,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            if (session.privateNote.isNotBlank()) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = "Has a private note",
                    tint = IronvellumColors.SovereignGold,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        // ONE line of figures, not five chips in a Row. Five unweighted chips
        // starved the last one, which then wrapped a digit per line and took
        // the card's height with it — the "empty space" under every row was
        // that stretched chip.
        Text(
            buildString {
                val exerciseCount = sets.map { it.exerciseId }.distinct().size
                append(exerciseCount).append(' ').append(plural(exerciseCount, "exercise", "exercises"))
                append(" · ").append(doneSets.size).append("/").append(sets.size).append(" sets")
                if (totals.reps > 0) append(" · ").append("%,d".fmt(totals.reps)).append(' ').append(plural(totals.reps, "rep", "reps"))
                if (totals.heldSeconds > 0) append(" · ").append("%,d".fmt(totals.heldSeconds)).append("s held")
                if (totals.attempts > 0) {
                    append(" · ").append("%,d".fmt(totals.attempts)).append(' ').append(plural(totals.attempts, "attempt", "attempts"))
                }
                if (totals.km > 0) append(" · ").append(formatBodyValue(totals.km)).append(" km")
                if (totals.secondsWorked > 0) {
                    val minutes = totals.secondsWorked / 60
                    append(" · ").append(if (minutes > 0) "$minutes min" else "<1 min")
                }
            },
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (armed) {
                // The inline confirm names the cost, because the deletion is
                // not just visual: XP and strength leave the ledger with it.
                Text(
                    "XP AND STRENGTH WILL BE RETURNED",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
                            onDelete()
                        }
                        .heightIn(min = 24.dp)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .wrapContentHeight(),
                )
            } else {
                Text(
                    "DELETE",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                    letterSpacing = IronvellumTracking.InlineLabel,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraSmall)
                        .clickable { armed = true }
                        // A 17dp glyph row sits under the WCAG 24dp floor the
                        // house sweep enforces, and is genuinely hard to hit.
                        .heightIn(min = 24.dp)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .wrapContentHeight(),
                )
            }
        }
    }
}

/**
 * The two figures worth ranking a session by, as fixed-width pills on the title
 * row. They sit beside the name rather than in the figure line so the headline
 * numbers stay scannable down a long log.
 */
@Composable
private fun ScorePill(value: String, label: String, accent: androidx.compose.ui.graphics.Color) {
    Column(
        Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(IronvellumColors.VaultHigh)
            .inkBorder(IronvellumColors.Rune, MaterialTheme.shapes.extraSmall, 1.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = accent,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            letterSpacing = IronvellumTracking.InlineLabel,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** A hold's figure is seconds; the catalogue metric decides, name is the fallback. */
private fun isHoldSet(set: SessionSet, exercises: Map<Long, Exercise>): Boolean =
    MovementDifficulty.isHoldSet(exercises[set.exerciseId]?.metric, set.exerciseName, set.modifiers)

/**
 * Which unit a set's figure is counted in. A legacy hold row catalogued as
 * REPS still holds seconds, so the name/modifier hold detection overrides the
 * catalogue before anything sums its `reps` as repetitions.
 */
private fun figureMetric(set: SessionSet, exercises: Map<Long, Exercise>): ExerciseMetric =
    if (isHoldSet(set, exercises)) ExerciseMetric.HOLD
    else exercises[set.exerciseId]?.metric ?: ExerciseMetric.REPS
