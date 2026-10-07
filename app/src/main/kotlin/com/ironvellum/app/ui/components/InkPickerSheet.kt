package com.ironvellum.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.inkBorder

/**
 * The full-screen picker sheet every "choose one of many" surface shares.
 * Not exercise-specific: the title, the search field, the result [count] and
 * a Close chip are pinned at the top, and [content] fills the rest as a lazy
 * list. The whole sheet sits above the keyboard (imePadding), so the field
 * and the rows under it stay reachable while typing.
 */
@Composable
fun InkPickerSheet(
    title: String,
    onDismiss: () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    searchLabel: String,
    count: Int,
    content: LazyListScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(IronvellumColors.VaultHigh)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = IronvellumColors.Ink,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "$count",
                    style = MaterialTheme.typography.labelSmall,
                    color = IronvellumColors.InkMuted,
                )
                NavChip("Close", null, onClick = onDismiss)
            }
            Spacer(Modifier.height(6.dp))
            PickerSearchField(query = query, onQueryChange = onQueryChange, label = searchLabel)
            Spacer(Modifier.height(10.dp))
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Top,
                // Breathing room under the last row, so the end of a long list
                // never sits flush against (or under) the navigation bar.
                contentPadding = PaddingValues(bottom = 24.dp),
                content = content,
            )
        }
    }
}

/** The ink search box shared by the sheet and the inline explorer list. */
@Composable
fun PickerSearchField(query: String, onQueryChange: (String) -> Unit, label: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(IronvellumColors.Vault, MaterialTheme.shapes.extraSmall)
            .inkBorder(IronvellumColors.Rune, MaterialTheme.shapes.extraSmall, 1.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = IronvellumColors.InkMuted)
        Box(Modifier.padding(start = 8.dp).fillMaxWidth()) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = IronvellumColors.Ink),
                cursorBrush = SolidColor(IronvellumColors.SystemGreen),
                // The placeholder below is a SIBLING Text, so the field itself
                // announced nothing and a screen reader landed on an unlabelled
                // input. The magnifier stays decorative: naming both would
                // read the same thing twice.
                modifier = Modifier
                    .fillMaxWidth()
                    // A single line of text measured 20dp, under the WCAG AA
                    // floor; the row's own padding supplies the visual height.
                    .heightIn(min = 24.dp)
                    .semantics { contentDescription = label },
            )
            if (query.isEmpty()) {
                Text(
                    label.lowercase(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = IronvellumColors.InkMuted,
                )
            }
        }
    }
}

/**
 * A section label with a faint rule running to the edge: it separates groups
 * without looking like a control. The old boxed strip read as a disabled button.
 */
@Composable
fun PickerSectionHeader(label: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = IronvellumColors.InkMuted,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(IronvellumColors.Rune.copy(alpha = 0.6f)))
    }
}
