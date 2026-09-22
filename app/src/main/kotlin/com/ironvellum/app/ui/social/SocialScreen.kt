package com.ironvellum.app.ui.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ironvellum.app.ui.components.IronvellumTabPill

private enum class GuildTab(val label: String) {
    FEED("FEED"),
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
fun SocialScreen(onOpenLifter: (userId: String, displayName: String) -> Unit) {
    var tab by remember { mutableStateOf(GuildTab.FEED) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GuildTab.entries.forEach { entry ->
                IronvellumTabPill(entry.label, entry == tab) { tab = entry }
            }
        }
        Spacer(Modifier.height(14.dp))

        when (tab) {
            // Account carries the sign-in form, so "back" from it just returns
            // to the feed rather than popping the whole tab off the stack.
            GuildTab.FEED -> FeedScreen(onOpenLifter = onOpenLifter)
            GuildTab.BOARD -> LeaderboardScreen(onOpenFriend = onOpenLifter)
            GuildTab.ALLIES -> AccountScreen(
                onBack = { tab = GuildTab.FEED },
                onOpenLifter = onOpenLifter,
            )
        }
    }
}

