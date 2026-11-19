package com.ironvellum.app.ui.social

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ironvellum.app.ui.components.PushedHeader
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.res.stringResource
import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.IronvellumDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.ironvellum.app.ui.theme.IronvellumColors
import androidx.compose.runtime.mutableIntStateOf
import com.ironvellum.app.data.InboxNotifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.data.cloud.Cloud
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.InkTabbedPager

private enum class GuildTab(private val labelText: () -> String) {
    FEED({ Words.tidings.c }),
    BOARD({ Words.reckoning.c }),
    // "ALLIES", not "GUILD" — the bottom nav tab already says Allies; repeating
    // it here put the same word on screen three times.
    ALLIES({ Words.ally.cpl });

    val label: String get() = labelText()
}

private enum class SocialPage { HUB, INBOX, CIRCLE, FIND }

/**
 * One home for everything social. Signed out (or with no backend) it is only
 * the account screen: every tab would otherwise show its own "sign in" panel,
 * four layouts for one message.
 */
@Composable
fun SocialScreen(
    onOpenLifter: (userId: String, displayName: String) -> Unit,
    onOpenComments: (sessionId: String, ownerId: String, headline: String) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenBoard: (BoardKind) -> Unit,
    inboxRequest: Int = 0,
) {
    val app = LocalContext.current.applicationContext as IronvellumApp
    val account by app.accountRepository.account.collectAsStateWithLifecycle()
    val cloudConfigured = Cloud.config.collectAsStateWithLifecycle().value != null
    val signedIn = cloudConfigured && account != null

    // Saveable: opening a workout's comments from the INBOX pushes a route
    // over this one, and a plain remember came back on FEED after BACK.
    // The pager state is saveable, so it carries the open tab too.
    val pager = rememberPagerState { GuildTab.entries.size }
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableStateOf(SocialPage.HUB) }
    val screens = rememberSaveableStateHolder()
    var circleFromInbox by rememberSaveable { mutableStateOf(false) }
    fun leavePage() {
        if (page == SocialPage.INBOX) screens.removeState(SocialPage.INBOX)
        page = if (page == SocialPage.CIRCLE && circleFromInbox) SocialPage.INBOX else SocialPage.HUB
        circleFromInbox = false
    }
    BackHandler(enabled = signedIn && (page != SocialPage.HUB || pager.currentPage != 0)) {
        if (page != SocialPage.HUB) leavePage()
        else scope.launch { pager.animateScrollToPage(0) }
    }
    // Signing in HERE lands on ALLIES, where a fresh Google account's
    // claim-your-name panel lives; a restored session keeps its tab.
    var sawSignedOut by remember { mutableStateOf(!signedIn) }
    LaunchedEffect(signedIn) {
        if (!signedIn) {
            screens.removeState(SocialPage.INBOX)
            page = SocialPage.HUB
            sawSignedOut = true
        } else if (sawSignedOut) {
            pager.scrollToPage(GuildTab.ALLIES.ordinal)
            sawSignedOut = false
        }
    }
    // A notification tap asks for INBOX. The served count is saveable so BACK
    // from a workout's comments (or a rotation) does not yank the lifter off
    // whichever tab they moved to since.
    var servedInboxRequest by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(inboxRequest, signedIn) {
        if (signedIn && inboxRequest > servedInboxRequest) {
            servedInboxRequest = inboxRequest
            page = SocialPage.INBOX
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
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) app.notificationsAllowed()
    }
    LaunchedEffect(page) {
        if (page != SocialPage.INBOX) return@LaunchedEffect
        InboxNotifier.cancel(context)
        if (InboxNotifier.takeFirstAsk(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            explainNotifications = true
        }
    }
    if (explainNotifications) {
        IronvellumDialog(
            onDismissRequest = { explainNotifications = false },
            title = { Text(UiText.themed(R.string.acct_notifications_title, R.string.acct_notifications_title__t).asString()) },
            text = { Text(UiText.themed(R.string.acct_notifications_text, R.string.acct_notifications_text__t).asString()) },
            // ALLOW is the only path to the system prompt; NOT NOW just closes.
            confirmButton = {
                IronvellumButton(
                    stringResource(R.string.acct_allow_button),
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
                IronvellumButton(stringResource(R.string.acct_not_now_button), quiet = true, onClick = { explainNotifications = false })
            },
        )
    }

    if (page != SocialPage.HUB) {
        val title = when (page) {
            SocialPage.INBOX -> Words.inbox.c
            SocialPage.CIRCLE -> Words.circle.c
            SocialPage.FIND -> UiText.themed(R.string.social_find_friend, R.string.social_find_friend__t).asString()
            SocialPage.HUB -> error("Not a pushed page")
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            PushedHeader(title, ::leavePage, Modifier.padding(top = 8.dp))
            screens.SaveableStateProvider(page) {
                when (page) {
                    SocialPage.INBOX -> InboxScreen(
                        onOpenLifter = onOpenLifter,
                        onOpenComments = onOpenComments,
                        onOpenCircle = {
                            circleFromInbox = true
                            page = SocialPage.CIRCLE
                        },
                    )
                    SocialPage.CIRCLE -> Column(Modifier.verticalScroll(rememberScrollState())) {
                        CircleSection(onOpenLifter)
                        Spacer(Modifier.height(16.dp))
                    }
                    SocialPage.FIND -> AccountScreen(onBack = {}, findOnly = true)
                    SocialPage.HUB -> Unit
                }
            }
        }
        return
    }

    screens.SaveableStateProvider(SocialPage.HUB) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.social_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = IronvellumColors.Ink,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { page = SocialPage.INBOX }) {
                    BadgedBox(badge = { if (unread > 0) Badge { Text(if (unread > 99) "99+" else unread.toString()) } }) {
                        Icon(
                            Icons.Outlined.Notifications,
                            if (unread == 0) Words.inbox.c else stringResource(R.string.nav_tab_unread_description, Words.inbox.c, unread),
                            tint = IronvellumColors.Ink,
                        )
                    }
                }
                val profileLabel = stringResource(R.string.own_social_profile)
                IconButton(onClick = onOpenProfile, modifier = Modifier.semantics { contentDescription = profileLabel }) {
                    LifterAvatar(account?.displayName.orEmpty(), 34.dp, ring = AvatarRing.Own)
                }
            }
            InkTabbedPager(
                labels = GuildTab.entries.map { it.label },
                state = pager,
                modifier = Modifier.weight(1f),
                tabsModifier = Modifier.padding(horizontal = 16.dp),
                tabsGap = 12.dp,
                fillTabs = true,
            ) { index ->
                Box(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    when (GuildTab.entries[index]) {
                        GuildTab.FEED -> FeedScreen(
                            onOpenLifter = onOpenLifter,
                            onOpenComments = onOpenComments,
                            onFindFriend = {
                                scope.launch { pager.scrollToPage(GuildTab.ALLIES.ordinal) }
                                page = SocialPage.FIND
                            },
                        )
                        GuildTab.BOARD -> LeaderboardScreen(onOpenBoard = onOpenBoard)
                        GuildTab.ALLIES -> AccountScreen(
                            onBack = {},
                            onOpenLifter = onOpenLifter,
                            onOpenMissives = { page = SocialPage.INBOX },
                            onOpenCircle = { page = SocialPage.CIRCLE },
                            onFindFriend = { page = SocialPage.FIND },
                        )
                    }
                }
            }
        }
    }
}
