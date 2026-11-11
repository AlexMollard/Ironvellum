package com.ironvellum.app.ui.social

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.data.cloud.Account
import com.ironvellum.app.data.cloud.AllyWorkout
import com.ironvellum.app.data.cloud.CloudReadCache
import com.ironvellum.app.data.cloud.Comment
import com.ironvellum.app.data.cloud.FeedEntry
import com.ironvellum.app.data.cloud.FriendRow
import com.ironvellum.app.data.cloud.Liker
import com.ironvellum.app.data.cloud.Reaction
import com.ironvellum.app.data.cloud.ReportReason
import com.ironvellum.app.ui.ShotRecorder
import com.ironvellum.app.ui.theme.IronvellumTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SocialRecoveryScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var originalThemed = false

    @Before fun plainWords() {
        originalThemed = Words.themed
        Words.setThemed(context.applicationContext as IronvellumApp, false)
    }

    @After fun restoreWords() {
        Words.setThemed(context.applicationContext as IronvellumApp, originalThemed)
    }

    private fun shot(name: String) {
        ShotRecorder.take(compose, name)
        compose.mainClock.autoAdvance = true
    }

    private class FeedBackend : FeedGateway {
        override val owners = MutableStateFlow(SocialOwner("me", Any()))
        override fun owner() = owners.value
        private val cache = CloudReadCache()
        var rows = listOf(entry("initial", "Initial Ally"))
        var failing = false
        override suspend fun feed(limit: Int, beforeMs: Long?, force: Boolean): Result<List<FeedEntry>> = runCatching {
            val owner = owner()
            cache.getOrFetch(CloudReadCache.KEY_FEED_FIRST_PAGE, 20_000, force, owner.userId, checkNotNull(owner.backend)) {
                check(!failing) { "Connection unavailable" }
                rows
            }
        }
        override suspend fun friends(force: Boolean): Result<List<FriendRow>> = Result.success(emptyList())
        override suspend fun react(sessionId: String, reaction: Reaction?) = Result.success(Unit)
        override suspend fun requestFriendById(userId: String) = Result.success(Unit)
    }

    @Test fun pullRefreshShowsFreshCachedFeedAndChangingOwnerRemovesPrivateRows() {
        val backend = FeedBackend()
        compose.activityRule.scenario.onActivity { activity ->
            val vm = FeedViewModel(backend, MutableStateFlow(null))
            activity.viewModelStore.put("social-feed-recovery", vm)
            ShotRecorder.darkWindow(activity)
            activity.setContent {
                IronvellumTheme(light = false) {
                    Box(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
                        FeedScreen(onOpenLifter = { _, _ -> }, onOpenComments = { _, _, _ -> }, viewModel = vm)
                    }
                }
            }
        }
        compose.onNodeWithText("Initial Ally", substring = true).assertIsDisplayed()
        shot("social-feed-before-refresh")
        compose.runOnIdle { backend.rows = listOf(entry("fresh", "Fresh Ally")) }
        compose.onNode(hasScrollAction()).performTouchInput { swipeDown() }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Fresh Ally", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Fresh Ally", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Initial Ally", substring = true).assertDoesNotExist()
        shot("social-feed-after-refresh")
        compose.runOnIdle {
            backend.failing = true
            backend.owners.value = SocialOwner("second", backend.owner().backend)
        }
        compose.onNodeWithText("Connection unavailable", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Fresh Ally", substring = true).assertDoesNotExist()
        shot("social-feed-new-owner-offline")
    }

    private class ThreadBackend : CommentsGateway {
        override val owners = MutableStateFlow(SocialOwner("me", Any()))
        override fun owner() = owners.value
        override fun account() = Account("me", "qa@example.invalid", "Current Lifter", "private")
        val read = CompletableDeferred<Result<List<Comment>>>()
        val post = CompletableDeferred<Result<Comment>>()
        override suspend fun comments(sessionId: String, force: Boolean) = withContext(NonCancellable) { read.await() }
        override suspend fun addComment(sessionId: String, body: String) = post.await()
        override suspend fun deleteComment(id: String) = Result.success(Unit)
        override suspend fun workout(sessionId: String, force: Boolean): Result<AllyWorkout> = Result.failure(IllegalStateException("Workout unavailable"))
        override suspend fun likers(sessionId: String): Result<List<Liker>> = Result.success(emptyList())
        override suspend fun friends(): Result<List<FriendRow>> = Result.success(emptyList())
        override suspend fun react(sessionId: String, reaction: Reaction?) = Result.success(Unit)
        override suspend fun report(userId: String, reason: ReportReason, note: String, sessionId: String, commentId: String) = Result.success(Unit)
    }

    @Test fun sendingWhileTheThreadLoadsKeepsThePostAndEarlierHistoryVisible() {
        val backend = ThreadBackend()
        lateinit var vm: CommentsViewModel
        compose.activityRule.scenario.onActivity { activity ->
            vm = CommentsViewModel(backend, "session")
            activity.viewModelStore.put("social-thread-recovery", vm)
            ShotRecorder.darkWindow(activity)
            activity.setContent {
                IronvellumTheme(light = false) {
                    Box(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
                        CommentsScreen(sessionId = "session", ownerId = "", headline = "Training session",
                            onBack = {}, onOpenLifter = { _, _ -> }, viewModel = vm)
                    }
                }
            }
        }
        compose.runOnIdle { vm.onDraft("Posted while loading") }
        compose.onNodeWithText(context.getString(R.string.comments_send_button)).assertIsEnabled().performClick()
        val now = System.currentTimeMillis()
        compose.runOnIdle { backend.post.complete(Result.success(Comment("new", "session", "me", "Current Lifter", "Posted while loading", now))) }
        compose.onNodeWithText("Posted while loading").assertIsDisplayed()
        compose.runOnIdle { backend.read.complete(Result.success(listOf(Comment("earlier", "session", "other", "Earlier Ally", "Earlier thread comment", now - 60_000)))) }
        compose.onNodeWithText("Earlier thread comment").assertIsDisplayed()
        compose.onNodeWithText("Posted while loading").assertIsDisplayed()
        shot("social-comments-after-late-read")
    }

    companion object {
        private fun entry(id: String, name: String) = FeedEntry(
            sessionId = id, userId = "ally", displayName = name, level = 3, title = "", note = "", label = "Pull day",
            completedAtMs = System.currentTimeMillis(), xpAwarded = 20, strengthScore = 15, setsDone = 3, repsDone = 30,
            heldSeconds = 0, currentTitleId = null, likeCount = 0, likedByMe = false, topMovements = null, bestSet = null,
            distanceM = null, hardestGrade = null, movementCount = 2, durationSec = null,
        )
    }
}
