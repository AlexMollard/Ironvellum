package com.ironvellum.app.ui.train

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
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
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.ListRowHeight
import com.ironvellum.app.ui.components.PushedHeader
import com.ironvellum.app.ui.components.formatBodyValue
import com.ironvellum.app.ui.components.formatDate
import com.ironvellum.app.ui.components.metricTotals
import com.ironvellum.app.ui.components.plural
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.theme.IronvellumColors
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class WorkoutLogUi(
    val history: List<Pair<WorkoutSession, List<SessionSet>>> = emptyList(),
    /** Catalogue by id, so a hold's seconds are never totalled as reps. */
    val exercises: Map<Long, Exercise> = emptyMap(),
)

/** A trial is deleted from its own detail screen, so this only observes the history. */
class WorkoutLogViewModel(repo: Repository) : ViewModel() {
    val ui: StateFlow<WorkoutLogUi> = combine(
        repo.observeHistory(),
        repo.observeExercises(),
    ) { history, exercises -> WorkoutLogUi(history, exercises.associateBy { it.id }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutLogUi())
}

/**
 * The complete training chronicle: every completed trial, grouped by month in one card each,
 * under a single neutral totals line. A row opens the trial; deleting lives in the trial.
 */
@Composable
fun WorkoutLogScreen(
    onOpenWorkout: (sessionId: Long) -> Unit,
    onBack: () -> Unit,
    viewModel: WorkoutLogViewModel =
        viewModel(factory = viewModelFactory { initializer { WorkoutLogViewModel(ironvellumRepository()) } }),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    if (ui.history.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(20.dp))
            PushedHeader("Chronicle", onBack)
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 96.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Outlined.Book, contentDescription = null, tint = IronvellumColors.InkMuted, modifier = Modifier.size(34.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    "The Chronicle is blank.",
                    style = MaterialTheme.typography.titleMedium,
                    color = IronvellumColors.Ink,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Seal a trial and it is written here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = IronvellumColors.InkMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }

    // LazyColumn, not a scrolling Column: this screen shows EVERY completed
    // trial, and a month is one item, so a long history stays cheap to compose.
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Spacer(Modifier.height(20.dp))
            PushedHeader("Chronicle", onBack)
            Text(
                totalsLine(ui.history, ui.exercises),
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
                modifier = Modifier.padding(start = 2.dp, top = 4.dp),
            )
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
                Text(
                    monthLabel(month),
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier.padding(start = 2.dp, top = 18.dp, bottom = 8.dp),
                )
                InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                    entries.forEachIndexed { index, (session, sets) ->
                        if (index > 0) InkDivider()
                        LogRow(session, sets, onClick = { onOpenWorkout(session.id) })
                    }
                }
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

/** "October 2026", in the lifter's language. */
internal fun monthLabel(month: YearMonth): String =
    "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}"

/**
 * "42 trials · 610 sets · 4,900 reps" as one neutral line. Only sets a lifter actually conquered
 * count: a trial carries its whole prescription, and the row below, the share card and the XP
 * paid all count what was done. Seconds held, attempts, kilometres and minutes are totalled over
 * sets that share their unit and appear only when the history logged them.
 */
private fun totalsLine(
    history: List<Pair<WorkoutSession, List<SessionSet>>>,
    exercises: Map<Long, Exercise>,
): String {
    val doneSets = history.flatMap { it.second }.filter { it.done }
    val totals = metricTotals(doneSets) { set -> figureMetric(set, exercises) }
    val trials = history.size
    return buildList {
        add("$trials ${plural(trials, "trial", "trials")}")
        add("${doneSets.size} ${plural(doneSets.size, "set", "sets")}")
        add("%,d".fmt(totals.reps) + " " + plural(totals.reps, "rep", "reps"))
        if (totals.heldSeconds > 0) add("%,d".fmt(totals.heldSeconds) + "s held")
        if (totals.attempts > 0) add("%,d".fmt(totals.attempts) + " " + plural(totals.attempts, "attempt", "attempts"))
        if (totals.km > 0) add(formatBodyValue(totals.km) + " km")
        if (totals.secondsWorked > 0) add("${(totals.secondsWorked + 59) / 60} min")
    }.joinToString(" · ")
}

/** "52 min", or "1 h 5 min"; null while a trial is unsealed. */
internal fun trialDuration(session: WorkoutSession): String? {
    val minutes = session.completedAtMs?.let { ((it - session.startedAtMs) / 60_000L).coerceAtLeast(0) } ?: return null
    return when {
        minutes < 1L -> "<1 min"
        minutes < 60L -> "$minutes min"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}

@Composable
private fun LogRow(session: WorkoutSession, sets: List<SessionSet>, onClick: () -> Unit) {
    val doneCount = sets.count { it.done }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = ListRowHeight)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.FitnessCenter, contentDescription = null, tint = IronvellumColors.InkMuted, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(
                // A named trial reads as its own entry; the preset label is the fallback identity.
                session.title.ifBlank { session.label },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = IronvellumColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    formatDate(session.completedAtMs ?: session.startedAtMs, "EEE d MMM"),
                    trialDuration(session),
                    "$doneCount ${plural(doneCount, "set", "sets")}",
                    // A zero strength score is no score (no bodyweight logged, or an activity-only trial).
                    session.strengthScore.takeIf { it > 0 }?.let { "%,d".fmt(it) + " STR" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Annotation glyphs so annotated trials are findable at a glance without opening each one.
        if (session.note.isNotBlank()) {
            Icon(
                Icons.Outlined.Description,
                contentDescription = "Has a shared note",
                tint = IronvellumColors.InkMuted,
                modifier = Modifier.size(14.dp),
            )
        }
        if (session.privateNote.isNotBlank()) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = "Has a private note",
                tint = IronvellumColors.InkMuted,
                modifier = Modifier.size(14.dp),
            )
        }
        // The one earned figure on the row: XP gained.
        Text(
            "+${session.xpAwarded} XP",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = IronvellumColors.SovereignGold,
            maxLines = 1,
            softWrap = false,
        )
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = IronvellumColors.InkMuted)
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
