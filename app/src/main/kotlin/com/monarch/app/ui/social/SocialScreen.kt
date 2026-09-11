package com.monarch.app.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monarch.app.ui.theme.ChakraPetch
import com.monarch.app.ui.theme.MonarchColors
import com.monarch.app.ui.theme.MonarchTracking

private enum class GuildTab(val label: String) {
    FEED("FEED"),
    BOARD("BOARD"),
    GUILD("GUILD"),
}

/**
 * One home for everything social. The feed, leaderboard and account screens all
 * existed but had no route into them, so signing in was impossible from the UI —
 * this is the single entry point the bottom bar points at.
 */
@Composable
fun SocialScreen(onOpenHunter: (userId: String, displayName: String) -> Unit) {
    var tab by remember { mutableStateOf(GuildTab.FEED) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(18.dp))
        Text(
            "GUILD",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = MonarchColors.InkMuted,
            letterSpacing = MonarchTracking.SectionHeader,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GuildTab.entries.forEach { entry ->
                TabPill(entry.label, entry == tab) { tab = entry }
            }
        }
        Spacer(Modifier.height(14.dp))

        when (tab) {
            // Account carries the sign-in gate, so "back" from it just returns
            // to the feed rather than popping the whole tab off the stack.
            GuildTab.FEED -> FeedScreen(onOpenHunter = onOpenHunter)
            GuildTab.BOARD -> LeaderboardScreen(onOpenFriend = onOpenHunter)
            GuildTab.GUILD -> AccountScreen(onBack = { tab = GuildTab.FEED })
        }
    }
}

/** Same pill treatment as the Codex tabs, so the two hubs read as siblings. */
@Composable
private fun TabPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp)
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        fontFamily = ChakraPetch,
        fontWeight = FontWeight.Bold,
        color = if (selected) MonarchColors.Abyss else MonarchColors.InkMuted,
        letterSpacing = 2.sp,
        modifier = Modifier
            .background(
                if (selected) {
                    Brush.verticalGradient(
                        listOf(MonarchColors.SystemGreen, MonarchColors.Emerald),
                    )
                } else {
                    Brush.verticalGradient(listOf(Color(0xFF141A18), Color(0xFF0E1312)))
                },
                shape,
            )
            .border(1.dp, if (selected) MonarchColors.EmeraldBright else MonarchColors.Rune, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}
