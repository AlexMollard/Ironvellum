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
        var trainingRows = listOf(LeaderboardRow("a", "old", 1, 1, 0, 0, 0, 0, null, workoutXpLast7d = 1))
        override suspend fun training(recentWorkoutXp: Boolean, force: Boolean): Result<List<LeaderboardRow>> = if (owner().userId != "a") Result.failure(IllegalStateException("new")) else if (delayReads) withContext(NonCancellable) { training.await() } else Result.success(trainingRows)
        override suspend fun muster(force: Boolean): Result<List<ShadowBoardRow>> = if (owner().userId != "a") Result.failure(IllegalStateException("new")) else if (delayReads) withContext(NonCancellable) { muster.await() } else Result.success(listOf(ShadowBoardRow("a", "old", null, 1, 1, 1, 1.0)))
        override suspend fun lifts(force: Boolean): Result<List<LiftBoardRow>> = if (owner().userId != "a") Result.failure(IllegalStateException("new")) else if (delayReads) withContext(NonCancellable) { lifts.await() } else Result.success(emptyList())
    }
    @Before fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @Test fun refreshingARetainedBoardRemovesExpiredWorkoutsAndShowsNewOnes() {
        val gateway = Gateway()
        val vm = LeaderboardViewModel(gateway, AccountRepository())
        assertEquals("a", vm.ui.value.rows.single().userId)
        gateway.trainingRows = emptyList()
        vm.refreshTraining()
        assertTrue(vm.ui.value.rows.isEmpty())
        gateway.trainingRows = listOf(LeaderboardRow("new", "Newcomer", 1, 10, 0, 0, 0, 1, null, workoutXpLast7d = 10))
        vm.refreshTraining()
        assertEquals("new", vm.ui.value.rows.single().userId)
    }
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

    @Test fun querySwitchesAndForcedRetriesIgnoreCancelledResponses() {
        data class Call(val result: CompletableDeferred<Result<List<LeaderboardRow>>>)
        val calls = mutableListOf<Call>()
        val ownerFlow = MutableStateFlow(SocialOwner("a", Any()))
        val gateway = object : LeaderboardGateway {
            override val owners = ownerFlow
            override fun owner() = owners.value
            override val frames = MutableStateFlow<String?>(null)
            override suspend fun training(recentWorkoutXp: Boolean, force: Boolean): Result<List<LeaderboardRow>> {
                val call = Call(CompletableDeferred())
                calls += call
                return withContext(NonCancellable) { call.result.await() }
            }
            override suspend fun muster(force: Boolean) = Result.success(emptyList<ShadowBoardRow>())
            override suspend fun lifts(force: Boolean) = Result.success(emptyList<LiftBoardRow>())
        }
        fun row(id: String) = LeaderboardRow(id, id, 1, 100, 0, 0, 0, 0, null, workoutXpLast7d = 5)
        val vm = LeaderboardViewModel(gateway, AccountRepository())
        vm.selectTrainingMetric(false)
        assertTrue(vm.ui.value.rows.isEmpty())
        calls[1].result.complete(Result.success(listOf(row("all"))))
        calls[0].result.complete(Result.success(listOf(row("staleRecent"))))
        assertEquals("all", vm.ui.value.rows.single().userId)
        vm.selectTrainingMetric(true)
        assertTrue(vm.ui.value.rows.isEmpty())
        vm.load()
        calls[2].result.complete(Result.success(listOf(row("cancelled"))))
        assertTrue(vm.ui.value.loading)
        calls[3].result.complete(Result.failure(IllegalStateException("recent failed")))
        assertTrue(vm.ui.value.rows.isEmpty())
        assertNotNull(vm.ui.value.error)
        vm.selectTrainingMetric(false)
        vm.selectTrainingMetric(true)
        calls[4].result.complete(Result.success(listOf(row("staleAll"))))
        calls[5].result.complete(Result.success(listOf(row("recent"))))
        assertEquals("recent", vm.ui.value.rows.single().userId)
    }

    @Test fun aCancelledCurrentReadStopsLoadingAndCanBeRetried() {
        var cancelRead = true
        val gateway = object : LeaderboardGateway {
            override val owners = MutableStateFlow(SocialOwner("a", Any()))
            override fun owner() = owners.value
            override val frames = MutableStateFlow<String?>(null)
            override suspend fun training(recentWorkoutXp: Boolean, force: Boolean): Result<List<LeaderboardRow>> {
                if (cancelRead) {
                    cancelRead = false
                    throw kotlinx.coroutines.CancellationException("cancelled current read")
                }
                return Result.success(listOf(LeaderboardRow("recovered", "Recovered", 1, 100, 0, 0, 0, 0, null, workoutXpLast7d = 5)))
            }
            override suspend fun muster(force: Boolean) = Result.success(emptyList<ShadowBoardRow>())
            override suspend fun lifts(force: Boolean) = Result.success(emptyList<LiftBoardRow>())
        }
        val vm = LeaderboardViewModel(gateway, AccountRepository())
        assertFalse(vm.ui.value.loading)
        assertNotNull(vm.ui.value.error)
        vm.selectTrainingMetric(true)
        assertEquals("recovered", vm.ui.value.rows.single().userId)
        assertNull(vm.ui.value.error)
        assertFalse(vm.ui.value.loading)
    }

    @Test fun backendChangeKeepsQueryAndRejectsPreviousBackendResponse() {
        val gateway = Gateway()
        val vm = LeaderboardViewModel(gateway, AccountRepository())
        vm.selectTrainingMetric(false)
        gateway.delayReads = true
        vm.load()
        gateway.delayReads = false
        gateway.owners.value = SocialOwner("a", Any())
        assertFalse(vm.ui.value.recentWorkoutXp)
        gateway.training.complete(Result.success(listOf(LeaderboardRow("old", "old", 1, 1, 0, 0, 0, 0, null))))
        assertFalse(vm.ui.value.rows.any { it.userId == "old" })
    }
}
