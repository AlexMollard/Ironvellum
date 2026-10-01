package com.ironvellum.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ironvellum.app.data.Notifications
import com.ironvellum.app.ui.theme.IronvellumColors

/**
 * The real notification state for one channel, re-read every time the screen
 * resumes: the lifter fixes a block in system settings and comes back, and
 * the screen must say so without being reopened.
 */
@Composable
fun rememberNotificationAccess(channelId: String): Notifications.Access {
    val context = LocalContext.current
    var access by remember(channelId) { mutableStateOf(Notifications.access(context, channelId)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        access = Notifications.access(context, channelId)
    }
    return access
}

/**
 * What a notification setting shows while its posts cannot arrive: [blockedText]
 * and the one way out, the system screen for whichever switch is off. Nothing
 * while [access] can post. The runtime dialog is no use here: after a second
 * denial Android stops showing it at all.
 */
@Composable
fun NotificationBlockedNotice(
    channelId: String,
    access: Notifications.Access,
    blockedText: String,
    modifier: Modifier = Modifier,
) {
    if (access.canPost) return
    val context = LocalContext.current
    Column(modifier) {
        Text(
            blockedText,
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.SovereignGold,
        )
        Spacer(Modifier.height(6.dp))
        IronvellumButton(
            label = "Open notification settings",
            quiet = true,
            onClick = {
                runCatching { context.startActivity(Notifications.settingsIntent(context, channelId, access)) }
            },
        )
    }
}
