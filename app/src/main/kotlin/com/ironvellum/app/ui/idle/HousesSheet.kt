package com.ironvellum.app.ui.idle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ironvellum.app.domain.RelicHouse
import com.ironvellum.app.domain.RelicHouses
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
        title = { Text("Relic houses") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "Every relic lifts your whole essence rate, and the strongest counts most. " +
                        "A house follows one habit and adds set bonuses that pay while you keep it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = IronvellumColors.InkMuted,
                )
                RelicHouse.entries.forEach { house ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${house.title} · ${house.habit}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = IronvellumColors.Ink)
                        Text("Follows ${house.follows}.", style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
                        Text("${RelicHouses.PAIR} relics: ${house.pairBonus}", style = MaterialTheme.typography.bodySmall, color = IronvellumColors.Ink)
                        Text("${RelicHouses.FULL} relics: ${house.fullBonus}", style = MaterialTheme.typography.bodySmall, color = IronvellumColors.Ink)
                    }
                }
                Text(
                    "Bonuses only touch your essence rate. They never change your level, XP, strength or titles. " +
                        "Relics come from levels and from essence you earned by training.",
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
            }
        },
        confirmButton = { IronvellumButton("Got it", onClick = onDismiss) },
    )
}
