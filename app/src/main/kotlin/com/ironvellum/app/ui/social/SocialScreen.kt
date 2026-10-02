package com.ironvellum.app.ui.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import com.ironvellum.app.data.InboxNotifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.data.cloud.Cloud
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.InkTabs

private enum class GuildTab(val label: String) {
    FEED("TIDINGS"),
    INBOX("MISSIVES"),
    BOARD("RECKONING"),
    // "ALLIES", not "GUILD" — the bottom nav tab already says Allies; repeating
    // it here put the same word on screen three times.
    ALLIES("ALLIES"),
}

/**
 * One home for everything social. Signed out (or with no backend) it is only
 * the account screen: every tab would otherwise show its own "sign in" panel,
 * four layouts for one message.
 */
@Composable
fun SocialScreen(
    onOpenLifter: (userId: String, displayName: String) -> Unit,
    onOpenComments: (sessionId: String, ownerId: String, headline: String) -> Unit,
    onOpenAccount: () -> Unit,
    inboxRequest: Int = 0,
) {
    val app = LocalContext.current.applicationContext as IronvellumApp
    val account by app.accountRepository.account.collectAsStateWithLifecycle()
    val cloudConfigured = Cloud.config.collectAsStateWithLifecycle().value != null
    val signedIn = cloudConfigured && account != null

    // Saveable: opening a workout's comments from the INBOX pushes a route
    // over this one, and a plain remember came back on FEED after BACK.
    var tab by rememberSaveable { mutableStateOf(GuildTab.FEED) }
    // Signing in HERE lands on ALLIES, where a fresh Google account's
    // claim-your-name panel lives; a restored session keeps its tab.
    var sawSignedOut by remember { mutableStateOf(!signedIn) }
    LaunchedEffect(signedIn) {
        if (!signedIn) {
            sawSignedOut = true
        } else if (sawSignedOut) {
            tab = GuildTab.ALLIES
            sawSignedOut = false
        }
    }
    // A notification tap asks for INBOX. The served count is saveable so BACK
    // from a workout's comments (or a rotation) does not yank the lifter off
    // whichever tab they moved to since.
    var servedInboxRequest by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(inboxRequest) {
        if (inboxRequest > servedInboxRequest) {
            servedInboxRequest = inboxRequest
            tab = GuildTab.INBOX
        }
    }

    if (!signedIn) {
        AccountScreen(onBack = {}, onOpenLifter = onOpenLifter)
        return
    }

    val unread by app.cloudSync.inboxUnread.collectAsStateWithLifecycle()

    // Opening the inbox is the moment ally notifications become relevant: clear
    // the one posted for it, and ask for the permission once, here, rather than
    // at launch where the lifter has no reason to say yes. Our own dialog comes
    // first so the system prompt never lands without context; NOT NOW consumes
    // the once-per-install ask exactly like ALLOW would.
    val context = LocalContext.current
    var explainNotifications by rememberSaveable { mutableStateOf(false) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(tab) {
        if (tab != GuildTab.INBOX) return@LaunchedEffect
        InboxNotifier.cancel(context)
        if (InboxNotifier.takeFirstAsk(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            explainNotifications = true
        }
    }
    if (explainNotifications) {
        AlertDialog(
            shape = MaterialTheme.shapes.medium,
            containerColor = Color(0xFF0D1110),
            onDismissRequest = { explainNotifications = false },
            title = { Text("Ally notifications?") },
            text = { Text("See ally requests, remarks and tributes.") },
            // ALLOW is the only path to the system prompt; NOT NOW just closes.
            confirmButton = {
                IronvellumButton(
                    "Allow",
                    onClick = {
                        explainNotifications = false
                        // The permission exists only from 33; older systems
                        // grant notifications by default and need no prompt.
                        if (Build.VERSION.SDK_INT >= 33) {
                            askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
            },
            dismissButton = {
                IronvellumButton("Not now", quiet = true, onClick = { explainNotifications = false })
            },
        )
    }

    // Every tab gets the same margins and starts at the same spot under the
    // pills; the tabs themselves add no outer padding or screen title.
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(18.dp))
        InkTabs(
            labels = GuildTab.entries.map { it.label },
            badges = GuildTab.entries.map { if (it == GuildTab.INBOX) unread else 0 },
            selectedIndex = tab.ordinal,
            onSelect = { tab = GuildTab.entries[it] },
        )
        Spacer(Modifier.height(14.dp))

        when (tab) {
            GuildTab.FEED -> FeedScreen(
                onOpenLifter = onOpenLifter,
                onOpenComments = onOpenComments,
            )
            GuildTab.INBOX -> InboxScreen(
                onOpenLifter = onOpenLifter,
                onOpenComments = onOpenComments,
                onOpenCircle = { tab = GuildTab.ALLIES },
            )
            GuildTab.BOARD -> LeaderboardScreen(onOpenFriend = onOpenLifter)
            GuildTab.ALLIES -> AccountScreen(
                onBack = { tab = GuildTab.FEED },
                onOpenLifter = onOpenLifter,
                onOpenAccount = onOpenAccount,
            )
        }
    }
}
