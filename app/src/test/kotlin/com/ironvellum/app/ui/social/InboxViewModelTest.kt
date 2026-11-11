package com.ironvellum.app.ui.social

import androidx.lifecycle.ViewModelStore
import com.ironvellum.app.data.cloud.Inbox
import com.ironvellum.app.data.cloud.InboxItem
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
        val seen = mutableListOf<SocialOwner>()
        val declined = mutableListOf<String>()
        override suspend fun read(force: Boolean) = readCall(force)
        override suspend fun markSeen(owner: SocialOwner): Result<Unit> { seen += owner; return markResult }
        override suspend fun accept(userId: String) = answerCall(userId)
        override suspend fun decline(userId: String): Result<Unit> { declined += userId; return answerCall(userId) }
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
        vm.accept("ally")
        assertEquals(setOf("ally"), vm.ui.value.answering)
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
        vm.decline("ally", "Ally")
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

    companion object {
        private fun inbox(name: String) = Inbox(listOf(InboxItem.FriendRequest(200, name, name)), null)
    }
}
