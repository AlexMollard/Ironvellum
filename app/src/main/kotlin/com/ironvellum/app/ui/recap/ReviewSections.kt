package com.ironvellum.app.ui.recap

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironvellum.app.R
import com.ironvellum.app.data.Units
import com.ironvellum.app.domain.PeriodReview
import com.ironvellum.app.domain.ReviewKind
import com.ironvellum.app.domain.ReviewLift
import com.ironvellum.app.domain.Skills
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.theme.InkPlateShape
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.inkBorder
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** Lifts listed before the rest fold into a count. */
private const val MAX_LIFTS = 12

/** Muscles that get a bar. */
private const val MAX_MUSCLES = 5

private fun dayOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

/** The headline: how many workouts, then volume, sets and time, and either the comparison or since when the review counts. */
@Composable
internal fun ReviewHero(review: PeriodReview) {
    val stats = review.stats
    val locale = LocalConfiguration.current.locales[0]
    InkPanel(Modifier.fillMaxWidth()) {
        CapsLabel(ReviewText.name(review.period, locale))
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stats.workouts.toString(),
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 44.sp),
                color = IronvellumColors.Ink,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                UiText.themedPlural(R.plurals.recap_hero_unit, R.plurals.recap_hero_unit__t, stats.workouts).asString(),
                style = MaterialTheme.typography.titleLarge,
                color = IronvellumColors.Ink,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val (volume, unit) = RecapCard.volumeFigure(stats.volumeKg, locale)
            val change = ReviewText.comparison(review, locale)
            Stat(
                Modifier.weight(1f), stringResource(R.string.recap_stat_volume_label), volume, unit,
                change?.let { it to (review.volumeChangePercent ?: 0) },
            )
            Stat(Modifier.weight(1f), stringResource(R.string.recap_stat_sets_label), stats.sets.toString(), null, null)
            Stat(Modifier.weight(1f), stringResource(R.string.recap_stat_time_label), RecapText.duration(stats.minutes), null, null)
        }
        ReviewText.sinceCaption(review, locale)?.let { Caption(it, Modifier.padding(top = 10.dp)) }
    }
}

/** The best week, as a row that opens that week's recap. */
@Composable
internal fun ReviewBestWeek(review: PeriodReview, onOpenWeek: (Long) -> Unit) {
    val week = review.bestWeek ?: return
    InkPanel(
        Modifier.fillMaxWidth(),
        onClick = { onOpenWeek(week.weekStart.toEpochDay()) },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            Modifier.semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.StarBorder, contentDescription = null, tint = IronvellumColors.SovereignGold, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                CapsLabel(stringResource(R.string.review_best_week_label))
                Text(
                    ReviewText.bestWeek(week).asString(),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                    color = IronvellumColors.Ink,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = IronvellumColors.InkMuted, modifier = Modifier.size(20.dp))
        }
    }
}

/** A lift's best estimated 1RM through the period, as a small line. */
@Composable
private fun Sparkline(series: List<Double>, modifier: Modifier = Modifier) {
    val color = IronvellumColors.EmeraldBright
    Canvas(modifier.clearAndSetSemantics {}) {
        if (series.size < 2) return@Canvas
        val low = series.min()
        val high = series.max()
        val span = (high - low).takeIf { it > 1e-9 } ?: 1.0
        val pad = 2.dp.toPx()
        val path = Path()
        series.forEachIndexed { i, value ->
            val x = pad + (size.width - 2 * pad) * i / (series.size - 1)
            val y = size.height - pad - ((value - low) / span).toFloat() * (size.height - 2 * pad)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** The lifts that gained and the lifts that began, with a line for each. */
@Composable
internal fun ReviewLifts(review: PeriodReview) {
    if (review.lifts.isEmpty()) return
    val locale = LocalConfiguration.current.locales[0]
    val from = review.since ?: review.period.from
    val shown = review.lifts.take(MAX_LIFTS)
    SectionTitle(stringResource(R.string.review_lifts_title)) {
        Caption(stringResource(R.string.review_lifts_caption, RecapText.compactRange(from, minOf(review.period.last, LocalDate.now()), locale)))
    }
    InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp)) {
        shown.forEachIndexed { index, lift ->
            if (index > 0) InkDivider()
            LiftRow(lift)
        }
        val more = review.lifts.size - shown.size
        if (more > 0) {
            InkDivider()
            Caption(
                pluralStringResource(R.plurals.review_lifts_more, more, more),
                Modifier.padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun LiftRow(lift: ReviewLift) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(lift.label, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp), color = IronvellumColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Caption(
                lift.beforeKg?.let { stringResource(R.string.review_lift_change, ReviewText.figure(it), Units.fixed(lift.afterKg, 1)) }
                    ?: stringResource(R.string.review_lift_new_sub, Units.fixed(lift.afterKg, 1)),
                Modifier.padding(top = 2.dp),
            )
        }
        Sparkline(lift.series, Modifier.width(64.dp).height(24.dp))
        Text(
            lift.gainKg?.let { RecapText.gain(it) } ?: stringResource(R.string.review_lift_new_badge),
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
            color = if (lift.isNew) IronvellumColors.SovereignGold else IronvellumColors.EmeraldBright,
            textAlign = TextAlign.End,
            modifier = Modifier.width(76.dp),
        )
    }
}

/** The five muscles with the most hard sets over the period, the leader in the accent. */
@Composable
internal fun ReviewMuscles(review: PeriodReview) {
    val top = review.muscles.take(MAX_MUSCLES)
    if (top.isEmpty()) return
    val most = top.first().second
    SectionTitle(stringResource(R.string.review_muscles_title)) { Caption(stringResource(R.string.review_muscles_caption)) }
    InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        top.forEachIndexed { index, (muscle, sets) ->
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(muscle.label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp), color = IronvellumColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(88.dp))
                Box(Modifier.weight(1f).height(10.dp).background(IronvellumColors.Rune)) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth((sets / most).toFloat().coerceIn(0f, 1f))
                            .background(if (index == 0) IronvellumColors.Emerald else IronvellumColors.InkMuted.copy(alpha = 0.6f)),
                    )
                }
                Text(setsText(sets), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp), color = IronvellumColors.Ink, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(40.dp))
            }
        }
    }
}

/** Total volume, and the period before it when there is a fair one to set beside it. */
@Composable
internal fun ReviewVolume(review: PeriodReview) {
    val locale = LocalConfiguration.current.locales[0]
    InkPanel(Modifier.fillMaxWidth()) {
        CapsLabel(stringResource(R.string.review_volume_label))
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                RecapText.volume(review.stats.volumeKg, locale),
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 36.sp),
                color = IronvellumColors.Ink,
                maxLines = 1,
            )
            Caption(stringResource(R.string.review_volume_lifted, RecapText.unit()), Modifier.padding(start = 8.dp, bottom = 6.dp))
        }
        val previous = review.previous
        if (previous != null && previous.volumeKg > 0.0) {
            Spacer(Modifier.height(10.dp))
            val top = maxOf(review.stats.volumeKg, previous.volumeKg)
            VolumeBar(ReviewText.short(review.period, locale), review.stats.volumeKg, top, IronvellumColors.Emerald, IronvellumColors.Ink)
            VolumeBar(ReviewText.short(review.period.previous, locale), previous.volumeKg, top, IronvellumColors.InkMuted.copy(alpha = 0.6f), IronvellumColors.InkMuted)
        }
    }
}

/** A month's days as a calendar (a year's months as bars), then the streak as it stood at the end. */
@Composable
internal fun ReviewDays(review: PeriodReview) {
    val locale = LocalConfiguration.current.locales[0]
    InkPanel(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                UiText.themed(R.string.review_days_title, R.string.review_days_title__t).asString(),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                color = IronvellumColors.Ink,
                modifier = Modifier.weight(1f),
            )
            Caption(stringResource(R.string.review_per_week, ReviewText.pace(review, locale)))
        }
        Spacer(Modifier.height(12.dp))
        if (review.period.kind == ReviewKind.MONTH) MonthCalendar(review, locale) else YearBars(review, locale)
        if (review.streakDays > 0) {
            Spacer(Modifier.height(12.dp))
            InkDivider()
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.LocalFireDepartment, contentDescription = null, tint = IronvellumColors.Emerald, modifier = Modifier.size(24.dp))
                Column(Modifier.weight(1f)) {
                    val plural = if (review.period.kind == ReviewKind.MONTH) {
                        UiText.themedPlural(R.plurals.review_streak_month_end, R.plurals.review_streak_month_end__t, review.streakDays)
                    } else {
                        UiText.themedPlural(R.plurals.review_streak_year_end, R.plurals.review_streak_year_end__t, review.streakDays)
                    }
                    Text(plural.asString(), style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp), color = IronvellumColors.Ink)
                    val body = when {
                        review.streakGoing && review.streakLongest -> R.string.review_streak_going_longest
                        review.streakGoing -> R.string.review_streak_going
                        review.streakLongest -> R.string.review_streak_longest
                        else -> null
                    }
                    if (body != null) Caption(stringResource(body), Modifier.padding(top = 1.dp))
                }
            }
        }
    }
}

/** Monday first: the month's days in rows of seven, a filled plate for each day with a workout. */
@Composable
private fun MonthCalendar(review: PeriodReview, locale: Locale) {
    val first = review.period.from
    val lead = first.dayOfWeek.value - 1
    val days = review.period.last.dayOfMonth
    Row(Modifier.fillMaxWidth()) {
        for (d in 1..7) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    DayOfWeek.of(d).getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = IronvellumColors.InkMuted,
                )
            }
        }
    }
    val rows = (lead + days + 6) / 7
    for (row in 0 until rows) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            for (col in 0 until 7) {
                val day = row * 7 + col - lead + 1
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (day in 1..days) DayPlate(first.plusDays(day - 1L), day, review)
                }
            }
        }
    }
}

@Composable
private fun DayPlate(date: LocalDate, day: Int, review: PeriodReview) {
    val worked = date in review.stats.days
    val shape = InkPlateShape(with(LocalDensity.current) { 5.dp.toPx() })
    Box(
        Modifier
            .size(32.dp)
            .clip(shape)
            .background(if (worked) IronvellumColors.Emerald else Color.Transparent)
            .inkBorder(if (worked) IronvellumColors.Emerald else IronvellumColors.Rune, shape, 1.dp)
            .semantics { contentDescription = RecapText.day(date) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            day.toString(),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = if (worked) IronvellumColors.OnEmerald else IronvellumColors.InkMuted,
        )
    }
}

/** A year's twelve months as bars of workouts, the month's letter below each. */
@Composable
private fun YearBars(review: PeriodReview, locale: Locale) {
    val counts = review.monthWorkouts
    val most = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(112.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        counts.forEachIndexed { index, count ->
            val month = review.period.from.plusMonths(index.toLong())
            val label = UiText.themedPlural(R.plurals.review_month_bar_description, R.plurals.review_month_bar_description__t, count, ReviewText.monthName(month, locale), count).asString()
            Column(
                Modifier.weight(1f).fillMaxHeight().semantics(mergeDescendants = true) { contentDescription = label },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    if (count > 0) count.toString() else "",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = IronvellumColors.InkMuted,
                    maxLines = 1,
                    softWrap = false,
                )
                Spacer(Modifier.height(2.dp))
                val bar = (60.dp * count / most).coerceAtLeast(2.dp)
                Box(Modifier.fillMaxWidth().height(bar).background(if (count > 0) IronvellumColors.Emerald else IronvellumColors.Rune))
                Spacer(Modifier.height(4.dp))
                Text(
                    month.month.getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = IronvellumColors.InkMuted,
                )
            }
        }
    }
}

/** Skills claimed in the period: the name, its tier and day, and the XP it paid. */
@Composable
internal fun ReviewSkills(review: PeriodReview) {
    if (review.skills.isEmpty()) return
    SectionTitle(stringResource(R.string.review_skills_title)) {
        Caption(
            stringResource(
                if (review.period.kind == ReviewKind.MONTH) R.string.review_skills_count_month else R.string.review_skills_count_year,
                review.skills.size,
            ),
        )
    }
    InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp)) {
        review.skills.forEachIndexed { index, skill ->
            if (index > 0) InkDivider()
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = IronvellumColors.SovereignGold, modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f)) {
                    Text(skill.name, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp), color = IronvellumColors.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Caption(
                        stringResource(R.string.review_skill_tier, Skills.tierLabel(skill.tier), shortDay(dayOf(skill.atMs))),
                        Modifier.padding(top = 2.dp),
                    )
                }
                Text(
                    stringResource(R.string.review_skill_xp, skill.xp),
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    color = IronvellumColors.EmeraldBright,
                )
            }
        }
    }
}

/** "9 Sep". */
private fun shortDay(date: LocalDate): String =
    java.time.format.DateTimeFormatter.ofPattern(com.ironvellum.app.data.Words.text(UiText.res(R.string.recap_pattern_date)), Locale.getDefault()).format(date)

/** A month's weeks, folded away: each opens its weekly recap. */
@Composable
internal fun ReviewWeeks(review: PeriodReview, onOpenWeek: (Long) -> Unit) {
    if (review.period.kind != ReviewKind.MONTH || review.weeks.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
        val label = stringResource(if (expanded) R.string.review_weeks_hide else R.string.review_weeks_caption, ReviewText.short(review.period, locale))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(onClickLabel = label, role = Role.Button) { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.review_weeks_title), style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp), color = IronvellumColors.Ink)
                Caption(label, Modifier.padding(top = 2.dp))
            }
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = IronvellumColors.InkMuted,
                modifier = Modifier.size(20.dp),
            )
        }
        if (expanded) {
            review.weeks.forEach { week ->
                InkDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(onClickLabel = stringResource(R.string.recap_open_description), role = Role.Button) { onOpenWeek(week.weekStart.toEpochDay()) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        UiText.join(listOf(UiText.Raw(RecapText.compactRange(week.weekStart, week.weekEnd, locale)), RecapText.workouts(week.workouts)), " · ").asString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = IronvellumColors.Ink,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = IronvellumColors.InkMuted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
