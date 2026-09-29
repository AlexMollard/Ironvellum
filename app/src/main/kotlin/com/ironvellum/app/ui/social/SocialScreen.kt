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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironvellum.app.IronvellumApp
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
 * One home for everything social. The feed, leaderboard and account screens all
 * existed but had no route into them, so signing in was impossible from the UI —
 * this is the single entry point the bottom bar points at.
 */
@Composable
fun SocialScreen(
    onOpenLifter: (userId: String, displayName: String) -> Unit,
    onOpenComments: (sessionId: String, ownerId: String, headline: String) -> Unit,
) {
    // Saveable: opening a workout's comments from the INBOX pushes a route
    // over this one, and a plain remember came back on FEED after BACK.
    var tab by rememberSaveable { mutableStateOf(GuildTab.FEED) }
    val appContext = LocalContext.current.applicationContext
    val unread by (appContext as IronvellumApp).cloudSync.inboxUnread.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(18.dp))
        // Scrollable as a safety net only: four pills plus an unread count
        // fill a 360dp row almost exactly, and a clipped ALLIES pill would
        // strand the sign-in form.
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
            // Account carries the sign-in form, so "back" from it just returns
            // to the feed rather than popping the whole tab off the stack.
            GuildTab.FEED -> FeedScreen(
                onOpenLifter = onOpenLifter,
                onSignIn = { tab = GuildTab.ALLIES },
                onOpenComments = onOpenComments,
            )
            GuildTab.INBOX -> InboxScreen(
                onOpenLifter = onOpenLifter,
                onOpenComments = onOpenComments,
                onSignIn = { tab = GuildTab.ALLIES },
            )
            GuildTab.BOARD -> LeaderboardScreen(onOpenFriend = onOpenLifter)
            GuildTab.ALLIES -> AccountScreen(
                onBack = { tab = GuildTab.FEED },
                onOpenLifter = onOpenLifter,
            )
        }
    }
}
