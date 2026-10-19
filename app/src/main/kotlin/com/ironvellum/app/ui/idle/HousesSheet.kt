package com.ironvellum.app.ui.idle

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.domain.RelicHouse
import com.ironvellum.app.domain.RelicHouses
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.IronvellumDialog
import com.ironvellum.app.ui.theme.IronvellumColors

/**
 * How the four relic houses work, as the app's one bottom sheet. Plain text in a plain column: the
 * dialog scrolls its own text slot, so nothing in here may scroll. The first line is the rule that
 * matters: every relic lifts the WHOLE essence rate, and a house only adds set bonuses.
 */
@Composable
internal fun HousesSheet(onDismiss: () -> Unit) {
    IronvellumDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.idle_houses_title)) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    stringResource(R.string.idle_houses_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = IronvellumColors.InkMuted,
                )
                RelicHouse.entries.forEach { house ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${house.title} · ${house.habit}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = IronvellumColors.Ink)
                        Text(stringResource(R.string.idle_houses_follows, house.follows), style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
                        Text(UiText.plural(R.plurals.idle_houses_set_bonus, RelicHouses.PAIR, RelicHouses.PAIR, house.pairBonus).asString(), style = MaterialTheme.typography.bodySmall, color = IronvellumColors.Ink)
                        Text(UiText.plural(R.plurals.idle_houses_set_bonus, RelicHouses.FULL, RelicHouses.FULL, house.fullBonus).asString(), style = MaterialTheme.typography.bodySmall, color = IronvellumColors.Ink)
                    }
                }
                Text(
                    stringResource(R.string.idle_houses_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
            }
        },
        confirmButton = { IronvellumButton(stringResource(R.string.idle_got_it_button), onClick = onDismiss) },
    )
}

/** The quiet link that opens the houses sheet, on a 48dp target. */
@Composable
internal fun HousesLink(onClick: () -> Unit) {
    val openLabel = stringResource(R.string.idle_houses_link_description)
    Text(
        stringResource(R.string.idle_houses_link),
        style = MaterialTheme.typography.bodySmall,
        color = IronvellumColors.SystemGreen,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = openLabel, onClick = onClick)
            .padding(horizontal = 4.dp)
            .wrapContentHeight(Alignment.CenterVertically),
    )
}
