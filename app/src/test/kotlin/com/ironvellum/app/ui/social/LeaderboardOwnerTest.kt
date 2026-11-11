package com.ironvellum.app.ui.social

import com.ironvellum.app.data.cloud.AccountRepository
import com.ironvellum.app.data.cloud.LeaderboardRow
import com.ironvellum.app.data.cloud.LiftBoardRow
import com.ironvellum.app.data.cloud.ShadowBoardRow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class LeaderboardOwnerTest {
    private class Gateway : LeaderboardGateway {
        override val owners = MutableStateFlow(SocialOwner("a", Any()))
        override fun owner() = owners.value
        override val frames = MutableStateFlow<String?>(null)
        val training = CompletableDeferred<Result<List<LeaderboardRow>>>()
        val muster = CompletableDeferred<Result<List<ShadowBoardRow>>>()
        val lifts = CompletableDeferred<Result<List<LiftBoardRow>>>()
        var delayReads = false
        override suspend fun training(): Result<List<LeaderboardRow>> = if (owner().userId != "a") Result.failure(IllegalStateException("new")) else if (delayReads) withContext(NonCancellable) { training.await() } else Result.success(listOf(LeaderboardRow("a", "old", 1, 1, 0, 0, 0, 0, null)))
        override suspend fun muster(force: Boolean): Result<List<ShadowBoardRow>> = if (owner().userId != "a") Result.failure(IllegalStateException("new")) else if (delayReads) withContext(NonCancellable) { muster.await() } else Result.success(listOf(ShadowBoardRow("a", "old", null, 1, 1, 1, 1.0)))
        override suspend fun lifts(force: Boolean): Result<List<LiftBoardRow>> = if (owner().userId != "a") Result.failure(IllegalStateException("new")) else if (delayReads) withContext(NonCancellable) { lifts.await() } else Result.success(emptyList())
    }
    @Before fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun teardown() = Dispatchers.resetMain()

    @Test fun oldResponsesCannotReplaceFailedNewOwnerBoards() {
        val gateway = Gateway()
        val vm = LeaderboardViewModel(gateway, AccountRepository())
        vm.loadLifts()
        assertEquals(1, vm.ui.value.rows.size)
        assertEquals(1, vm.muster.value.rows.size)
        gateway.delayReads = true
        vm.load()
        vm.loadMuster(force = true)
        vm.loadLifts(force = true)
        gateway.owners.value = SocialOwner("b", Any())
        assertTrue(vm.ui.value.rows.isEmpty())
        assertNotNull(vm.ui.value.error)
        assertNotNull(vm.muster.value.error)
        assertNotNull(vm.lifts.value.error)
        val newLiftError = vm.lifts.value.error
        gateway.training.complete(Result.success(listOf(LeaderboardRow("a", "old", 1, 1, 0, 0, 0, 0, null))))
        gateway.muster.complete(Result.success(listOf(ShadowBoardRow("a", "old", null, 1, 1, 1, 1.0))))
        gateway.lifts.complete(Result.failure(IllegalStateException("old")))
        assertTrue(vm.ui.value.rows.isEmpty())
        assertTrue(vm.muster.value.rows.isEmpty())
        assertTrue(vm.lifts.value.rows.isEmpty())
        assertEquals(newLiftError, vm.lifts.value.error)
        gateway.owners.value = SocialOwner("b", Any())
        assertTrue(vm.ui.value.rows.isEmpty())
        assertNotNull(vm.ui.value.error)
    }
    @Test fun firstOwnerEmissionReloadsEveryBoardAfterAnEarlyLiveOwnerAdoption() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val gateway = Gateway()
        val vm = LeaderboardViewModel(gateway, AccountRepository())
        gateway.owners.value = SocialOwner("b", Any())
        vm.loadLifts()
        runCurrent()
        assertEquals("b", vm.ui.value.myUserId)
        assertNotNull(vm.ui.value.error)
        assertNotNull(vm.muster.value.error)
        assertNotNull(vm.lifts.value.error)
        assertFalse(vm.ui.value.loading)
        assertFalse(vm.muster.value.loading)
        assertFalse(vm.lifts.value.loading)
    }
}
