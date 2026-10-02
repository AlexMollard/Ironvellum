package com.ironvellum.app.ui.program

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.ProgramRules
import com.ironvellum.app.ui.components.InfoAction
import com.ironvellum.app.ui.components.InfoFigure
import com.ironvellum.app.ui.components.InfoFigures
import com.ironvellum.app.ui.components.InfoSheet
import com.ironvellum.app.ui.theme.IronvellumColors
import kotlin.math.roundToInt

/**
 * "Muscles this rite works": the rite's own sets per muscle on a body figure
 * and in a list. A rite is a fraction of a week, so nothing here is judged
 * against the weekly range; the figure's green is each muscle's share of the
 * rite's most worked muscle, and the list gives the sets behind it.
 */
@Composable
internal fun RiteMusclesSheet(
    title: String,
    entries: List<PlannedEntry>,
    onDismiss: () -> Unit,
) {
    val sets = remember(title, entries) {
        ProgramRules.weeklyVolume(listOf(PlannedPreset(title, "", null, entries)))
    }
    val worked = remember(sets) { riteMuscleRows(sets) }
    InfoSheet(
        title = title,
        subtitle = "Muscles this rite works",
        onDismiss = onDismiss,
        summary = if (worked.isEmpty()) null else {
            {
                InfoFigures(
                    listOf(
                        InfoFigure("MUSCLES", "${worked.size}"),
                        InfoFigure("SETS", entries.sumOf { it.sets }.toString()),
                    ),
                )
            }
        },
        actions = listOf(InfoAction("Close", onDismiss, quiet = true)),
    ) {
        if (worked.isEmpty()) {
            text(null, "No muscle data for this rite yet.", IronvellumColors.InkMuted)
        } else {
            section("WHERE IT LANDS") {
                RiteMuscleMap(sets, title, Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(
                    "Brighter means more of this rite's sets. One rite is only part of a week.",
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
            }
            rows("SETS PER MUSCLE", worked.map { (muscle, count) -> muscle.label to riteSetsLabel(count) }, collapseAfter = 6)
        }
    }
}

/** Worked muscles, most sets first; equal counts fall back to name so the order is stable. */
internal fun riteMuscleRows(sets: Map<Muscle, Double>): List<Pair<Muscle, Double>> =
    sets.filter { it.value > 0.0 }
        .toList()
        .sortedWith(compareByDescending<Pair<Muscle, Double>> { it.second }.thenBy { it.first.label })

/**
 * "6 sets", "3.8 sets", "1 set": fractional credits (a share can be any
 * value from 0 to 1, so 3 sets at 0.75 and 1 at 0.6 sum to 2.85) rounded to
 * one decimal, as the generator's notes print them, never down to nothing.
 */
internal fun riteSetsLabel(sets: Double): String {
    val tenths = maxOf(1, (sets * 10).roundToInt())
    val number = if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
    return if (tenths == 10) "$number set" else "$number sets"
}
