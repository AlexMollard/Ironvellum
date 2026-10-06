package com.ironvellum.app.ui.train

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ironvellum.app.data.Repository
import com.ironvellum.app.domain.SessionPeaks
import com.ironvellum.app.domain.Sex
import com.ironvellum.app.domain.TitleDef
import com.ironvellum.app.domain.TitleRarity
import com.ironvellum.app.domain.WorkoutShare
import com.ironvellum.app.domain.Xp
import com.ironvellum.app.domain.fmt
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.animatorsOn
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.inkArc
import com.ironvellum.app.ui.theme.inkDot
import com.ironvellum.app.ui.theme.inkStroke
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * What a seal earned, and the order it is told in: a level-up page if the trial
 * levelled, one deeds page if it earned any, then the summary, always. Only the
 * steps that exist are visited and counted ("1 of 2"), so a quiet trial goes
 * straight to the summary.
 */
internal object CelebrationFlow {

    fun hasLevelUp(result: Repository.CompletionResult): Boolean = result.levelAfter > result.levelBefore

    /** Deeds with a name to show: a nameless one cannot be celebrated or worn. */
    fun deedsOf(result: Repository.CompletionResult): List<TitleDef> = result.newTitles.filter { it.name.isNotBlank() }

    /** The first stage at or after [from] that has something to show. */
    fun resolve(from: SessionViewModel.Finish, hasLevel: Boolean, hasDeeds: Boolean): SessionViewModel.Finish {
        var stage = from
        if (stage == SessionViewModel.Finish.LEVEL && !hasLevel) stage = SessionViewModel.Finish.DEEDS
        if (stage == SessionViewModel.Finish.DEEDS && !hasDeeds) stage = SessionViewModel.Finish.SUMMARY
        return stage
    }

    /** The stage after [from]; the summary is the last of the celebration, then the routine question. */
    fun after(from: SessionViewModel.Finish, hasLevel: Boolean, hasDeeds: Boolean): SessionViewModel.Finish {
        val next = SessionViewModel.Finish.entries.getOrNull(from.ordinal + 1) ?: return from
        return resolve(next, hasLevel, hasDeeds)
    }

    /** The numbered steps before the summary. */
    fun steps(hasLevel: Boolean, hasDeeds: Boolean): List<SessionViewModel.Finish> = buildList {
        if (hasLevel) add(SessionViewModel.Finish.LEVEL)
        if (hasDeeds) add(SessionViewModel.Finish.DEEDS)
    }

    /**
     * The deed Wear title puts on: the rarest, first in catalogue order on a
     * tie, the same pick the seal makes when nothing is worn yet.
     */
    fun wearTarget(deeds: List<TitleDef>): TitleDef? = deeds.maxByOrNull { it.rarity.ordinal }
}

// Level-up timeline, in ms from the page landing: the bar starts at 700 and
// runs out in 600, holds, then refills in 500; the number rolls as it runs out.
private const val XP_START = 700L
private const val XP_FILL = 600L
private const val XP_HOLD = 220L
private const val XP_REFILL = 500L
private const val CROSS_AT = XP_START + XP_FILL
private const val ROLL_MS = 500L
private const val LEVEL_END = XP_START + XP_FILL + XP_HOLD + XP_REFILL + 100

private val RollEasing = CubicBezierEasing(0.3f, 0.9f, 0.3f, 1f)
private val Dim = IronvellumColors.InkMuted

/**
 * XP counted so far at [t]: up to the end of the starting level over the fill,
 * a hold while the number rolls, then on to the full gain over the refill.
 */
internal fun xpCounted(gain: Int, toLevelEnd: Int, t: Long): Float {
    val e = t - XP_START
    return when {
        e <= 0 -> 0f
        e < XP_FILL -> toLevelEnd * FastOutSlowInEasing.transform(e / XP_FILL.toFloat())
        e < XP_FILL + XP_HOLD -> toLevelEnd.toFloat()
        e < XP_FILL + XP_HOLD + XP_REFILL -> {
            val k = (e - XP_FILL - XP_HOLD) / XP_REFILL.toFloat()
            toLevelEnd + (gain - toLevelEnd) * (1f - (1f - k) * (1f - k) * (1f - k))
        }
        else -> gain.toFloat()
    }
}

/** 0 before [from], 1 after [from] + [ms], eased between. */
private fun phase(t: Long, from: Long, ms: Long, easing: (Float) -> Float = { it }): Float =
    easing(((t - from) / ms.toFloat()).coerceIn(0f, 1f))

private fun count(value: Int): String = String.format(Locale.ENGLISH, "%,d", value)

private fun count(value: Long): String = String.format(Locale.ENGLISH, "%,d", value)

/**
 * The celebration after a seal, one page at a time inside one full-screen
 * dialog: [SessionViewModel.Finish.LEVEL], [SessionViewModel.Finish.DEEDS] or
 * [SessionViewModel.Finish.SUMMARY]. Every page ends in the same dock - one
 * emerald primary and one quiet link - and nothing else on the page is a tap
 * target. Motion is one ring draw, one pulse, one bar and one number roll; with
 * animations off every page lands in its final state at once.
 */
@Composable
internal fun TrialCelebration(
    stage: SessionViewModel.Finish,
    result: Repository.CompletionResult,
    title: String,
    peaks: List<SessionPeaks.Peak>,
    totals: WorkoutShare.Totals,
    sex: Sex,
    wornTitleId: String?,
    /** Wall-clock deadline of "Sealed too soon? Keep going"; null when it is not offered. */
    reopenUntilMs: Long?,
    onContinue: () -> Unit,
    onSkipToSummary: () -> Unit,
    onWear: (titleId: String) -> Unit,
    onShare: () -> Unit,
    onReopen: () -> Unit,
) {
    val hasLevel = CelebrationFlow.hasLevelUp(result)
    val deeds = CelebrationFlow.deedsOf(result)
    val steps = CelebrationFlow.steps(hasLevel, deeds.isNotEmpty())
    Dialog(
        onDismissRequest = onContinue,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(IronvellumColors.Abyss),
        ) {
            when (stage) {
                SessionViewModel.Finish.LEVEL -> LevelUpPage(
                    result = result,
                    step = steps.indexOf(stage),
                    steps = steps.size,
                    // Skip exists only because a summary-bound step follows.
                    canSkip = deeds.isNotEmpty(),
                    onContinue = onContinue,
                    onSkip = onSkipToSummary,
                )
                SessionViewModel.Finish.DEEDS -> DeedsPage(
                    deeds = deeds,
                    sex = sex,
                    step = steps.indexOf(stage),
                    steps = steps.size,
                    wornTitleId = wornTitleId,
                    onContinue = onContinue,
                    onWear = onWear,
                )
                else -> SummaryPage(
                    result = result,
                    title = title,
                    peaks = peaks,
                    totals = totals,
                    deeds = deeds,
                    reopenUntilMs = reopenUntilMs,
                    onDone = onContinue,
                    onShare = onShare,
                    onReopen = onReopen,
                )
            }
        }
    }
}

/** Time since the page landed, in ms: one clock per page; at its end everything has landed. */
@Composable
private fun rememberClock(endMs: Long, motion: Boolean): Long {
    var ms by remember { mutableLongStateOf(if (motion) 0L else endMs) }
    LaunchedEffect(motion) {
        if (!motion) {
            ms = endMs
            return@LaunchedEffect
        }
        val start = withFrameMillis { it }
        while (ms < endMs) ms = (withFrameMillis { it } - start).coerceAtMost(endMs)
    }
    return ms
}

/** A 6dp rise and fade, the page's only entrance. */
@Composable
private fun Modifier.reveal(shown: Boolean, motion: Boolean): Modifier {
    val t by animateFloatAsState(if (shown) 1f else 0f, if (motion) tween(350) else snap(), label = "reveal")
    return graphicsLayer {
        alpha = t
        translationY = (1f - t) * 6.dp.toPx()
    }
}

/** The ring's quick settle as the seal closes: 1 to 1.06 and back over 300ms. */
private fun stamp(t: Long, at: Long): Float {
    val p = ((t - at) / 300f).coerceIn(0f, 1f)
    return 1f + 0.06f * if (p < 0.4f) p / 0.4f else (1f - p) / 0.6f
}

/** One haptic at [atMs]; with animations off, the first alone and at once. */
@Composable
private fun Haptics(motion: Boolean, vararg beats: Pair<Long, HapticFeedbackType>) {
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        if (beats.isEmpty()) return@LaunchedEffect
        if (!motion) {
            haptic.performHapticFeedback(beats.last().second)
            return@LaunchedEffect
        }
        var at = 0L
        for ((beatAt, type) in beats) {
            delay(beatAt - at)
            at = beatAt
            haptic.performHapticFeedback(type)
        }
    }
}

/**
 * The dock every celebration page ends in: a 52dp emerald primary, then a
 * quiet 48dp link 16dp below. The link's slot keeps its height when it is
 * empty, so the primary never moves between pages.
 */
@Composable
private fun CelebrationDock(
    primary: String,
    onPrimary: () -> Unit,
    link: String? = null,
    linkOn: Boolean = true,
    linkColor: Color = IronvellumColors.SystemGreen,
    onLink: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(IronvellumColors.VaultHigh)
            .drawBehind { drawRect(IronvellumColors.Rune, size = Size(size.width, 1.dp.toPx())) }
            .navigationBarsPadding()
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 14.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(IronvellumColors.Emerald)
                .clickable(role = Role.Button, onClick = onPrimary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                primary,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .then(
                    if (link != null && linkOn) {
                        Modifier.clickable(role = Role.Button, onClick = onLink)
                    } else {
                        Modifier.semantics { if (link != null) disabled() }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (link != null) {
                Text(
                    link,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = linkColor,
                )
            }
        }
    }
}

/** "1 of 2": two dots and a count at the top of a step page. */
@Composable
private fun StepIndicator(step: Int, steps: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clearAndSetSemantics { contentDescription = "Step ${step + 1} of $steps" },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(width = (steps * 16 - 8).dp, height = 8.dp)) {
            repeat(steps) { i ->
                inkDot(
                    Offset((i * 16 + 4).dp.toPx(), 4.dp.toPx()),
                    4.dp.toPx(),
                    if (i == step) IronvellumColors.Emerald else IronvellumColors.Rune,
                )
            }
        }
        Text(
            "${step + 1} of $steps",
            style = MaterialTheme.typography.labelMedium,
            color = Dim,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/** A step page: the indicator when there are several steps, the content centred, the dock below. */
@Composable
private fun StepPage(
    step: Int,
    steps: Int,
    dock: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        if (steps > 1) StepIndicator(step, steps)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                content()
            }
        }
        dock()
    }
}

// ---------------------------------------------------------------- level up

/**
 * The level-up page: the bar fills and runs out, the number rolls, the bar
 * refills. Gold is only what was earned: the ring, the XP and the new number.
 */
@Composable
private fun LevelUpPage(
    result: Repository.CompletionResult,
    step: Int,
    steps: Int,
    canSkip: Boolean,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    val motion = animatorsOn(LocalContext.current)
    val t = rememberClock(LEVEL_END, motion)
    Haptics(
        motion,
        500L to HapticFeedbackType.SegmentTick,
        CROSS_AT to HapticFeedbackType.Confirm,
    )
    val start = remember(result) { Xp.progress(result.totalXp - result.xpAwarded) }
    val end = remember(result) { Xp.progress(result.totalXp) }
    val toLevelEnd = (start.needed - start.intoLevel).toInt()
    val x = xpCounted(result.xpAwarded, toLevelEnd, t)
    // The bar and its figure: filling the old level, then the new one.
    val into: Float
    val needed: Long
    if (x <= toLevelEnd) {
        into = start.intoLevel + x
        needed = start.needed
    } else {
        into = end.intoLevel * ((x - toLevelEnd) / (result.xpAwarded - toLevelEnd).coerceAtLeast(1))
        needed = end.needed
    }
    val tier = result.classAfter.takeIf { it != result.classBefore }?.let { " · $it" }.orEmpty()

    StepPage(
        step = step,
        steps = steps,
        dock = {
            CelebrationDock(
                primary = "Continue",
                onPrimary = onContinue,
                link = "Skip to summary".takeIf { canSkip },
                linkColor = Dim,
                onLink = onSkip,
            )
        },
    ) {
        Text(
            "Level up",
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            letterSpacing = 0.5.sp,
            color = IronvellumColors.SovereignGold,
            modifier = Modifier.reveal(t >= 100, motion),
        )
        Spacer(Modifier.height(18.dp))
        Box(
            Modifier
                .size(210.dp)
                .graphicsLayer {
                    val s = stamp(t, 550)
                    scaleX = s
                    scaleY = s
                }
                .clearAndSetSemantics { contentDescription = "Level ${result.levelAfter}" },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val centre = Offset(size.width / 2f, size.height / 2f)
                val radius = size.width * 84f / 180f
                inkArc(centre, radius, -90f, 360f * phase(t, 0, 600, FastOutSlowInEasing::transform), IronvellumColors.SovereignGold, 2.dp.toPx())
                // The pulse as the old level runs out: one ring leaving the seal.
                val p = phase(t, CROSS_AT, 900) { 1f - (1f - it) * (1f - it) }
                if (t >= CROSS_AT && p < 1f) {
                    inkArc(centre, radius * (1f + 0.7f * p), -90f, 360f, IronvellumColors.SovereignGold.copy(alpha = 0.5f * (1f - p)), 1.5.dp.toPx())
                }
            }
            LevelRoll(result.levelBefore, result.levelAfter, phase(t, CROSS_AT, ROLL_MS, RollEasing::transform))
        }
        Spacer(Modifier.height(34.dp))
        Column(Modifier.fillMaxWidth().reveal(t >= 500, motion)) {
            Text(
                "+${count(x.toInt())} XP",
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                style = TextStyle(fontFeatureSettings = "tnum"),
                color = IronvellumColors.SovereignGold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "${result.xpAwarded} XP earned" },
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Level ${result.levelBefore} to ${result.levelAfter}$tier",
                    style = MaterialTheme.typography.labelMedium,
                    color = Dim,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${count(into.toLong())} / ${count(needed)} XP",
                    style = MaterialTheme.typography.labelMedium,
                    color = Dim,
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).background(IronvellumColors.Rune)) {
                Box(
                    Modifier
                        .fillMaxWidth((into / needed).coerceIn(0f, 1f))
                        .height(6.dp)
                        .background(IronvellumColors.Emerald),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "${count(end.needed - end.intoLevel)} XP to level ${result.levelAfter + 1}",
            style = MaterialTheme.typography.labelMedium,
            color = Dim,
            modifier = Modifier.reveal(t >= CROSS_AT, motion),
        )
    }
}

/** The level as a two-line roll: the old number slides up and the new one, in gold, replaces it. */
@Composable
private fun LevelRoll(from: Int, to: Int, roll: Float) {
    val line = 130.sp
    val lineDp = with(androidx.compose.ui.platform.LocalDensity.current) { line.toDp() }
    val style = TextStyle(
        fontFamily = ChakraPetch,
        fontWeight = FontWeight.Bold,
        fontSize = 104.sp,
        lineHeight = line,
        textAlign = TextAlign.Center,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        fontFeatureSettings = "tnum",
    )
    Box(Modifier.height(lineDp).clipToBounds()) {
        Column(
            Modifier
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .graphicsLayer { translationY = -roll * lineDp.toPx() },
        ) {
            Text("$from", style = style, color = IronvellumColors.Ink, modifier = Modifier.height(lineDp).defaultMinSize(minWidth = 73.dp))
            Text("$to", style = style, color = IronvellumColors.SovereignGold, modifier = Modifier.height(lineDp).defaultMinSize(minWidth = 73.dp))
        }
    }
}

// ------------------------------------------------------------------- deeds

private const val DEEDS_END = 1_800L

/**
 * One page for every deed the trial earned, several sharing it as a list.
 * Wear title lives in the dock and reads "Wearing ✓" once the title is on.
 */
@Composable
private fun DeedsPage(
    deeds: List<TitleDef>,
    sex: Sex,
    step: Int,
    steps: Int,
    wornTitleId: String?,
    onContinue: () -> Unit,
    onWear: (String) -> Unit,
) {
    val motion = animatorsOn(LocalContext.current)
    val t = rememberClock(DEEDS_END, motion)
    val masterwork = deeds.any { it.rarity == TitleRarity.Masterwork }
    Haptics(motion, 550L to if (masterwork) HapticFeedbackType.LongPress else HapticFeedbackType.Confirm)
    val target = remember(deeds) { CelebrationFlow.wearTarget(deeds) }
    var wornHere by remember(deeds) { mutableStateOf<String?>(null) }
    val worn = target != null && (wornHere == target.id || wornTitleId == target.id)

    StepPage(
        step = step,
        steps = steps,
        dock = {
            CelebrationDock(
                primary = "Continue",
                onPrimary = onContinue,
                link = if (target == null) null else if (worn) "Wearing ✓" else "Wear title",
                linkOn = !worn,
                linkColor = if (worn) Dim else IronvellumColors.SystemGreen,
                onLink = {
                    if (target != null) {
                        onWear(target.id)
                        wornHere = target.id
                    }
                },
            )
        },
    ) {
        Text(
            if (deeds.size == 1) "Deed earned" else "Deeds earned",
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            letterSpacing = 0.5.sp,
            color = IronvellumColors.SovereignGold,
            modifier = Modifier.reveal(t >= 100, motion),
        )
        Spacer(Modifier.height(20.dp))
        val seal = if (deeds.size == 1) 156.dp else 104.dp
        Canvas(
            Modifier
                .size(seal)
                .graphicsLayer {
                    val s = stamp(t, 550)
                    scaleX = s
                    scaleY = s
                },
        ) {
            val centre = Offset(size.width / 2f, size.height / 2f)
            val unit = size.width / 132f
            val gold = IronvellumColors.SovereignGold
            inkArc(centre, 58f * unit, -90f, 360f * phase(t, 0, 600, FastOutSlowInEasing::transform), gold, 2.dp.toPx())
            inkArc(centre, 48f * unit, -90f, 360f, gold.copy(alpha = 0.35f), 1.dp.toPx())
            // The diamond is drawn edge by edge once the ring has closed.
            val corners = listOf(Offset(66f, 38f), Offset(82f, 66f), Offset(66f, 94f), Offset(50f, 66f), Offset(66f, 38f))
            val drawn = 4f * phase(t, 450, 400, FastOutSlowInEasing::transform)
            for (i in 0 until 4) {
                val k = (drawn - i).coerceIn(0f, 1f)
                if (k <= 0f) continue
                val a = corners[i]
                val b = corners[i + 1]
                val tip = Offset(a.x + (b.x - a.x) * k, a.y + (b.y - a.y) * k)
                inkStroke(Offset(a.x * unit, a.y * unit), Offset(tip.x * unit, tip.y * unit), gold, 2.dp.toPx())
            }
            val p = phase(t, 750, 900) { 1f - (1f - it) * (1f - it) }
            if (t >= 750 && p < 1f) {
                inkArc(centre, 58f * unit * (1f + 0.7f * p), -90f, 360f, gold.copy(alpha = 0.5f * (1f - p)), 1.5.dp.toPx())
            }
        }
        Spacer(Modifier.height(26.dp))
        if (deeds.size == 1) {
            val deed = deeds.single()
            Text(
                deed.name,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                textAlign = TextAlign.Center,
                color = IronvellumColors.Ink,
                modifier = Modifier.reveal(t >= 650, motion),
            )
            Spacer(Modifier.height(6.dp))
            DeedSubline(deed, sex, Modifier.reveal(t >= 800, motion))
        } else {
            Column(Modifier.fillMaxWidth()) {
                deeds.forEachIndexed { i, deed ->
                    Column(Modifier.fillMaxWidth().reveal(t >= 650 + 150L * i, motion)) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(IronvellumColors.Rune))
                        Column(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 10.dp), verticalArrangement = Arrangement.Center) {
                            Text(
                                deed.name,
                                fontFamily = ChakraPetch,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                                color = IronvellumColors.Ink,
                            )
                            DeedSubline(deed, sex, Modifier.padding(top = 2.dp), center = false)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(IronvellumColors.Rune))
            }
        }
    }
}

/** "Rare · Hang 10 kg from the bar": the rarity is earned, so it is gold. */
@Composable
private fun DeedSubline(deed: TitleDef, sex: Sex, modifier: Modifier, center: Boolean = true) {
    Row(modifier, horizontalArrangement = if (center) Arrangement.Center else Arrangement.Start) {
        Text(deed.rarity.label, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.SovereignGold)
        Text(
            " · ${deed.describeFor(sex)}",
            style = MaterialTheme.typography.bodySmall,
            color = Dim,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

// ----------------------------------------------------------------- summary

private const val SUMMARY_END = 1_900L

/**
 * What the trial paid: the seal closing, the best new peak as the hero, then
 * level, XP and deeds, and the totals. Done leaves; the routine question, when
 * there is one, follows it. Within ten minutes of sealing a quiet line above
 * the dock takes the trial back.
 */
@Composable
private fun SummaryPage(
    result: Repository.CompletionResult,
    title: String,
    peaks: List<SessionPeaks.Peak>,
    totals: WorkoutShare.Totals,
    deeds: List<TitleDef>,
    reopenUntilMs: Long?,
    onDone: () -> Unit,
    onShare: () -> Unit,
    onReopen: () -> Unit,
) {
    val motion = animatorsOn(LocalContext.current)
    val t = rememberClock(SUMMARY_END, motion)
    Haptics(
        motion,
        *buildList {
            add(550L to HapticFeedbackType.Confirm)
            if (peaks.isNotEmpty()) add(650L to HapticFeedbackType.SegmentTick)
        }.toTypedArray(),
    )
    // The offer ends by itself: the repository checks the window again on tap.
    var offered by remember(reopenUntilMs) { mutableStateOf(reopenUntilMs != null && System.currentTimeMillis() < reopenUntilMs) }
    LaunchedEffect(reopenUntilMs) {
        if (reopenUntilMs == null) return@LaunchedEffect
        delay((reopenUntilMs - System.currentTimeMillis()).coerceAtLeast(0))
        offered = false
    }
    val best = remember(peaks) { peaks.maxByOrNull { it.deltaScore } }
    val rows = remember(result, deeds) {
        buildList {
            val tier = result.classAfter.takeIf { it != result.classBefore }?.let { " · $it" }.orEmpty()
            add(Triple("Level", if (result.levelAfter > result.levelBefore) "${result.levelBefore} → ${result.levelAfter}$tier" else "${result.levelAfter}", false))
            add(Triple("XP", "+${count(result.xpAwarded)}", true))
            if (result.questBonus) add(Triple("Today's trial", "+${Xp.QUEST_BONUS} bonus", true))
            result.rankUp?.let { add(Triple("Rank up", it, true)) }
            deeds.forEach { add(Triple("Deed", it.name, false)) }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(42.dp))
            Canvas(
                Modifier
                    .size(56.dp)
                    .graphicsLayer {
                        val s = stamp(t, 550)
                        scaleX = s
                        scaleY = s
                    },
            ) {
                val unit = size.width / 80f
                val gold = IronvellumColors.SovereignGold
                inkArc(Offset(size.width / 2f, size.height / 2f), 34f * unit, -90f, 360f * phase(t, 0, 600, FastOutSlowInEasing::transform), gold, 2.dp.toPx())
                val check = listOf(Offset(27f, 41.5f), Offset(36f, 50f), Offset(53f, 31f))
                val drawn = 2f * phase(t, 450, 400, FastOutSlowInEasing::transform)
                for (i in 0 until 2) {
                    val k = (drawn - i).coerceIn(0f, 1f)
                    if (k <= 0f) continue
                    val a = check[i]
                    val b = check[i + 1]
                    inkStroke(
                        Offset(a.x * unit, a.y * unit),
                        Offset((a.x + (b.x - a.x) * k) * unit, (a.y + (b.y - a.y) * k) * unit),
                        gold,
                        2.dp.toPx(),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier.fillMaxWidth().reveal(t >= 400, motion),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    title,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                    color = IronvellumColors.Ink,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Sealed · ${result.durationMinutes} min · ${totals.sets} ${if (totals.sets == 1) "set" else "sets"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Dim,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            if (best != null) {
                Spacer(Modifier.height(28.dp))
                PeaksCard(best, peaks.size, peaks.filter { it !== best }, Modifier.reveal(t >= 650, motion))
            }
            Spacer(Modifier.height(22.dp))
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(IronvellumColors.Rune))
                rows.forEachIndexed { i, (label, value, earned) ->
                    Column(Modifier.fillMaxWidth().reveal(t >= 1_050 + 150L * i, motion)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .semantics(mergeDescendants = true) {},
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label, style = MaterialTheme.typography.bodyMedium, color = Dim)
                            Text(
                                value,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (earned) IronvellumColors.SovereignGold else IronvellumColors.Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(IronvellumColors.Rune))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                totalsLine(totals),
                style = MaterialTheme.typography.labelMedium,
                color = Dim,
                textAlign = TextAlign.Center,
                modifier = Modifier.reveal(t >= 1_050 + 150L * rows.size, motion),
            )
            Spacer(Modifier.height(16.dp))
        }
        if (offered) {
            // Above the dock, not in it: the dock keeps one primary and one link.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable(role = Role.Button, onClick = onReopen),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Sealed too soon? Keep going",
                    style = MaterialTheme.typography.labelMedium,
                    color = Dim,
                )
            }
        }
        CelebrationDock(primary = "Done", onPrimary = onDone, link = "Share trial", onLink = onShare)
    }
}

/** "18 sets · 142 reps · 3,420 kg moved", leaving out what the trial did not do. */
private fun totalsLine(totals: WorkoutShare.Totals): String = buildList {
    add("${count(totals.sets)} ${if (totals.sets == 1) "set" else "sets"}")
    if (totals.reps > 0) add("${count(totals.reps)} reps")
    if (totals.heldSeconds > 0) add("${count(totals.heldSeconds)} s held")
    if (totals.movedKg > 0) add("${count(totals.movedKg)} kg moved")
}.joinToString(" · ")

/** The best peak as the hero, and any others as quiet rows under it. */
@Composable
private fun PeaksCard(best: SessionPeaks.Peak, total: Int, others: List<SessionPeaks.Peak>, modifier: Modifier) {
    InkPanel(modifier.fillMaxWidth()) {
        Text(
            if (total == 1) "New peak" else "New peaks · $total",
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            letterSpacing = 0.3.sp,
            color = IronvellumColors.SovereignGold,
        )
        Text(
            best.exerciseName,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.SemiBold,
            fontSize = 19.sp,
            color = IronvellumColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .clearAndSetSemantics { contentDescription = peakDescription(best) },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                peakFigure(best.figure, best.weightKg, best.isHold),
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                style = TextStyle(fontFeatureSettings = "tnum"),
                color = IronvellumColors.Ink,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                peakGain(best),
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color = IronvellumColors.SovereignGold,
                modifier = Modifier.padding(start = 12.dp, bottom = 4.dp),
            )
        }
        Text(
            "Set ${best.setIndex + 1} · was ${peakFigure(best.was.reps, best.was.weightKg, best.isHold)}",
            style = MaterialTheme.typography.bodySmall,
            color = Dim,
            modifier = Modifier.padding(top = 4.dp),
        )
        others.forEach { peak ->
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(IronvellumColors.Rune))
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clearAndSetSemantics { contentDescription = peakDescription(peak) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    peak.exerciseName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = IronvellumColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    peakFigure(peak.figure, peak.weightKg, peak.isHold),
                    style = MaterialTheme.typography.bodySmall,
                    color = Dim,
                    modifier = Modifier.padding(start = 8.dp),
                )
                Text(
                    peakGain(peak),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = IronvellumColors.SovereignGold,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

private fun peakDescription(peak: SessionPeaks.Peak): String =
    "New peak, ${peak.exerciseName}, set ${peak.setIndex + 1}, " +
        "${peakFigure(peak.figure, peak.weightKg, peak.isHold)}, was ${peakFigure(peak.was.reps, peak.was.weightKg, peak.isHold)}, " +
        "up ${peakGain(peak)}" + if (peak.count > 1) ", ${peak.count} peaks" else ""

/**
 * How far past its record the headline set went, in the unit the lifter reads:
 * kg when only the load moved, seconds for a hold, else reps, else the score.
 */
internal fun peakGain(peak: SessionPeaks.Peak): String {
    val load = (peak.weightKg ?: 0.0) - (peak.was.weightKg ?: 0.0)
    return when {
        peak.isHold && peak.figure > peak.was.reps -> "+${peak.figure - peak.was.reps}s"
        !peak.isHold && peak.weightKg != null && load > 0.001 -> "+" + (if (load == load.toLong().toDouble()) load.toLong().toString() else "%.1f".fmt(load)) + " kg"
        !peak.isHold && peak.figure > peak.was.reps -> "+${peak.figure - peak.was.reps} reps"
        else -> "+" + "%.1f".fmt(peak.deltaScore)
    }
}

/** "6×82.5kg", "10×BW", or a hold's "45s" (with any added load). Same shape as the live badge. */
private fun peakFigure(figure: Int, weightKg: Double?, isHold: Boolean): String =
    if (isHold) {
        "${figure}s" + (weightKg?.takeIf { it > 0.0 }?.let { " +${formatKg(it)}" } ?: "")
    } else {
        "$figure×${formatKg(weightKg)}"
    }
