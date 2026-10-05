package com.ironvellum.app.ui.components

import androidx.compose.runtime.Composable
import com.ironvellum.app.domain.Rank
import com.ironvellum.app.domain.RankBreakdown
import com.ironvellum.app.ui.theme.IronvellumColors

/** Strength Rank written out: each pattern's mark, what earned it, and what lifts it next. */
@Composable
fun RankSheet(breakdown: RankBreakdown?, onDismiss: () -> Unit) {
    InfoSheet(
        title = breakdown?.band ?: Rank.UNRANKED,
        subtitle = "Strength Rank",
        onDismiss = onDismiss,
        summary = if (breakdown != null) ({ RankProgress(breakdown) }) else null,
    ) {
        if (breakdown == null) {
            text(null, "Seal a trial with a pull, push or leg lift, or clear a pull, push or leg technique, to earn a rank.")
            return@InfoSheet
        }
        breakdown.patterns.forEach { p ->
            rows(
                p.pattern.label,
                listOfNotNull(
                    "Holds" to Rank.forStep(p.step),
                    "From" to p.source,
                    p.liftTarget?.let { "Next lift" to it },
                    p.skillTarget?.let { "Next technique" to it },
                    ("Next" to "Top of the scale").takeIf { p.liftTarget == null && p.skillTarget == null },
                ),
            )
        }
        text(
            null,
            "A pattern you have not trained in ${Rank.WINDOW_DAYS} days is left out, not counted as zero. " +
                "Core work never lowers your rank.",
            color = IronvellumColors.InkMuted,
        )
    }
}

@Composable
private fun RankProgress(b: RankBreakdown) {
    val steps = b.stepsToNext
    InfoProgress(
        fraction = b.progress,
        line = if (b.nextBand == null || steps == null) "Top of the scale" else "$steps ${if (steps == 1) "step" else "steps"} to ${b.nextBand}",
        caption = "Average of ${b.patterns.size} of 3 patterns over the last ${Rank.WINDOW_DAYS} days",
    )
}
