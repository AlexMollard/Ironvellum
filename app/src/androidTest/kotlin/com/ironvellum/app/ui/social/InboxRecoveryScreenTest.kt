package com.ironvellum.app.ui.social

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.data.cloud.Inbox
import com.ironvellum.app.data.cloud.InboxItem
import com.ironvellum.app.ui.ShotRecorder
import com.ironvellum.app.ui.theme.IronvellumTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InboxRecoveryScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var originalThemed = false

    @Before fun plainWords() { originalThemed = Words.themed; Words.setThemed(context.applicationContext as IronvellumApp, false) }
    @After fun restoreWords() { Words.setThemed(context.applicationContext as IronvellumApp, originalThemed) }

    @Test fun cancelledFirstLoadOffersAWorkingRetryInsteadOfAnEndlessLoadingPanel() {
        val gateway = object : InboxGateway {
            override val owners = MutableStateFlow(SocialOwner("me", "isolated"))
            override fun owner() = owners.value
            private var first = true
            override suspend fun read(force: Boolean): Result<Inbox> {
                if (first) { first = false; return Result.failure(CancellationException("shared producer cancelled")) }
                return Result.success(Inbox(listOf(InboxItem.FriendRequest(200, "ally", "Ready Ally")), null))
            }
            override suspend fun markSeen(owner: SocialOwner) = Result.success(Unit)
            override suspend fun accept(userId: String) = Result.success(Unit)
            override suspend fun decline(userId: String, owner: SocialOwner) = Result.success(true)
            override fun dismissReadNotification(owner: SocialOwner, throughMs: Long) = Unit
        }
        compose.activityRule.scenario.onActivity { activity ->
            val vm = InboxViewModel(gateway)
            activity.viewModelStore.put("inbox-recovery", vm)
            ShotRecorder.darkWindow(activity)
            activity.setContent {
                IronvellumTheme(light = false) {
                    Box(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
                        InboxScreen(onOpenLifter = { _, _ -> }, onOpenComments = { _, _, _ -> }, onOpenCircle = {}, viewModel = vm)
                    }
                }
            }
        }
        val retry = context.getString(R.string.inbox_try_again)
        compose.onNodeWithText(retry).assertIsDisplayed()
        ShotRecorder.take(compose, "inbox-cancelled-read-retry")
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText(retry).performClick()
        compose.onNodeWithText("Ready Ally", substring = true).assertIsDisplayed()
        ShotRecorder.take(compose, "inbox-recovered-request")
    }

    @Test fun aRestoredVisitLoadsFreshRowsWithoutResettingTheUnreadFilter() {
        val gateway = object : InboxGateway {
            override val owners = MutableStateFlow(SocialOwner("me", "isolated"))
            override fun owner() = owners.value
            var inbox = Inbox(listOf(InboxItem.FriendRequest(200, "ally", "Original Ally")), null)
            override suspend fun read(force: Boolean) = Result.success(inbox)
            override suspend fun markSeen(owner: SocialOwner) = Result.success(Unit)
            override suspend fun accept(userId: String) = Result.success(Unit)
            override suspend fun decline(userId: String, owner: SocialOwner) = Result.success(true)
            override fun dismissReadNotification(owner: SocialOwner, throughMs: Long) = Unit
        }
        var vm = InboxViewModel(gateway)
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            IronvellumTheme(light = false) {
                InboxScreen(onOpenLifter = { _, _ -> }, onOpenComments = { _, _, _ -> }, onOpenCircle = {}, viewModel = vm)
            }
        }
        compose.onNodeWithText("Original Ally", substring = true).assertIsDisplayed()
        val unread = compose.onNodeWithText(context.getString(R.string.social_inbox_filter_unread))
        unread.performClick()
        compose.runOnIdle {
            gateway.inbox = Inbox(
                listOf(InboxItem.FriendRequest(250, "new", "New Ally"), InboxItem.FriendRequest(200, "ally", "Original Ally")),
                seenAtMs = 250,
            )
            vm = InboxViewModel(gateway)
        }
        restoration.emulateSavedInstanceStateRestore()
        unread.assertIsSelected()
        compose.onNodeWithText("New Ally", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Original Ally", substring = true).assertIsDisplayed()
    }
}
