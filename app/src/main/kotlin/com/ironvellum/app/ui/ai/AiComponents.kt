package com.ironvellum.app.ui.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.theme.IronvellumColors

@Composable
internal fun AiCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    InkPanel(modifier.fillMaxWidth(), contentPadding = contentPadding) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
internal fun AiDetail(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, color = IronvellumColors.InkMuted, style = MaterialTheme.typography.bodyMedium)
}

@Composable
internal fun AiActionCard(title: String, detail: String, action: String, icon: ImageVector, onClick: () -> Unit, quiet: Boolean = false) {
    AiCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = IronvellumColors.InkMuted)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AiTitle(title)
                AiDetail(detail)
            }
        }
        IronvellumButton(action, onClick, Modifier.fillMaxWidth(), quiet = quiet)
    }
}

@Composable
internal fun AiStateCard(
    title: String,
    icon: ImageVector? = null,
    detail: String? = null,
    name: String? = null,
    accent: Boolean = false,
) {
    AiCard(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        if (icon == null) {
            CircularProgressIndicator(Modifier.size(24.dp), color = IronvellumColors.Emerald, strokeWidth = 2.dp)
        } else {
            Icon(icon, contentDescription = null, tint = if (accent) IronvellumColors.Emerald else IronvellumColors.InkMuted)
        }
        AiTitle(title)
        name?.let { AiTitle(it) }
        detail?.let { AiDetail(it) }
    }
}

@Composable
internal fun AiDisclosure(title: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val state = stringResource(if (expanded) R.string.ai_disclosure_expanded else R.string.ai_disclosure_collapsed)
    InkPanel(contentPadding = PaddingValues(0.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = !expanded }
                .semantics { stateDescription = state }.heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink)
            Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null, tint = IronvellumColors.InkMuted)
        }
        if (expanded) {
            InkDivider()
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}
