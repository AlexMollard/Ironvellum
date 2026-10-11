package com.ironvellum.app.ui.train

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironvellum.app.domain.SetKind
import com.ironvellum.app.ui.components.Term
import com.ironvellum.app.ui.components.TermDialog
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors

/**
 * A muted colour per kind, from the structural palette: a tag is a label, not a
 * reward. Working is the plain default and has none.
 */
internal fun SetKind.tint(): Color = when (this) {
    SetKind.WORKING -> IronvellumColors.InkMuted
    SetKind.WARMUP -> IronvellumColors.InkMuted
    SetKind.DROP -> IronvellumColors.SystemGreen
    SetKind.FAILURE -> IronvellumColors.DangerRed.copy(alpha = 0.8f)
    SetKind.REST_PAUSE -> IronvellumColors.Ink.copy(alpha = 0.85f)
}

/**
 * The set's number with its kind tag under it, and the one tap target that
 * changes the kind. A warm-up's number already reads "W", so only Drop, Failure
 * and Rest-pause add a letter. [showHint] puts a faint caret under a plain
 * working set's number, so the active row says the number can be tapped.
 */
@Composable
internal fun SetNumberCell(
    label: String,
    kind: SetKind,
    onKind: (SetKind) -> Unit,
    tone: Color,
    modifier: Modifier = Modifier,
    showHint: Boolean = false,
    numberStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodySmall,
) {
    var open by remember { mutableStateOf(false) }
    var explain by remember { mutableStateOf(false) }
    val tag = kind.tag.takeIf { it.isNotEmpty() && it != label }
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = "Change set type") { open = true }
            .semantics { contentDescription = "Set $label, ${kind.label}" },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label,
                style = numberStyle,
                fontFamily = ChakraPetch,
                color = tone,
                textAlign = TextAlign.Center,
            )
            if (tag != null) {
                Text(
                    tag,
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.Bold,
                    color = kind.tint(),
                )
            } else if (showHint && kind == SetKind.WORKING) {
                Text("▾", fontSize = 9.sp, lineHeight = 9.sp, color = IronvellumColors.InkMuted.copy(alpha = 0.5f))
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = MaterialTheme.shapes.small,
            containerColor = IronvellumColors.VaultHigh,
        ) {
            SetKind.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option.label + if (option == kind) "  ✓" else "",
                            color = if (option == SetKind.FAILURE) IronvellumColors.DangerRed else IronvellumColors.Ink,
                        )
                    },
                    onClick = {
                        open = false
                        if (option != kind) onKind(option)
                    },
                )
            }
            HorizontalDivider(color = IronvellumColors.Rune)
            DropdownMenuItem(
                text = { Text("What are set types?", color = IronvellumColors.InkMuted) },
                onClick = {
                    open = false
                    explain = true
                },
            )
        }
    }
    if (explain) TermDialog(Term.SET_TYPES) { explain = false }
}
