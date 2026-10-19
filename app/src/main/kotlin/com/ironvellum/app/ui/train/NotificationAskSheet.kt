package com.ironvellum.app.ui.train

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.domain.FirstRun
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.IronvellumDialog
import com.ironvellum.app.ui.theme.IronvellumColors

/**
 * The in-app ask that comes before Android's own notification prompt, on a trial's first opens. It says
 * what the notifications are for, so the system's one-shot prompt is answered with the reason in hand.
 * Allow fires that prompt; Not now (or a swipe, the scrim, back) does not, and the sheet comes back on
 * the next trial until it has been declined [FirstRun.MAX_NOTIFICATION_ASKS] times.
 */
@Composable
internal fun NotificationAskSheet(onAllow: () -> Unit, onNotNow: () -> Unit) {
    IronvellumDialog(
        onDismissRequest = onNotNow,
        title = { Text(stringResource(R.string.notification_ask_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.notification_ask_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = IronvellumColors.InkMuted,
                )
                Column(Modifier.padding(top = 8.dp)) {
                    AskRow(
                        Icons.Outlined.Timer,
                        stringResource(R.string.notification_ask_rest_title),
                        stringResource(R.string.notification_ask_rest_body),
                    )
                    InkDivider()
                    AskRow(
                        Icons.Outlined.Notifications,
                        UiText.themed(R.string.notification_ask_live_title, R.string.notification_ask_live_title__t).asString(),
                        UiText.themed(R.string.notification_ask_live_body, R.string.notification_ask_live_body__t).asString(),
                    )
                }
                Text(
                    UiText.themedPlural(
                        R.plurals.notification_ask_again,
                        R.plurals.notification_ask_again__t,
                        FirstRun.MAX_NOTIFICATION_ASKS,
                    ).asString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        },
        confirmButton = { IronvellumButton(label = stringResource(R.string.notification_ask_allow_button), onClick = onAllow) },
        dismissButton = { IronvellumButton(label = stringResource(R.string.notification_ask_not_now_button), onClick = onNotNow, quiet = true) },
    )
}

@Composable
private fun AskRow(icon: ImageVector, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, contentDescription = null, tint = IronvellumColors.InkMuted, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = IronvellumColors.Ink)
            Text(body, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
        }
    }
}
