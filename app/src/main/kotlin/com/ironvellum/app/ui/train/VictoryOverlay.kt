package com.ironvellum.app.ui.train

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ironvellum.app.data.Repository
import com.ironvellum.app.domain.SessionPeaks
import com.ironvellum.app.domain.WorkoutShare
import com.ironvellum.app.domain.Xp
import com.ironvellum.app.domain.fmt
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.LedgerEmbers
import com.ironvellum.app.ui.components.ledgerDawn
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.floor

/**
 * The trial report shown the moment a trial is sealed: what was set (peaks),
 * what was done (totals), then what it paid (XP). Peaks lead because they are
 * the performance; the XP is the receipt for it.
 *
 * The beats run in order - header lands, peaks tick in one by one, then the
 * level bar fills - and each is felt as well as seen: a confirm on landing and
 * a tick per peak. Rows hold their space while hidden, so nothing reflows.
 */
@Composable
internal fun VictoryOverlay(
    result: Repository.CompletionResult,
    title: String,
    peaks: List<SessionPeaks.Peak>,
    totals: WorkoutShare.Totals,
    onShare: () -> Unit,
    onContinue: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val land = remember { Animatable(0f) }
    var peaksShown by remember { mutableIntStateOf(0) }
    var rankShown by remember { mutableStateOf(false) }
    // Level progress as one number, level + fraction, so a level-up animates
    // as the bar running out and refilling instead of jumping backwards.
    val before = remember(result) { levelPosition(result.totalXp - result.xpAwarded) }
    val after = remember(result) { levelPosition(result.totalXp) }
    val levelPos = remember { Animatable(before) }
    val xpShown = remember { Animatable(0f) }

    LaunchedEffect(result) {
        land.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 420f))
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        delay(250)
        repeat(peaks.size) { i ->
            peaksShown = i + 1
            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            delay(220)
        }
        if (result.rankUp != null) {
            rankShown = true
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            delay(400)
        }
        // XP figure and bar run together over the same second.
        coroutineScope {
            launch { xpShown.animateTo(result.xpAwarded.toFloat(), tween(900, easing = FastOutSlowInEasing)) }
            launch {
                val span = (after - before).coerceAtLeast(0f)
                levelPos.animateTo(after, tween((700 + 500 * span.coerceAtMost(2f)).toInt(), easing = FastOutSlowInEasing))
            }
        }
    }

    Dialog(
        onDismissRequest = onContinue,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(IronvellumColors.Abyss)
                .ledgerDawn(0.16f),
        ) {
            LedgerEmbers()
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                // Centred while the report is short, scrolling once it is not:
                // a quiet day left a screen-high gap above the buttons.
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(24.dp))
                        Header(title, result.durationMinutes, land.value)
                        if (peaks.isNotEmpty()) {
                            Spacer(Modifier.height(24.dp))
                            PeaksPanel(peaks, peaksShown)
                        }
                        result.rankUp?.let { band ->
                            Spacer(Modifier.height(16.dp))
                            RankUpBanner(band, rankShown)
                        }
                        Spacer(Modifier.height(16.dp))
                        TotalsStrip(totals, result.strengthScore)
                        Spacer(Modifier.height(16.dp))
                        XpPanel(result, xpShown.value.toInt(), levelPos.value)
                        Spacer(Modifier.height(16.dp))
                    }
                }
                // Two separate actions, each its own full-width button: Share
                // opens the card and returns here; Continue moves on.
                IronvellumButton(
                    label = "Share trial",
                    quiet = true,
                    onClick = onShare,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                IronvellumButton(
                    label = "Continue",
                    gold = true,
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Level plus the fraction of the way through it, e.g. 14.6. */
private fun levelPosition(totalXp: Long): Float {
    val p = Xp.progress(totalXp)
    return p.level + p.intoLevel.toFloat() / p.needed.toFloat()
}

@Composable
private fun Header(title: String, minutes: Long, land: Float) {
    Column(
        Modifier.graphicsLayer {
            val s = 0.85f + 0.15f * land
            scaleX = s
            scaleY = s
            alpha = land.coerceIn(0f, 1f)
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "SEALED",
            style = MaterialTheme.typography.displaySmall,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            letterSpacing = IronvellumTracking.ScreenTitle,
            color = IronvellumColors.SovereignGold,
        )
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .width(56.dp)
                .height(2.dp)
                .background(IronvellumColors.SovereignGold.copy(alpha = 0.6f)),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.SemiBold,
            color = IronvellumColors.Ink,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "$minutes MIN",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            letterSpacing = IronvellumTracking.InlineLabel,
            color = IronvellumColors.InkMuted,
        )
    }
}

@Composable
private fun PeaksPanel(peaks: List<SessionPeaks.Peak>, shown: Int) {
    InkPanel(Modifier.fillMaxWidth(), accent = IronvellumColors.SovereignGold) {
        Text(
            if (peaks.size == 1) "NEW PEAK" else "NEW PEAKS · ${peaks.size}",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            letterSpacing = IronvellumTracking.SectionHeader,
            color = IronvellumColors.SovereignGold,
        )
        peaks.forEachIndexed { i, peak ->
            Spacer(Modifier.height(12.dp))
            PeakRow(peak, visible = i < shown)
        }
    }
}

/** A band never held before, announced once. */
@Composable
private fun RankUpBanner(band: String, visible: Boolean) {
    val t by animateFloatAsState(if (visible) 1f else 0f, tween(320), label = "rank")
    InkPanel(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .graphicsLayer {
                alpha = t
                val s = 0.92f + 0.08f * t
                scaleX = s
                scaleY = s
            },
        accent = IronvellumColors.SovereignGold,
    ) {
        Text(
            "RANK UP",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            letterSpacing = IronvellumTracking.SectionHeader,
            color = IronvellumColors.SovereignGold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            band.uppercase(),
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = IronvellumColors.Ink,
        )
        Text(
            "Strength Rank",
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
        )
    }
}

@Composable
private fun PeakRow(peak: SessionPeaks.Peak, visible: Boolean) {
    val t by animateFloatAsState(if (visible) 1f else 0f, tween(260), label = "peak")
    val now = peakFigure(peak.figure, peak.weightKg, peak.isHold)
    val was = peakFigure(peak.was.reps, peak.was.weightKg, peak.isHold)
    val gain = "+" + "%.1f".fmt(peak.deltaScore)
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = t
                translationX = (1f - t) * 24.dp.toPx()
            }
            .clearAndSetSemantics {
                contentDescription = "New peak, ${peak.exerciseName}, set ${peak.setIndex + 1}, " +
                    "$now, was $was, up $gain" + if (peak.count > 1) ", ${peak.count} peaks" else ""
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Bolt,
            contentDescription = null,
            tint = IronvellumColors.SovereignGold,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                peak.exerciseName,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.SemiBold,
                color = IronvellumColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Set ${peak.setIndex + 1} · $now · was $was",
                style = MaterialTheme.typography.labelSmall,
                color = IronvellumColors.InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "▲ $gain",
                style = MaterialTheme.typography.titleSmall,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = IronvellumColors.EmeraldBright,
            )
            if (peak.count > 1) {
                Text(
                    "${peak.count} PEAKS",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    letterSpacing = IronvellumTracking.InlineLabel,
                    color = IronvellumColors.InkMuted,
                )
            }
        }
    }
}

/** "6×82.5kg", "10×BW", or a hold's "45s" (with any added load). Same shape as the live badge. */
private fun peakFigure(figure: Int, weightKg: Double?, isHold: Boolean): String =
    if (isHold) {
        "${figure}s" + (weightKg?.takeIf { it > 0.0 }?.let { " +${formatKg(it)}" } ?: "")
    } else {
        "$figure×${formatKg(weightKg)}"
    }

@Composable
private fun TotalsStrip(totals: WorkoutShare.Totals, strength: Int) {
    val cells = buildList {
        add(count(totals.sets) to if (totals.sets == 1) "SET" else "SETS")
        if (totals.reps > 0) add(count(totals.reps) to "REPS")
        if (totals.heldSeconds > 0) add("${count(totals.heldSeconds)}s" to "HELD")
        if (totals.movedKg > 0) add(count(totals.movedKg) to "KG")
        if (strength > 0) add(count(strength) to "STR")
    }
    InkPanel(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            cells.forEach { (value, label) ->
                Column(
                    Modifier
                        .weight(1f)
                        .semantics(mergeDescendants = true) {},
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        value,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        color = IronvellumColors.Ink,
                        maxLines = 1,
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        letterSpacing = IronvellumTracking.InlineLabel,
                        color = IronvellumColors.InkMuted,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun count(value: Int): String = String.format(Locale.ENGLISH, "%,d", value)

@Composable
private fun XpPanel(result: Repository.CompletionResult, xpShown: Int, levelPos: Float) {
    val level = floor(levelPos).toInt()
    val fraction = levelPos - level
    val final = Xp.progress(result.totalXp)
    InkPanel(Modifier.fillMaxWidth(), accent = IronvellumColors.Emerald) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                "+$xpShown XP",
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = IronvellumColors.Emerald,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "${result.xpAwarded} XP earned" },
            )
            Text(
                "LEVEL $level",
                style = MaterialTheme.typography.titleSmall,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                letterSpacing = IronvellumTracking.InlineLabel,
                color = if (level > result.levelBefore) IronvellumColors.SovereignGold else IronvellumColors.Ink,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        val shape = MaterialTheme.shapes.small
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(shape)
                .background(IronvellumColors.Rune),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(8.dp)
                    .background(Brush.horizontalGradient(listOf(IronvellumColors.Emerald, IronvellumColors.EmeraldBright))),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "${final.intoLevel} / ${final.needed} XP",
            style = MaterialTheme.typography.labelSmall,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.align(Alignment.End),
        )
        val rewards = buildList {
            if (result.questBonus) add("TODAY'S TRIAL" to "+${Xp.QUEST_BONUS} bonus")
            if (result.levelAfter > result.levelBefore) {
                // Ascension is a band of levels: crossing one is part of this line, not a second reward.
                val tier = result.classAfter.takeIf { it != result.classBefore }?.let { " · ${it.uppercase()}" }.orEmpty()
                add("LEVEL UP" to "${result.levelBefore} → ${result.levelAfter}$tier")
            }
            result.newTitles.filter { it.name.isNotBlank() }.forEach { add("DEED EARNED" to it.name) }
        }
        if (rewards.isNotEmpty()) Spacer(Modifier.height(10.dp))
        rewards.forEach { (label, value) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = ChakraPetch,
                    letterSpacing = IronvellumTracking.InlineLabel,
                    color = IronvellumColors.InkMuted,
                )
                Text(
                    value,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.Bold,
                    color = IronvellumColors.SovereignGold,
                )
            }
        }
    }
}
