package com.ironvellum.app.ui.titles

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
import com.ironvellum.app.domain.Skills
import androidx.compose.foundation.layout.ColumnScope
import com.ironvellum.app.ui.components.InfoAction
import com.ironvellum.app.ui.components.InfoChip
import com.ironvellum.app.ui.components.InfoFigure
import com.ironvellum.app.ui.components.InfoFigures
import com.ironvellum.app.ui.components.InfoProgress
import com.ironvellum.app.ui.components.InfoSheet
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.exerciseFacts
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.components.InkRail
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
    mastered: Boolean,
    unlocked: Boolean,
    entries: List<SkillPractice>,
    /** Best set of this movement from real logged sessions; null when never trained. */
    training: SkillTrainingEvidence? = null,
    /** The published female bar, when this lifter's profile says it applies. */
    sexBar: String? = null,
    /** Latest weigh-in; null when none, which leaves a loaded standard unjudged. */
    bodyweightKg: Double? = null,
    female: Boolean = false,
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
    val cleared = remember(skill, practiced, trained, bodyweightKg, female) {
        SkillGuidance.cleared(
            skill,
            practiced.map { SkillGuidance.Effort(it.value, it.weightKg) } +
                listOfNotNull(trained?.let { SkillGuidance.Effort(it.value, it.weightKg) }),
            bodyweightKg,
            female,
        )
    }
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
            cleared -> "standard cleared"
            // Reps alone cannot judge a bodyweight-multiple bar.
            !SkillGuidance.judgeable(skill, bodyweightKg, female) -> "check the load yourself"
            bestOverall >= skill.target && needKg != null -> "needs ${if (added) "+" else ""}${formatLoad(needKg)}kg"
            else -> "${SkillGuidance.withUnit(skill.target - bestOverall, skill)} to standard"
        }
    }
    val summary: (@Composable ColumnScope.() -> Unit)? = if (mastered) {
        null
    } else if (hasEvidence) {
        {
            InfoProgress(
                fraction = if (cleared) 1f else if (skill.target <= 0) 0f else (bestOverall.toFloat() / skill.target).coerceIn(0f, 1f),
                line = "${SkillGuidance.withUnit(bestOverall, skill)} / ${SkillGuidance.withUnit(skill.target, skill)} · $standardWord",
                caption = listOfNotNull(
                    if (best > 0) "Practice best ${SkillGuidance.withUnit(best, skill)}" else null,
                    trained?.let { t ->
                        buildString {
                            append("from training ${SkillGuidance.withUnit(t.value, skill)}")
                            val w = t.weightKg
                            if (w != null && w > 0.0) append(" @ ${formatLoad(w)}kg")
                        }
                    },
                ).joinToString(" · "),
                fill = Brush.horizontalGradient(
                    listOf(if (cleared) IronvellumColors.SovereignGold else IronvellumColors.SystemGreen, if (cleared) IronvellumColors.SovereignGold else IronvellumColors.Emerald),
                ),
                seed = 41,
            )
        }
    } else {
        {
            InfoFigures(
                listOf(
                    InfoFigure("TO CLAIM", SkillGuidance.withUnit(skill.target, skill)),
                    InfoFigure("REWARD", "+${skill.xp}", unit = "XP", color = IronvellumColors.SovereignGold),
                ),
            )
        }
    }

    InfoSheet(
        title = skill.name,
        onDismiss = onDismiss,
        titleColor = if (mastered) IronvellumColors.SovereignGold else IronvellumColors.Ink,
        chips = buildList {
            add(InfoChip("Tier ${Skills.tierLabel(skill.tier)}", IronvellumColors.SystemGreen))
            add(InfoChip(skill.line))
            // A mastered technique shows what the claim paid, which is what giving it back removes.
            if (hasEvidence || mastered) add(InfoChip("+${if (mastered) refundXp else skill.xp} XP", IronvellumColors.SovereignGold))
            if (mastered) add(InfoChip("Mastered", IronvellumColors.SovereignGold))
            else if (!unlocked) add(InfoChip("Locked", IronvellumColors.DangerRed))
        },
        summary = summary,
        actions = when {
            mastered && confirmUnclaim -> listOf(
                InfoAction("Keep it", { confirmUnclaim = false }, quiet = true),
                InfoAction("Unclaim −$refundXp XP", onUnclaim, danger = true),
            )
            mastered -> listOf(
                InfoAction("Train it", onTrain, quiet = true),
                InfoAction("Give back", { confirmUnclaim = true }, danger = true),
            )
            unlocked -> listOf(
                InfoAction("Train it", onTrain, quiet = true),
                InfoAction("Claim", onClaim, gold = cleared, quiet = !cleared),
            )
            else -> listOf(InfoAction("Train it", onTrain, quiet = true))
        },
    ) {
        if (mastered && confirmUnclaim) {
            text(null, "Give the technique back? The $refundXp XP is removed too.", IronvellumColors.DangerRed)
        }
        // A female lifter reads her own published bar, not the male default.
        text("CLAIM STANDARD", sexBar ?: skill.standard, IronvellumColors.SovereignGold)
        text("WHY IT MATTERS", skill.why, IronvellumColors.InkMuted)
        if (terms.isNotEmpty()) {
            // The words a beginner trips on, each one tap from a plain definition.
            section("WORDS TO KNOW") {
                FlowRow(Modifier.fillMaxWidth()) { terms.forEach { TermChip(it) } }
            }
        }
        if (skill.prerequisites.isNotEmpty()) {
            section("REQUIRES") {
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
        if (!mastered && unlocked) {
            section("LOG AN ATTEMPT") {
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
            text(
                "BEFORE YOU CLAIM",
                (if (cleared) "You've cleared this: claim it when you're ready. " else "Claiming is self-declared: the app never claims for you. ") +
                    "Claim only once you can hit ${SkillGuidance.withUnit(skill.target, skill)} on demand. It awards ${skill.xp} XP and can be undone.",
                IronvellumColors.InkMuted,
            )
        } else if (!mastered) {
            text(
                null,
                "Locked until ${skill.firstUnmetPrerequisite(masteredSkills) ?: skill.prerequisites.joinToString(" and ")} is mastered.",
                IronvellumColors.InkMuted,
            )
        }
        if (entries.isNotEmpty()) {
            // Every attempt for this technique, newest first: the sheet folds the
            // list past six with its own "Show more". The Journal timeline only
            // holds the last fortnight, so this is the full record.
            rows(
                "ATTEMPTS",
                recent.map { entry ->
                    formatDate(entry.practicedAtMs, "EEE d MMM · HH:mm") to when {
                        entry.claimed -> "CLAIMED"
                        entry.weightKg != null -> "${SkillGuidance.withUnit(entry.value, skill)} @ ${formatLoad(entry.weightKg)}kg"
                        else -> SkillGuidance.withUnit(entry.value, skill)
                    }
                },
                collapseAfter = 6,
            )
        }
        // How to do it, what it works and what it needs: the same
        // facts the exercise info card shows.
        exerciseFacts(skill.name)
    }
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
    val readoutShape = MaterialTheme.shapes.small
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF101614), readoutShape)
            .inkBorder(IronvellumColors.Rune, readoutShape, 1.dp)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepGlyph("−", "Fewer") { onAttempt((attempt - stepDown(skill, attempt)).coerceAtLeast(0)) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                attempt.toString(),
                style = MaterialTheme.typography.displaySmall,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = if (attempt >= skill.target) IronvellumColors.SovereignGold else IronvellumColors.Ink,
            )
            Text(
                when (skill.metric) {
                    Skills.Metric.SECONDS -> "SECONDS HELD"
                    Skills.Metric.REPS -> "REPS DONE"
                    Skills.Metric.METRES -> "METRES"
                },
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = IronvellumColors.InkMuted,
                letterSpacing = 2.sp,
            )
        }
        StepGlyph("+", "More") { onAttempt(attempt + stepUp(skill, attempt)) }
    }

    Spacer(Modifier.height(8.dp))
    // progress against the claim standard
    InkRail(fraction = pct, seed = 41)
    Text(
        SkillGuidance.attemptReadout(skill, attempt, touched),
        style = MaterialTheme.typography.labelSmall,
        color = IronvellumColors.InkMuted,
        modifier = Modifier.padding(top = 4.dp),
    )

    Spacer(Modifier.height(10.dp))
    MiniLabel("QUICK SET")
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
            MiniLabel(if (barLoad) "BAR LOAD" else "ADDED LOAD")
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepGlyph("−", "Less added load") { onLoad((load - 2.5).coerceAtLeast(0.0)) }
                Text(
                    if (load <= 0.0) (if (barLoad) "0kg" else "BW") else "${formatLoad(load)}kg",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.Bold,
                    color = IronvellumColors.Ink,
                )
                StepGlyph("+", "More added load") { onLoad(load + 2.5) }
            }
        }
    } else {
        Text(
            if (barLoad) "+ ADD BAR LOAD" else "+ ADD LOAD",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.SystemGreen,
            letterSpacing = 2.sp,
            modifier = Modifier
                .clickable(role = Role.Button, onClickLabel = "Add a load") { onShowLoad() }
                .heightIn(min = 48.dp)
                .wrapContentHeight(),
        )
    }

    // The ADD LOAD link is already a 48dp target with its text centred, so
    // it supplies its own gap; only the stepper row needs one.
    Spacer(Modifier.height(if (showLoad || load > 0.0) 12.dp else 0.dp))
    IronvellumButton(
        "Log attempt",
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
    if (kg % 1.0 == 0.0) kg.toLong().toString() else formatBodyValue(kg)

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
                    Brush.verticalGradient(listOf(Color(0xFF2C7A5A), Color(0xFF1B4D3A)))
                } else {
                    Brush.verticalGradient(listOf(Color(0xFF151C19), Color(0xFF0F1412)))
                },
                shape,
            )
            .inkBorder(if (selected) IronvellumColors.SystemGreen else IronvellumColors.Rune, shape, 1.dp)
            .clickable(role = Role.Button, onClickLabel = "Set $label") { onClick() }
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
            .background(Color(0xFF151C19), shape)
            .inkBorder(color, shape, 1.dp)
            .clickable(role = Role.Button, onClickLabel = "Open $name") { onClick() }
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
            "$name  ·  ${if (met) "mastered" else "not yet mastered"}",
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
            .background(Color(0xFF16201C), MaterialTheme.shapes.small)
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
