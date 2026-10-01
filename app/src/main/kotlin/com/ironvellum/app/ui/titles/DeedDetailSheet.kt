package com.ironvellum.app.ui.titles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ironvellum.app.domain.Sex
import com.ironvellum.app.domain.TitleDef
import com.ironvellum.app.domain.TitleRarity
import com.ironvellum.app.domain.TitleRule
import com.ironvellum.app.domain.Titles
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkListRow
import com.ironvellum.app.ui.components.InkRail
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.PanelLabel
import com.ironvellum.app.ui.components.formatDate
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.InkCircleShape
import com.ironvellum.app.ui.theme.IronvellumColors
import java.util.Locale

/** Rarity accent per tier. Gold is kept for what is earned or worn, so the top tier reads as bone ink instead. */
internal fun rarityColor(rarity: TitleRarity): Color = when (rarity) {
    TitleRarity.Common -> IronvellumColors.InkMuted
    TitleRarity.Rare -> IronvellumColors.SystemGreen
    TitleRarity.Epic -> IronvellumColors.Emerald
    TitleRarity.Masterwork -> IronvellumColors.Ink
}

/** A small accent dot and the tier's name; the word carries the meaning, the colour only echoes it. */
@Composable
internal fun RarityMark(rarity: TitleRarity, modifier: Modifier = Modifier) {
    val accent = rarityColor(rarity)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(InkCircleShape(7)).background(accent))
        Text(
            rarity.label,
            style = MaterialTheme.typography.bodySmall,
            color = accent,
            fontWeight = if (rarity == TitleRarity.Masterwork) FontWeight.Bold else null,
            maxLines = 1,
        )
    }
}

/** Green while in progress; gold only once the deed is earned. */
internal fun railFill(earned: Boolean): Brush =
    if (earned) {
        Brush.horizontalGradient(listOf(IronvellumColors.SovereignGold, IronvellumColors.SovereignGold))
    } else {
        Brush.horizontalGradient(listOf(IronvellumColors.SystemGreen, IronvellumColors.Emerald))
    }

internal fun formatCount(n: Long): String = String.format(Locale.US, "%,d", n)

/** How far along a deed is, worded for a row, a sheet and a screen reader alike. */
internal data class DeedProgressText(
    /** "12 / 25" */
    val counts: String,
    /** "13 to go", or "complete" once earned. */
    val toGo: String,
    /** What the numbers count: "trials", "steps lifetime". */
    val caption: String,
)

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
            toGo = if (earned) "complete" else "${formatCount(gap)} ${if (gap == 1L) "grade" else "grades"} to go",
            caption = "hardest climb sent",
        )
    }
    // Lift deeds count whole percent of bodyweight.
    val suffix = if (rule is TitleRule.LiftMultiple) "%" else ""
    val current = if (earned) progress.target else progress.current
    val remaining = if (earned) 0L else progress.remaining
    return DeedProgressText(
        counts = "${formatCount(current)}$suffix / ${formatCount(progress.target)}$suffix",
        toGo = if (earned) "complete" else "${formatCount(remaining)}$suffix to go",
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
@OptIn(ExperimentalMaterial3Api::class)
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
    val text = deedProgressText(def, progress, ledger, earned)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = MaterialTheme.shapes.large,
        containerColor = Color(0xFF0D1110),
        contentColor = IronvellumColors.Ink,
        // The stock handle is a machined pill; scrim tap, drag and back all dismiss.
        dragHandle = null,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    def.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.Bold,
                    color = if (earned) IronvellumColors.SovereignGold else IronvellumColors.Ink,
                    modifier = Modifier.weight(1f),
                )
                if (earned) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Earned",
                        tint = IronvellumColors.SovereignGold,
                    )
                }
            }
            RarityMark(def.rarity, Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                def.describeFor(sex),
                style = MaterialTheme.typography.bodyMedium,
                color = IronvellumColors.Ink,
            )
            Spacer(Modifier.height(16.dp))
            InkRail(
                fraction = if (earned) 1f else progress.fraction,
                height = 10.dp,
                fill = railFill(earned),
                seed = 5,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${text.counts} · ${text.toGo}",
                style = MaterialTheme.typography.titleSmall,
                color = IronvellumColors.Ink,
            )
            Text(
                text.caption,
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            InkDivider()
            InkListRow(
                label = "Earned",
                value = earnedAtMs?.let { formatDate(it, "d MMM yyyy") } ?: "Not yet",
            )
            InkDivider()
            Spacer(Modifier.height(16.dp))
            if (earned) {
                IronvellumButton(
                    label = if (worn) "Worn now" else "Wear title",
                    onClick = onWear,
                    enabled = !worn,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                )
            } else {
                PanelLabel("HOW TO EARN IT")
                Spacer(Modifier.height(4.dp))
                Text(
                    earnPointer(Titles.category(def.rule)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = IronvellumColors.InkMuted,
                )
            }
        }
    }
}
