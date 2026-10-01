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
import androidx.compose.material3.AlertDialog
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
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.components.InkRail
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.theme.inkHairline
import com.ironvellum.app.ui.theme.IronvellumColors
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.Role
import com.ironvellum.app.ui.components.ExerciseFacts
import com.ironvellum.app.ui.components.TermChip
import com.ironvellum.app.ui.components.termsIn


/**
 * Tapping a node opens this, never an instant claim: the standard to clear,
 * why it matters, its XP value, and reversible claim controls.
 */
@Composable
fun SkillDetailDialog(
    skill: Skills.SkillDef,
    mastered: Boolean,
    unlocked: Boolean,
    entries: List<SkillPractice>,
    /** Best set of this movement from real logged sessions; null when never trained. */
    training: SkillTrainingEvidence? = null,
    /** The published female bar, when this lifter's profile says it applies. */
    sexBar: String? = null,
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
    var confirmUnclaim by remember { mutableStateOf(false) }
    val practiced = remember(entries) { entries.filterNot { it.claimed } }
    val bestEntry = remember(practiced) { practiced.maxByOrNull { it.value } }
    val recent = remember(entries) { entries.sortedByDescending { it.practicedAtMs }.take(6) }
    val best = bestEntry?.value ?: 0
    // The standard is judged on the better of practice and real training: a
    // lifter who already hit it in a session must not read "to go".
    val trained = training
    val bestOverall = maxOf(best, trained?.value ?: 0)
    val cleared = remember(skill, practiced, trained) {
        SkillGuidance.cleared(
            skill,
            practiced.map { SkillGuidance.Effort(it.value, it.weightKg) } +
                listOfNotNull(trained?.let { SkillGuidance.Effort(it.value, it.weightKg) }),
        )
    }
    val terms = remember(skill, sexBar) { termsIn("${skill.name} ${sexBar ?: skill.standard} ${skill.why}") }
    // Starts on the last attempt, or zero. Logging stays off until the value
    // is set by hand, so one tap can never record a hold nobody did.
    var attempt by remember(skill.name) { mutableIntStateOf(SkillGuidance.defaultAttempt(entries)) }
    var touched by remember(skill.name) { mutableStateOf(false) }
    var load by remember(skill.name) {
        mutableDoubleStateOf(entries.firstOrNull { it.weightKg != null }?.weightKg ?: 0.0)
    }
    var showLoad by remember(skill.name) { mutableStateOf(false) }

    AlertDialog(
        // Material's dialog container is a 28dp rounded rect - the most
        // obviously stock surface in the app. Give it the ink shape.
        shape = MaterialTheme.shapes.medium,
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0D1110),
        title = {
            Column {
                Text(
                    "TIER ${Skills.tierLabel(skill.tier)} · ${skill.line.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.SystemGreen,
                    letterSpacing = 2.sp,
                )
                Text(
                    skill.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.Bold,
                    color = if (mastered) IronvellumColors.SovereignGold else IronvellumColors.Ink,
                )
            }
        },
        text = {
            // The dialog's text slot is height-capped, and this column is long:
            // standard, why, prerequisite, tally, load stepper, log, claim. Once
            // it overflowed, the LAST children measured at zero height — the
            // "Claim mastery" button rendered 272x0 dp and could not be pressed.
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                // A female lifter reads her own published bar, not the male default.
                DetailBlock("CLAIM STANDARD", sexBar ?: skill.standard, IronvellumColors.SovereignGold)
                DetailBlock("WHY IT MATTERS", skill.why, IronvellumColors.InkMuted)
                if (terms.isNotEmpty()) {
                    // The words a beginner trips on, each one tap from a plain definition.
                    FlowRow(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                        terms.forEach { TermChip(it) }
                    }
                }
                val prerequisites = skill.prerequisites()
                if (prerequisites.isNotEmpty()) {
                    Text(
                        "REQUIRES",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        color = IronvellumColors.InkMuted,
                        letterSpacing = 2.sp,
                    )
                    FlowRow(
                        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        prerequisites.forEach { name ->
                            PrerequisiteChip(name, met = name in masteredSkills) { onOpenSkill(name) }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "REWARD  +${skill.xp} XP",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = ChakraPetch,
                        color = IronvellumColors.SovereignGold,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        entries.count { !it.claimed }.let { n -> "$n ${plural(n, "attempt", "attempts")}" },
                        style = MaterialTheme.typography.labelMedium,
                        color = IronvellumColors.InkMuted,
                    )
                }
                if (best > 0 || trained != null) {
                    Spacer(Modifier.height(6.dp))
                    if (best > 0) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "PRACTICE BEST  $best${skill.unit}",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = ChakraPetch,
                                color = IronvellumColors.SystemGreen,
                                letterSpacing = 1.sp,
                            )
                            Text(
                                bestEntry?.let { formatDate(it.practicedAtMs, "d MMM") }.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = IronvellumColors.InkMuted,
                            )
                        }
                    }
                    if (trained != null) {
                        // Clearly sourced: this came out of her logged sessions,
                        // not an honour-system practice entry.
                        Row(
                            Modifier.fillMaxWidth().padding(top = if (best > 0) 3.dp else 0.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                buildString {
                                    append("FROM TRAINING  ${trained.value}${skill.unit}")
                                    val w = trained.weightKg
                                    if (w != null && w > 0.0) append(" @ ${formatLoad(w)}kg")
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = ChakraPetch,
                                color = IronvellumColors.SystemGreen,
                                letterSpacing = 1.sp,
                            )
                            Text(
                                formatDate(trained.achievedAtMs, "d MMM"),
                                style = MaterialTheme.typography.labelSmall,
                                color = IronvellumColors.InkMuted,
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "STANDARD",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = ChakraPetch,
                            color = IronvellumColors.InkMuted,
                            letterSpacing = 1.sp,
                        )
                        val needKg = SkillGuidance.requiredAddedKg(skill)
                        Text(
                            when {
                                cleared -> "standard cleared"
                                // Reps alone cannot judge a bodyweight-multiple bar.
                                !SkillGuidance.judgeable(skill) -> "check the load yourself"
                                bestOverall >= skill.target && needKg != null -> "needs +${formatLoad(needKg)}kg"
                                else -> "${SkillGuidance.withUnit(skill.target - bestOverall, skill)} to standard"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (cleared) IronvellumColors.SovereignGold else IronvellumColors.InkMuted,
                        )
                    }
                }

                if (entries.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "RECENT ATTEMPTS",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        color = IronvellumColors.InkMuted,
                        letterSpacing = 2.sp,
                    )
                    recent.forEach { entry ->
                        Row(
                            Modifier.fillMaxWidth().padding(top = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                formatDate(entry.practicedAtMs, "EEE d MMM · HH:mm"),
                                style = MaterialTheme.typography.labelSmall,
                                color = IronvellumColors.Ink,
                            )
                            Text(
                                when {
                                    entry.claimed -> "CLAIMED"
                                    entry.weightKg != null ->
                                        "${entry.value}${skill.unit} @ ${formatLoad(entry.weightKg)}kg"
                                    else -> "${entry.value}${skill.unit}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = ChakraPetch,
                                color = if (entry.claimed) IronvellumColors.SovereignGold else IronvellumColors.SystemGreen,
                            )
                        }
                    }
                    if (entries.size > 6) {
                        Text(
                            "+${entries.size - 6} earlier — full list in JOURNAL",
                            style = MaterialTheme.typography.bodySmall,
                            color = IronvellumColors.InkMuted,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                if (mastered) {
                    if (confirmUnclaim) {
                        Text(
                            "Give the technique back? The ${skill.xp} XP is removed too.",
                            style = MaterialTheme.typography.bodySmall,
                            color = IronvellumColors.DangerRed,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IronvellumButton("Unclaim", onClick = onUnclaim, modifier = Modifier.fillMaxWidth(0.5f))
                            IronvellumButton("Keep it", onClick = { confirmUnclaim = false }, quiet = true)
                        }
                    } else {
                        Text(
                            "MASTERED",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = ChakraPetch,
                            color = IronvellumColors.SovereignGold,
                            letterSpacing = 3.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Claimed by mistake?",
                            style = MaterialTheme.typography.labelSmall,
                            color = IronvellumColors.InkMuted,
                            modifier = Modifier
                                .clickable(role = Role.Button, onClickLabel = "Give the technique back") { confirmUnclaim = true }
                                .heightIn(min = 48.dp)
                                .wrapContentHeight(),
                        )
                    }
                } else if (unlocked) {
                    val pct = if (skill.target <= 0) 0f else (attempt.toFloat() / skill.target).coerceIn(0f, 1f)
                    val setAttempt = { v: Int ->
                        attempt = v
                        touched = true
                    }
                    Text(
                        "LOG AN ATTEMPT",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        color = IronvellumColors.InkMuted,
                        letterSpacing = 2.sp,
                    )
                    Spacer(Modifier.height(8.dp))

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
                        StepGlyph("−", "Fewer") { setAttempt((attempt - attemptStep(skill)).coerceAtLeast(0)) }
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
                        StepGlyph("+", "More") { setAttempt(attempt + attemptStep(skill)) }
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
                    Text(
                        "QUICK SET",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        color = IronvellumColors.InkMuted,
                        letterSpacing = 2.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        quickValues(skill).forEach { v ->
                            QuickChip(
                                label = "$v${skill.unit}",
                                selected = attempt == v,
                                modifier = Modifier.weight(1f),
                            ) { setAttempt(v) }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    if (showLoad || load > 0.0) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "ADDED LOAD",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = ChakraPetch,
                                color = IronvellumColors.InkMuted,
                                letterSpacing = 2.sp,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StepGlyph("−", "Less added load") { load = (load - 2.5).coerceAtLeast(0.0) }
                                Text(
                                    if (load <= 0.0) "BW" else "${formatLoad(load)}kg",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = ChakraPetch,
                                    fontWeight = FontWeight.Bold,
                                    color = IronvellumColors.Ink,
                                )
                                StepGlyph("+", "More added load") { load += 2.5 }
                            }
                        }
                    } else {
                        Text(
                            "+ ADD LOAD",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = ChakraPetch,
                            color = IronvellumColors.SystemGreen,
                            letterSpacing = 2.sp,
                            modifier = Modifier
                                .clickable(role = Role.Button, onClickLabel = "Add a load") { showLoad = true }
                                .heightIn(min = 48.dp)
                                .wrapContentHeight(),
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    IronvellumButton(
                        "Log attempt",
                        onClick = { onLogPractice(attempt, load.takeIf { it > 0.0 }) },
                        // Off until a value is set by hand and is above zero.
                        enabled = touched && attempt > 0,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(14.dp))
                    // Brushed divider, not a ruled 1dp rectangle.
                    Box(Modifier.fillMaxWidth().height(1.dp).inkHairline(IronvellumColors.Rune, seed = 7, thickness = 1.dp))
                    Spacer(Modifier.height(14.dp))
                    if (cleared) {
                        // The logged evidence meets the standard: say so, and
                        // make the claim the obvious next move.
                        Text(
                            "You've cleared this — claim it",
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = ChakraPetch,
                            color = IronvellumColors.SovereignGold,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    IronvellumButton(
                        "Claim mastery",
                        onClick = onClaim,
                        gold = cleared,
                        quiet = !cleared,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    if (!cleared) {
                        Text(
                            "Claiming is self-declared: the app never claims for you.",
                            style = MaterialTheme.typography.bodySmall,
                            color = IronvellumColors.Ink,
                        )
                    }
                    Text(
                        "Claim only once you can hit ${SkillGuidance.withUnit(skill.target, skill)} on demand — it awards ${skill.xp} XP and can be undone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.InkMuted,
                    )
                } else {
                    Text(
                        "Locked until ${skill.firstUnmetPrerequisite(masteredSkills) ?: skill.prerequisites().joinToString(" and ")} is mastered.",
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.InkMuted,
                    )
                }

                Spacer(Modifier.height(12.dp))
                IronvellumButton(
                    "Train it",
                    onClick = onTrain,
                    quiet = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Adds it to a rite, so it comes up in your sessions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier.padding(top = 4.dp),
                )

                // How to do it, what it works and what it needs: the same
                // facts the exercise info card shows.
                ExerciseFacts(skill.name)
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun DetailBlock(label: String, body: String, color: Color) {
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            letterSpacing = 2.sp,
        )
        Text(body, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}


/** Step size follows the metric: seconds move in 5s, reps and metres in 1. */
private fun attemptStep(skill: Skills.SkillDef): Int =
    if (skill.metric == Skills.Metric.SECONDS) 5 else 1

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
            "$name  ·  ${if (met) "cleared" else "not yet mastered"}",
            style = MaterialTheme.typography.bodySmall,
            color = color,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** Chunky ± target: 48dp of tappable area, not a text glyph you have to hunt. */
@Composable
private fun StepGlyph(symbol: String, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .background(Color(0xFF16201C), MaterialTheme.shapes.small)
            .inkBorder(IronvellumColors.Rune, MaterialTheme.shapes.small, 1.dp)
            .clickable(role = Role.Button) { onClick() }
            // The glyph is announced as a bare character: "−" says nothing
            // about what it steps. The label carries the meaning instead.
            .semantics { contentDescription = label },
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
