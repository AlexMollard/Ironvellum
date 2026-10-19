package com.ironvellum.app.ui.program

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.ProgramRules
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.InfoSheet
import com.ironvellum.app.ui.theme.IronvellumColors
import kotlin.math.roundToInt

/**
 * "Muscles this rite works": the rite's own sets per muscle, as a list. The
 * body figure lives on the rite's page; this is the exact count behind it. A
 * rite is a fraction of a week, so nothing here is judged against the weekly
 * range.
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
    val emptyText = UiText.themed(R.string.ritemuscles_empty, R.string.ritemuscles_empty__t).asString()
    val setsHeading = stringResource(R.string.ritemuscles_sets_heading)
    InfoSheet(
        title = title,
        subtitle = if (worked.isEmpty()) {
            UiText.themed(R.string.ritemuscles_title_empty, R.string.ritemuscles_title_empty__t).asString()
        } else {
            UiText.plural(
                R.plurals.ritemuscles_summary,
                worked.size,
                worked.size,
                UiText.plural(R.plurals.common_sets_count, entries.sumOf { it.sets }),
            ).asString()
        },
        onDismiss = onDismiss,
    ) {
        if (worked.isEmpty()) {
            text(null, emptyText, IronvellumColors.InkMuted)
        } else {
            rows(setsHeading, worked.map { (muscle, count) -> muscle.label to riteSetsLabel(count) }, collapseAfter = 6)
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
    return Words.text(UiText.plural(R.plurals.ritemuscles_sets, if (tenths == 10) 1 else 2, number))
}
