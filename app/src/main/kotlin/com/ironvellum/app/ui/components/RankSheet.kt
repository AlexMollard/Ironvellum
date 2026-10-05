package com.ironvellum.app.ui.components

import androidx.compose.runtime.Composable
import com.ironvellum.app.domain.LiftBoards
import com.ironvellum.app.domain.Rank
import com.ironvellum.app.domain.RankBreakdown
import com.ironvellum.app.domain.RankPatterns
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
            text(null, "Seal a trial with a pull, push or leg technique you cleared, or with a squat, bench press, deadlift, overhead press, pull-up or dip after logging your bodyweight, to earn a rank.")
            return@InfoSheet
        }
        breakdown.patterns.forEach { p ->
            bullets(
                p.pattern.label,
                listOfNotNull(
                    "${Rank.forStep(p.step)}, from ${p.source}",
                    p.liftTarget?.let { "Next lift: $it" },
                    p.skillTarget?.let { "Next technique: $it" },
                    if (p.liftTarget == null && p.skillTarget == null) {
                        "Next: " + if (p.step >= LiftBoards.MAX_STEP) {
                            "Top of the scale"
                        } else {
                            "Log a ${RankPatterns.lifts(p.pattern).joinToString(" or ") { it.label }} to climb further"
                        }
                    } else {
                        null
                    },
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
