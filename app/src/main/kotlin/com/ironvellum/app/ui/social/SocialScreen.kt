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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.data.cloud.Cloud
import com.ironvellum.app.ui.components.IronvellumTabPill

private enum class GuildTab(val label: String) {
    FEED("FEED"),
    INBOX("INBOX"),
    BOARD("BOARD"),
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

    if (!signedIn) {
        AccountScreen(onBack = {}, onOpenLifter = onOpenLifter)
        return
    }

    val unread by app.cloudSync.inboxUnread.collectAsStateWithLifecycle()

    // Every tab gets the same margins and starts at the same spot under the
    // pills; the tabs themselves add no outer padding or screen title.
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(18.dp))
        // Scrollable as a safety net only: four pills plus an unread count
        // fill a 360dp row almost exactly.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GuildTab.entries.forEach { entry ->
                val label = if (entry == GuildTab.INBOX && unread > 0) {
                    "${entry.label} ${if (unread > 99) "99+" else unread.toString()}"
                } else {
                    entry.label
                }
                IronvellumTabPill(label, entry == tab) { tab = entry }
            }
        }
        Spacer(Modifier.height(14.dp))

        when (tab) {
            GuildTab.FEED -> FeedScreen(
                onOpenLifter = onOpenLifter,
                onOpenComments = onOpenComments,
            )
            GuildTab.INBOX -> InboxScreen(
                onOpenLifter = onOpenLifter,
                onOpenComments = onOpenComments,
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
