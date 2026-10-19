package com.ironvellum.app.ui.train

import com.ironvellum.app.data.Units
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ironvellum.app.R
import com.ironvellum.app.data.Repository
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.RepMax
import com.ironvellum.app.domain.SessionPeaks
import com.ironvellum.app.domain.Sex
import com.ironvellum.app.domain.TitleDef
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.domain.WorkoutShare
import com.ironvellum.app.domain.Xp
import com.ironvellum.app.domain.fmt
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.CelebrationDock
import com.ironvellum.app.ui.components.DeedsPage
import com.ironvellum.app.ui.components.Dim
import com.ironvellum.app.ui.components.Haptics
import com.ironvellum.app.ui.components.XpRevealPage
import com.ironvellum.app.ui.components.XpRevealPanel
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.LevelUp
import com.ironvellum.app.ui.components.LevelUpPage
import com.ironvellum.app.ui.components.animatorsOn
import com.ironvellum.app.ui.components.count
import com.ironvellum.app.ui.components.phase
import com.ironvellum.app.ui.components.rememberClock
import com.ironvellum.app.ui.components.reveal
import com.ironvellum.app.ui.components.reveal
import com.ironvellum.app.ui.components.stamp
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.METAL_WASH
import com.ironvellum.app.ui.theme.Metal
import com.ironvellum.app.ui.theme.inkArc
import com.ironvellum.app.ui.theme.inkStroke
import kotlinx.coroutines.delay

/**
 * What a seal earned, and the order it is told in: the XP page when the trial
 * earned any (its level crossing is the level-up, so a trial that has the XP page
 * never gets a separate level-up page), else a level-up page if it levelled, one
 * deeds page if it earned any, then the summary, always. Only the steps that exist
 * are visited, and the numbered ones counted ("1 of 2"), so a quiet trial goes
 * straight to the summary.
 */
internal object CelebrationFlow {

    fun hasLevelUp(result: Repository.CompletionResult): Boolean = result.levelAfter > result.levelBefore

    /** The full-screen XP page: only when the seal paid something to tell, segment by segment. */
    fun hasXpPage(result: Repository.CompletionResult): Boolean = result.breakdown.lines.isNotEmpty()

    /** Deeds with a name to show: a nameless one cannot be celebrated or worn. */
    fun deedsOf(result: Repository.CompletionResult): List<TitleDef> = result.newTitles.filter { it.name.isNotBlank() }

    /** The first stage at or after [from] that has something to show. */
    fun resolve(from: SessionViewModel.Finish, hasXp: Boolean, hasLevel: Boolean, hasDeeds: Boolean): SessionViewModel.Finish {
        var stage = from
        if (stage == SessionViewModel.Finish.XP && !hasXp) stage = SessionViewModel.Finish.LEVEL
        // The XP page already played the level-up; the page of its own is for a seal without one.
        if (stage == SessionViewModel.Finish.LEVEL && !(hasLevel && !hasXp)) stage = SessionViewModel.Finish.DEEDS
        if (stage == SessionViewModel.Finish.DEEDS && !hasDeeds) stage = SessionViewModel.Finish.SUMMARY
        return stage
    }

    /** The stage after [from]; the summary is the last of the celebration, then the routine question. */
    fun after(from: SessionViewModel.Finish, hasXp: Boolean, hasLevel: Boolean, hasDeeds: Boolean): SessionViewModel.Finish {
        val next = SessionViewModel.Finish.entries.getOrNull(from.ordinal + 1) ?: return from
        return resolve(next, hasXp, hasLevel, hasDeeds)
    }

    /** The numbered steps before the summary; the XP page is not one of them. */
    fun steps(hasXp: Boolean, hasLevel: Boolean, hasDeeds: Boolean): List<SessionViewModel.Finish> = buildList {
        if (hasLevel && !hasXp) add(SessionViewModel.Finish.LEVEL)
        if (hasDeeds) add(SessionViewModel.Finish.DEEDS)
    }

    /**
     * The deed Wear title puts on: the rarest, first in catalogue order on a
     * tie, the same pick the seal makes when nothing is worn yet.
     */
    fun wearTarget(deeds: List<TitleDef>): TitleDef? = com.ironvellum.app.ui.components.wearTarget(deeds)
}

/**
 * The level-up page's quiet line: what the level banked, from the inscriptions actually paid
 * ([Repository.CompletionResult.inscriptionsBanked]). Null when none were, so no line is drawn.
 */
internal fun inscriptionNote(banked: Int): String? =
    banked.takeIf { it > 0 }?.let { Words.text(UiText.plural(R.plurals.always_victory_inscription_note, it)) }

/** The level-up page's view of a sealed trial. */
private fun Repository.CompletionResult.toLevelUp() =
    LevelUp(levelBefore, levelAfter, classBefore, classAfter, totalXp, xpAwarded)

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
    /** False when the lifter has animations off: the XP page then opens finished. */
    motion: Boolean = animatorsOn(LocalContext.current),
) {
    val hasXp = CelebrationFlow.hasXpPage(result)
    val hasLevel = CelebrationFlow.hasLevelUp(result)
    val deeds = CelebrationFlow.deedsOf(result)
    val steps = CelebrationFlow.steps(hasXp, hasLevel, deeds.isNotEmpty())
    Dialog(
        onDismissRequest = onContinue,
        // Edge to edge, so the page's glow runs up under the status bar instead of stopping at a black band.
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(IronvellumColors.Abyss),
        ) {
            when (stage) {
                SessionViewModel.Finish.XP -> XpRevealPage(
                    breakdown = result.breakdown,
                    totalXp = result.totalXp,
                    levelBefore = result.levelBefore,
                    levelAfter = result.levelAfter,
                    tierAfter = result.classAfter.takeIf { it != result.classBefore },
                    inscriptionsBanked = result.inscriptionsBanked,
                    caption = UiText.themed(R.string.victory_xp_caption, R.string.victory_xp_caption__t, title).asString(),
                    onContinue = onContinue,
                    onSkip = onSkipToSummary,
                    motion = motion,
                )
                SessionViewModel.Finish.LEVEL -> LevelUpPage(
                    levelUp = result.toLevelUp(),
                    step = steps.indexOf(stage),
                    steps = steps.size,
                    // Skip exists only because a summary-bound step follows.
                    canSkip = deeds.isNotEmpty(),
                    onContinue = onContinue,
                    onSkip = onSkipToSummary,
                    note = inscriptionNote(result.inscriptionsBanked),
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


// ----------------------------------------------------------------- summary

private const val SUMMARY_END = 1_900L

/** When the XP reveal starts on the summary's clock: after the seal, the title and the peak card. */
private const val XP_REVEAL_AT = 1_050L

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
    // The XP page has already played the reveal; the panel here is its finished state, then the rest lands.
    val segments = result.breakdown.lines.size
    val rowsAt = XP_REVEAL_AT
    val t = rememberClock(maxOf(SUMMARY_END, rowsAt + 900L), motion)
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
    val best = remember(peaks) { peaks.maxWithOrNull(compareBy({ it.xp }, { it.weightKg ?: 0.0 }, { it.figure })) }
    val rows = remember(result, deeds) {
        buildList {
            val tier = result.classAfter.takeIf { it != result.classBefore }?.let { " · $it" }.orEmpty()
            // With the XP page the level-up was told once, there; here it is only where the lifter stands.
            val crossed = result.levelAfter > result.levelBefore && segments == 0
            add(Triple(UiText.res(R.string.victory_row_level), if (crossed) "${result.levelBefore} → ${result.levelAfter}$tier" else "${result.levelAfter}$tier", false))
            // With a breakdown the reveal panel says what earned the XP, quest bonus included.
            if (result.breakdown.lines.isEmpty()) {
                add(Triple(UiText.res(R.string.victory_row_xp), "+${count(result.xpAwarded)}", true))
                if (result.questBonus) {
                    add(
                        Triple(
                            UiText.themed(R.string.victory_row_quest, R.string.victory_row_quest__t),
                            Words.text(UiText.res(R.string.victory_row_quest_bonus, Xp.QUEST_BONUS)),
                            true,
                        ),
                    )
                }
            }
            result.rankUp?.let { add(Triple(UiText.res(R.string.victory_row_rank_up), com.ironvellum.app.domain.Rank.label(it), true)) }
            deeds.forEach { add(Triple(UiText.res(R.string.always_deed_c), it.name, false)) }
        }
    }

    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
      Box(Modifier.weight(1f).fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
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
                // The seal is one flat gold gradient: a faint wash inside, the ring and the check in line.
                val gold = Metal.Fabled.bounds()
                val ring = phase(t, 0, 600, FastOutSlowInEasing::transform)
                drawOval(gold, Offset(6f * unit, 6f * unit), Size(68f * unit, 68f * unit), alpha = METAL_WASH * ring, style = Fill)
                inkArc(Offset(size.width / 2f, size.height / 2f), 34f * unit, -90f, 360f * ring, gold, 2.dp.toPx())
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
                    UiText.themedPlural(
                        R.plurals.victory_summary,
                        R.plurals.victory_summary__t,
                        totals.sets,
                        result.durationMinutes,
                        totals.sets,
                    ).asString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Dim,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            if (best != null) {
                Spacer(Modifier.height(28.dp))
                PeaksCard(best, peaks.size, peaks.filter { it !== best }, Modifier.reveal(t >= 650, motion))
            }
            if (segments > 0) {
                Spacer(Modifier.height(22.dp))
                XpRevealPanel(
                    breakdown = result.breakdown,
                    totalXp = result.totalXp,
                    levelBefore = result.levelBefore,
                    levelAfter = result.levelAfter,
                    t = t,
                    startMs = XP_REVEAL_AT,
                    modifier = Modifier.reveal(t >= XP_REVEAL_AT, motion),
                    final = true,
                )
            }
            Spacer(Modifier.height(22.dp))
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(IronvellumColors.Rune))
                rows.forEachIndexed { i, (label, value, earned) ->
                    Column(Modifier.fillMaxWidth().reveal(t >= rowsAt + 150L * i, motion)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .semantics(mergeDescendants = true) {},
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label.asString(), style = MaterialTheme.typography.bodyMedium, color = Dim)
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
                modifier = Modifier.reveal(t >= rowsAt + 150L * rows.size, motion),
            )
            Spacer(Modifier.height(16.dp))
        }
        // More below: a fade at the foot says so, in place of a card cut off with no bottom edge.
        if (scroll.canScrollForward) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, IronvellumColors.Abyss))),
            )
        }
      }
        if (offered) {
            // Above the dock, not in it: the dock keeps one primary and one link.
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClick = onReopen),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    UiText.themed(R.string.victory_reopen_offer, R.string.victory_reopen_offer__t).asString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = IronvellumColors.Ink,
                    textDecoration = TextDecoration.Underline,
                    // Wraps inside the margins at a large text size instead of running edge to edge.
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        CelebrationDock(
            primary = stringResource(R.string.common_done),
            onPrimary = onDone,
            link = UiText.themed(R.string.victory_share_button, R.string.victory_share_button__t).asString(),
            onLink = onShare,
        )
    }
}

/** "18 sets · 142 reps · 3,420 kg moved", leaving out what the trial did not do. */
private fun totalsLine(totals: WorkoutShare.Totals): String = buildList {
    add(Words.text(UiText.plural(R.plurals.victory_totals_sets, totals.sets, count(totals.sets))))
    if (totals.reps > 0) add(Words.text(UiText.plural(R.plurals.victory_totals_reps, totals.reps, count(totals.reps))))
    if (totals.heldSeconds > 0) add(Words.text(UiText.res(R.string.victory_totals_held, count(totals.heldSeconds))))
    if (totals.movedKg > 0) {
        add(Words.text(UiText.res(R.string.victory_totals_moved, count(Units.whole(totals.movedKg.toDouble()).toInt()), Units.name)))
    }
}.joinToString(" · ")

/** The best peak as the hero, and any others as quiet rows under it. */
@Composable
private fun PeaksCard(best: SessionPeaks.Peak, total: Int, others: List<SessionPeaks.Peak>, modifier: Modifier) {
    // The best peak is the one earned thing here, so the card wears the gold plate.
    InkPanel(modifier.fillMaxWidth(), metal = Metal.Fabled) {
        Text(
            if (total == 1) {
                UiText.themed(R.string.victory_peaks_title, R.string.victory_peaks_title__t).asString()
            } else {
                UiText.themed(R.string.victory_peaks_many, R.string.victory_peaks_many__t, total).asString()
            },
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            letterSpacing = 0.3.sp,
            color = IronvellumColors.SovereignGold,
        )
        Text(
            best.label,
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
            UiText.res(
                R.string.victory_peak_detail,
                best.setIndex + 1,
                best.kind.label,
                peakFigure(best.was.figure, best.was.weightKg, best.isHold),
                peakPay(best),
            ).asString(),
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
                    peak.label,
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

private fun peakDescription(peak: SessionPeaks.Peak): String {
    val args = arrayOf<Any>(
        peak.kind.label,
        peak.label,
        peak.setIndex + 1,
        peakFigure(peak.figure, peak.weightKg, peak.isHold),
        peakFigure(peak.was.figure, peak.was.weightKg, peak.isHold),
        peakGain(peak),
        peakPay(peak),
    )
    return Words.text(
        if (peak.count > 1) {
            UiText.themed(R.string.victory_peak_description_count, R.string.victory_peak_description_count__t, *args, peak.count)
        } else {
            UiText.themed(R.string.victory_peak_description, R.string.victory_peak_description__t, *args)
        },
    )
}

/** What the lift's peak pays, said honestly: a jump too small to matter pays nothing but is still a peak. */
internal fun peakPay(peak: SessionPeaks.Peak): String =
    Words.text(if (peak.xp > 0) UiText.res(R.string.session_xp_gain, peak.xp) else UiText.res(R.string.victory_peak_no_xp))

/**
 * How far past the earlier best the headline set went, in the unit the lifter reads: kg for a heavier
 * peak, else seconds for a hold, else reps.
 */
internal fun peakGain(peak: SessionPeaks.Peak): String {
    val load = (peak.weightKg ?: 0.0) - peak.was.weightKg
    return when {
        peak.kind == RepMax.Kind.HEAVIER && load > 0.001 -> "+" + Units.withUnit(load)
        peak.isHold && peak.figure > peak.was.figure -> "+${peak.figure - peak.was.figure}s"
        peak.figure > peak.was.figure -> Words.text(UiText.plural(R.plurals.victory_peak_gain_reps, peak.figure - peak.was.figure))
        else -> "+" + Units.withUnit(load.coerceAtLeast(0.0))
    }
}

/** "6 × 82.5kg", "10 × BW", or a hold's "45s" (with any added load). Same shape as the live badge. */
private fun peakFigure(figure: Int, weightKg: Double?, isHold: Boolean): String =
    if (isHold) {
        "${figure}s" + (weightKg?.takeIf { it > 0.0 }?.let { " +${formatKg(it)}" } ?: "")
    } else {
        "$figure × ${formatKg(weightKg)}"
    }
