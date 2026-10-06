package com.ironvellum.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ironvellum.app.ui.theme.IronvellumColors

/**
 * Title left, BACK chip right: the header every pushed screen uses, 52dp tall.
 * The caller supplies the gutter (and any top padding) through [modifier].
 * [actions] sit between the title and BACK (icon buttons, a second chip).
 * [backDescription] is what a screen reader says for BACK when "BACK" alone is not enough.
 */
@Composable
fun PushedHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backDescription: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = IronvellumColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        actions()
        NavChip(
            "BACK",
            null,
            onClick = onBack,
            modifier = if (backDescription != null) Modifier.semantics { contentDescription = backDescription } else Modifier,
        )
    }
}
