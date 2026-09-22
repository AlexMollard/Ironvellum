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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironvellum.app.data.SkillTrainingEvidence
import com.ironvellum.app.domain.SkillClaimResult
import com.ironvellum.app.domain.SkillPractice
import com.ironvellum.app.ui.components.formatDate
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
    onLogPractice: (value: Int, weightKg: Double?) -> Unit,
    onClaim: () -> Unit,
    onUnclaim: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmUnclaim by remember { mutableStateOf(false) }
    val best = entries.filterNot { it.claimed }.maxOfOrNull { it.value } ?: 0
    // The standard is judged on the better of practice and real training: a
    // lifter who already hit it in a session must not read "to go".
    val trained = training
    val bestOverall = maxOf(best, trained?.value ?: 0)
    var attempt by remember(skill.name) { mutableIntStateOf(if (bestOverall > 0) bestOverall else skill.target) }
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
                    "TIER ${Skills.tierLabel(skill.tier)}  ·  ${skill.line.uppercase()}",
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
                if (skill.requires != null) {
                    DetailBlock(
                        "REQUIRES",
                        skill.requires + if (unlocked) "  ·  cleared" else "  ·  not yet mastered",
                        if (unlocked) IronvellumColors.SystemGreen else IronvellumColors.DangerRed,
                    )
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
                        "${entries.count { !it.claimed }} attempts",
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
                                formatDate(entries.filterNot { it.claimed }.maxBy { it.value }.practicedAtMs, "d MMM"),
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
                        Text(
                            if (bestOverall >= skill.target) "standard cleared"
                            else "${skill.target - bestOverall}${skill.unit} to standard",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (bestOverall >= skill.target) IronvellumColors.SovereignGold else IronvellumColors.InkMuted,
                        )
                    }
                }

                if (entries.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "HISTORY",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        color = IronvellumColors.InkMuted,
                        letterSpacing = 2.sp,
                    )
                    entries.sortedByDescending { it.practicedAtMs }.take(6).forEach { entry ->
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
                            style = MaterialTheme.typography.labelSmall,
                            color = IronvellumColors.InkMuted,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                if (mastered) {
                    if (confirmUnclaim) {
                        Text(
                            "Give the title back? The ${skill.xp} XP is removed too.",
                            style = MaterialTheme.typography.bodySmall,
                            color = IronvellumColors.DangerRed,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IronvellumButton("Unclaim", onClick = onUnclaim, modifier = Modifier.fillMaxWidth(0.5f))
                            IronvellumButton("Keep it", onClick = { confirmUnclaim = false })
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
                                .clickable { confirmUnclaim = true }
                                .padding(vertical = 4.dp),
                        )
                    }
                } else if (unlocked) {
                    val pct = if (skill.target <= 0) 0f else (attempt.toFloat() / skill.target).coerceIn(0f, 1f)
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
                        StepGlyph("−", "Fewer") { attempt = (attempt - attemptStep(skill)).coerceAtLeast(0) }
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
                        StepGlyph("+", "More") { attempt += attemptStep(skill) }
                    }

                    Spacer(Modifier.height(8.dp))
                    // progress against the claim standard
                    InkRail(fraction = pct, seed = 41)
                    Text(
                        "${(pct * 100).toInt()}% of the ${skill.target}${skill.unit} standard",
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
                            ) { attempt = v }
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
                                .clickable { showLoad = true }
                                .padding(vertical = 6.dp),
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    IronvellumButton(
                        "Log attempt",
                        onClick = { onLogPractice(attempt, load.takeIf { it > 0.0 }) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(14.dp))
                    // Brushed divider, not a ruled 1dp rectangle.
                    Box(Modifier.fillMaxWidth().height(1.dp).inkHairline(IronvellumColors.Rune, seed = 7, thickness = 1.dp))
                    Spacer(Modifier.height(14.dp))
                    IronvellumButton(
                        "Claim mastery",
                        onClick = onClaim,
                        gold = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Claim only once you can hit ${skill.target}${skill.unit} on demand — it awards ${skill.xp} XP and can be undone.",
                        style = MaterialTheme.typography.labelSmall,
                        color = IronvellumColors.InkMuted,
                    )
                } else {
                    Text(
                        "Locked until ${skill.requires} is mastered.",
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.InkMuted,
                    )
                }
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

private fun formatLoad(kg: Double): String =
    if (kg % 1.0 == 0.0) kg.toLong().toString() else kg.toString()

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
            .clickable { onClick() }
            .padding(vertical = 8.dp),
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

/** Chunky ± target: 44dp of tappable area, not a text glyph you have to hunt. */
@Composable
private fun StepGlyph(symbol: String, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .background(Color(0xFF16201C), MaterialTheme.shapes.small)
            .inkBorder(IronvellumColors.Rune, MaterialTheme.shapes.small, 1.dp)
            .clickable { onClick() }
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
