package com.ironvellum.app.ui.idle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.domain.CrestEffects
import com.ironvellum.app.domain.Gacha
import com.ironvellum.app.domain.Veil
import com.ironvellum.app.domain.fmt
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkRail
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.IronvellumDialog
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.components.TierDot
import com.ironvellum.app.ui.components.rarityWord
import com.ironvellum.app.ui.theme.IronvellumColors

/**
 * Buying an inscription, as one sheet: what it costs, what you have and what is left, the next price,
 * what a draw can give and what pity says about it, then one confirm. It is drawn the moment it is
 * bought, so the sheet says so. Every figure is read from the rules ([Veil], [Gacha]) and not restated.
 *
 * There is no scroll in the text slot: the sheet scrolls its own column, and these rows are short.
 */
@Composable
internal fun VeilBuySheet(
    cost: Long,
    essence: Long,
    nextCost: Long,
    crest: CrestEffects,
    ownedFrames: Set<String>,
    pity: Gacha.Pity,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val shares = remember(ownedFrames) { Gacha.typeShares(ownedFrames) }
    IronvellumDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.idle_buy_title)) },
        text = {
            Column {
                Text(stringResource(R.string.idle_buy_intro), style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
                Column(Modifier.padding(top = 10.dp)) {
                    InkDivider()
                    SheetRow(stringResource(R.string.idle_buy_price_label), stringResource(R.string.idle_buy_price_value, cost), strong = true)
                    InkDivider()
                    SheetRow(stringResource(R.string.idle_buy_have_label), "%,d".fmt(essence))
                    InkDivider()
                    SheetRow(stringResource(R.string.idle_buy_after_label), "%,d".fmt((essence - cost).coerceAtLeast(0L)))
                    InkDivider()
                }
                Text(
                    stringResource(R.string.idle_buy_next_cost, "%,d".fmt(nextCost), crest.offeringStep),
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier.padding(top = 8.dp),
                )
                SectionHeader(stringResource(R.string.idle_buy_gives_header), topPadding = 16.dp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Share(stringResource(R.string.idle_buy_share_echoes), shares.echoes, Modifier.weight(1f))
                    Share(stringResource(R.string.idle_buy_share_relic), shares.relic, Modifier.weight(1f))
                    Share(stringResource(R.string.idle_buy_share_crest), shares.crest, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Gacha.DROP_TABLE.forEach { odds ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            TierDot(odds.rarity, Modifier.size(18.dp))
                            Text(percent(odds.chance), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = IronvellumColors.Ink)
                            Text(rarityWord(odds.rarity), style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
                        }
                    }
                }
                // What an echo draw pays at each rarity, under the crest worn now (the roller's own function).
                val echoes = Gacha.DROP_TABLE.joinToString(", ") {
                    val range = it.echoRange(crest.draw)
                    "%,d–%,d".fmt(range.first, range.last)
                }
                Text(
                    stringResource(R.string.idle_buy_echo_pays, echoes),
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    Gacha.pityLine(pity),
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        },
        dismissButton = { IronvellumButton(label = stringResource(R.string.common_cancel), onClick = onDismiss) },
        confirmButton = { IronvellumButton(label = stringResource(R.string.idle_buy_confirm_button), onClick = onConfirm, enabled = essence >= cost) },
    )
}

private fun percent(fraction: Double): String = "${Math.round(fraction * 100)}%"

/** A row of the sheet: the label muted on the left, the figure on the right. */
@Composable
private fun SheetRow(label: String, value: String, strong: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = IronvellumColors.InkMuted, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
            color = IronvellumColors.Ink,
        )
    }
}

/** One kind of reward and its share of a draw, over a flat bar. */
@Composable
private fun Share(label: String, share: Double, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink, modifier = Modifier.weight(1f))
            Text(percent(share), style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.InkMuted)
        }
        InkRail(share.toFloat(), Modifier.padding(top = 6.dp), height = 4.dp, fill = SolidColor(IronvellumColors.InkMuted))
    }
}
