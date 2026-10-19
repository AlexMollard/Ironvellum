package com.ironvellum.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.LiftBoards
import com.ironvellum.app.domain.Rank
import com.ironvellum.app.domain.RankBreakdown
import com.ironvellum.app.domain.RankPatterns
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.theme.IronvellumColors

/** Strength Rank written out: each pattern's mark, what earned it, and what lifts it next. */
@Composable
fun RankSheet(breakdown: RankBreakdown?, onDismiss: () -> Unit) {
    InfoSheet(
        title = Rank.label(breakdown?.band ?: Rank.UNRANKED),
        subtitle = stringResource(R.string.rank_sheet_subtitle),
        onDismiss = onDismiss,
        summary = if (breakdown != null) ({ RankProgress(breakdown) }) else null,
    ) {
        if (breakdown == null) {
            text(null, Words.text(UiText.themed(R.string.rank_sheet_empty, R.string.rank_sheet_empty__t)))
            explainer()
            return@InfoSheet
        }
        breakdown.patterns.forEach { p ->
            bullets(
                p.pattern.label,
                listOfNotNull(
                    Words.text(UiText.res(R.string.rank_sheet_step_from, Rank.label(Rank.forStep(p.step)), p.source)),
                    p.liftTarget?.let { Words.text(UiText.res(R.string.rank_sheet_next_lift, it)) },
                    p.skillTarget?.let { Words.text(UiText.themed(R.string.rank_sheet_next_skill, R.string.rank_sheet_next_skill__t, it)) },
                    if (p.liftTarget == null && p.skillTarget == null) {
                        if (p.step >= LiftBoards.MAX_STEP) {
                            Words.text(UiText.res(R.string.rank_sheet_next_top))
                        } else {
                            Words.text(UiText.res(R.string.rank_sheet_next_log, RankPatterns.lifts(p.pattern).joinToString(" or ") { it.label }))
                        }
                    } else {
                        null
                    },
                ),
            )
        }
        text(
            null,
            Words.text(UiText.plural(R.plurals.rank_sheet_window_note, Rank.WINDOW_DAYS)),
            color = IronvellumColors.InkMuted,
        )
        explainer()
    }
}

/** What Strength Rank and Ascension mean: the explainer that used to sit behind an (i) on Today. */
private fun InfoSheetScope.explainer() = text(Term.RANKS.title, Term.RANKS.definition, color = IronvellumColors.InkMuted)

@Composable
private fun RankProgress(b: RankBreakdown) {
    val steps = b.stepsToNext
    InfoProgress(
        fraction = b.progress,
        line = if (b.nextBand == null || steps == null) {
            stringResource(R.string.rank_sheet_top_of_scale)
        } else {
            UiText.plural(R.plurals.rank_sheet_steps_to_next, steps, steps, b.nextBand).asString()
        },
        caption = UiText.plural(R.plurals.rank_sheet_progress_caption, Rank.WINDOW_DAYS, b.patterns.size, Rank.WINDOW_DAYS).asString(),
    )
}
