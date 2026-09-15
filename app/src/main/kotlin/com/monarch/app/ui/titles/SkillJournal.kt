package com.monarch.app.ui.titles

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monarch.app.R
import com.monarch.app.domain.SkillPractice
import com.monarch.app.domain.Skills
import com.monarch.app.ui.components.SectionHeader
import com.monarch.app.ui.components.SystemWindow
import com.monarch.app.ui.components.formatDate
import com.monarch.app.ui.theme.ChakraPetch
import com.monarch.app.ui.theme.MonarchColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val WEEKS = 12

/**
 * Lines get a typographic monogram, not clip-art: the sourced icon set had no
 * honest match for "Lever" or "Mobility", and near-miss pictograms read worse
 * than a consistent two-letter seal.
 */
private fun lineMonogram(line: String): String = when (line) {
    "Pull" -> "PU"
    "Push" -> "PS"
    "Handstand" -> "HS"
    "Lever" -> "LV"
    "Planche" -> "PL"
    "Rings" -> "RG"
    "Movement" -> "MV"
    "Legs" -> "LG"
    "Core" -> "CR"
    "Mobility" -> "MB"
    else -> line.take(2).uppercase()
}

/**
 * The practice record as a dashboard, not a list: a training heatmap, per-line
 * emblem cards with progress, a podium of the most-drilled techniques, and a
 * railed timeline of every logged attempt.
 */
@Composable
fun SkillJournal(
    log: List<SkillPractice>,
    claimed: Set<String>,
    practiceCounts: Map<String, Int>,
    onOpenLine: (String) -> Unit,
    onSelect: (String) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val attempts = log.filterNot { it.claimed }
    val byDay = attempts.groupingBy {
        Instant.ofEpochMilli(it.practicedAtMs).atZone(zone).toLocalDate()
    }.eachCount()
    val today = LocalDate.now()

    // ---- headline counters -------------------------------------------------
    SectionHeader("Practice Record")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("ATTEMPTS", attempts.size.toString(), Modifier.weight(1f))
        StatTile("MASTERED", "${claimed.size}", Modifier.weight(1f), gold = claimed.isNotEmpty())
        StatTile("STREAK", "${practiceStreak(byDay, today)}d", Modifier.weight(1f))
        StatTile(
            "LAST",
            attempts.firstOrNull()?.let { formatDate(it.practicedAtMs, "d MMM") } ?: "—",
            Modifier.weight(1f),
        )
    }

    // ---- training heatmap --------------------------------------------------
    SectionHeader("Last 12 Weeks")
    SystemWindow(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            val start = today.minusWeeks((WEEKS - 1).toLong())
                .with(java.time.DayOfWeek.MONDAY)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                repeat(WEEKS) { w ->
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        repeat(7) { d ->
                            val date = start.plusDays((w * 7 + d).toLong())
                            val count = byDay[date] ?: 0
                            val future = date.isAfter(today)
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(12.dp)
                                    .background(
                                        when {
                                            future -> Color(0xFF0D110F)
                                            count >= 3 -> MonarchColors.SovereignGold
                                            count == 2 -> MonarchColors.Emerald
                                            count == 1 -> MonarchColors.SystemGreen.copy(alpha = 0.65f)
                                            else -> Color(0xFF18211D)
                                        },
                                    )
                                    .border(
                                        1.dp,
                                        if (date == today) MonarchColors.SovereignGold else Color.Transparent,
                                    ),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeatKey(Color(0xFF18211D), "none")
                HeatKey(MonarchColors.SystemGreen.copy(alpha = 0.65f), "1")
                HeatKey(MonarchColors.Emerald, "2")
                HeatKey(MonarchColors.SovereignGold, "3+")
            }
        }
    }

    // ---- per-line emblem cards --------------------------------------------
    SectionHeader("Progression Lines")
    Skills.LINES.chunked(2).forEach { pair ->
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            pair.forEach { line ->
                val lineSkills = Skills.ALL.filter { it.line == line }
                val done = lineSkills.count { it.name in claimed }
                val tries = attempts.count { Skills.forName(it.skillName)?.line == line }
                LineCard(
                    line = line,
                    done = done,
                    total = lineSkills.size,
                    attempts = tries,
                    modifier = Modifier.weight(1f),
                    onClick = { onOpenLine(line) },
                )
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }

    // ---- podium ------------------------------------------------------------
    if (practiceCounts.isNotEmpty()) {
        SectionHeader("Most Drilled")
        practiceCounts.entries.sortedByDescending { it.value }.take(3)
            .forEachIndexed { index, (name, count) ->
                val def = Skills.forName(name)
                val best = attempts.filter { it.skillName == name }.maxOfOrNull { it.value } ?: 0
                val rowShape = MaterialTheme.shapes.small
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF17211C), Color(0xFF0E1311)),
                            ),
                            rowShape,
                        )
                        .border(
                            1.dp,
                            if (index == 0) MonarchColors.SovereignGold else MonarchColors.Rune,
                            rowShape,
                        )
                        .clickable { onSelect(name) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "#${index + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        color = if (index == 0) MonarchColors.SovereignGold else MonarchColors.InkMuted,
                        modifier = Modifier.width(38.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.bodyMedium, color = MonarchColors.Ink)
                        Text(
                            "$count attempts · best $best${def?.unit ?: ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MonarchColors.InkMuted,
                        )
                    }
                    def?.let {
                        MonogramBadge(lineMonogram(it.line), 26.dp)
                    }
                }
            }
    }

    // ---- timeline ----------------------------------------------------------
    SectionHeader("Timeline")
    if (log.isEmpty()) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.art_empty_skills),
                contentDescription = null,
                modifier = Modifier.size(96.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "No attempts yet. Open a technique and log what you actually hit.",
                style = MaterialTheme.typography.bodySmall,
                color = MonarchColors.InkMuted,
            )
        }
        return
    }

    log.groupBy { Instant.ofEpochMilli(it.practicedAtMs).atZone(zone).toLocalDate() }
        .toSortedMap(compareByDescending { it })
        .forEach { (date, entries) ->
            Text(
                when (date) {
                    today -> "TODAY"
                    today.minusDays(1) -> "YESTERDAY"
                    else -> date.toString().uppercase()
                },
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = MonarchColors.SystemGreen,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
            )
            entries.forEachIndexed { i, entry ->
                val def = Skills.forName(entry.skillName)
                Row(
                    Modifier.fillMaxWidth().clickable { onSelect(entry.skillName) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // timeline rail
                    Box(Modifier.width(18.dp).height(54.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .width(1.5.dp)
                                .height(if (i == entries.lastIndex) 27.dp else 54.dp)
                                .align(if (i == entries.lastIndex) Alignment.TopCenter else Alignment.Center)
                                .background(MonarchColors.Rune),
                        )
                        Box(
                            Modifier
                                .size(9.dp)
                                .background(
                                    if (entry.claimed) MonarchColors.SovereignGold else MonarchColors.SystemGreen,
                                    MaterialTheme.shapes.extraSmall,
                                ),
                        )
                    }
                    Row(
                        Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp)
                            .background(
                                Brush.horizontalGradient(
                                    if (entry.claimed) {
                                        listOf(Color(0xFF2A1E07), Color(0xFF10140F))
                                    } else {
                                        listOf(Color(0xFF141B18), Color(0xFF0E1311))
                                    },
                                ),
                                MaterialTheme.shapes.small,
                            )
                            .border(
                                1.dp,
                                if (entry.claimed) MonarchColors.SovereignGold else MonarchColors.Rune,
                                MaterialTheme.shapes.small,
                            )
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                entry.skillName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (entry.claimed) MonarchColors.SovereignGold else MonarchColors.Ink,
                                maxLines = 1,
                            )
                            Text(
                                formatDate(entry.practicedAtMs, "HH:mm") +
                                    (def?.let { "  ·  ${it.line}  ·  ${Skills.tierLabel(it.tier)}" } ?: ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = MonarchColors.InkMuted,
                                maxLines = 1,
                            )
                        }
                        Text(
                            when {
                                entry.claimed -> "MASTERED"
                                entry.weightKg != null -> "${entry.value}${def?.unit ?: ""} @${entry.weightKg}kg"
                                else -> "${entry.value}${def?.unit ?: ""}"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = ChakraPetch,
                            fontWeight = FontWeight.Bold,
                            color = if (entry.claimed) MonarchColors.SovereignGold else MonarchColors.SystemGreen,
                        )
                    }
                }
            }
        }
}

/** Consecutive days with at least one logged attempt, counting back from today. */
private fun practiceStreak(byDay: Map<LocalDate, Int>, today: LocalDate): Int {
    var streak = 0
    var day = today
    if (byDay[day] == null) day = today.minusDays(1)
    while ((byDay[day] ?: 0) > 0) {
        streak++
        day = day.minusDays(1)
    }
    return streak
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier, gold: Boolean = false) {
    val shape = MaterialTheme.shapes.small
    Column(
        modifier
            .background(
                Brush.verticalGradient(listOf(Color(0xFF16201C), Color(0xFF0D1210))),
                shape,
            )
            .border(1.dp, if (gold) MonarchColors.SovereignGold else MonarchColors.Rune, shape)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = if (gold) MonarchColors.SovereignGold else MonarchColors.Ink,
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

@Composable
private fun HeatKey(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MonarchColors.InkMuted)
    }
}

@Composable
private fun LineCard(
    line: String,
    done: Int,
    total: Int,
    attempts: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = MaterialTheme.shapes.small
    val complete = done == total && total > 0
    Column(
        modifier
            .background(
                Brush.verticalGradient(listOf(Color(0xFF17211C), Color(0xFF0D1210))),
                shape,
            )
            .border(1.dp, if (complete) MonarchColors.SovereignGold else MonarchColors.Rune, shape)
            .clickable { onClick() }
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MonogramBadge(lineMonogram(line), 30.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    line.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.Bold,
                    color = MonarchColors.Ink,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                )
                Text(
                    "$done/$total mastered",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = MonarchColors.InkMuted,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(Color(0xFF1E2A24))
                .border(1.dp, MonarchColors.Rune),
        ) {
            if (done > 0) {
                Box(
                    Modifier
                        .fillMaxWidth(done.toFloat() / total.coerceAtLeast(1))
                        .height(6.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(MonarchColors.SystemGreen, MonarchColors.SovereignGold),
                            ),
                        ),
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            "$attempts attempts logged",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            color = MonarchColors.SystemGreen,
        )
    }
}

/** Two-letter line seal: consistent, legible, and never a wrong pictogram. */
@Composable
private fun MonogramBadge(text: String, size: androidx.compose.ui.unit.Dp) {
    val shape = CutCornerShape(topStart = size / 4, bottomEnd = size / 4)
    Box(
        Modifier
            .size(size)
            .background(
                Brush.verticalGradient(listOf(Color(0xFF1D2B24), Color(0xFF111815))),
                shape,
            )
            .border(1.dp, MonarchColors.SystemGreen, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = MonarchColors.SystemGreen,
            letterSpacing = 0.sp,
        )
    }
}
