package com.monarch.app.ui.titles

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CutCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monarch.app.domain.SkillClaimResult
import com.monarch.app.domain.SkillPractice
import com.monarch.app.ui.components.formatDate
import com.monarch.app.domain.Skills
import com.monarch.app.ui.components.MonarchButton
import com.monarch.app.ui.theme.ChakraPetch
import com.monarch.app.ui.theme.MonarchColors
import kotlin.math.sin
import kotlin.random.Random

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
    onLogPractice: (value: Int, weightKg: Double?) -> Unit,
    onClaim: () -> Unit,
    onUnclaim: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmUnclaim by remember { mutableStateOf(false) }
    val best = entries.filterNot { it.claimed }.maxOfOrNull { it.value } ?: 0
    var attempt by remember(skill.name) { mutableStateOf(if (best > 0) best else skill.target) }
    var load by remember(skill.name) {
        mutableStateOf(entries.firstOrNull { it.weightKg != null }?.weightKg ?: 0.0)
    }
    var showLoad by remember(skill.name) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0D1110),
        title = {
            Column {
                Text(
                    "TIER ${Skills.tierLabel(skill.tier)}  ·  ${skill.line.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = MonarchColors.SystemGreen,
                    letterSpacing = 2.sp,
                )
                Text(
                    skill.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.Bold,
                    color = if (mastered) MonarchColors.SovereignGold else MonarchColors.Ink,
                )
            }
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                DetailBlock("CLAIM STANDARD", skill.standard, MonarchColors.SovereignGold)
                DetailBlock("WHY IT MATTERS", skill.why, MonarchColors.InkMuted)
                if (skill.requires != null) {
                    DetailBlock(
                        "REQUIRES",
                        skill.requires + if (unlocked) "  ·  cleared" else "  ·  not yet mastered",
                        if (unlocked) MonarchColors.SystemGreen else MonarchColors.DangerRed,
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
                        color = MonarchColors.SovereignGold,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        "${entries.count { !it.claimed }} attempts",
                        style = MaterialTheme.typography.labelMedium,
                        color = MonarchColors.InkMuted,
                    )
                }
                if (best > 0) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "BEST  $best${skill.unit}",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = ChakraPetch,
                            color = if (best >= skill.target) MonarchColors.SovereignGold else MonarchColors.SystemGreen,
                            letterSpacing = 1.sp,
                        )
                        Text(
                            if (best >= skill.target) "standard cleared"
                            else "${skill.target - best}${skill.unit} to standard",
                            style = MaterialTheme.typography.labelMedium,
                            color = MonarchColors.InkMuted,
                        )
                    }
                }

                if (entries.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "HISTORY",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        color = MonarchColors.InkMuted,
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
                                color = MonarchColors.Ink,
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
                                color = if (entry.claimed) MonarchColors.SovereignGold else MonarchColors.SystemGreen,
                            )
                        }
                    }
                    if (entries.size > 6) {
                        Text(
                            "+${entries.size - 6} earlier — full list in JOURNAL",
                            style = MaterialTheme.typography.labelSmall,
                            color = MonarchColors.InkMuted,
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
                            color = MonarchColors.DangerRed,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MonarchButton("Unclaim", onClick = onUnclaim, modifier = Modifier.fillMaxWidth(0.5f))
                            MonarchButton("Keep it", onClick = { confirmUnclaim = false })
                        }
                    } else {
                        Text(
                            "MASTERED",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = ChakraPetch,
                            color = MonarchColors.SovereignGold,
                            letterSpacing = 3.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Claimed by mistake?",
                            style = MaterialTheme.typography.labelSmall,
                            color = MonarchColors.InkMuted,
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
                        color = MonarchColors.InkMuted,
                        letterSpacing = 2.sp,
                    )
                    Spacer(Modifier.height(8.dp))

                    // hero readout: one big number, big tap targets either side
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF101614), CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
                            .border(1.dp, MonarchColors.Rune, CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StepGlyph("−") { attempt = (attempt - attemptStep(skill)).coerceAtLeast(0) }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                attempt.toString(),
                                style = MaterialTheme.typography.displaySmall,
                                fontFamily = ChakraPetch,
                                fontWeight = FontWeight.Bold,
                                color = if (attempt >= skill.target) MonarchColors.SovereignGold else MonarchColors.Ink,
                            )
                            Text(
                                when (skill.metric) {
                                    Skills.Metric.SECONDS -> "SECONDS HELD"
                                    Skills.Metric.REPS -> "REPS DONE"
                                    Skills.Metric.METRES -> "METRES"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = ChakraPetch,
                                color = MonarchColors.InkMuted,
                                letterSpacing = 2.sp,
                            )
                        }
                        StepGlyph("+") { attempt += attemptStep(skill) }
                    }

                    Spacer(Modifier.height(8.dp))
                    // progress against the claim standard
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(Color(0xFF1E2A24))
                            .border(1.dp, MonarchColors.Rune),
                    ) {
                        if (pct > 0f) {
                            Box(
                                Modifier
                                    .fillMaxWidth(pct)
                                    .height(6.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(MonarchColors.SystemGreen, MonarchColors.SovereignGold),
                                        ),
                                    ),
                            )
                        }
                    }
                    Text(
                        "${(pct * 100).toInt()}% of the ${skill.target}${skill.unit} standard",
                        style = MaterialTheme.typography.labelSmall,
                        color = MonarchColors.InkMuted,
                        modifier = Modifier.padding(top = 4.dp),
                    )

                    Spacer(Modifier.height(10.dp))
                    Text(
                        "QUICK SET",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        color = MonarchColors.InkMuted,
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
                                color = MonarchColors.InkMuted,
                                letterSpacing = 2.sp,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StepGlyph("−") { load = (load - 2.5).coerceAtLeast(0.0) }
                                Text(
                                    if (load <= 0.0) "BW" else "${formatLoad(load)}kg",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = ChakraPetch,
                                    fontWeight = FontWeight.Bold,
                                    color = MonarchColors.Ink,
                                )
                                StepGlyph("+") { load += 2.5 }
                            }
                        }
                    } else {
                        Text(
                            "+ ADD LOAD",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = ChakraPetch,
                            color = MonarchColors.SystemGreen,
                            letterSpacing = 2.sp,
                            modifier = Modifier
                                .clickable { showLoad = true }
                                .padding(vertical = 6.dp),
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    MonarchButton(
                        "Log attempt",
                        onClick = { onLogPractice(attempt, load.takeIf { it > 0.0 }) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(MonarchColors.Rune))
                    Spacer(Modifier.height(14.dp))
                    MonarchButton(
                        "Claim mastery",
                        onClick = onClaim,
                        gold = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Claim only once you can hit ${skill.target}${skill.unit} on demand — it awards ${skill.xp} XP and can be undone.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MonarchColors.InkMuted,
                    )
                } else {
                    Text(
                        "Locked until ${skill.requires} is mastered.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MonarchColors.InkMuted,
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
            color = MonarchColors.InkMuted,
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
    val shape = CutCornerShape(topStart = 5.dp, bottomEnd = 5.dp)
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
            .border(1.dp, if (selected) MonarchColors.SystemGreen else MonarchColors.Rune, shape)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = if (selected) MonarchColors.Ink else MonarchColors.InkMuted,
        )
    }
}

/** Chunky ± target: 44dp of tappable area, not a text glyph you have to hunt. */
@Composable
private fun StepGlyph(symbol: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .background(Color(0xFF16201C), CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp))
            .border(1.dp, MonarchColors.Rune, CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            symbol,
            style = MaterialTheme.typography.titleLarge,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = MonarchColors.SystemGreen,
        )
    }
}
