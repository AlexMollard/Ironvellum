package com.ironvellum.app.ui.titles

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import java.time.format.DateTimeFormatter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironvellum.app.R
import com.ironvellum.app.domain.SkillPractice
import com.ironvellum.app.domain.Skills
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.formatDate
import com.ironvellum.app.ui.components.plural
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.components.InkRail
import com.ironvellum.app.ui.theme.inkHairline
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.theme.InkPlateShape
import androidx.compose.ui.platform.LocalDensity
import com.ironvellum.app.ui.theme.TileShape
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
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
    /** The lifter, so the podium's "best" on a loaded standard is judged against their bodyweight. */
    bodyweightKg: Double? = null,
    female: Boolean = false,
) {
    val zone = ZoneId.systemDefault()
    val attempts = log.filterNot { it.claimed }
    val byDay = attempts.groupingBy {
        Instant.ofEpochMilli(it.practicedAtMs).atZone(zone).toLocalDate()
    }.eachCount()
    val today = LocalDate.now()

    // ---- headline counters -------------------------------------------------
    SectionHeader("Journal")
    // An empty Journal is a grid of blank tiles: say what fills it.
    if (log.isEmpty()) {
        Text(
            "Every attempt at a technique is written here. Open a path below, pick a technique and log an attempt.",
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
    val tiles = listOf(
        Triple("ATTEMPTS", attempts.size.toString(), false),
        Triple("MASTERED", "${claimed.size}", claimed.isNotEmpty()),
        Triple("DAYS KEPT", "${practiceStreak(byDay, today)}d", false),
        Triple("LAST", attempts.firstOrNull()?.let { formatDate(it.practicedAtMs, "d MMM") } ?: "—", false),
    )
    // Four across on a phone; two by two once the font is scaled up, where a
    // label would otherwise break mid-word.
    tiles.chunked(if (LocalDensity.current.fontScale > 1.3f) 2 else 4).forEachIndexed { r, rowTiles ->
        if (r > 0) Spacer(Modifier.height(8.dp))
        // Equal heights, whichever label wraps.
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rowTiles.forEach { (label, value, gold) ->
                StatTile(label, value, Modifier.weight(1f).fillMaxHeight(), gold = gold)
            }
        }
    }

    // ---- training heatmap --------------------------------------------------
    SectionHeader("Last 12 Weeks")
    InkPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            val start = today.minusWeeks((WEEKS - 1).toLong())
                .with(java.time.DayOfWeek.MONDAY)
            val cellShape = TileShape
            // Colour alone carries the cells, so the grid reads out as one summary
            // and its cells stay out of the accessibility tree.
            val summary = heatmapSummary(byDay, start, today)
            Row(
                Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = summary },
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
                            // Days that have not happened draw nothing. Filling
                            // them near-black made the corner of the grid look
                            // like missing cells instead of an unfinished week.
                            if (date.isAfter(today)) {
                                Spacer(Modifier.fillMaxWidth().aspectRatio(1f))
                            } else {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        // Square, derived from the column width.
                                        // A fixed 12dp height against a ~25dp
                                        // column drew wide bricks; a day is a
                                        // day, so the cell is a square.
                                        .aspectRatio(1f)
                                        .background(
                                            when {
                                                count >= 3 -> IronvellumColors.SovereignGold
                                                count == 2 -> IronvellumColors.Emerald
                                                count == 1 -> IronvellumColors.SystemGreen.copy(alpha = 0.65f)
                                                else -> Color(0xFF18211D)
                                            },
                                            cellShape,
                                        )
                                        .then(
                                            if (date == today) {
                                                Modifier.inkBorder(IronvellumColors.SovereignGold, cellShape, 1.dp)
                                            } else {
                                                Modifier
                                            },
                                        ),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The grid had no time axis at all: twelve anonymous columns.
                Text(
                    "${formatDate(start.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), "d MMM")} — today",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                    letterSpacing = IronvellumTracking.InlineLabel,
                )
                // No "none" key: an unlit cell needs no legend entry.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeatKey(IronvellumColors.SystemGreen.copy(alpha = 0.65f), "1")
                    HeatKey(IronvellumColors.Emerald, "2")
                    HeatKey(IronvellumColors.SovereignGold, "3+")
                }
            }
        }
    }

    // ---- per-line emblem cards --------------------------------------------
    SectionHeader("Paths")
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
                val best = def?.let { d ->
                    SkillGuidance.bestEffort(
                        d,
                        attempts.filter { it.skillName == name }.map { SkillGuidance.Effort(it.value, it.weightKg) },
                        bodyweightKg,
                        female,
                    )?.let { SkillGuidance.effortText(d, it, female) }
                } ?: "none"
                val rowShape = MaterialTheme.shapes.medium
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
                        .inkBorder(
                            if (index == 0) IronvellumColors.SovereignGold else IronvellumColors.Rune,
                            rowShape,
                        )
                        .clickable(onClickLabel = "Open $name", role = Role.Button) { onSelect(name) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "#${index + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        color = if (index == 0) IronvellumColors.SovereignGold else IronvellumColors.InkMuted,
                        modifier = Modifier.width(38.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink)
                        Text(
                            "$count ${plural(count, "attempt", "attempts")} · best $best",
                            style = MaterialTheme.typography.labelSmall,
                            color = IronvellumColors.InkMuted,
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
                "The Journal is blank. Open a technique and log what you actually hit.",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
        }
        return
    }

    // Recent practice days only: this journal grows for as long as the lifter
    // trains, and it renders inside a plain scrolling Column that composes
    // every row it is handed rather than only the visible ones.
    log.groupBy { Instant.ofEpochMilli(it.practicedAtMs).atZone(zone).toLocalDate() }
        .toSortedMap(compareByDescending { it })
        .entries.take(PRACTICE_DAYS)
        .forEach { (date, entries) ->
            Text(
                when (date) {
                    today -> "TODAY"
                    today.minusDays(1) -> "YESTERDAY"
                    else -> date.toString().uppercase()
                },
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                color = IronvellumColors.SystemGreen,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
            )
            entries.forEachIndexed { i, entry ->
                val def = Skills.forName(entry.skillName)
                Row(
                    Modifier.fillMaxWidth().clickable(onClickLabel = "Open ${entry.skillName}", role = Role.Button) { onSelect(entry.skillName) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // timeline rail
                    Box(Modifier.width(18.dp).height(54.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .height(if (i == entries.lastIndex) 27.dp else 54.dp)
                                .align(if (i == entries.lastIndex) Alignment.TopCenter else Alignment.Center)
                                // Timeline spine: a brushed run, not a ruled one.
                                .inkHairline(IronvellumColors.Rune, thickness = 1.5.dp),
                        )
                        Box(
                            Modifier
                                .size(9.dp)
                                .background(
                                    if (entry.claimed) IronvellumColors.SovereignGold else IronvellumColors.SystemGreen,
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
                                MaterialTheme.shapes.medium,
                            )
                            .inkBorder(if (entry.claimed) IronvellumColors.SovereignGold else IronvellumColors.Rune, MaterialTheme.shapes.medium, 1.dp)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                entry.skillName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (entry.claimed) IronvellumColors.SovereignGold else IronvellumColors.Ink,
                                maxLines = 1,
                            )
                            Text(
                                formatDate(entry.practicedAtMs, "HH:mm") +
                                    (def?.let { " · ${it.line} · ${Skills.tierLabel(it.tier)}" } ?: ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = IronvellumColors.InkMuted,
                                maxLines = 1,
                            )
                        }
                        Text(
                            when {
                                entry.claimed -> "MASTERED"
                                entry.weightKg != null -> "${def?.let { SkillGuidance.withUnit(entry.value, it) } ?: entry.value} @${formatLoad(entry.weightKg)}kg"
                                else -> def?.let { SkillGuidance.withUnit(entry.value, it) } ?: "${entry.value}"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = ChakraPetch,
                            fontWeight = FontWeight.Bold,
                            color = if (entry.claimed) IronvellumColors.SovereignGold else IronvellumColors.SystemGreen,
                        )
                    }
                }
            }
        }
}

/**
 * One sentence for the training heatmap: "12 weeks: 9 days practised, most 4
 * attempts on 3 Sep". Only days from [start] to [today] count.
 */
internal fun heatmapSummary(byDay: Map<LocalDate, Int>, start: LocalDate, today: LocalDate): String {
    val days = byDay.filter { (date, n) -> n > 0 && !date.isBefore(start) && !date.isAfter(today) }
    if (days.isEmpty()) return "$WEEKS weeks: no attempts logged"
    // the latest day among those tied for most
    val top = days.entries.maxWith(compareBy({ it.value }, { it.key }))
    val on = top.key.format(DateTimeFormatter.ofPattern("d MMM", java.util.Locale.getDefault()))
    return "$WEEKS weeks: ${days.size} ${plural(days.size, "day", "days")} practised, " +
        "most ${top.value} ${plural(top.value, "attempt", "attempts")} on $on"
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
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier
            .background(
                Brush.verticalGradient(listOf(Color(0xFF16201C), Color(0xFF0D1210))),
                shape,
            )
            .inkBorder(if (gold) IronvellumColors.SovereignGold else IronvellumColors.Rune, shape, 1.dp)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = if (gold) IronvellumColors.SovereignGold else IronvellumColors.Ink,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            fontSize = 10.sp,
            color = IronvellumColors.InkMuted,
            letterSpacing = 0.5.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun HeatKey(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // A bare Box is a hard rectangle: the key has to be cut like the cells
        // it stands for, or it reads as a different widget.
        Box(Modifier.size(9.dp).background(color, MaterialTheme.shapes.extraSmall))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = IronvellumColors.InkMuted)
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
    val shape = MaterialTheme.shapes.medium
    val complete = done == total && total > 0
    Column(
        modifier
            .background(
                Brush.verticalGradient(listOf(Color(0xFF17211C), Color(0xFF0D1210))),
                shape,
            )
            .inkBorder(if (complete) IronvellumColors.SovereignGold else IronvellumColors.Rune, shape, 1.dp)
            .clickable(onClickLabel = "Open $line path", role = Role.Button) { onClick() }
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
                    color = IronvellumColors.Ink,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                )
                Text(
                    "$done/$total mastered",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = IronvellumColors.InkMuted,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        InkRail(fraction = done.toFloat() / total.coerceAtLeast(1), seed = 43)
        // Eight tiles all reading "0 attempts logged" said one thing eight
        // times, under a rail already sitting at zero.
        if (attempts > 0) {
            Spacer(Modifier.height(5.dp))
            Text(
                "$attempts attempts logged",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = IronvellumColors.SystemGreen,
            )
        }
    }
}

/** Two-letter line seal: consistent, legible, and never a wrong pictogram. */
@Composable
private fun MonogramBadge(text: String, size: androidx.compose.ui.unit.Dp) {
    val plateCut = with(LocalDensity.current) { (size / 4).toPx() }
    val shape = InkPlateShape(plateCut)
    Box(
        Modifier
            .size(size)
            .background(
                Brush.verticalGradient(listOf(Color(0xFF1D2B24), Color(0xFF111815))),
                shape,
            )
            .inkBorder(IronvellumColors.SystemGreen, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = IronvellumColors.SystemGreen,
            letterSpacing = 0.sp,
        )
    }
}

/** Recent practice days; the per-skill detail carries the full record. */
private const val PRACTICE_DAYS = 14
