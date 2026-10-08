package com.ironvellum.app.ui.titles

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
import com.ironvellum.app.domain.Sex
import com.ironvellum.app.domain.TitleDef
import com.ironvellum.app.domain.TitleRarity
import com.ironvellum.app.domain.TitleRule
import com.ironvellum.app.domain.Titles
import com.ironvellum.app.domain.fmt
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

internal fun formatCount(n: Long): String = String.format(Locale.US, "%,d", n)

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
 * The word a deed's remaining amount is counted in, so "to go" never floats
 * without a unit: "32 min to go", "1,200 steps to go", "0.4 km to go". Lift deeds
 * count whole percent of bodyweight and attach the sign instead.
 */
internal fun toGoUnit(rule: TitleRule, amount: Long): String {
    fun noun(one: String, many: String) = if (amount == 1L) one else many
    return when (rule) {
        TitleRule.FirstWorkout, is TitleRule.Workouts, is TitleRule.WorkoutsInWeek, is TitleRule.SportSessions ->
            noun("trial", "trials")
        is TitleRule.ReachLevel -> noun("level", "levels")
        is TitleRule.SetsLogged -> noun("set", "sets")
        is TitleRule.RepsLogged, is TitleRule.SessionReps -> noun("rep", "reps")
        is TitleRule.SessionStrength, is TitleRule.LifetimeStrength -> "strength"
        is TitleRule.StepsInDay, is TitleRule.StepsLifetime -> noun("step", "steps")
        is TitleRule.DistanceKmLifetime, is TitleRule.ActivityDistanceKm,
        is TitleRule.LongestRun, is TitleRule.LongestSwim -> "km"
        is TitleRule.ActiveKcalInDay -> "kcal"
        is TitleRule.SleepMinutesInNight, is TitleRule.ActivityMinutes -> "min"
        is TitleRule.StepGoalDays, is TitleRule.TrainingStreak -> noun("day", "days")
        is TitleRule.SkillsMastered -> noun("technique", "techniques")
        is TitleRule.PracticeAttempts -> noun("attempt", "attempts")
        is TitleRule.DistinctActivities -> noun("activity", "activities")
        is TitleRule.LongestHold -> "s"
        is TitleRule.LiftMultiple -> "%"
        is TitleRule.HardestGrade -> noun("grade", "grades")
    }
}

internal fun deedProgressText(
    def: TitleDef,
    progress: Titles.Progress,
    ledger: Titles.Ledger,
    earned: Boolean,
): DeedProgressText {
    val rule = def.rule
    // Climbing grades are ranks, not amounts: "7 / 12" would mean nothing.
    if (rule is TitleRule.HardestGrade) {
        val best = if (earned) rule.grade else ledger.hardestGrade.ifBlank { "none yet" }
        val gap = if (earned) 0L else progress.remaining
        return DeedProgressText(
            counts = "$best / ${rule.grade}",
            toGo = if (earned) "complete" else "${formatCount(gap)} ${toGoUnit(rule, gap)} to go",
            caption = "hardest climb sent",
        )
    }
    // Lift deeds count whole percent of bodyweight.
    val suffix = if (rule is TitleRule.LiftMultiple) "%" else ""
    val current = if (earned) progress.target else progress.current
    val remaining = if (earned) 0L else progress.remaining
    val left = formatProgress(remaining, progress.scale)
    return DeedProgressText(
        counts = "${formatProgress(current, progress.scale)}$suffix / ${formatProgress(progress.target, progress.scale)}$suffix",
        toGo = when {
            earned -> "complete"
            rule is TitleRule.LiftMultiple -> "$left% to go"
            else -> "$left ${toGoUnit(rule, remaining)} to go"
        },
        caption = progress.unit.removePrefix("% "),
    )
}

/** Where an unearned deed is won, in one plain line. */
internal fun earnPointer(category: String): String = when (category) {
    "Trials" -> "Seal trials from Today to earn it."
    "Level" -> "Seal trials to gain levels."
    "Volume" -> "Log sets and reps in a trial and they add up here."
    "Strength" -> "Train in a trial; your logged lifts count toward it."
    "Steps" -> "Walk. Steps, distance and calories come from Health Connect, which you can turn on in Settings."
    "Recovery" -> "Sleep. Your nights come from Health Connect, which you can turn on in Settings."
    "Mastery" -> "Practise techniques on Paths and log attempts in the Journal."
    else -> "Log activities in a trial; time, distance and variety all count."
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
                DeedSeal(def.rarity, category = category, size = 96.dp, earned = earned)
                if (earnedAtMs != null) {
                    Text(
                        "Earned ${formatDate(earnedAtMs, "d MMM yyyy")}",
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
        actions = if (earned && !worn) listOf(InfoAction("Wear title", onWear)) else emptyList(),
    ) {
        if (earnedAtMs != null) {
            text(null, def.describeFor(sex))
            if (worn) text(null, "Worn now", IronvellumColors.InkMuted)
        } else {
            text(null, def.describeFor(sex))
            text("HOW TO EARN IT", earnPointer(category), IronvellumColors.InkMuted)
        }
        section("RARITY") { RarityStrip(def.rarity, category, earned) }
    }
}

/** How the tiers rank, in a sentence the strip above it repeats in shape. */
internal fun rarityNote(rarity: TitleRarity): String {
    val rank = when (rarity) {
        TitleRarity.Common -> "the most common of the four tiers"
        TitleRarity.Rare -> "the second of the four tiers"
        TitleRarity.Epic -> "the third of the four tiers"
        TitleRarity.Masterwork -> "the rarest of the four tiers"
    }
    return "${rarity.label} is $rank. Each tier has its own shape and frame, so you can tell them apart without colour."
}

/**
 * The four tiers in order, each as its seal, with this deed's tier lit: full strength, a bold
 * name and a short underline in its metal. The rest sit back at low strength. A locked deed's
 * seals are all muted, as everywhere else.
 */
@Composable
internal fun RarityStrip(current: TitleRarity, category: String, earned: Boolean) {
    val order = TitleRarity.entries
    Column(
        Modifier.semantics(mergeDescendants = true) {
            contentDescription = "Rarity tiers in order: ${order.joinToString(", ") { it.label }}. This deed is ${current.label}."
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
