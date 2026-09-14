package com.monarch.app.ui.train

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.monarch.app.data.Repository
import com.monarch.app.domain.SessionSet
import com.monarch.app.domain.WorkoutSession
import com.monarch.app.ui.components.SectionHeader
import com.monarch.app.ui.components.SystemWindow
import com.monarch.app.ui.components.TrendChart
import com.monarch.app.ui.components.formatDate
import com.monarch.app.ui.monarchRepository
import com.monarch.app.ui.theme.ChakraPetch
import com.monarch.app.ui.theme.MonarchColors
import com.monarch.app.ui.theme.MonarchTracking
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map

data class WorkoutLogUi(
    val history: List<Pair<WorkoutSession, List<SessionSet>>> = emptyList(),
)

class WorkoutLogViewModel(repo: Repository) : ViewModel() {
    val ui: StateFlow<WorkoutLogUi> = repo.observeHistory()
        .map { WorkoutLogUi(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutLogUi())
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
        viewModel(factory = viewModelFactory { initializer { WorkoutLogViewModel(monarchRepository()) } }),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

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
                "WORKOUT LOG",
                style = MaterialTheme.typography.labelLarge,
                color = MonarchColors.SystemGreen,
                letterSpacing = MonarchTracking.ScreenTitle,
            )
            Text(
                "BACK",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = MonarchColors.InkMuted,
                letterSpacing = MonarchTracking.InlineLabel,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable { onBack() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(12.dp))

        if (ui.history.isEmpty()) {
            SystemWindow(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "\u25C7",
                        fontSize = 34.sp,
                        color = MonarchColors.InkMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "NO SESSIONS RECORDED",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        color = MonarchColors.Ink,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Your chronicle is blank. Complete a workout and it will be carved into the record here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MonarchColors.InkMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
        } else {
            LifetimeLedger(ui.history)
            Spacer(Modifier.height(4.dp))

            // Month groups, newest first: observeHistory is already newest-first,
            // so grouping preserves order and a long history stays navigable.
            val zone = ZoneId.systemDefault()
            val byMonth = ui.history.groupBy { (session, _) ->
                val ms = session.completedAtMs ?: session.startedAtMs
                YearMonth.from(Instant.ofEpochMilli(ms).atZone(zone))
            }
            for ((month, entries) in byMonth) {
                SectionHeader("${month.year} · ${month.month.name}")
                entries.forEach { (session, sets) ->
                    LogRow(
                        session = session,
                        sets = sets,
                        onClick = { onOpenWorkout(session.id) },
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun LifetimeLedger(history: List<Pair<WorkoutSession, List<SessionSet>>>) {
    val sessions = history.map { it.first }
    val allSets = history.flatMap { it.second }
    val totalReps = allSets.sumOf { it.reps }
    val totalXp = sessions.sumOf { it.xpAwarded }

    SystemWindow(Modifier.fillMaxWidth(), accent = MonarchColors.SovereignGold) {
        Text(
            "LIFETIME RECORD",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = MonarchColors.SovereignGold,
            letterSpacing = MonarchTracking.InlineLabel,
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            LedgerStat("${sessions.size}", "WORKOUTS")
            LedgerStat("${allSets.size}", "SETS")
            LedgerStat("%,d".format(totalReps), "REPS")
            LedgerStat("%,d".format(totalXp), "XP")
        }
        Spacer(Modifier.height(14.dp))
        // Strength across the most recent stretch; a longer series just turns
        // to noise at this width, so cap the chart at the last 20 sessions.
        val series = sessions.take(20).map { it.strengthScore.toDouble() }
        Text(
            "STRENGTH · LAST ${series.size} SESSIONS",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = MonarchColors.InkMuted,
            letterSpacing = MonarchTracking.InlineLabel,
        )
        TrendChart(values = series, color = MonarchColors.Emerald)
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
            color = MonarchColors.Ink,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = MonarchColors.InkMuted,
            letterSpacing = MonarchTracking.InlineLabel,
        )
    }
}

@Composable
private fun LogRow(session: WorkoutSession, sets: List<SessionSet>, onClick: () -> Unit) {
    val doneSets = sets.filter { it.done }
    val totalReps = doneSets.sumOf { it.reps }
    SystemWindow(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    // A named session reads as its own entry; the preset label
                    // is the fallback identity.
                    session.title.ifBlank { session.label },
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.SemiBold,
                    color = MonarchColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    formatDate(session.completedAtMs ?: session.startedAtMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MonarchColors.InkMuted,
                )
            }
            // Annotation glyphs so annotated sessions are findable at a glance
            // without opening each one.
            if (session.note.isNotBlank()) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = "Has a shared note",
                    tint = MonarchColors.Emerald,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            if (session.privateNote.isNotBlank()) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = "Has a private note",
                    tint = MonarchColors.SovereignGold,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatChip("${sets.map { it.exerciseId }.distinct().size}", "EXERCISES")
            StatChip("${doneSets.size}/${sets.size}", "SETS")
            StatChip("${totalReps}", "REPS")
            StatChip("+${session.xpAwarded}", "XP", MonarchColors.Emerald)
            StatChip("${session.strengthScore}", "STR", MonarchColors.SovereignGold)
        }
    }
}

@Composable
private fun StatChip(value: String, label: String, accent: androidx.compose.ui.graphics.Color = MonarchColors.InkMuted) {
    Column(
        Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MonarchColors.VaultHigh)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = MonarchColors.InkMuted,
            letterSpacing = MonarchTracking.InlineLabel,
        )
    }
}
