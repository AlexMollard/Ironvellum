package com.ironvellum.app.ui.titles

import com.ironvellum.app.R
import com.ironvellum.app.data.Lines
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.Loads
import com.ironvellum.app.domain.LoadUnit
import com.ironvellum.app.data.Units
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import com.ironvellum.app.ui.components.PanelLabel
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironvellum.app.data.SkillTrainingEvidence
import com.ironvellum.app.domain.SkillClaimResult
import com.ironvellum.app.domain.SkillPractice
import com.ironvellum.app.ui.components.formatBodyValue
import com.ironvellum.app.ui.components.formatDate
import com.ironvellum.app.ui.components.plural
import com.ironvellum.app.domain.SexHold
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.domain.Skills
import androidx.compose.foundation.layout.ColumnScope
import com.ironvellum.app.ui.components.InfoAction
import com.ironvellum.app.ui.components.InfoChip
import com.ironvellum.app.ui.components.InfoFigure
import com.ironvellum.app.ui.components.InfoFigures
import com.ironvellum.app.ui.components.InfoPage
import com.ironvellum.app.ui.components.InfoProgress
import com.ironvellum.app.ui.components.InfoSheet
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.formFacts
import com.ironvellum.app.ui.components.gearFacts
import com.ironvellum.app.ui.components.muscleFacts
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.components.InkRail
import com.ironvellum.app.ui.theme.Metal
import com.ironvellum.app.ui.theme.emeraldRamp
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.theme.inkHairline
import com.ironvellum.app.ui.theme.IronvellumColors
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableDoubleStateOf

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.ironvellum.app.ui.components.TermChip
import com.ironvellum.app.ui.components.termsIn


/**
 * Tapping a node opens this, never an instant claim: the standard to clear,
 * why it matters, its XP value, and reversible claim controls. The standard and
 * the lifter's best sit in the summary, guidance in the sections, and the claim
 * and refund buttons are pinned in the action bar.
 */
@Composable
fun SkillDetailSheet(
    skill: Skills.SkillDef,
    /** Claimed by the lifter. A passed skill is not: it stands as mastered but was marked by a harder claim. */
    mastered: Boolean,
    unlocked: Boolean,
    entries: List<SkillPractice>,
    /** Marked passed by a harder claim ([impliedBy]); claiming it for real pays nothing more. */
    passed: Boolean = false,
    impliedBy: String? = null,
    /** How many skills claiming this one also marks passed, and the XP the whole claim pays. */
    claimPasses: Int = 0,
    claimXp: Int = skill.xp,
    /** How many skills giving this one back also reverts (they are refunded inside [refundXp]). */
    unclaimReverts: Int = 0,
    /** Best set of this movement from real logged sessions; null when never trained. */
    training: SkillTrainingEvidence? = null,
    /** The published female bar, when this lifter's profile says it applies. */
    sexBar: String? = null,
    /** Latest weigh-in; null when none, which leaves a loaded standard unjudged. */
    bodyweightKg: Double? = null,
    female: Boolean = false,
    /** No sex chosen yet and this standard depends on it: the standard and its progress read [SexHold.TEXT]. */
    sexHeld: Boolean = false,
    /** The XP an unclaim takes back: what the claim actually paid. */
    refundXp: Int = skill.xp,
    /** Every mastered technique, so each prerequisite chip can say whether it is met. */
    masteredSkills: Set<String> = emptySet(),
    onLogPractice: (value: Int, weightKg: Double?) -> Unit,
    onClaim: () -> Unit,
    onUnclaim: () -> Unit,
    onDismiss: () -> Unit,
    /** Opens another technique's detail: a prerequisite chip's target. */
    onOpenSkill: (String) -> Unit = {},
    /** Puts this technique into a rite. */
    onTrain: () -> Unit = {},
) {
    var confirmUnclaim by rememberSaveable { mutableStateOf(false) }
    val practiced = remember(entries) { entries.filterNot { it.claimed } }
    val bestEntry = remember(practiced) { practiced.maxByOrNull { it.value } }
    val recent = remember(entries) { entries.sortedByDescending { it.practicedAtMs } }
    val best = bestEntry?.value ?: 0
    // The standard is judged on the better of practice and real training: a
    // lifter who already hit it in a session must not read "to go".
    val trained = training
    val bestOverall = maxOf(best, trained?.value ?: 0)
    val clearedAtDefault = remember(skill, practiced, trained, bodyweightKg, female) {
        SkillGuidance.cleared(
            skill,
            practiced.map { SkillGuidance.Effort(it.value, it.weightKg) } +
                listOfNotNull(trained?.let { SkillGuidance.Effort(it.value, it.weightKg) }),
            bodyweightKg,
            female,
        )
    }
    val cleared = !sexHeld && clearedAtDefault
    val terms = remember(skill, sexBar) { termsIn("${skill.name} ${sexBar ?: skill.standard} ${skill.why}") }
    // Starts on the last attempt, or zero. Logging stays off until the value
    // is set by hand, so one tap can never record a hold nobody did.
    // Saveable, so a rotation keeps what was typed into the form.
    var attempt by rememberSaveable(skill.name) { mutableIntStateOf(SkillGuidance.defaultAttempt(entries)) }
    var touched by rememberSaveable(skill.name) { mutableStateOf(false) }
    // The last load used, else the load the standard asks for, so a barbell
    // lift starts near its figure instead of 40 taps away from it.
    var load by rememberSaveable(skill.name) {
        mutableDoubleStateOf(
            entries.firstOrNull { it.weightKg != null }?.weightKg
                ?: SkillGuidance.requiredKg(skill, bodyweightKg, female)?.let { roundToPlate(it) }
                ?: 0.0,
        )
    }
    var showLoad by rememberSaveable(skill.name) { mutableStateOf(false) }
    val barLoad = Skills.loadBar(skill.name, female)?.added == false

    val hasEvidence = best > 0 || trained != null
    val standardWord = run {
        val needKg = SkillGuidance.requiredKg(skill, bodyweightKg, female)
        val added = Skills.loadBar(skill.name, female)?.added == true
        when {
            sexHeld -> SexHold.TEXT
            cleared -> UiText.res(R.string.skilldetail_standard_cleared).asString()
            // Reps alone cannot judge a bodyweight-multiple bar.
            !SkillGuidance.judgeable(skill, bodyweightKg, female) -> UiText.res(R.string.skilldetail_check_load).asString()
            bestOverall >= skill.target && needKg != null ->
                UiText.res(R.string.skilldetail_standard_needs, (if (added) "+" else "") + Units.withUnit(needKg)).asString()
            else -> UiText.res(R.string.skilldetail_standard_to_go, SkillGuidance.withUnit(skill.target - bestOverall, skill)).asString()
        }
    }
    val summary: (@Composable ColumnScope.() -> Unit)? = if (mastered || passed) {
        null
    } else if (hasEvidence) {
        {
            InfoProgress(
                fraction = if (cleared) 1f else if (skill.target <= 0) 0f else (bestOverall.toFloat() / skill.target).coerceIn(0f, 1f),
                line = "${SkillGuidance.withUnit(bestOverall, skill)} / ${SkillGuidance.withUnit(skill.target, skill)} · $standardWord",
                caption = listOfNotNull(
                    if (best > 0) UiText.res(R.string.skilldetail_caption_practice_best, SkillGuidance.withUnit(best, skill)).asString() else null,
                    trained?.let { t ->
                        buildString {
                            append(UiText.res(R.string.skilldetail_caption_from_training, SkillGuidance.withUnit(t.value, skill)).asString())
                            val w = t.weightKg
                            if (w != null && w > 0.0) append(" @ ${Units.withUnit(w)}")
                        }
                    },
                ).joinToString(" · "),
                fill = if (cleared) Metal.Fabled.bounds() else emeraldRamp(),
            )
        }
    } else {
        {
            InfoFigures(
                listOf(
                    InfoFigure(
                        UiText.themed(R.string.skilldetail_figure_to_claim, R.string.skilldetail_figure_to_claim__t).asString(),
                        SkillGuidance.withUnit(skill.target, skill),
                    ),
                    InfoFigure(
                        UiText.res(R.string.skilldetail_figure_reward).asString(),
                        "+${skill.xp}",
                        unit = "XP",
                        color = IronvellumColors.SovereignGold,
                    ),
                ),
            )
        }
    }

    // The page builders below are not composable, so what they show is read here.
    val trainLabel = UiText.res(R.string.skilldetail_train_button).asString()
    val aboutLabel = UiText.res(R.string.skilldetail_page_about).asString()
    val logLabel = UiText.res(R.string.skilldetail_page_log).asString()
    val formLabel = UiText.res(R.string.skilldetail_page_form).asString()
    val whyTitle = UiText.res(R.string.skilldetail_why_title).asString()
    val wordsTitle = UiText.res(R.string.skilldetail_words_title).asString()
    val requiresTitle = UiText.res(R.string.skilldetail_requires_title).asString()
    val logTitle = UiText.res(R.string.skilldetail_log_title).asString()
    val attemptsTitle = UiText.res(R.string.skilldetail_attempts_title).asString()
    val beforeTitle = UiText.themed(R.string.skilldetail_before_title, R.string.skilldetail_before_title__t).asString()
    val lockedUntil = UiText.res(R.string.skilldetail_locked_until, skill.firstUnmetPrerequisite(masteredSkills) ?: skill.prerequisites.joinToString(" and ")).asString()
    val passedByLine = UiText.res(R.string.skilldetail_passed_by, impliedBy ?: skill.name).asString()
    val passedHint = UiText.themed(R.string.skilldetail_passed_hint, R.string.skilldetail_passed_hint__t).asString()
    val claimPassesLine = UiText.themedPlural(
        R.plurals.skilldetail_claim_passes, R.plurals.skilldetail_claim_passes__t, claimPasses, claimPasses, claimXp,
    ).asString()
    val attemptTimePattern = UiText.res(R.string.skilldetail_attempt_time_pattern).asString()
    val attemptRows = recent.map { entry ->
        formatDate(entry.practicedAtMs, attemptTimePattern) to when {
            entry.passed -> UiText.res(R.string.skilldetail_chip_passed).asString()
            entry.claimed -> Words.claimed.u
            entry.weightKg != null -> "${SkillGuidance.withUnit(entry.value, skill)} @ ${Units.withUnit(entry.weightKg)}"
            else -> SkillGuidance.withUnit(entry.value, skill)
        }
    }
    InfoSheet(
        title = skill.name,
        // Back steps out of the unclaim question first, as "Keep it" does.
        onDismiss = { if (mastered && confirmUnclaim) confirmUnclaim = false else onDismiss() },
        titleColor = if (mastered) IronvellumColors.SovereignGold else IronvellumColors.Ink,
        chips = buildList {
            add(InfoChip(UiText.res(R.string.skilldetail_chip_tier, Skills.tierLabel(skill.tier)).asString(), IronvellumColors.SystemGreen))
            add(InfoChip(skill.line))
            // A mastered technique shows what the claim paid, which is what giving it back removes.
            if (hasEvidence || mastered || passed) add(InfoChip(UiText.res(R.string.skilldetail_chip_xp, if (mastered) refundXp else skill.xp).asString(), IronvellumColors.SovereignGold))
            if (mastered) add(InfoChip(UiText.res(R.string.skilldetail_chip_mastered).asString(), IronvellumColors.SovereignGold))
            else if (passed) add(InfoChip(UiText.res(R.string.skilldetail_chip_passed).asString()))
            else if (!unlocked) add(InfoChip(UiText.res(R.string.skilldetail_chip_locked).asString(), IronvellumColors.DangerRed))
        },
        // Fixed in the header, so the question stays on screen whichever page is open.
        subtitle = if (mastered && confirmUnclaim) {
            if (unclaimReverts > 0) {
                UiText.themedPlural(
                    R.plurals.skilldetail_unclaim_question_passes, R.plurals.skilldetail_unclaim_question_passes__t,
                    unclaimReverts, refundXp, unclaimReverts,
                ).asString()
            } else {
                UiText.themed(R.string.skilldetail_unclaim_question, R.string.skilldetail_unclaim_question__t, refundXp).asString()
            }
        } else {
            null
        },
        subtitleColor = IronvellumColors.DangerRed,
        actions = when {
            mastered && confirmUnclaim -> listOf(
                InfoAction(UiText.res(R.string.skilldetail_keep_button).asString(), { confirmUnclaim = false }, quiet = true),
                InfoAction(UiText.res(R.string.skilldetail_unclaim_button, refundXp).asString(), onUnclaim, danger = true),
            )
            mastered -> listOf(
                InfoAction(trainLabel, onTrain, quiet = true),
                InfoAction(UiText.res(R.string.skilldetail_give_back_button).asString(), { confirmUnclaim = true }, danger = true),
            )
            passed -> listOf(
                InfoAction(trainLabel, onTrain, quiet = true),
                InfoAction(Words.claim.c, onClaim, quiet = true),
            )
            unlocked -> listOf(
                InfoAction(trainLabel, onTrain, quiet = true),
                InfoAction(Words.claim.c, onClaim, gold = cleared, quiet = !cleared),
            )
            // Locked skills can be claimed too: it passes everything beneath them.
            else -> listOf(
                InfoAction(trainLabel, onTrain, quiet = true),
                InfoAction(Words.claim.c, onClaim, quiet = true),
            )
        },
        pages = listOf(
            InfoPage(aboutLabel, summary) {
                // A female lifter reads her own published bar, not the male default.
                val standard = if (sexHeld) SexHold.TEXT else sexBar ?: skill.standard
                val drawing = techniqueDrawing(skill.name)
                if (drawing != null) {
                    // The drawing in its plate, with the standard and the reason beside it: the art fills
                    // the width the text leaves, instead of a band of its own above the text. A proxy says what it shows.
                    section(null) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            val plateState = when {
                                mastered -> ArtState.MASTERED
                                passed -> ArtState.PASSED
                                unlocked -> ArtState.OPEN
                                else -> ArtState.LOCKED
                            }
                            Column(Modifier.weight(0.42f), horizontalAlignment = Alignment.CenterHorizontally) {
                                TechniqueDrawingPlate(drawing, plateState, Modifier.fillMaxWidth())
                                techniqueCaption(skill.name)?.let {
                                    Spacer(Modifier.height(6.dp))
                                    Text(it, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(0.58f)) {
                                PanelLabel(Lines.claimStandard)
                                Spacer(Modifier.height(8.dp))
                                Text(standard, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.SovereignGold)
                                Spacer(Modifier.height(20.dp))
                                PanelLabel(whyTitle)
                                Spacer(Modifier.height(8.dp))
                                Text(skill.why, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.InkMuted)
                            }
                        }
                    }
                } else {
                    text(Lines.claimStandard, standard, IronvellumColors.SovereignGold)
                    text(whyTitle, skill.why, IronvellumColors.InkMuted)
                }
                // The same figure and gear line the exercise sheet shows.
                muscleFacts(skill.name)
                gearFacts(skill.name)
                if (terms.isNotEmpty()) {
                    // The words a beginner trips on, each one tap from a plain definition.
                    section(wordsTitle) {
                        FlowRow(Modifier.fillMaxWidth()) { terms.forEach { TermChip(it) } }
                    }
                }
                if (skill.prerequisites.isNotEmpty()) {
                    section(requiresTitle) {
                        FlowRow(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            skill.prerequisites.forEach { name ->
                                PrerequisiteChip(name, met = name in masteredSkills) { onOpenSkill(name) }
                            }
                        }
                    }
                }
                if (passed) {
                    text(null, passedByLine, IronvellumColors.InkMuted)
                    text(null, passedHint, IronvellumColors.InkMuted)
                }
                if (!mastered && !passed && !unlocked) {
                    text(null, lockedUntil, IronvellumColors.InkMuted)
                }
                if (!mastered && claimPasses > 0) {
                    text(null, claimPassesLine, IronvellumColors.InkMuted)
                }
            },
            InfoPage(logLabel) {
                if (!mastered && unlocked) {
                    section(logTitle) {
                        AttemptLogger(
                            skill = skill,
                            attempt = attempt,
                            touched = touched,
                            load = load,
                            showLoad = showLoad,
                            barLoad = barLoad,
                            onAttempt = {
                                attempt = it
                                touched = true
                            },
                            onLoad = { load = it },
                            onShowLoad = { showLoad = true },
                            onLog = { onLogPractice(attempt, load.takeIf { it > 0.0 }) },
                        )
                    }
                }
                if (entries.isNotEmpty()) {
                    // Every attempt for this technique, newest first: the sheet folds the
                    // list past six with its own "Show more". The Journal timeline only
                    // holds the last fortnight, so this is the full record.
                    rows(attemptsTitle, attemptRows, collapseAfter = 6)
                }
            },
            InfoPage(formLabel) {
                // How to do it: the same page the exercise info card shows.
                formFacts(skill.name)
                if (!mastered && unlocked) {
                    text(
                        beforeTitle,
                        Lines.claimHelp(cleared, SkillGuidance.withUnit(skill.target, skill), skill.xp),
                        IronvellumColors.InkMuted,
                    )
                }
            },
        ),
    )
}

/** The attempt form: a big readout with ± targets, the quick values, an optional added load and LOG. */
@Composable
private fun AttemptLogger(
    skill: Skills.SkillDef,
    attempt: Int,
    touched: Boolean,
    load: Double,
    showLoad: Boolean,
    barLoad: Boolean,
    onAttempt: (Int) -> Unit,
    onLoad: (Double) -> Unit,
    onShowLoad: () -> Unit,
    onLog: () -> Unit,
) {
    val pct = if (skill.target <= 0) 0f else (attempt.toFloat() / skill.target).coerceIn(0f, 1f)
    // hero readout: one big number, big tap targets either side
    // One shape value for fill and border: two instances could
    // silently diverge, and a drawn edge must trace the same
    // line twice or it reads as a double outline.
    val readoutShape = MaterialTheme.shapes.medium
    Row(
        Modifier
            .fillMaxWidth()
            .background(IronvellumColors.Wells.readout, readoutShape)
            .inkBorder(IronvellumColors.Rune, readoutShape, 1.dp)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepGlyph("−", UiText.res(R.string.skilldetail_step_fewer).asString()) { onAttempt((attempt - stepDown(skill, attempt)).coerceAtLeast(0)) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                attempt.toString(),
                style = MaterialTheme.typography.displaySmall,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = if (attempt >= skill.target) IronvellumColors.SovereignGold else IronvellumColors.Ink,
            )
            Text(
                UiText.res(
                    when (skill.metric) {
                        Skills.Metric.SECONDS -> R.string.skilldetail_metric_seconds
                        Skills.Metric.REPS -> R.string.skilldetail_metric_reps
                        Skills.Metric.METRES -> R.string.skilldetail_metric_metres
                    },
                ).asString(),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = IronvellumColors.InkMuted,
                letterSpacing = 2.sp,
            )
        }
        StepGlyph("+", UiText.res(R.string.skilldetail_step_more).asString()) { onAttempt(attempt + stepUp(skill, attempt)) }
    }

    Spacer(Modifier.height(8.dp))
    // progress against the claim standard
    InkRail(fraction = pct)
    Text(
        SkillGuidance.attemptReadout(skill, attempt, touched),
        style = MaterialTheme.typography.labelSmall,
        color = IronvellumColors.InkMuted,
        modifier = Modifier.padding(top = 4.dp),
    )

    Spacer(Modifier.height(10.dp))
    MiniLabel(UiText.res(R.string.skilldetail_quick_set_label).asString())
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        quickValues(skill).forEach { v ->
            QuickChip(
                label = SkillGuidance.withUnit(v, skill),
                selected = attempt == v,
                modifier = Modifier.weight(1f),
            ) { onAttempt(v) }
        }
    }

    Spacer(Modifier.height(10.dp))
    if (showLoad || load > 0.0) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A barbell lift logs the whole bar; a weighted bodyweight move, what hangs on top.
            MiniLabel(UiText.res(if (barLoad) R.string.skilldetail_load_bar_label else R.string.skilldetail_load_added_label).asString())
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepGlyph("−", UiText.res(R.string.skilldetail_step_less_load).asString()) { onLoad(if (Units.current == LoadUnit.LB) Loads.stepDown(load, Units.current, 5.0, 2.5) ?: 0.0 else (load - 2.5).coerceAtLeast(0.0)) }
                Text(
                    Units.label(load, weighted = barLoad),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.Bold,
                    color = IronvellumColors.Ink,
                )
                StepGlyph("+", UiText.res(R.string.skilldetail_step_more_load).asString()) { onLoad(if (Units.current == LoadUnit.LB) Loads.stepUp(load, Units.current, 5.0, 2.5) else load + 2.5) }
            }
        }
    } else {
        Text(
            UiText.res(if (barLoad) R.string.skilldetail_add_bar_load_button else R.string.skilldetail_add_load_button).asString(),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.SystemGreen,
            letterSpacing = 2.sp,
            modifier = Modifier
                .clickable(role = Role.Button, onClickLabel = UiText.res(R.string.skilldetail_add_load_description).asString()) { onShowLoad() }
                .heightIn(min = 48.dp)
                .wrapContentHeight(),
        )
    }

    // The ADD LOAD link is already a 48dp target with its text centred, so
    // it supplies its own gap; only the stepper row needs one.
    Spacer(Modifier.height(if (showLoad || load > 0.0) 12.dp else 0.dp))
    IronvellumButton(
        UiText.res(R.string.skilldetail_log_button).asString(),
        onClick = onLog,
        // Off until a value is set by hand and is above zero.
        enabled = touched && attempt > 0,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun MiniLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = ChakraPetch,
        color = IronvellumColors.InkMuted,
        letterSpacing = 2.sp,
    )
}

/**
 * Step size follows the metric: reps and metres move in 1; seconds in 1 up to
 * 20 and 5 beyond, so an 8s, 11s or 15s hold can be entered exactly and a
 * minute is still a few taps. Going up from 20 gives 25, going down gives 19.
 */
internal fun stepUp(skill: Skills.SkillDef, from: Int): Int =
    if (skill.metric == Skills.Metric.SECONDS && from >= SECONDS_FINE_LIMIT) 5 else 1

internal fun stepDown(skill: Skills.SkillDef, from: Int): Int =
    if (skill.metric == Skills.Metric.SECONDS && from > SECONDS_FINE_LIMIT) 5 else 1

private const val SECONDS_FINE_LIMIT = 20

/** A load to the nearest 2.5 kg plate step. */
internal fun roundToPlate(kg: Double): Double = Math.round(kg / 2.5) * 2.5

/** Plate-stepped load: whole kilograms drop the ".0"; anything else keeps its decimals. */
internal fun formatLoad(kg: Double): String =
    if (Units.current == LoadUnit.LB) Units.format(kg)
    else if (kg % 1.0 == 0.0) kg.toLong().toString() else formatBodyValue(kg)

/** Preset attempts around the standard, so common values are one tap away. */
private fun quickValues(skill: Skills.SkillDef): List<Int> {
    val t = skill.target.coerceAtLeast(1)
    val raw = when (skill.metric) {
        Skills.Metric.SECONDS -> listOf(t / 4, t / 2, (t * 3) / 4, t, t * 2)
        else -> listOf(1, t / 2, t, t + 2, t * 2)
    }
    return raw.map { it.coerceAtLeast(1) }.distinct().take(4)
}

@Composable
private fun QuickChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = MaterialTheme.shapes.extraSmall
    Box(
        modifier
            .background(
                if (selected) {
                    Brush.verticalGradient(listOf(androidx.compose.ui.graphics.lerp(IronvellumColors.Emerald, IronvellumColors.Vault, 0.6f), androidx.compose.ui.graphics.lerp(IronvellumColors.Emerald, IronvellumColors.Vault, 0.8f)))
                } else {
                    Brush.verticalGradient(listOf(IronvellumColors.Wells.top, IronvellumColors.Wells.bottom))
                },
                shape,
            )
            .inkBorder(if (selected) IronvellumColors.SystemGreen else IronvellumColors.Rune, shape, 1.dp)
            .clickable(role = Role.Button, onClickLabel = UiText.res(R.string.skilldetail_quick_set_description, label).asString()) { onClick() }
            .heightIn(min = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = if (selected) IronvellumColors.Ink else IronvellumColors.InkMuted,
        )
    }
}

/** A prerequisite as a button: tapping it opens that technique's own detail. */
@Composable
private fun PrerequisiteChip(name: String, met: Boolean, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.extraSmall
    val color = if (met) IronvellumColors.SystemGreen else IronvellumColors.DangerRed
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .background(IronvellumColors.Wells.top, shape)
            .inkBorder(if (met) emeraldRamp(0.7f) else SolidColor(color), shape, 1.dp)
            .clickable(role = Role.Button, onClickLabel = UiText.res(R.string.titles_open_description, name).asString()) { onClick() }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (met) Icons.Filled.Check else Icons.Filled.Lock,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Text(
            UiText.res(if (met) R.string.skilldetail_prerequisite_met else R.string.skilldetail_prerequisite_unmet, name).asString(),
            style = MaterialTheme.typography.bodySmall,
            color = color,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** Chunky ± target: 48dp of tappable area, not a text glyph you have to hunt. */
@Composable
private fun StepGlyph(symbol: String, label: String, step: () -> Unit) {
    // Held down, it repeats: a barbell load or a long hold is not 40 separate taps.
    val current by rememberUpdatedState(step)
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .size(48.dp)
            .background(IronvellumColors.Wells.flat, MaterialTheme.shapes.small)
            .inkBorder(IronvellumColors.Rune, MaterialTheme.shapes.small, 1.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        current()
                        val repeat = scope.launch {
                            delay(450)
                            while (true) {
                                current()
                                delay(90)
                            }
                        }
                        tryAwaitRelease()
                        repeat.cancel()
                    },
                )
            }
            // The glyph is announced as a bare character: "−" says nothing
            // about what it steps. The label carries the meaning instead; the
            // press handler above is invisible to TalkBack, so it gets its own click.
            .semantics {
                contentDescription = label
                role = Role.Button
                onClick(label) {
                    current()
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            symbol,
            style = MaterialTheme.typography.titleLarge,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = IronvellumColors.SystemGreen,
        )
    }
}
