package com.ironvellum.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.DayNames
import com.ironvellum.app.domain.EntryAdjust
import com.ironvellum.app.domain.FatigueAdvice
import com.ironvellum.app.domain.FatigueLevel
import com.ironvellum.app.domain.FatigueReport
import com.ironvellum.app.domain.FatigueTuning
import com.ironvellum.app.domain.LightBy
import com.ironvellum.app.domain.Lighten
import com.ironvellum.app.domain.ListText
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.MuscleMap
import com.ironvellum.app.domain.PlanFatigue
import com.ironvellum.app.domain.PresetEntry
import com.ironvellum.app.domain.SchemeText
import com.ironvellum.app.domain.SessionOverride
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.domain.WorkoutPreset
import com.ironvellum.app.domain.WorkoutSession
import com.ironvellum.app.domain.reading
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.IronvellumDialog
import com.ironvellum.app.ui.program.ChoiceRow
import com.ironvellum.app.ui.program.FatigueSwatch
import com.ironvellum.app.ui.program.dayWord
import com.ironvellum.app.ui.program.freshByLine
import com.ironvellum.app.ui.program.levelWord
import com.ironvellum.app.ui.theme.IronvellumColors
import java.time.ZoneId
import java.util.Locale

/*
 * Today's fatigue advice (docs/superpowers/specs/2026-10-27-fatigue-integration-design.md, items 1 and 4). The decision
 * is [todayAdvice], pure; the rest is the line, the rest panel and the adjust sheet that show it. Nothing here edits a
 * routine: Adjust begins today's workout with a [SessionOverride], Do <routine> and Begin anyway begin as usual, and
 * Not today / Rest today only hide the advice until midnight.
 */

/** A muscle at High or above that an exercise leans on, as the exercise line shows it. */
internal data class FatigueTag(val muscle: Muscle, val level: FatigueLevel)

/** What the day card says about fatigue. [projected] is true for another day than today: levels at 08:00 on that day. */
internal data class TodayAdvice(
    val advice: FatigueAdvice.Today,
    val projected: Boolean,
    val atMs: Long,
    /** The routine "Do <routine>" begins instead (rest panel only); null when none qualifies. */
    val other: WorkoutPreset?,
) {
    val leans: List<FatigueAdvice.Lean> = when (advice) {
        is FatigueAdvice.Today.Adjust -> advice.leans
        is FatigueAdvice.Today.Rest -> advice.leans
        FatigueAdvice.Today.None -> emptyList()
    }

    /** Each leaning exercise's tag, by routine entry id. */
    val tags: Map<Long, FatigueTag> = leans.associate { it.entry.id to FatigueTag(it.muscle, it.level) }
}

/**
 * The advice for the selected day's [rite], or null for none: the setting is off, the card is not a BEGIN or PLANNED one,
 * no report yet, the lifter said [notToday], or nothing leans on a tired muscle. Today's levels are taken now; another
 * day's at 08:00 on it ([PlanFatigue.nextEightAm]) and marked projected. Only a BEGIN day can rest or begin another
 * routine; [weekDone] are the workouts sealed this week, so a routine already done is not offered instead.
 */
internal fun todayAdvice(
    kind: DayKind,
    rite: WorkoutPreset?,
    presets: List<WorkoutPreset>,
    weekDone: Collection<WorkoutSession>,
    report: FatigueReport?,
    selectedDay: Int,
    zone: ZoneId,
    enabled: Boolean,
    notToday: Boolean,
): TodayAdvice? {
    if (report == null || rite == null || (kind != DayKind.BEGIN && kind != DayKind.PLANNED)) return null
    val begin = kind == DayKind.BEGIN
    if (begin && notToday) return null
    val atMs = if (begin) report.nowMs else PlanFatigue.nextEightAm(selectedDay, report.nowMs, zone)
    val advice = FatigueAdvice.today(rite.entries, report, atMs, restOffered = begin, enabled = enabled)
    if (advice == FatigueAdvice.Today.None) return null
    val other = if (advice is FatigueAdvice.Today.Rest) {
        FatigueAdvice.freshestOther(presets, rite.id, weekDone.mapNotNull { it.presetId }.toSet(), report, atMs)
    } else {
        null
    }
    return TodayAdvice(advice, projected = !begin, atMs = atMs, other = other)
}

// ----------------------------------------------------------------------------------------------- words

/** "Wed": the word for when a muscle is fresh; null when it already is. */
private fun freshWord(freshBy: LightBy, locale: Locale): String? = when (freshBy) {
    LightBy.None -> null
    LightBy.Today -> Words.text(UiText.res(R.string.fatigue_fresh_word_today))
    LightBy.Tomorrow -> Words.text(UiText.res(R.string.fatigue_fresh_word_tomorrow))
    is LightBy.Weekday -> DayNames.short(freshBy.day.value, locale).orEmpty()
    LightBy.OverAWeek -> Words.text(UiText.res(R.string.fatigue_fresh_word_over_week))
}

private fun joinDot(parts: List<String>): String =
    parts.reduce { head, next -> Words.text(UiText.res(R.string.fatigue_advice_sub, head, next)) }

/** "Lower chest is still High". */
internal fun adjustTitle(advice: TodayAdvice, adjust: FatigueAdvice.Today.Adjust): String =
    Words.text(
        UiText.res(
            if (advice.projected) R.string.fatigue_advice_title_projected else R.string.fatigue_advice_title_now,
            adjust.worst.label, levelWord(adjust.level),
        ),
    )

/** "Fresh by Tue · 2 exercises lean on it", with "Projected" first on another day. */
internal fun adjustSub(advice: TodayAdvice, adjust: FatigueAdvice.Today.Adjust, report: FatigueReport, zone: ZoneId, locale: Locale = Locale.getDefault()): String {
    val parts = listOfNotNull(
        Words.text(UiText.res(R.string.fatigue_advice_projected)).takeIf { advice.projected },
        freshByLine(report.reading(adjust.worst, zone).freshBy, locale),
        Words.text(UiText.plural(R.plurals.fatigue_advice_leans, adjust.leans.size, adjust.leans.size)),
    )
    return joinDot(parts)
}

/** "Quads Very high, glutes and hamstrings High · from Saturday's session". */
internal fun restSub(rest: FatigueAdvice.Today.Rest, report: FatigueReport, zone: ZoneId, locale: Locale = Locale.getDefault()): String {
    val groups = rest.muscles.groupBy({ it.second }, { it.first })
        .entries.sortedByDescending { it.key }
        .map { (level, muscles) ->
            val names = ListText.and(muscles.mapIndexed { i, m -> if (i == 0) m.label else m.label.lowercase() })
            Words.text(UiText.res(R.string.fatigue_rest_level_item, names, levelWord(level)))
        }
        .reduce { head, next -> Words.text(UiText.res(R.string.list_join, head, next)) }
    val cause = report.reading(rest.muscles.first().first, zone).causes.firstOrNull() ?: return groups
    val day = dayWord(cause.sessionAtMs, report.nowMs, zone, locale)
    return Words.text(UiText.res(R.string.fatigue_right_now_subtitle, groups, day))
}

/** "Fresh by quads Wed, glutes Tue"; null when every listed muscle is already fresh. */
internal fun restFresh(rest: FatigueAdvice.Today.Rest, report: FatigueReport, zone: ZoneId, locale: Locale = Locale.getDefault()): String? {
    val items = rest.muscles.mapNotNull { (muscle, _) ->
        freshWord(report.reading(muscle, zone).freshBy, locale)?.let { word ->
            Words.text(UiText.res(R.string.fatigue_rest_fresh_item, muscle.label.lowercase(), word))
        }
    }
    if (items.isEmpty()) return null
    return Words.text(UiText.res(R.string.fatigue_rest_fresh_line, items.reduce { head, next -> Words.text(UiText.res(R.string.list_join, head, next)) }))
}

// ---------------------------------------------------------------------------------------- the card parts

/** The amber rule and faint wash behind the line and the panel: a note inside the card, not a card of its own. */
private fun Modifier.noteBar(): Modifier = this
    .fillMaxWidth()
    .background(IronvellumColors.Ink.copy(alpha = 0.05f))
    .drawBehind { drawRect(IronvellumColors.Caution, Offset.Zero, Size(3.dp.toPx(), size.height)) }

/** Item 1's line: the worst muscle, its level and when it is fresh, and Adjust (null on a day that cannot begin). */
@Composable
internal fun FatigueAdviceLine(title: String, sub: String, level: FatigueLevel, onAdjust: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(
        modifier.noteBar().heightIn(min = 48.dp).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FatigueSwatch(level)
        Spacer(Modifier.width(10.dp))
        Column(
            Modifier.weight(1f).padding(vertical = 6.dp).semantics(mergeDescendants = true) { contentDescription = "$title. $sub" },
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = IronvellumColors.Ink, maxLines = 1)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted, maxLines = 2)
        }
        if (onAdjust != null) {
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.fatigue_advice_adjust_button), onClick = onAdjust)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.fatigue_advice_adjust_button),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = IronvellumColors.SystemGreen,
                )
            }
        }
    }
}

/** Item 4's panel: why to rest, and Do <routine> (when one is fresh) beside Rest today. */
@Composable
internal fun FatigueRestPanel(
    title: String,
    sub: String,
    fresh: String?,
    doLabel: String?,
    onDo: () -> Unit,
    onRest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.noteBar().padding(start = 15.dp, end = 12.dp, top = 10.dp, bottom = 8.dp)) {
        Column(Modifier.semantics(mergeDescendants = true) {}) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = IronvellumColors.Ink)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
            if (fresh != null) Text(fresh, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (doLabel != null) IronvellumButton(doLabel, onDo, Modifier.weight(1f))
            IronvellumButton(stringResource(R.string.fatigue_rest_rest_button), onRest, Modifier.weight(1f), quiet = true)
        }
    }
}

/** A quiet centred link: the card's last line on a rest-panel day (begin as planned, anyway) and the sheet's Not today. */
@Composable
internal fun QuietLink(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = IronvellumColors.InkMuted, textAlign = TextAlign.Center)
    }
}

// ----------------------------------------------------------------------------------------------- the sheet

internal enum class AdjustChoice { KEEP, LIGHTEN, SWAP }

/** "Shoulders, triceps; spares lower chest": what the stand-in works, and what it does to the muscle that is tired. */
internal fun swapCaption(swap: EntryAdjust.Swap, tired: Muscle): String {
    val shares = MuscleMap.profile(swap.exerciseName, swap.modifiers)?.muscles.orEmpty()
    val main = shares.filterValues { it >= FatigueTuning.TRAINS_SHARE }.entries
        .sortedWith(compareByDescending<Map.Entry<Muscle, Double>> { it.value }.thenBy { it.key.ordinal })
        .mapIndexed { i, (muscle, _) -> if (i == 0) muscle.label else muscle.label.lowercase() }
    val worked = main.joinToString(", ")
    val onTired = shares[tired] ?: 0.0
    return Words.text(
        UiText.res(if (onTired > 0.0) R.string.fatigue_adjust_swap_light else R.string.fatigue_adjust_swap_spares, worked, tired.label.lowercase()),
    )
}

/** The override the chosen [choices] make; a swap chosen with no stand-in to swap to is a Keep. */
internal fun adjustOverride(choices: Map<Long, AdjustChoice>, swaps: Map<Long, EntryAdjust.Swap>): SessionOverride =
    SessionOverride(
        choices.mapNotNull { (id, choice) ->
            when (choice) {
                AdjustChoice.KEEP -> null
                AdjustChoice.LIGHTEN -> id to EntryAdjust(lighten = true)
                AdjustChoice.SWAP -> swaps[id]?.let { id to EntryAdjust(swap = it) }
            }
        }.toMap(),
    )

/** How many exercises the [override] changes: the "N" of "Begin with N changes". */
internal fun changeCount(override: SessionOverride): Int = override.entries.values.count { !it.isEmpty }

/**
 * Adjust today: each exercise that leans on a tired muscle with Keep (default), Lighten, and Swap when there is a
 * stand-in whose muscles are all below High. Begin starts today's workout with the changes; the routine is not edited.
 */
@Composable
internal fun AdjustDialog(
    rite: WorkoutPreset,
    advice: TodayAdvice,
    adjust: FatigueAdvice.Today.Adjust,
    report: FatigueReport,
    zone: ZoneId,
    swaps: Map<Long, EntryAdjust.Swap>,
    onBegin: (SessionOverride) -> Unit,
    onNotToday: () -> Unit,
    onDismiss: () -> Unit,
) {
    val choices = remember { mutableStateMapOf<Long, AdjustChoice>() }
    val override = adjustOverride(choices, swaps)
    val changes = changeCount(override)
    val cause = report.reading(adjust.worst, zone).causes.firstOrNull()
    val day = cause?.let { dayWord(it.sessionAtMs, report.nowMs, zone) } ?: Words.text(UiText.res(R.string.fatigue_day_today))
    IronvellumDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fatigue_adjust_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    UiText.themed(R.string.fatigue_adjust_caption, R.string.fatigue_adjust_caption__t, adjust.worst.label, levelWord(adjust.level), day, rite.name).asString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
                advice.leans.forEach { lean ->
                    val swap = swaps[lean.entry.id]
                    AdjustGroup(
                        entry = lean.entry,
                        tired = lean.muscle,
                        choice = choices[lean.entry.id] ?: AdjustChoice.KEEP,
                        swap = swap,
                        onChoose = { choices[lean.entry.id] = it },
                    )
                }
                QuietLink(stringResource(R.string.fatigue_adjust_not_today), onNotToday)
            }
        },
        dismissButton = { IronvellumButton(stringResource(R.string.common_cancel), onDismiss) },
        confirmButton = {
            IronvellumButton(
                label = if (changes == 0) stringResource(R.string.today_begin_button, rite.name)
                else UiText.plural(R.plurals.fatigue_adjust_begin_changes, changes, changes).asString(),
                onClick = { onBegin(override) },
            )
        },
    )
}

@Composable
private fun AdjustGroup(
    entry: PresetEntry,
    tired: Muscle,
    choice: AdjustChoice,
    swap: EntryAdjust.Swap?,
    onChoose: (AdjustChoice) -> Unit,
) {
    val sets = Lighten.workingSets(entry)
    val lighter = Lighten.workingSets(Lighten.apply(entry, repsCounted = true))
    val groupName = stringResource(R.string.fatigue_adjust_group_description, entry.exerciseName)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(entry.exerciseName, style = MaterialTheme.typography.titleSmall, color = IronvellumColors.Ink, modifier = Modifier.weight(1f))
            Text(
                SchemeText.figure(entry.scheme, entry.targetSets, entry.targetReps, null, null),
                style = MaterialTheme.typography.labelMedium,
                color = IronvellumColors.InkMuted,
            )
        }
        Column(Modifier.selectableGroup().semantics { contentDescription = groupName }) {
            ChoiceRow(
                title = stringResource(R.string.fatigue_adjust_keep),
                caption = UiText.plural(R.plurals.fatigue_adjust_keep_caption, sets, sets).asString(),
                selected = choice == AdjustChoice.KEEP,
                onSelect = { onChoose(AdjustChoice.KEEP) },
            )
            ChoiceRow(
                title = stringResource(R.string.fatigue_adjust_lighten),
                caption = UiText.plural(R.plurals.fatigue_adjust_lighten_caption, lighter, lighter).asString(),
                selected = choice == AdjustChoice.LIGHTEN,
                onSelect = { onChoose(AdjustChoice.LIGHTEN) },
            )
            if (swap != null) {
                ChoiceRow(
                    title = stringResource(R.string.fatigue_adjust_swap, swap.exerciseName),
                    caption = swapCaption(swap, tired),
                    selected = choice == AdjustChoice.SWAP,
                    onSelect = { onChoose(AdjustChoice.SWAP) },
                )
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}
