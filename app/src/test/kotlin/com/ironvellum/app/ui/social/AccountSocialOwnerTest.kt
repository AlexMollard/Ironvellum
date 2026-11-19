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
        var friendResult: Result<List<FriendRow>> = Result.success(listOf(FriendRow("a", "old", true, false)))
        val friendForces = mutableListOf<Boolean>()
        override suspend fun friends(force: Boolean): Result<List<FriendRow>> {
            friendForces += force
            return if (owner().userId != "a") Result.failure(IllegalStateException("new")) else if (delayReads) withContext(NonCancellable) { friends.await() } else friendResult
        }
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
    @Test fun failedFirstRosterStaysUnknownUntilSuccessfulRetry() {
        val gateway = Gateway().apply { friendResult = Result.failure(IllegalStateException("offline")) }
        val vm = AccountViewModel(AccountRepository(), gateway)
        assertFalse(vm.ui.value.friendsLoaded)
        assertNotNull(vm.ui.value.friendsError)
        assertEquals(0L, vm.ui.value.friendsRevision)
        gateway.friendResult = Result.success(emptyList())
        vm.refreshFriends()
        assertTrue(vm.ui.value.friendsLoaded)
        assertNull(vm.ui.value.friendsError)
        assertTrue(vm.ui.value.friends.isEmpty())
        assertEquals(1L, vm.ui.value.friendsRevision)
        assertTrue(gateway.friendForces.all { it })
    }

    @Test fun failedRefreshPreservesRosterRevisionAndSuccessfulRefreshReplacesIt() {
        val gateway = Gateway()
        val vm = AccountViewModel(AccountRepository(), gateway)
        val revision = vm.ui.value.friendsRevision
        gateway.friendResult = Result.failure(IllegalStateException("offline"))
        vm.refreshFriends()
        assertEquals(revision, vm.ui.value.friendsRevision)
        assertEquals("old", vm.ui.value.friends.single().displayName)
        gateway.friendResult = Result.success(emptyList())
        vm.refreshFriends()
        assertEquals(revision + 1, vm.ui.value.friendsRevision)
        assertTrue(vm.ui.value.friends.isEmpty())
    }

    @Test fun olderRosterCannotOverwriteNewerRefresh() {
        val gateway = Gateway()
        val vm = AccountViewModel(AccountRepository(), gateway)
        gateway.delayReads = true
        vm.refreshFriends()
        gateway.delayReads = false
        gateway.friendResult = Result.success(emptyList())
        vm.refreshFriends()
        val revision = vm.ui.value.friendsRevision
        gateway.friends.complete(Result.success(listOf(FriendRow("a", "stale", false, false))))
        assertTrue(vm.ui.value.friends.isEmpty())
        assertEquals(revision, vm.ui.value.friendsRevision)
    }
}
