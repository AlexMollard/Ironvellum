package com.ironvellum.app.ui.program

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.theme.IronvellumColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Temper's recovery finding, one routine opened: when each of its last workouts began and how tired the muscle was
 * then, where the load came from, and the choices (move the routine, trim the lift that causes it, or keep as is).
 * One radio group and one docked button; nothing is written until it is pressed.
 */

/** "Tue 7 Oct": the day a workout began. */
internal fun startedOn(atMs: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
    Instant.ofEpochMilli(atMs).atZone(zone).format(DateTimeFormatter.ofPattern("EEE d MMM", locale))

/** The hub row's second line: "Lats was High at 3 of its last 3 starts". */
@Composable
internal fun recoverySubline(rite: RecoveryRite): String {
    val f = rite.finding
    return stringResource(R.string.optimise_recovery_subline, f.muscle.label, f.tiredStarts, f.starts.size)
}

@Composable
internal fun RecoveryPage(
    rite: RecoveryRite,
    saving: Boolean,
    error: String?,
    onApply: (OptOption) -> Unit,
    onKeep: () -> Unit,
    onBack: () -> Unit,
) {
    val finding = rite.finding
    val options = rite.change.options
    val keepIndex = options.size
    var chosen by rememberSaveable(rite.change.id) { mutableIntStateOf(0) }
    val picked = chosen.coerceIn(0, keepIndex)
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(20.dp))
            ForgeHeader(
                finding.rite,
                onBack,
                subtitle = stringResource(R.string.optimise_recovery_subtitle, finding.muscle.label),
                status = stringResource(R.string.optimise_kind_recovery),
            )
            Text(
                stringResource(R.string.optimise_recovery_intro, finding.muscle.label, finding.tiredStarts, finding.starts.size),
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
                modifier = Modifier.padding(top = 8.dp),
            )
            error?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.DangerRed, modifier = Modifier.padding(top = 8.dp)) }

            OptLabel(stringResource(R.string.optimise_recovery_starts_label))
            InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp)) {
                finding.starts.forEachIndexed { index, start ->
                    if (index > 0) InkDivider()
                    val level = levelWord(start.level)
                    val day = startedOn(start.startedAtMs)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .semantics(mergeDescendants = true) { contentDescription = "$day, ${finding.muscle.label} $level" },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(day, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink, modifier = Modifier.weight(1f))
                        FatigueSwatch(start.level)
                        Text(level, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink, modifier = Modifier.width(88.dp))
                    }
                }
            }
            finding.cause?.let { cause ->
                Text(
                    if (rite.causeRite != null) {
                        stringResource(R.string.optimise_recovery_cause, rite.causeRite, startedOn(cause.atMs), cause.exerciseName)
                    } else {
                        UiText.themed(R.string.optimise_recovery_cause_free, R.string.optimise_recovery_cause_free__t, startedOn(cause.atMs), cause.exerciseName).asString()
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            OptLabel(stringResource(R.string.optimise_recovery_choose_label))
            Column(Modifier.fillMaxWidth().selectableGroup()) {
                options.forEachIndexed { index, option ->
                    ChoiceRow(option.label, option.reason, selected = picked == index, onSelect = { chosen = index })
                }
                ChoiceRow(
                    stringResource(R.string.optimise_recovery_keep_option),
                    stringResource(R.string.optimise_recovery_keep_reason, finding.muscle.label, finding.rite),
                    selected = picked == keepIndex,
                    onSelect = { chosen = keepIndex },
                )
            }
            Spacer(Modifier.height(20.dp))
        }
        ForgeDock(
            primary = stringResource(if (picked == keepIndex) R.string.optimise_recovery_keep_option else R.string.common_apply),
            onPrimary = { if (picked == keepIndex) onKeep() else onApply(options[picked]) },
            busy = saving,
            note = if (picked == keepIndex) null else stringResource(R.string.optimise_apply_note),
        )
    }
}
