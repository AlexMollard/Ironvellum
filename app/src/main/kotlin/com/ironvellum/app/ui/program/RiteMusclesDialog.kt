package com.ironvellum.app.ui.program

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.ProgramRules
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import kotlin.math.roundToInt

/**
 * "Muscles this rite works": the rite's own sets per muscle on a body figure
 * and in a list. A rite is a fraction of a week, so nothing here is judged
 * against the weekly range; the figure's green is each muscle's share of the
 * rite's most worked muscle, and the list gives the sets behind it.
 *
 * [goal] is accepted so callers can pass the week's goal alongside the rite;
 * it is deliberately unused, because a single rite is never judged against it.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
internal fun RiteMusclesDialog(
    title: String,
    entries: List<PlannedEntry>,
    goal: CoverageGoal?,
    onDismiss: () -> Unit,
) {
    val sets = remember(title, entries) {
        ProgramRules.weeklyVolume(listOf(PlannedPreset(title, "", null, entries)))
    }
    val rows = remember(sets) { riteMuscleRows(sets) }
    AlertDialog(
        shape = MaterialTheme.shapes.medium,
        containerColor = Color(0xFF0D1110),
        onDismissRequest = onDismiss,
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = ChakraPetch,
                color = IronvellumColors.Ink,
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "MUSCLES THIS RITE WORKS",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                    letterSpacing = IronvellumTracking.InlineLabel,
                )
                Spacer(Modifier.height(8.dp))
                if (rows.isEmpty()) {
                    Text(
                        "No muscle data for this rite yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = IronvellumColors.InkMuted,
                    )
                } else {
                    RiteMuscleMap(sets, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Brighter means more of this rite's sets. One rite is only part of a week.",
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.InkMuted,
                    )
                    Spacer(Modifier.height(10.dp))
                    rows.forEach { (muscle, count) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(
                                muscle.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = IronvellumColors.Ink,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                riteSetsLabel(count),
                                style = MaterialTheme.typography.labelLarge,
                                fontFamily = ChakraPetch,
                                color = IronvellumColors.InkMuted,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            IronvellumButton(label = "Close", onClick = onDismiss, quiet = true)
        },
    )
}

/** Worked muscles, most sets first; equal counts fall back to name so the order is stable. */
internal fun riteMuscleRows(sets: Map<Muscle, Double>): List<Pair<Muscle, Double>> =
    sets.filter { it.value > 0.0 }
        .toList()
        .sortedWith(compareByDescending<Pair<Muscle, Double>> { it.second }.thenBy { it.first.label })

/**
 * "6 sets", "4.5 sets", "1 set": fractional credits (an assisting muscle gets
 * half a set per set) rounded to the nearest half, never down to nothing.
 */
internal fun riteSetsLabel(sets: Double): String {
    val half = maxOf(1, (sets * 2).roundToInt()) / 2.0
    val number = if (half == half.toLong().toDouble()) half.toLong().toString() else half.toString()
    return if (half == 1.0) "$number set" else "$number sets"
}
