package com.ironvellum.app.ui.components

import com.ironvellum.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironvellum.app.domain.ModifierChip
import com.ironvellum.app.domain.ModifierFlag
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.inkBorder

/**
 * The one modifier picker: toggle chips, three to a row, for the [chips] of [com.ironvellum.app.domain.modifierChips].
 * The live trial's dialog and the rite editor both draw it, so a modifier looks and behaves the same wherever it is set.
 *
 * A chip that is set though the movement does not offer it (a deficit on a bench press, a typo) wears a red outline and
 * is explained under the grid; it stays tappable so it can be removed, and is never dropped for the lifter.
 */
@Composable
fun ModifierChipGrid(
    chips: List<ModifierChip>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        chips.chunked(3).forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { chip -> ModifierChipCell(chip, Modifier.weight(1f), onToggle) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        val misfit = chips.filter { it.flag == ModifierFlag.NOT_APPLICABLE }.map { it.token }
        val unknown = chips.filter { it.flag == ModifierFlag.UNKNOWN }.map { it.token }
        if (misfit.isNotEmpty()) {
            FlagNote(stringResource(R.string.modchips_misfit_note, misfit.joinToString(", ")))
        }
        if (unknown.isNotEmpty()) {
            FlagNote(stringResource(R.string.modchips_unknown_note, unknown.joinToString(", ")))
        }
    }
}

@Composable
private fun FlagNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = IronvellumColors.DangerRed,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun ModifierChipCell(chip: ModifierChip, modifier: Modifier, onToggle: (String) -> Unit) {
    val on = chip.selected
    val shape = MaterialTheme.shapes.extraSmall
    val flagged = chip.flag != ModifierFlag.NONE
    val state = when {
        chip.flag == ModifierFlag.UNKNOWN -> stringResource(R.string.modchips_state_unknown)
        chip.flag == ModifierFlag.NOT_APPLICABLE -> stringResource(R.string.modchips_state_misfit)
        on -> stringResource(R.string.modchips_state_set)
        else -> stringResource(R.string.modchips_state_not_set)
    }
    Box(
        modifier
            .background(
                if (on) {
                    Brush.verticalGradient(listOf(lerp(IronvellumColors.Emerald, IronvellumColors.Vault, 0.6f), lerp(IronvellumColors.Emerald, IronvellumColors.Vault, 0.8f)))
                } else {
                    Brush.verticalGradient(listOf(IronvellumColors.Wells.top, IronvellumColors.Wells.bottom))
                },
                shape,
            )
            .inkBorder(
                when {
                    flagged -> IronvellumColors.DangerRed
                    on -> IronvellumColors.SystemGreen
                    else -> IronvellumColors.Rune
                },
                shape,
                1.dp,
            )
            .semantics { contentDescription = "${chip.token}, $state" }
            .clickable(role = Role.Button, onClickLabel = stringResource(if (on) R.string.modchips_remove_label else R.string.modchips_set_label, chip.token)) { onToggle(chip.token) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Centred and never wrapped: "hold seconds" wrapped, so it measured the full cell and sat start-aligned.
        // At 360dp or a large font it shrinks to fit.
        Text(
            chip.token,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = if (on) IronvellumColors.Ink else IronvellumColors.InkMuted,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 8.sp,
                maxFontSize = MaterialTheme.typography.labelSmall.fontSize,
            ),
        )
    }
}
