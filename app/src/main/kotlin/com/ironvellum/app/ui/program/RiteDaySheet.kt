package com.ironvellum.app.ui.program

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.domain.WorkoutPreset
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.IronvellumDialog
import com.ironvellum.app.ui.theme.IronvellumColors

/** The seven weekdays as the pick grid draws them, then the option that leaves the rite off the cycle. */
internal val DAY_PICKS: List<Pair<Int?, String>>
    get() = listOf(
        1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu",
        5 to "Fri", 6 to "Sat", 7 to "Sun", null to Words.text(UiText.res(R.string.riteday_no_day)),
    )

/**
 * What the sheet says under the grid about the day that is ticked. A free day says so, a held one names its
 * rite, and "No day" says what it costs: Weekly coverage counts only rites that have a day.
 */
internal fun daySheetNote(picked: Int?, saved: List<WorkoutPreset>): String {
    if (picked == null) return Words.text(UiText.themed(R.string.riteday_note_no_day, R.string.riteday_note_no_day__t))
    val holders = saved.filter { it.scheduledDay == picked }.map { it.name }
    return if (holders.isEmpty()) {
        Words.text(UiText.res(R.string.riteday_note_free, dayLabel(picked)))
    } else {
        Words.text(UiText.plural(R.plurals.riteday_note_taken, holders.size, holders.joinToString(" and "), dayLabel(picked)))
    }
}

/**
 * The save step of a single rite: which day of the week it goes on. The first free day is ticked, "No day" is
 * offered, and taken days stay pickable (two rites may share a day) but are named under the grid so the choice
 * is never made blind. A bottom sheet, like the app's other pop-ups.
 */
@Composable
internal fun RiteDaySheet(
    riteName: String,
    saved: List<WorkoutPreset>,
    picked: Int?,
    onPick: (Int?) -> Unit,
    saving: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val taken = saved.filter { it.scheduledDay != null }.groupBy { it.scheduledDay!! }
    IronvellumDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (riteName.isBlank()) {
                    UiText.themed(R.string.riteday_title_blank, R.string.riteday_title_blank__t).asString()
                } else {
                    UiText.res(R.string.riteday_title, riteName).asString()
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                DAY_PICKS.chunked(4).forEach { chunk ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        chunk.forEach { (day, label) ->
                            val holders = day?.let { taken[it] }.orEmpty()
                            PickCell(
                                label = label,
                                selected = picked == day,
                                modifier = Modifier.weight(1f),
                                description = when {
                                    day == null -> UiText.res(R.string.riteday_no_day).asString()
                                    holders.isEmpty() -> UiText.res(R.string.riteday_free_description, dayLabel(day)).asString()
                                    else -> UiText.res(
                                        R.string.riteday_taken_description,
                                        dayLabel(day),
                                        holders.joinToString(" and ") { it.name },
                                    ).asString()
                                },
                                onClick = { onPick(day) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    daySheetNote(picked, saved),
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                if (taken.isNotEmpty()) {
                    Text(
                        UiText.res(
                            R.string.riteday_already_on,
                            taken.entries.sortedBy { it.key }
                                .joinToString(" · ") { (day, rites) -> "${dayShort(day)} ${rites.joinToString(" and ") { it.name }}" },
                        ).asString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = IronvellumColors.InkMuted,
                    )
                }
            }
        },
        confirmButton = { IronvellumButton(label = UiText.themed(R.string.builder_add_rite, R.string.builder_add_rite__t).asString(), onClick = onConfirm, enabled = !saving, modifier = Modifier.heightIn(min = 48.dp)) },
        dismissButton = { IronvellumButton(label = stringResource(R.string.common_cancel), onClick = onDismiss, quiet = true, modifier = Modifier.heightIn(min = 48.dp)) },
    )
}
