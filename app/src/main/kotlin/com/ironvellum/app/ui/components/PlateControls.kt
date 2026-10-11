package com.ironvellum.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ironvellum.app.domain.LoadUnit
import com.ironvellum.app.domain.PlateRack
import com.ironvellum.app.domain.PlateSizes
import com.ironvellum.app.ui.theme.IronvellumColors

/** The bar chooser's "Other" option: not a bar size, so it can never collide with one. */
private const val OTHER_BAR = -1.0

/** The bar's custom stepper range, kg or lb. */
private const val BAR_MAX = 60.0

/**
 * The bar: the unit's usual bars and "Other" with a stepper. Shared by the Armoury page and the plate
 * sheet's per-exercise override, so both read and set a bar the same way.
 */
@Composable
internal fun BarChooser(unit: LoadUnit, bar: Double, onPick: (Double) -> Unit) {
    val presets = PlateSizes.bars(unit)
    val custom = bar !in presets
    val step = if (unit == LoadUnit.LB) 5.0 else 2.5
    Column {
        InkSegmented(
            options = presets.map { it to "${PlateRack.number(it)} ${unit.label}" } + (OTHER_BAR to "Other"),
            selected = if (custom) OTHER_BAR else bar,
            // A custom bar starts one step above the one in use, which is never a standard bar.
            onPick = { picked -> onPick(if (picked == OTHER_BAR) (if (custom) bar else bar + step) else picked) },
        )
        if (custom) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Bar weight", style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted, modifier = Modifier.weight(1f))
                StepButton("−", "Lighter bar") { onPick((bar - step).coerceAtLeast(step)) }
                Box(Modifier.width(64.dp), contentAlignment = Alignment.Center) {
                    Text(
                        PlateRack.number(bar),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = IronvellumColors.Ink,
                        textAlign = TextAlign.Center,
                    )
                }
                StepButton("+", "Heavier bar") { onPick((bar + step).coerceAtMost(BAR_MAX)) }
                Text(unit.label, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted, modifier = Modifier.width(28.dp).padding(start = 4.dp))
            }
        }
    }
}

/** One plate size: "25 kg", then − pairs +. */
@Composable
internal fun PairRow(size: Double, unit: LoadUnit, count: Int, onCount: (Int) -> Unit) {
    val name = "${PlateRack.number(size)} ${unit.label}"
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink, modifier = Modifier.weight(1f))
        StepButton("−", "Fewer $name pairs") { onCount(count - 1) }
        Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "$count",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (count == 0) IronvellumColors.InkMuted else IronvellumColors.Ink,
                textAlign = TextAlign.Center,
            )
        }
        StepButton("+", "More $name pairs") { onCount(count + 1) }
        Text(
            if (count == 1) "pair" else "pairs",
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.width(40.dp).padding(start = 4.dp),
        )
    }
}

/** "Heaviest you can load 297.5 kg · Loads step by 2.5 kg", the Armoury block's footer. */
internal fun rackFooter(rack: PlateRack): String {
    val unit = rack.unit.label
    val heaviest = "Heaviest you can load ${PlateRack.number(rack.heaviest)} $unit"
    val step = rack.step ?: return heaviest
    return "$heaviest · Loads step by ${PlateRack.number(step)} $unit"
}
