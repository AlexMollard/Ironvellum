package com.ironvellum.app.ui.social

import androidx.lifecycle.ViewModelStore
import com.ironvellum.app.data.cloud.Inbox
import com.ironvellum.app.data.cloud.InboxItem
import com.ironvellum.app.data.cloud.InboxRowDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModelTest {
    private val models = ViewModelStore()

    private class Gateway : InboxGateway {
        override val owners = MutableStateFlow(SocialOwner("a", "backend-one"))
        override fun owner() = owners.value
        var readCall: suspend (Boolean) -> Result<Inbox> = { Result.success(inbox("current")) }
        var markResult: Result<Unit> = Result.success(Unit)
        var answerCall: suspend (String) -> Result<Unit> = { Result.success(Unit) }
        var declineResult = Result.success(true)
        val dismissedThrough = mutableListOf<Long>()
        val seen = mutableListOf<SocialOwner>()
        val declined = mutableListOf<String>()
        override suspend fun read(force: Boolean) = readCall(force)
        override suspend fun markSeen(owner: SocialOwner): Result<Unit> { seen += owner; return markResult }
        override suspend fun accept(request: RequestIdentity, owner: SocialOwner) = answerCall(request.userId).map { true }
        override suspend fun decline(request: RequestIdentity, owner: SocialOwner): Result<Boolean> {
            declined += request.userId
            return declineResult
        }
        override fun dismissReadNotification(owner: SocialOwner, throughMs: Long) { dismissedThrough += throughMs }
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() { models.clear(); Dispatchers.resetMain() }
    private fun model(gateway: Gateway) = InboxViewModel(gateway).also { models.put("inbox", it) }

    @Test fun lateAccountReadCannotReplaceTheNewInboxOrMarkTheNewAccountSeen() = runTest {
        val gateway = Gateway()
        val old = CompletableDeferred<Result<Inbox>>()
        gateway.readCall = {
            if (gateway.owner().userId == "a") withContext(NonCancellable) { old.await() }
            else Result.success(inbox("new account"))
        }
        val vm = model(gateway)
        vm.open()
        assertTrue(vm.ui.value.loading)
        gateway.owners.value = SocialOwner("b", "backend-one")
        vm.open()
        old.complete(Result.success(inbox("old account")))
        runCurrent()
        assertEquals(inbox("new account").items, vm.ui.value.items)
        assertEquals(listOf(SocialOwner("b", "backend-one")), gateway.seen)
        assertFalse(vm.ui.value.loading)
        assertNull(vm.ui.value.error)
    }

    @Test fun backendChangeWithTheSameUserClearsOldContentAndAllowsAFreshVisit() {
        val gateway = Gateway()
        val vm = model(gateway)
        vm.open()
        gateway.owners.value = SocialOwner("a", "backend-two")
        assertTrue(vm.ui.value.items.isEmpty())
        assertFalse(vm.ui.value.loaded)
        gateway.readCall = { Result.success(inbox("new backend")) }
        vm.open()
        assertEquals(inbox("new backend").items, vm.ui.value.items)
        assertEquals(SocialOwner("a", "backend-two"), gateway.seen.last())
    }

    @Test fun cancelledReadLeavesLoadingAndExposesTheRetryState() {
        val gateway = Gateway()
        gateway.readCall = { Result.failure(CancellationException("superseded")) }
        val vm = model(gateway)
        vm.open()
        assertFalse(vm.ui.value.loading)
        assertTrue(vm.ui.value.loaded)
        assertNotNull(vm.ui.value.error)
        gateway.readCall = { Result.success(inbox("recovered")) }
        vm.open()
        assertEquals(inbox("recovered").items, vm.ui.value.items)
        assertTrue(vm.ui.value.loaded)
    }

    @Test fun lateActionFailureDoesNotContaminateAnotherAccountsState() = runTest {
        val gateway = Gateway()
        val old = CompletableDeferred<Result<Unit>>()
        gateway.answerCall = { withContext(NonCancellable) { old.await() } }
        val vm = model(gateway)
        vm.accept(RequestIdentity("ally", 200))
        assertEquals(setOf(RequestIdentity("ally", 200)), vm.ui.value.answering)
        gateway.owners.value = SocialOwner("b", "backend-one")
        old.complete(Result.failure(IllegalStateException("old failure")))
        runCurrent()
        assertTrue(vm.ui.value.answering.isEmpty())
        assertTrue(vm.ui.value.answered.isEmpty())
        assertNull(vm.ui.value.actionError)
        assertEquals("b", vm.ui.value.owner.userId)
    }

    @Test fun changingAccountDiscardsAnUncommittedDecline() {
        val gateway = Gateway()
        val vm = model(gateway)
        vm.decline(RequestIdentity("ally", 200), "Ally")
        gateway.owners.value = SocialOwner("b", "backend-one")
        vm.commitDecline()
        assertNull(vm.ui.value.pendingDecline)
        assertTrue("leaving the old tab must not decline as another account", gateway.declined.isEmpty())
    }

    @Test fun failedSeenAcknowledgementKeepsTheInboxAndAllowsAnotherRead() {
        val gateway = Gateway()
        gateway.markResult = Result.failure(IllegalStateException("offline"))
        val vm = model(gateway)
        vm.open()
        assertEquals(inbox("current").items, vm.ui.value.items)
        assertNotNull(vm.ui.value.error)
        assertFalse(vm.ui.value.loading)
        gateway.markResult = Result.success(Unit)
        vm.open()
        assertNull(vm.ui.value.error)
        assertTrue(vm.ui.value.loaded)
    }

    @Test fun aStaleDeclineReloadsWithoutClaimingItRemovedARequest() {
        val gateway = Gateway()
        val vm = model(gateway)
        vm.open()
        gateway.declineResult = Result.success(false)
        gateway.readCall = { force ->
            assertTrue(force)
            Result.success(Inbox(emptyList(), 200))
        }
        vm.decline(RequestIdentity("current", 200), "Current")
        vm.commitDecline()
        assertTrue(vm.ui.value.items.isEmpty())
        assertTrue(vm.ui.value.answered.isEmpty())
        assertEquals(listOf(200L), gateway.dismissedThrough)
    }

    @Test fun aFailedReadCannotDismissOrReplaceTheLastSuccessfulCutoff() {
        val gateway = Gateway()
        val vm = model(gateway)
        vm.open()
        gateway.readCall = { Result.failure(IllegalStateException("offline")) }
        vm.open(force = true)
        assertEquals(1L, vm.ui.value.successfulReads)
        assertEquals(listOf(200L), gateway.dismissedThrough)
        gateway.readCall = { Result.success(Inbox(inbox("current").items, 220)) }
        vm.open(force = true)
        assertEquals(2L, vm.ui.value.successfulReads)
        assertEquals(220L, vm.ui.value.seenAtMs)
        assertEquals(listOf(200L, 220L), gateway.dismissedThrough)
    }

    @Test fun onlyConfirmedAnswersNotifyTheRetainedRoster() {
        val gateway = Gateway()
        val refreshed = mutableListOf<SocialOwner>()
        val vm = InboxViewModel(gateway) { refreshed += it }
        models.put("inbox", vm)
        vm.accept(RequestIdentity("accepted", 200))
        assertEquals(listOf(gateway.owner()), refreshed)
        gateway.declineResult = Result.success(false)
        vm.decline(RequestIdentity("stale", 200), "Stale")
        vm.commitDecline()
        assertEquals(1, refreshed.size)
        gateway.answerCall = { Result.failure(IllegalStateException("offline")) }
        vm.accept(RequestIdentity("failed", 200))
        assertEquals(1, refreshed.size)
        gateway.declineResult = Result.success(true)
        vm.decline(RequestIdentity("declined", 200), "Declined")
        vm.commitDecline()
        assertEquals(listOf(gateway.owner(), gateway.owner()), refreshed)
    }

    @Test fun aNewRequestFromTheSamePersonIsNotMarkedDeclined() {
        val gateway = Gateway()
        val vm = model(gateway)
        val old = RequestIdentity("current", 200)
        val fresh = RequestIdentity("current", 300)
        vm.open()
        vm.decline(old, "Current")
        vm.commitDecline()
        gateway.readCall = { Result.success(Inbox(listOf(InboxItem.FriendRequest(300, "current", "Current")), null)) }
        vm.open()
        assertEquals(RequestAnswer.DECLINED, vm.ui.value.answered[old])
        assertNull(vm.ui.value.answered[fresh])
        vm.accept(fresh)
        assertEquals(RequestAnswer.ACCEPTED, vm.ui.value.answered[fresh])
    }

    @Test fun requestIdentityKeepsSubMillisecondPrecisionFromInbox() {
        fun row(at: String) = InboxRowDto("request", at, "current", "Current").toInboxItem() as InboxItem.FriendRequest
        val old = row("2026-10-01T18:00:00.123123Z")
        val fresh = row("2026-10-01T18:00:00.123456Z")
        assertEquals(old.occurredAtMs, fresh.occurredAtMs)
        val gateway = Gateway()
        gateway.readCall = { Result.success(Inbox(listOf(old), null)) }
        val vm = model(gateway)
        vm.open()
        val oldId = RequestIdentity(old.actorId, old.occurredAtMs, old.createdAt)
        val freshId = RequestIdentity(fresh.actorId, fresh.occurredAtMs, fresh.createdAt)
        vm.decline(oldId, old.actorName)
        vm.commitDecline()
        gateway.readCall = { Result.success(Inbox(listOf(fresh), null)) }
        vm.open()
        assertEquals(RequestAnswer.DECLINED, vm.ui.value.answered[oldId])
        assertNull(vm.ui.value.answered[freshId])
        vm.accept(freshId)
        assertEquals(RequestAnswer.ACCEPTED, vm.ui.value.answered[freshId])
    }

    companion object {
        private fun inbox(name: String) = Inbox(listOf(InboxItem.FriendRequest(200, name, name)), null)
    }
}
