package com.ironvellum.app.ui.titles

import com.ironvellum.app.domain.NumberText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ironvellum.app.data.Lines
import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.Sex
import com.ironvellum.app.domain.SexHold
import com.ironvellum.app.domain.TitleDef
import com.ironvellum.app.domain.TitleRarity
import com.ironvellum.app.domain.TitleRule
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.domain.Titles
import com.ironvellum.app.domain.fmt
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.InfoAction
import com.ironvellum.app.ui.components.InfoProgress
import com.ironvellum.app.ui.components.InfoSheet
import com.ironvellum.app.ui.components.formatDate
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.RarityTint
import java.util.Locale

/**
 * An earned deed's rarity word takes its tier's metal ([RarityTint]); a locked deed's stays
 * InkMuted, so a tier is only shown in colour once it is held. Worn is marked in words, never
 * by colour alone.
 */
internal fun rarityColor(rarity: TitleRarity, earned: Boolean = false): Color =
    if (earned) RarityTint.of(rarity) else IronvellumColors.InkMuted

/** The tier's name as a word; the word carries the meaning, so it takes no dot or box. */
@Composable
internal fun RarityMark(rarity: TitleRarity, modifier: Modifier = Modifier, earned: Boolean = false) {
    Text(
        rarity.label,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = rarityColor(rarity, earned),
        fontWeight = if (earned && rarity == TitleRarity.Masterwork) FontWeight.Bold else null,
        maxLines = 1,
    )
}

/** Solid Emerald while in progress; solid gold only once the deed is earned. */
internal fun railFill(earned: Boolean): Brush =
    SolidColor(if (earned) IronvellumColors.SovereignGold else IronvellumColors.Emerald)

internal fun formatCount(n: Long): String = NumberText.grouped(n)

/** A progress figure in the units the bar counts: whole numbers, or one decimal for tenths of a km. */
internal fun formatProgress(value: Long, scale: Int): String =
    if (scale == 1) formatCount(value) else "%.1f".fmt(value.toDouble() / scale)

/** How far along a deed is, worded for a row, a sheet and a screen reader alike. */
internal data class DeedProgressText(
    /** "12 / 25" */
    val counts: String,
    /** "13 trials to go", or "complete" once earned. */
    val toGo: String,
    /** What the numbers count: "trials", "steps lifetime". */
    val caption: String,
)

/**
 * What is left of a deed's bar as one phrase, so "to go" never floats without a unit: "32 min to go",
 * "1,200 steps to go", "0.4 km to go". [figure] is the amount already formatted for the bar's scale and
 * [amount] only picks the singular. Lift deeds count whole percent of bodyweight and attach the sign instead.
 */
internal fun toGoText(rule: TitleRule, amount: Long, figure: String): String {
    val count = amount.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    val text = when (rule) {
        TitleRule.FirstWorkout, is TitleRule.Workouts, is TitleRule.WorkoutsInWeek, is TitleRule.SportSessions ->
            UiText.themedPlural(R.plurals.deeds_togo_trials, R.plurals.deeds_togo_trials__t, count, figure)
        is TitleRule.ReachLevel -> UiText.plural(R.plurals.deeds_togo_levels, count, figure)
        is TitleRule.SetsLogged -> UiText.plural(R.plurals.deeds_togo_sets, count, figure)
        is TitleRule.RepsLogged, is TitleRule.SessionReps -> UiText.plural(R.plurals.deeds_togo_reps, count, figure)
        is TitleRule.SessionStrength, is TitleRule.LifetimeStrength -> UiText.res(R.string.deeds_togo_strength, figure)
        is TitleRule.StepsInDay, is TitleRule.StepsLifetime -> UiText.plural(R.plurals.deeds_togo_steps, count, figure)
        is TitleRule.DistanceKmLifetime, is TitleRule.ActivityDistanceKm,
        is TitleRule.LongestRun, is TitleRule.LongestSwim -> UiText.res(R.string.deeds_togo_km, figure)
        is TitleRule.ActiveKcalInDay -> UiText.res(R.string.deeds_togo_kcal, figure)
        is TitleRule.SleepMinutesInNight, is TitleRule.ActivityMinutes -> UiText.res(R.string.deeds_togo_min, figure)
        is TitleRule.StepGoalDays, is TitleRule.TrainingStreak -> UiText.plural(R.plurals.deeds_togo_days, count, figure)
        is TitleRule.SkillsMastered ->
            UiText.themedPlural(R.plurals.deeds_togo_skills, R.plurals.deeds_togo_skills__t, count, figure)
        is TitleRule.PracticeAttempts -> UiText.plural(R.plurals.deeds_togo_attempts, count, figure)
        is TitleRule.DistinctActivities -> UiText.plural(R.plurals.deeds_togo_activities, count, figure)
        is TitleRule.LongestHold -> UiText.res(R.string.deeds_togo_seconds, figure)
        is TitleRule.LiftMultiple -> UiText.res(R.string.deeds_togo_percent, figure)
        is TitleRule.HardestGrade -> UiText.plural(R.plurals.deeds_togo_grades, count, figure)
        is TitleRule.MovedOffEasier, TitleRule.DeficitBeatsStandard -> UiText.plural(R.plurals.deeds_togo_lifts, count, figure)
        TitleRule.BeatAssisted -> UiText.plural(R.plurals.deeds_togo_sets, count, figure)
        is TitleRule.VariationSet ->
            if (rule.reps == 1) UiText.plural(R.plurals.deeds_togo_sets, count, figure) else UiText.plural(R.plurals.deeds_togo_reps, count, figure)
        is TitleRule.DistinctVariations, is TitleRule.EveryFace -> UiText.plural(R.plurals.deeds_togo_variations, count, figure)
        is TitleRule.HarderInOneSet -> UiText.plural(R.plurals.deeds_togo_modifiers, count, figure)
    }
    return Words.text(text)
}

private fun completeText(): String = Words.text(UiText.res(R.string.deeds_progress_complete))

/** What a deed asks, in the lifter's own bar; the hold text while a sex-scaled bar has no sex to scale by. */
internal fun deedRequirement(def: TitleDef, sex: Sex, ledger: Titles.Ledger): String =
    if (ledger.sexUnset && SexHold.deedScaled(def.rule)) SexHold.TEXT else def.describeFor(sex)

internal fun deedProgressText(
    def: TitleDef,
    progress: Titles.Progress,
    ledger: Titles.Ledger,
    earned: Boolean,
): DeedProgressText {
    val rule = def.rule
    // A bar that depends on sex waits for one: no figure is shown against the stored default.
    if (ledger.sexUnset && !earned && SexHold.deedScaled(rule)) {
        return DeedProgressText(counts = "—", toGo = Words.text(UiText.res(R.string.deeds_progress_needs_sex)), caption = "")
    }
    // Climbing grades are ranks, not amounts: "7 / 12" would mean nothing.
    if (rule is TitleRule.HardestGrade) {
        val best = if (earned) rule.grade else ledger.hardestGrade.ifBlank { Words.text(UiText.res(R.string.deeds_progress_none_yet)) }
        val gap = if (earned) 0L else progress.remaining
        return DeedProgressText(
            counts = "$best / ${rule.grade}",
            toGo = if (earned) completeText() else toGoText(rule, gap, formatCount(gap)),
            caption = Words.text(UiText.res(R.string.deeds_progress_hardest_climb)),
        )
    }
    // Lift deeds count whole percent of bodyweight.
    val suffix = if (rule is TitleRule.LiftMultiple) "%" else ""
    val current = if (earned) progress.target else progress.current
    val remaining = if (earned) 0L else progress.remaining
    val left = formatProgress(remaining, progress.scale)
    return DeedProgressText(
        counts = "${formatProgress(current, progress.scale)}$suffix / ${formatProgress(progress.target, progress.scale)}$suffix",
        toGo = if (earned) completeText() else toGoText(rule, remaining, left),
        caption = progress.unit.removePrefix("% "),
    )
}

/** Where an unearned deed is won, in one plain line. */
internal fun earnPointer(category: String): String = when (category) {
    Words.trial.cpl -> Words.text(UiText.themed(R.string.deeds_earn_pointer_today, R.string.deeds_earn_pointer_today__t))
    "Level" -> Words.text(UiText.themed(R.string.deeds_earn_pointer_level, R.string.deeds_earn_pointer_level__t))
    "Volume" -> Words.text(UiText.themed(R.string.deeds_earn_pointer_volume, R.string.deeds_earn_pointer_volume__t))
    "Strength" -> Words.text(UiText.themed(R.string.deeds_earn_pointer_strength, R.string.deeds_earn_pointer_strength__t))
    "Steps" -> Words.text(UiText.res(R.string.deeds_earn_pointer_steps))
    "Recovery" -> Words.text(UiText.res(R.string.deeds_earn_pointer_recovery))
    "Mastery" -> Lines.practiseSkills
    else -> Words.text(UiText.themed(R.string.deeds_earn_pointer_activity, R.string.deeds_earn_pointer_activity__t))
}

/**
 * Everything about one deed: what it asks, how far off you are, when you won
 * it, and the one thing to do next. Earned deeds offer to be worn; the rest
 * point at where they are earned.
 */
@Composable
internal fun DeedDetailSheet(
    def: TitleDef,
    progress: Titles.Progress,
    ledger: Titles.Ledger,
    sex: Sex,
    earnedAtMs: Long?,
    worn: Boolean,
    onWear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val earned = earnedAtMs != null
    val progressText = deedProgressText(def, progress, ledger, earned)
    val category = Titles.category(def.rule)
    // The sheet's section builders are not composable, so their labels are read here.
    val howTitle = UiText.res(R.string.deeds_detail_how_title).asString()
    val rarityTitle = UiText.res(R.string.deeds_detail_rarity_title).asString()
    val wornNow = UiText.res(R.string.deeds_detail_worn_now).asString()
    val wearLabel = UiText.res(R.string.deeds_detail_wear_button).asString()
    InfoSheet(
        title = def.name,
        onDismiss = onDismiss,
        subtitle = "${def.rarity.label} · $category",
        subtitleColor = rarityColor(def.rarity, earned),
        summary = {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DeedSeal(def.rarity, category = category, size = 96.dp, earned = earned, stampIn = earned)
                if (earnedAtMs != null) {
                    Text(
                        UiText.res(R.string.deeds_detail_earned, formatDate(earnedAtMs, "d MMM yyyy")).asString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = IronvellumColors.SovereignGold,
                    )
                }
            }
            InfoProgress(
                fraction = if (earned) 1f else progress.fraction,
                line = "${progressText.counts} · ${progressText.toGo}",
                caption = progressText.caption,
                fill = railFill(earned),
            )
        },
        actions = if (earned && !worn) listOf(InfoAction(wearLabel, onWear)) else emptyList(),
    ) {
        if (earnedAtMs != null) {
            text(null, deedRequirement(def, sex, ledger))
            if (worn) text(null, wornNow, IronvellumColors.InkMuted)
        } else {
            text(null, deedRequirement(def, sex, ledger))
            text(howTitle, earnPointer(category), IronvellumColors.InkMuted)
        }
        section(rarityTitle) { RarityStrip(def.rarity, category, earned) }
    }
}

/** How the tiers rank, in a sentence the strip above it repeats in shape. */
internal fun rarityNote(rarity: TitleRarity): String {
    val note = when (rarity) {
        TitleRarity.Common -> R.string.deeds_rarity_note_common
        TitleRarity.Rare -> R.string.deeds_rarity_note_rare
        TitleRarity.Epic -> R.string.deeds_rarity_note_epic
        TitleRarity.Masterwork -> R.string.deeds_rarity_note_masterwork
    }
    return Words.text(UiText.res(note, rarity.label))
}

/**
 * The four tiers in order, each as its seal, with this deed's tier lit: full strength, a bold
 * name and a short underline in its metal. The rest sit back at low strength. A locked deed's
 * seals are all muted, as everywhere else.
 */
@Composable
internal fun RarityStrip(current: TitleRarity, category: String, earned: Boolean) {
    val order = TitleRarity.entries
    val description = UiText.res(R.string.always_deeds_rarity_description, order.joinToString(", ") { it.label }, current.label).asString()
    Column(
        Modifier.semantics(mergeDescendants = true) {
            contentDescription = description
        },
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            order.forEach { tier ->
                val lit = tier == current
                Column(
                    Modifier.weight(1f).alpha(if (lit) 1f else 0.42f).clearAndSetSemantics {},
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DeedSeal(tier, category = category, earned = earned)
                    Text(
                        tier.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (lit) IronvellumColors.Ink else IronvellumColors.InkMuted,
                        fontWeight = if (lit) FontWeight.Bold else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                    Box(
                        Modifier
                            .size(width = if (lit) 24.dp else 0.dp, height = 2.dp)
                            .background(if (earned) RarityTint.of(tier) else IronvellumColors.InkMuted),
                    )
                }
            }
        }
        Text(
            rarityNote(current),
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
        )
    }
}
