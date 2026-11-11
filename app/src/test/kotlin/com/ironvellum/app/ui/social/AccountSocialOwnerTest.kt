package com.ironvellum.app.ui.social

import com.ironvellum.app.data.Repository
import com.ironvellum.app.data.cloud.AccountRepository
import com.ironvellum.app.data.cloud.BlockedLifter
import com.ironvellum.app.data.cloud.CloudSync
import com.ironvellum.app.data.cloud.FriendRow
import com.ironvellum.app.data.cloud.SyncOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class AccountSocialOwnerTest {
    private class Gateway : AccountCloudGateway {
        override val owners = MutableStateFlow(SocialOwner("a", Any()))
        var liveOwner: SocialOwner? = null
        val removedAs = mutableListOf<String?>()
        override fun owner() = liveOwner ?: owners.value
        val friends = CompletableDeferred<Result<List<FriendRow>>>()
        val blocked = CompletableDeferred<Result<List<BlockedLifter>>>()
        var delayReads = false
        override suspend fun friends(): Result<List<FriendRow>> = if (owner().userId != "a") Result.failure(IllegalStateException("new")) else if (delayReads) withContext(NonCancellable) { friends.await() } else Result.success(listOf(FriendRow("a", "old", true, false)))
        override suspend fun blocked(force: Boolean): Result<List<BlockedLifter>> = if (owner().userId != "a") Result.failure(IllegalStateException("new")) else if (delayReads) withContext(NonCancellable) { blocked.await() } else Result.success(listOf(BlockedLifter("a", "old")))
        override suspend fun latestBackup(): Result<CloudSync.BackupInfo?> = Result.success(null)
        override suspend fun forgetPushedState() = error("unexpected")
        override suspend fun push(): Result<SyncOutcome> = error("unexpected")
        override suspend fun backupArchive(): Result<CloudSync.BackupInfo> = error("unexpected")
        override suspend fun restoreArchive(): Result<Repository.ImportResult> = error("unexpected")
        override suspend fun acceptFriend(userId: String): Result<Unit> = error("unexpected")
        override suspend fun requestFriend(name: String): Result<Unit> = error("unexpected")
        override suspend fun removeFriend(userId: String): Result<Unit> {
            removedAs += owner().userId
            return Result.success(Unit)
        }
        override suspend fun unblock(userId: String): Result<Unit> = error("unexpected")
    }
    @Before fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun teardown() = Dispatchers.resetMain()

    @Test fun oldReadsCannotPopulateNewOwnerAfterFailure() {
        val gateway = Gateway()
        val vm = AccountViewModel(AccountRepository(), gateway)
        assertEquals(1, vm.ui.value.friends.size)
        assertEquals(1, vm.ui.value.blocked.size)
        gateway.delayReads = true
        vm.refreshFriends()
        vm.refreshBlocked()
        gateway.owners.value = SocialOwner("b", Any())
        assertTrue(vm.ui.value.friends.isEmpty())
        assertTrue(vm.ui.value.blocked.isEmpty())
        assertNotNull(vm.ui.value.error)
        val newError = vm.ui.value.error
        gateway.friends.complete(Result.success(listOf(FriendRow("a", "old", true, false))))
        gateway.blocked.complete(Result.failure(IllegalStateException("old")))
        assertTrue(vm.ui.value.friends.isEmpty())
        assertTrue(vm.ui.value.blocked.isEmpty())
        assertEquals(newError, vm.ui.value.error)
        gateway.owners.value = SocialOwner("b", Any())
        assertTrue(vm.ui.value.friends.isEmpty())
        assertNotNull(vm.ui.value.error)
    }
    @Test fun anActionFromThePreviousOwnerIsNotReboundBeforeItsFlowEmission() {
        val gateway = Gateway()
        val vm = AccountViewModel(AccountRepository(), gateway)
        assertEquals("old", vm.ui.value.friends.single().displayName)
        gateway.liveOwner = SocialOwner("b", Any())
        vm.removeFriend("a")
        assertTrue(gateway.removedAs.isEmpty())
        assertTrue(vm.ui.value.friends.isEmpty())
        assertNotNull(vm.ui.value.error)
    }
}
