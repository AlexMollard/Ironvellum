package com.ironvellum.app.ui.recap

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import com.ironvellum.app.ui.theme.inkBorder
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironvellum.app.R
import com.ironvellum.app.data.Units
import com.ironvellum.app.domain.AimState
import com.ironvellum.app.domain.ListText
import com.ironvellum.app.domain.MuscleRow
import com.ironvellum.app.domain.ProgramRules
import com.ironvellum.app.domain.RecapKind
import com.ironvellum.app.domain.RecapNext
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.domain.WeeklyRecap
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import com.ironvellum.app.ui.theme.InkPlateShape
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** The bar's full width: a muscle that gets this many hard sets in a week is already heavy ([ProgramRules.HEAVY_WEEK]). */
private val BAR_SCALE = ProgramRules.HEAVY_WEEK

@Composable
internal fun SectionTitle(text: String, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), color = IronvellumColors.Ink, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
internal fun Caption(text: String, modifier: Modifier = Modifier, color: Color = IronvellumColors.InkMuted) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = modifier)
}

/** A small capitals label: "SETS", "NEXT WEEK". */
@Composable
internal fun CapsLabel(text: String) {
    Text(
        text.uppercase(LocalConfiguration.current.locales[0]),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = IronvellumTracking.InlineLabel),
        color = IronvellumColors.InkMuted,
        maxLines = 1,
    )
}

/** The week's headline: how many workouts, then sets, volume and time, and (when there is a week to compare) the two volume bars. */
@Composable
internal fun HeroCard(recap: WeeklyRecap) {
    val stats = recap.stats
    val previous = recap.previous
    val compare = recap.kind == RecapKind.FULL
    InkPanel(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Spacer(Modifier.weight(1f))
            if (previous.workouts > 0) {
                Caption(
                    stringResource(R.string.recap_vs_last_week, previous.workouts, RecapText.compactRange(previous.from, previous.to.minusDays(1))),
                    Modifier.alignByBaseline(),
                )
            }
        }
        if (recap.kind != RecapKind.EMPTY) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val delta = compare && previous.workouts > 0
                Stat(
                    Modifier.weight(1f), stringResource(R.string.recap_stat_sets_label), stats.sets.toString(), null,
                    if (delta) stringResource(R.string.recap_sets_delta, RecapText.signed(stats.sets - previous.sets)) to (stats.sets - previous.sets) else null,
                )
                Stat(
                    Modifier.weight(1f), stringResource(R.string.recap_stat_volume_label), RecapText.volume(stats.volumeKg), RecapText.unit(),
                    recap.volumeChangePercent?.takeIf { compare }?.let { stringResource(R.string.recap_percent_delta, RecapText.signed(it)) to it },
                )
                Stat(
                    Modifier.weight(1f), stringResource(R.string.recap_stat_time_label), RecapText.duration(stats.minutes), null,
                    if (delta) stringResource(R.string.recap_minutes_delta, RecapText.signed((stats.minutes - previous.minutes).toInt())) to (stats.minutes - previous.minutes).toInt() else null,
                )
            }
            if (compare && previous.volumeKg > 0.0) {
                Spacer(Modifier.height(14.dp))
                val top = maxOf(stats.volumeKg, previous.volumeKg)
                VolumeBar(stringResource(R.string.recap_bar_this_week), stats.volumeKg, top, IronvellumColors.Emerald, IronvellumColors.Ink)
                VolumeBar(stringResource(R.string.recap_bar_last_week), previous.volumeKg, top, IronvellumColors.InkMuted.copy(alpha = 0.6f), IronvellumColors.InkMuted)
            }
        }
    }
}

@Composable
internal fun Stat(modifier: Modifier, label: String, value: String, unit: String?, delta: Pair<String, Int>?) {
    Column(modifier) {
        CapsLabel(label)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                color = IronvellumColors.Ink,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (unit != null) {
                Caption(unit, Modifier.padding(start = 3.dp, bottom = 3.dp))
            }
        }
        if (delta != null) {
            Text(
                delta.first,
                style = MaterialTheme.typography.bodySmall,
                color = if (delta.second > 0) IronvellumColors.EmeraldBright else IronvellumColors.InkMuted,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
internal fun VolumeBar(label: String, kg: Double, top: Double, fill: Color, valueColor: Color) {
    Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Caption(label, Modifier.width(76.dp))
        Box(Modifier.weight(1f).height(10.dp).background(IronvellumColors.Rune)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth((kg / top).toFloat().coerceIn(0f, 1f)).background(fill))
        }
        Text(
            RecapText.volume(kg) + " " + RecapText.unit(),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = valueColor,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(84.dp),
        )
    }
}

/** A full week: records, the best set, the muscles, the streak. */
@Composable
internal fun ColumnScope.FullWeek(recap: WeeklyRecap, today: LocalDate) {
    Records(recap)
    BestSetCard(recap)
    Muscles(recap)
    StreakCard(recap, today)
}

/** A light week: no bars; what was done, and any record that was earned. */
@Composable
internal fun ColumnScope.LightWeek(recap: WeeklyRecap, today: LocalDate) {
    val title = recap.stats.titles.firstOrNull().orEmpty()
    InkPanel(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.recap_light_title), style = MaterialTheme.typography.titleMedium, color = IronvellumColors.Ink)
        Spacer(Modifier.height(4.dp))
        Caption(
            UiText.themedPlural(R.plurals.recap_light_body, R.plurals.recap_light_body__t, recap.streak.days, title, recap.streak.days).asString(),
        )
    }
    if (recap.stats.records.isNotEmpty()) Records(recap)
    DoneThisWeek(recap)
    StreakCard(recap, today)
}

/** An empty week: what is at stake for the streak and when the next recap comes. */
@Composable
internal fun ColumnScope.EmptyWeek(recap: WeeklyRecap, today: LocalDate) {
    Caption(
        if (recap.previous.workouts > 0) {
            stringResource(R.string.recap_empty_body, RecapText.day(recap.weekStart), RecapText.day(recap.weekEnd), recap.previous.workouts)
        } else {
            stringResource(R.string.recap_empty_none_last, RecapText.day(recap.weekStart), RecapText.day(recap.weekEnd))
        },
        Modifier.padding(horizontal = 4.dp),
    )
    if (recap.streak.days > 0) StreakCard(recap, today)
    Caption(UiText.themed(R.string.recap_empty_note, R.string.recap_empty_note__t).asString(), Modifier.padding(horizontal = 4.dp))
}

@Composable
private fun Records(recap: WeeklyRecap) {
    val records = recap.stats.records
    if (records.isEmpty()) return
    SectionTitle(stringResource(R.string.recap_records_title)) {
        Text(
            stringResource(R.string.recap_records_new_badge, records.size),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = IronvellumColors.SovereignGold,
            modifier = Modifier
                .background(IronvellumColors.SovereignGold.copy(alpha = 0.16f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
    InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp)) {
        records.forEachIndexed { index, record ->
            if (index > 0) InkDivider()
            Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = IronvellumColors.SovereignGold, modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f)) {
                    Text(record.label, style = MaterialTheme.typography.titleMedium, color = IronvellumColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Caption(RecapText.recordSet(record), Modifier.padding(top = 2.dp))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Caption(stringResource(R.string.recap_record_e1rm, RecapText.load(record.e1rmKg)))
                    Text(
                        RecapText.gain(record.gainKg),
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                        color = IronvellumColors.EmeraldBright,
                    )
                }
            }
        }
    }
}

@Composable
private fun BestSetCard(recap: WeeklyRecap) {
    val best = recap.stats.bestSet ?: return
    InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.StarBorder, contentDescription = null, tint = IronvellumColors.SovereignGold, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                CapsLabel(stringResource(R.string.recap_best_set_label))
                Text(
                    stringResource(R.string.recap_best_set_line, best.label, RecapText.set(best.set, best.addsToBody)),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                    color = IronvellumColors.Ink,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Caption(
                RecapText.day(java.time.Instant.ofEpochMilli(best.atMs).atZone(java.time.ZoneId.systemDefault()).toLocalDate()),
            )
        }
    }
}

/** The status line of a muscle row: its state, and the plan's aim when there is one. */
@Composable
private fun statusText(row: MuscleRow): String {
    val state = stringResource(
        when (row.state) {
            AimState.NOT_TRAINED -> R.string.recap_state_not_trained
            AimState.BELOW_MINIMUM -> R.string.recap_state_below_minimum
            AimState.UNDER_PLAN -> R.string.recap_state_under_plan
            AimState.ON_AIM -> R.string.recap_state_on_aim
        },
    )
    val aim = row.aim ?: return state
    return stringResource(R.string.recap_muscle_status_aim, state, stringResource(R.string.recap_aim_range, aim.first, aim.last))
}

/** Hard sets as a figure: a whole number, or one place when the credit is fractional ("3.5"). */
internal fun setsText(sets: Double): String {
    val rounded = Math.round(sets * 10) / 10.0
    return if (rounded == Math.floor(rounded)) rounded.toLong().toString() else rounded.toString()
}

@Composable
private fun Muscles(recap: WeeklyRecap) {
    val off = recap.muscles.filter { it.state != AimState.ON_AIM }
    val on = recap.onAim
    var expanded by rememberSaveable { mutableStateOf(false) }
    val hasPlan = recap.muscles.any { it.aim != null }
    SectionTitle(stringResource(R.string.recap_muscles_title)) {
        Caption(stringResource(R.string.recap_muscles_on_aim_count, on.size, recap.muscles.size))
    }
    Caption(
        stringResource(if (hasPlan) R.string.recap_muscles_caption else R.string.recap_muscles_caption_no_plan, ProgramRules.COVERED_MIN.toInt()),
        Modifier.padding(top = 2.dp, bottom = 4.dp),
    )
    InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp)) {
        off.forEach { MuscleBar(it) }
        if (on.isNotEmpty()) {
            if (off.isNotEmpty()) InkDivider()
            val summaryLabel = stringResource(if (expanded) R.string.recap_on_aim_collapse else R.string.recap_on_aim_expand)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(onClickLabel = summaryLabel, role = Role.Button) { expanded = !expanded }
                    .padding(top = 10.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.recap_on_aim_summary, on.size), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = IronvellumColors.Ink)
                    if (!expanded) {
                        Caption(
                            on.map { stringResource(R.string.recap_muscle_figure, it.muscle.label, setsText(it.sets)) }.joinToString(" · "),
                            Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = IronvellumColors.InkMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (expanded) on.forEach { MuscleBar(it) }
        }
    }
}

/** One muscle: its name, state and hard sets over a bar with the plan's aim as a box and the minimum as a line. */
@Composable
private fun MuscleBar(row: MuscleRow) {
    val ink = IronvellumColors.Ink
    val muted = IronvellumColors.InkMuted
    val track = IronvellumColors.Rune
    Column(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 6.dp), verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(row.muscle.label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp), color = ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Caption(statusText(row), color = muted)
            Text(
                setsText(row.sets),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                color = ink,
                textAlign = TextAlign.End,
                modifier = Modifier.width(32.dp),
            )
        }
        Canvas(Modifier.fillMaxWidth().padding(top = 5.dp).height(14.dp)) {
            val w = size.width
            val barTop = 3.dp.toPx()
            val barHeight = 8.dp.toPx()
            drawRect(track, Offset(0f, barTop), Size(w, barHeight))
            drawRect(ink.copy(alpha = 0.55f), Offset(0f, barTop), Size((row.sets / BAR_SCALE).toFloat().coerceIn(0f, 1f) * w, barHeight))
            row.aim?.let { aim ->
                val x0 = (aim.first / BAR_SCALE).toFloat().coerceIn(0f, 1f) * w
                val x1 = (aim.last / BAR_SCALE).toFloat().coerceIn(0f, 1f) * w
                drawRect(muted, Offset(x0, 1.dp.toPx()), Size((x1 - x0).coerceAtLeast(1.dp.toPx()), 12.dp.toPx()), style = Stroke(1.dp.toPx()))
            }
            val min = (ProgramRules.COVERED_MIN / BAR_SCALE).toFloat() * w
            drawRect(ink.copy(alpha = 0.7f), Offset(min, 0f), Size(1.dp.toPx(), 14.dp.toPx()))
        }
    }
}

/** The streak, as of the end of the week, over the week's seven days. */
@Composable
internal fun StreakCard(recap: WeeklyRecap, today: LocalDate) {
    val streak = recap.streak
    if (streak.days == 0) return
    InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.LocalFireDepartment, contentDescription = null, tint = IronvellumColors.Emerald, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                val atRisk = streak.atRisk && recap.kind == RecapKind.EMPTY
                Text(
                    if (atRisk) UiText.themed(R.string.recap_streak_at_risk_title, R.string.recap_streak_at_risk_title__t).asString()
                    else UiText.themedPlural(R.plurals.recap_streak_days, R.plurals.recap_streak_days__t, streak.days).asString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = IronvellumColors.Ink,
                )
                Caption(
                    when {
                        atRisk -> UiText.themed(
                            R.string.recap_streak_at_risk_body, R.string.recap_streak_at_risk_body__t,
                            streak.lastWorkout?.let { RecapText.day(it) }.orEmpty(), trainWhen(streak.trainBy, today), streak.days,
                        ).asString()
                        streak.longest -> stringResource(R.string.recap_streak_kept_longest)
                        else -> stringResource(R.string.recap_streak_kept)
                    },
                    Modifier.padding(top = 1.dp),
                )
            }
        }
        if (recap.kind != RecapKind.EMPTY) {
            Spacer(Modifier.height(12.dp))
            WeekStrip(recap)
        }
        val trainBy = streak.trainBy
        if (trainBy != null && !trainBy.isBefore(today) && !(streak.atRisk && recap.kind == RecapKind.EMPTY)) {
            Caption(
                when (trainBy) {
                    today -> stringResource(R.string.recap_streak_train_today)
                    today.plusDays(1) -> stringResource(R.string.recap_streak_train_tomorrow)
                    else -> stringResource(R.string.recap_streak_train_date, RecapText.day(trainBy))
                },
                Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** When the streak must next be fed, as a phrase for the at-risk sentence: "today", "by tomorrow", "by Sat 24 Oct". */
private fun trainWhen(trainBy: LocalDate?, today: LocalDate): UiText = when (trainBy) {
    null -> UiText.res(R.string.recap_train_when_today)
    today -> UiText.res(R.string.recap_train_when_today)
    today.plusDays(1) -> UiText.res(R.string.recap_train_when_tomorrow)
    else -> UiText.res(R.string.recap_train_when_date, RecapText.day(trainBy))
}

/** Monday to Sunday: a filled plate for each day with a workout. */
@Composable
private fun WeekStrip(recap: WeeklyRecap) {
    val locale = LocalConfiguration.current.locales[0]
    Row(Modifier.fillMaxWidth()) {
        for (offset in 0L until 7L) {
            val date = recap.weekStart.plusDays(offset)
            val worked = date in recap.stats.days
            val letter = DayOfWeek.of(date.dayOfWeek.value).getDisplayName(TextStyle.NARROW, locale)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val shape = InkPlateShape(with(androidx.compose.ui.platform.LocalDensity.current) { 6.dp.toPx() })
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
                        letter,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                        color = if (worked) IronvellumColors.OnEmerald else IronvellumColors.InkMuted,
                    )
                }
            }
        }
    }
}

/** A light week's work: each lift, its sets and the load, in the order it was done. */
@Composable
private fun DoneThisWeek(recap: WeeklyRecap) {
    if (recap.stats.lifts.isEmpty()) return
    InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
        CapsLabel(stringResource(R.string.recap_done_title))
        recap.stats.lifts.forEachIndexed { index, lift ->
            if (index > 0) InkDivider()
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(lift.label, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                val load = lift.topKg?.let { (if (lift.addsToBody) "+" else "") + Units.withUnit(it) }
                Caption(
                    when {
                        lift.reps != null && load != null -> stringResource(R.string.recap_lift_sets_reps_load, lift.sets, lift.reps, load)
                        lift.reps != null -> stringResource(R.string.recap_lift_sets_reps, lift.sets, lift.reps)
                        load != null -> stringResource(R.string.recap_lift_sets_top, UiText.plural(R.plurals.common_sets_count, lift.sets).asString(), load)
                        else -> UiText.plural(R.plurals.common_sets_count, lift.sets).asString()
                    },
                )
            }
        }
    }
}

/** What the plan holds for the coming week and what this week left short of it. */
@Composable
internal fun NextWeekCard(next: RecapNext) {
    InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        CapsLabel(stringResource(R.string.recap_next_label))
        Text(
            UiText.themedPlural(
                R.plurals.recap_next_planned, R.plurals.recap_next_planned__t, next.planned, next.planned, next.firstName, RecapText.weekday(next.firstDay),
            ).asString(),
            style = MaterialTheme.typography.titleMedium,
            color = IronvellumColors.Ink,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (next.short.isNotEmpty()) {
            // The least-trained first: the list arrives most-trained first.
            val names = next.short.takeLast(3).reversed().map { it.label }
            val list = ListText.and(names.mapIndexed { i, name -> if (i == 0) name else name.replaceFirstChar { it.lowercase() } })
            val add = if (next.addName != null && next.addDay != null) {
                " " + stringResource(R.string.recap_next_add, RecapText.weekday(next.addDay!!), next.addName)
            } else ""
            Caption(stringResource(R.string.recap_next_short, list) + add, Modifier.padding(top = 6.dp))
        }
    }
}
