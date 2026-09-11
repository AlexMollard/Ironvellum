package com.monarch.app.ui.titles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monarch.app.domain.TitleDef
import com.monarch.app.domain.Titles
import com.monarch.app.ui.components.SectionHeader
import com.monarch.app.ui.components.SystemWindow
import com.monarch.app.ui.components.formatDate
import com.monarch.app.ui.theme.ChakraPetch
import com.monarch.app.ui.theme.MonarchColors

/**
 * The deeds half of the codex: what you wear, what you are closest to earning,
 * a showcase of what you hold, and the rest grouped by the kind of deed it
 * demands — every locked entry showing exactly how far off it is.
 */
@Composable
fun DeedsBoard(
    unlocked: Map<String, Long>,
    equippedId: String?,
    ledger: Titles.Ledger,
    onEquip: (String) -> Unit,
) {
    val earned = Titles.ALL.filter { it.id in unlocked }
    val locked = Titles.ALL.filter { it.id !in unlocked }
    val equipped = equippedId?.let { Titles.byId(it) }

    SectionHeader("Worn Title")
    SystemWindow(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                equipped?.name?.uppercase() ?: "NO TITLE WORN",
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = if (equipped != null) MonarchColors.SovereignGold else MonarchColors.InkMuted,
            )
            Text(
                equipped?.description ?: "Earn a deed below, then wear it.",
                style = MaterialTheme.typography.bodySmall,
                color = MonarchColors.InkMuted,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CodexStat("HELD", "${earned.size}/${Titles.ALL.size}")
                CodexStat("CAMPAIGNS", ledger.workouts.toString())
                CodexStat("SETS", ledger.sets.toString())
                CodexStat("REPS", ledger.reps.toString())
            }
        }
    }

    // closest unearned deed — the thing worth chasing today
    val next = locked
        .map { it to Titles.progress(it.rule, ledger) }
        .sortedByDescending { it.second.fraction }
        .firstOrNull()

    if (next != null) {
        val (def, progress) = next
        SectionHeader("Closest Deed")
        SystemWindow(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        def.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = ChakraPetch,
                        color = MonarchColors.Ink,
                    )
                    Text(
                        "${(progress.fraction * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = ChakraPetch,
                        color = MonarchColors.SystemGreen,
                    )
                }
                Text(
                    def.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MonarchColors.InkMuted,
                )
                Spacer(Modifier.height(8.dp))
                ProgressTrack(progress.fraction, tall = true)
                Spacer(Modifier.height(6.dp))
                Text(
                    "${progress.current} / ${progress.target} · ${progress.remaining} ${progress.unit} to go",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = ChakraPetch,
                    color = MonarchColors.SovereignGold,
                )
            }
        }
    }

    if (earned.isNotEmpty()) {
        SectionHeader("Claimed")
        earned.chunked(2).forEach { pair ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pair.forEach { def ->
                    val worn = def.id == equippedId
                    Box(Modifier.weight(1f)) {
                        SealCard(
                            def = def,
                            unlockedAtMs = unlocked[def.id],
                            worn = worn,
                            onClick = { onEquip(def.id) },
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }

    val byCategory = locked.groupBy { Titles.category(it.rule) }
    byCategory.forEach { (category, defs) ->
        SectionHeader(category)
        defs.sortedBy { Titles.progress(it.rule, ledger).target }.forEach { def ->
            val progress = Titles.progress(def.rule, ledger)
            SystemWindow(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.width(0.dp).weight(1f)) {
                            Text(
                                def.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MonarchColors.Ink,
                            )
                            Text(
                                def.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MonarchColors.InkMuted,
                                maxLines = 1,
                            )
                        }
                        Text(
                            "${progress.current}/${progress.target}",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = ChakraPetch,
                            color = if (progress.fraction > 0f) MonarchColors.SystemGreen else MonarchColors.InkMuted,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    ProgressTrack(progress.fraction, tall = false)
                }
            }
        }
    }
}

@Composable
private fun SealCard(def: TitleDef, unlockedAtMs: Long?, worn: Boolean, onClick: () -> Unit) {
    val shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    if (worn) listOf(Color(0xFF5A3F0C), Color(0xFF1E1606))
                    else listOf(Color(0xFF1C2119), Color(0xFF10140F)),
                ),
                shape,
            )
            .border(if (worn) 2.dp else 1.dp, MonarchColors.SovereignGold, shape)
            .clickable { onClick() }
            .padding(10.dp),
    ) {
        Text(
            def.name,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = MonarchColors.SovereignGold,
            maxLines = 2,
        )
        unlockedAtMs?.let {
            Text(
                formatDate(it, "d MMM yyyy"),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = MonarchColors.InkMuted,
            )
        }
        Text(
            if (worn) "WORN" else "TAP TO WEAR",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            fontSize = 9.sp,
            color = if (worn) MonarchColors.SovereignGold else MonarchColors.SystemGreen,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun ProgressTrack(fraction: Float, tall: Boolean) {
    val h = if (tall) 10.dp else 6.dp
    Box(
        Modifier
            .fillMaxWidth()
            .height(h)
            .background(Color(0xFF1E2A24))
            .border(1.dp, MonarchColors.Rune),
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(h)
                    .background(
                        Brush.horizontalGradient(
                            listOf(MonarchColors.SystemGreen, MonarchColors.SovereignGold),
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun CodexStat(label: String, value: String) {
    Column {
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = MonarchColors.Ink,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            fontSize = 9.sp,
            color = MonarchColors.InkMuted,
            letterSpacing = 1.sp,
        )
    }
}
