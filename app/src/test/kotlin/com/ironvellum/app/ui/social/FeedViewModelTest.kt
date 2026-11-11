package com.ironvellum.app.ui.social

import androidx.lifecycle.ViewModelStore
import com.ironvellum.app.data.cloud.CloudReadCache
import com.ironvellum.app.data.cloud.FeedEntry
import com.ironvellum.app.data.cloud.FriendRow
import com.ironvellum.app.data.cloud.Reaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {
    private val models = ViewModelStore()
    private class Gateway : FeedGateway {
        override val owners = MutableStateFlow(SocialOwner("a", "one"))
        override fun owner() = owners.value
        var read: suspend (Int, Long?, Boolean) -> Result<List<FeedEntry>> = { _, _, _ -> Result.success(emptyList()) }
        var friendRead: suspend (Boolean) -> Result<List<FriendRow>> = { Result.success(emptyList()) }
        var reaction: suspend (String, Reaction?) -> Result<Unit> = { _, _ -> Result.success(Unit) }
        var ally: suspend (String) -> Result<Unit> = { Result.success(Unit) }
        override suspend fun feed(limit: Int, beforeMs: Long?, force: Boolean) = read(limit, beforeMs, force)
        override suspend fun friends(force: Boolean) = friendRead(force)
        override suspend fun react(sessionId: String, reaction: Reaction?) = this.reaction(sessionId, reaction)
        override suspend fun requestFriendById(userId: String) = ally(userId)
    }
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() { models.clear(); Dispatchers.resetMain() }
    private fun model(gateway: Gateway, pageSize: Int = 50) =
        FeedViewModel(gateway, MutableStateFlow(null), pageSize).also { models.put("feed", it) }

    @Test fun forceRefreshReplacesCachedContentWhileOlderReadIsPending() = runTest {
        val gateway = Gateway()
        val cache = CloudReadCache()
        val backend = Any()
        val old = CompletableDeferred<Unit>()
        var rows = listOf(entry(1))
        var blockNextFetch = false
        gateway.read = { _, _, force ->
            runCatching {
                cache.getOrFetch(CloudReadCache.KEY_FEED_FIRST_PAGE, 20_000, force, "a", backend) {
                    val snapshot = rows
                    if (blockNextFetch) {
                        blockNextFetch = false
                        withContext(NonCancellable) { old.await() }
                    }
                    snapshot
                }
            }
        }
        val vm = model(gateway)
        rows = listOf(entry(2))
        vm.load()
        assertEquals(listOf("1"), vm.ui.value.entries.map { it.sessionId })
        cache.invalidate(CloudReadCache.KEY_FEED_FIRST_PAGE)
        blockNextFetch = true
        vm.load()
        rows = listOf(entry(3))
        vm.load(force = true)
        assertEquals(listOf("3"), vm.ui.value.entries.map { it.sessionId })
        old.complete(Unit)
        runCurrent()
        assertEquals(listOf("3"), vm.ui.value.entries.map { it.sessionId })
    }

    @Test fun ownerChangeDiscardsLateReadAndActionFailuresAndFailedNewRoster() = runTest {
        val gateway = Gateway()
        val oldRead = CompletableDeferred<Result<List<FeedEntry>>>()
        val oldReaction = CompletableDeferred<Result<Unit>>()
        val oldAlly = CompletableDeferred<Result<Unit>>()
        gateway.read = { _, _, _ -> Result.success(listOf(entry(1))) }
        gateway.friendRead = { Result.success(listOf(FriendRow("friend", "Friend", true, false))) }
        val vm = model(gateway)
        gateway.reaction = { _, _ -> withContext(NonCancellable) { oldReaction.await() } }
        gateway.ally = { withContext(NonCancellable) { oldAlly.await() } }
        vm.react(entry(1), Reaction.SALUTE)
        vm.addAlly("ally")
        gateway.read = { _, _, _ -> if (gateway.owner().userId == "a") withContext(NonCancellable) { oldRead.await() } else Result.failure(IllegalStateException()) }
        vm.load()
        gateway.friendRead = { Result.failure(IllegalStateException()) }
        gateway.owners.value = SocialOwner("b", "one")
        assertTrue(vm.ui.value.entries.isEmpty())
        assertTrue(vm.ui.value.friends.isEmpty())
        assertNotNull(vm.ui.value.error)
        oldRead.complete(Result.success(listOf(entry(9))))
        oldReaction.complete(Result.failure(IllegalStateException()))
        oldAlly.complete(Result.failure(IllegalStateException()))
        runCurrent()
        assertTrue(vm.ui.value.entries.isEmpty())
        assertTrue(vm.ui.value.requestedAlly.isEmpty())
        assertTrue(vm.ui.value.reactionErrors.isEmpty())
        assertNull(vm.ui.value.allyError)
        assertEquals("b", vm.ui.value.myUserId)
    }

    @Test fun sameUserNewBackendResetsStateEvenWhenReadFails() {
        val gateway = Gateway()
        gateway.read = { _, _, _ -> Result.success(listOf(entry(1))) }
        gateway.friendRead = { Result.success(listOf(FriendRow("friend", "Friend", true, false))) }
        val vm = model(gateway)
        gateway.read = { _, _, _ -> Result.failure(IllegalStateException()) }
        gateway.owners.value = SocialOwner("a", "two")
        assertTrue(vm.ui.value.entries.isEmpty())
        assertTrue(vm.ui.value.friends.isEmpty())
        assertNotNull(vm.ui.value.error)
    }

    @Test fun refreshKeepsReactionChangedWhileReadWasPending() = runTest {
        val gateway = Gateway()
        val pending = CompletableDeferred<Result<List<FeedEntry>>>()
        var first = true
        gateway.read = { _, _, _ ->
            if (first) { first = false; Result.success(listOf(entry(1))) }
            else withContext(NonCancellable) { pending.await() }
        }
        val vm = model(gateway)
        vm.load(true)
        val reaction = Reaction.SALUTE
        vm.react(entry(1), reaction)
        pending.complete(Result.success(listOf(entry(1))))
        runCurrent()
        assertEquals(reaction, vm.ui.value.entries.single().myReaction)
        assertEquals(1, vm.ui.value.entries.single().likeCount)
    }

    @Test fun refreshInvalidatesPendingPageAndUsesFreshCursor() = runTest {
        val gateway = Gateway()
        val pending = CompletableDeferred<Result<List<FeedEntry>>>()
        var initial = true
        gateway.read = { _, before, _ ->
            when {
                before == null && initial -> { initial = false; Result.success((100 downTo 51).map(::entry)) }
                before == 51L -> withContext(NonCancellable) { pending.await() }
                before == null -> Result.success((110 downTo 61).map(::entry))
                before == 61L -> Result.success((60 downTo 11).map(::entry))
                else -> Result.failure(IllegalStateException("wrong cursor $before"))
            }
        }
        val vm = model(gateway)
        vm.loadMore()
        vm.load(force = true)
        vm.loadMore()
        pending.complete(Result.success((50 downTo 1).map(::entry)))
        runCurrent()
        assertEquals((110 downTo 11).map(Int::toString), vm.ui.value.entries.map { it.sessionId })
        assertFalse(vm.ui.value.loadingMore)
        assertFalse(vm.ui.value.exhausted)
        assertNull(vm.ui.value.pagingError)
    }

    @Test fun pagingPreservesAnInFlightReactionAfterRefreshMovesItsPost() = runTest {
        val gateway = Gateway()
        val pending = CompletableDeferred<Result<Unit>>()
        var first = true
        gateway.read = { _, before, _ ->
            Result.success(when {
                before != null -> (60 downTo 11).map(::entry)
                first -> { first = false; (100 downTo 51).map(::entry) }
                else -> (110 downTo 61).map(::entry)
            })
        }
        gateway.reaction = { _, _ -> withContext(NonCancellable) { pending.await() } }
        val vm = model(gateway)
        vm.react(entry(51), Reaction.SALUTE)
        vm.load(force = true)
        vm.loadMore()
        assertEquals(Reaction.SALUTE, vm.ui.value.entries.single { it.sessionId == "51" }.myReaction)
        assertEquals(1, vm.ui.value.entries.single { it.sessionId == "51" }.likeCount)
        pending.complete(Result.success(Unit))
        runCurrent()
    }

    @Test fun failedRefreshPreservesContentAndPageCancellationAllowsRetry() {
        val gateway = Gateway()
        var mode = 0
        gateway.read = { _, before, _ ->
            when {
                before == null && mode == 0 -> Result.success(listOf(entry(2), entry(1)))
                before == null -> Result.failure(IllegalStateException())
                mode == 1 -> Result.failure(CancellationException())
                else -> Result.success(emptyList())
            }
        }
        val vm = model(gateway, 2)
        mode = 1
        vm.load(true)
        assertEquals(listOf("2", "1"), vm.ui.value.entries.map { it.sessionId })
        assertNotNull(vm.ui.value.error)
        vm.loadMore()
        assertFalse(vm.ui.value.loadingMore)
        assertNotNull(vm.ui.value.pagingError)
        mode = 2
        vm.loadMore()
        assertTrue(vm.ui.value.exhausted)
        assertNull(vm.ui.value.pagingError)
    }

    companion object {
        private fun entry(id: Int) = FeedEntry(
            sessionId = id.toString(), userId = "lifter", displayName = "Lifter", level = 1,
            title = "", note = "", label = "", completedAtMs = id.toLong(), xpAwarded = 0,
            strengthScore = 0, setsDone = 0, repsDone = 0, heldSeconds = 0, currentTitleId = null,
            likeCount = 0, likedByMe = false, topMovements = null, bestSet = null, distanceM = null,
            hardestGrade = null, movementCount = 0, durationSec = null,
        )
    }
}
