package com.ironvellum.app.ui.social

import com.ironvellum.app.data.CircleBonusPaid
import com.ironvellum.app.data.CircleGateway
import com.ironvellum.app.data.CircleRead
import com.ironvellum.app.domain.Circle
import com.ironvellum.app.domain.UiText
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CircleUiTest {

    private class FakeGateway(signedIn: String? = "me") : CircleGateway {
        val user = MutableStateFlow(signedIn)
        override val userId: StateFlow<String?> = user
        override fun currentUserId(): String? = user.value

        var circle: Circle? = null
        var paid: CircleBonusPaid? = null
        var readError: Throwable? = null
        var actionError: Throwable? = null
        val reads = mutableListOf<Boolean>()
        var delayedRead: CompletableDeferred<Result<CircleRead>>? = null
        var delayedAction: CompletableDeferred<Result<Unit>>? = null
        val calls = mutableListOf<String>()
        var onAccepted: (String) -> Unit = {}

        override suspend fun read(force: Boolean): Result<CircleRead> {
            reads += force
            delayedRead?.let { pending ->
                delayedRead = null
                return withContext(NonCancellable) { pending.await() }
            }
            readError?.let { return Result.failure(it) }
            return Result.success(CircleRead(circle, paid.also { paid = null }))
        }

        private fun act(name: String): Result<Unit> {
            calls += name
            return actionError?.let { Result.failure(it) } ?: Result.success(Unit).also { onAccepted(name) }
        }

        override suspend fun create(name: String): Result<Unit> =
            delayedAction?.let { withContext(NonCancellable) { it.await() } } ?: act("create:$name")
        override suspend fun join(code: String) = act("join:$code")
        override suspend fun leave() = act("leave")
        override suspend fun setGoal(perMember: Int) = act("goal:$perMember")
        override suspend fun rename(name: String) = act("rename:$name")
        override suspend fun rotateCode() = act("rotate")
        override suspend fun removeMember(userId: String) = act("remove:$userId")
    }

    private fun circle(name: String = "North Gate") =
        Circle(id = "c1", name = name, code = "K7M2PQ4X", ownerId = "me", members = emptyList())

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a signed in lifter's circle loads on creation`() {
        val gateway = FakeGateway().apply { circle = circle() }
        val vm = CircleViewModel(gateway)
        assertEquals("North Gate", vm.ui.value.circle?.name)
        assertFalse(vm.ui.value.loading)
        assertEquals(listOf(false), gateway.reads)
    }

    @Test
    fun `signing in later loads the circle and signing out clears it`() {
        val gateway = FakeGateway(signedIn = null).apply { circle = circle() }
        val vm = CircleViewModel(gateway)
        assertFalse(vm.ui.value.signedIn)
        assertTrue(gateway.reads.isEmpty())
        gateway.user.value = "me"
        assertEquals("North Gate", vm.ui.value.circle?.name)
        gateway.user.value = null
        assertNull(vm.ui.value.circle)
        assertFalse(vm.ui.value.signedIn)
    }

    @Test
    fun `a bonus paid by a read shows once and is dismissed`() {
        val gateway = FakeGateway().apply {
            circle = circle()
            paid = CircleBonusPaid(40, "North Gate")
        }
        val vm = CircleViewModel(gateway)
        assertEquals(CircleBonusPaid(40, "North Gate"), vm.ui.value.paid)
        // A later read that pays nothing must not wipe the overlay before it is seen.
        vm.load(force = true)
        assertNotNull(vm.ui.value.paid)
        vm.dismissPayout()
        assertNull(vm.ui.value.paid)
    }

    @Test
    fun `a forced reload replaces the roster`() {
        val gateway = FakeGateway().apply { circle = circle() }
        val vm = CircleViewModel(gateway)
        gateway.circle = circle("New")
        vm.load(force = true)
        assertEquals("New", vm.ui.value.circle?.name)
    }

    @Test
    fun `a failed reload over a circle on screen keeps it and says so`() {
        val gateway = FakeGateway().apply { circle = circle() }
        val vm = CircleViewModel(gateway)
        gateway.readError = IllegalStateException("offline")
        vm.load(force = true)
        assertEquals("North Gate", vm.ui.value.circle?.name)
        assertTrue(vm.ui.value.refreshFailed)
        assertEquals(UiText.Raw("offline"), vm.ui.value.error)
        gateway.readError = null
        vm.load(force = true)
        assertFalse(vm.ui.value.refreshFailed)
        assertNull(vm.ui.value.error)
    }

    @Test
    fun `an accepted action closes its dialog and re-reads the circle forced`() {
        val gateway = FakeGateway().apply { circle = circle() }
        val vm = CircleViewModel(gateway)
        var done = false
        vm.setGoal(5) { done = true }
        assertTrue(done)
        assertEquals(listOf("goal:5"), gateway.calls)
        assertEquals(listOf(false, true), gateway.reads)
        assertFalse(vm.ui.value.actionBusy)
    }

    @Test
    fun `a refused action keeps its dialog open with the refusal inline`() {
        val gateway = FakeGateway().apply {
            circle = circle()
            actionError = IllegalStateException("That circle is full")
        }
        val vm = CircleViewModel(gateway)
        var done = false
        vm.join("K7M2PQ4X") { done = true }
        assertFalse(done)
        assertEquals(UiText.Raw("That circle is full"), vm.ui.value.actionError)
        assertFalse(vm.ui.value.actionBusy)
        vm.dismissActionError()
        assertNull(vm.ui.value.actionError)
    }

    @Test
    fun `a refused action re-reads the circle so a stale roster is corrected`() {
        val gateway = FakeGateway().apply { circle = circle() }
        val vm = CircleViewModel(gateway)
        gateway.actionError = IllegalStateException("Only the Keeper changes the circle code")
        gateway.circle = circle("Gate House")
        vm.newCode()
        assertEquals(UiText.Raw("Only the Keeper changes the circle code"), vm.ui.value.actionError)
        assertEquals("Gate House", vm.ui.value.circle?.name)
        assertEquals(listOf(false, true), gateway.reads)
    }

    @Test
    fun `the Keeper actions reach the gateway`() {
        val gateway = FakeGateway().apply { circle = circle() }
        val vm = CircleViewModel(gateway)
        vm.rename("Gate House")
        vm.newCode()
        vm.remove("u2")
        assertEquals(listOf("rename:Gate House", "rotate", "remove:u2"), gateway.calls)
    }

    @Test
    fun `owner switch clears roster even when old read completes after new failure`() {
        val gateway = FakeGateway().apply { circle = circle() }
        val backend = Any()
        val owners = MutableStateFlow(SocialOwner("me", backend))
        val vm = CircleViewModel(gateway, owners) { owners.value }
        val late = CompletableDeferred<Result<CircleRead>>()
        gateway.delayedRead = late
        vm.load(force = true)
        gateway.readError = IllegalStateException("offline")
        owners.value = SocialOwner("other", backend)
        assertNull(vm.ui.value.circle)
        assertNotNull(vm.ui.value.error)
        late.complete(Result.success(CircleRead(circle("Old"), CircleBonusPaid(4, "Old"))))
        assertNull(vm.ui.value.circle)
        assertNull(vm.ui.value.paid)
        owners.value = SocialOwner("other", Any())
        assertNull(vm.ui.value.circle)
        assertNotNull(vm.ui.value.error)
    }

    @Test
    fun `old action cannot close new owner dialog or replace payout`() {
        val gateway = FakeGateway().apply { circle = circle() }
        val owners = MutableStateFlow(SocialOwner("me", Any()))
        val vm = CircleViewModel(gateway, owners) { owners.value }
        val late = CompletableDeferred<Result<Unit>>()
        gateway.delayedAction = late
        var done = false
        vm.create("Old") { done = true }
        gateway.paid = CircleBonusPaid(8, "New")
        owners.value = SocialOwner("other", Any())
        late.complete(Result.success(Unit))
        assertFalse(done)
        assertEquals(CircleBonusPaid(8, "New"), vm.ui.value.paid)
        assertFalse(vm.ui.value.actionBusy)
    }

    @Test
    fun `an action from the old roster cannot leave the new circle before its flow emission`() {
        val gateway = FakeGateway().apply { circle = circle("Old") }
        var liveOwner = SocialOwner("me", Any())
        val owners = MutableStateFlow(liveOwner)
        val vm = CircleViewModel(gateway, owners) { liveOwner }
        gateway.circle = circle("New").copy(ownerId = "other")
        gateway.onAccepted = { if (it == "leave") gateway.circle = null }
        liveOwner = SocialOwner("other", Any())
        vm.leave()
        assertEquals("New", vm.ui.value.circle?.name)
        assertEquals("other", vm.ui.value.myUserId)
        assertFalse(vm.ui.value.actionBusy)
    }

    @Test
    fun `the reset is shown in the lifter's own clock`() {
        // Wednesday 1 October 2026, 12:00 UTC: the week closes Monday 5 October 00:00 UTC.
        val now = Instant.parse("2026-10-01T12:00:00Z")
        assertEquals("Mon 5 Oct, 00:00", circleResetLabel(now, ZoneId.of("UTC"), Locale.ENGLISH))
        // Los Angeles is seven hours behind: the same instant is Sunday evening there.
        assertEquals("Sun 4 Oct, 17:00", circleResetLabel(now, ZoneId.of("America/Los_Angeles"), Locale.ENGLISH))
        // Auckland is thirteen ahead: Monday lunchtime.
        assertEquals("Mon 5 Oct, 13:00", circleResetLabel(now, ZoneId.of("Pacific/Auckland"), Locale.ENGLISH))
        // On the Monday itself the NEXT Monday is the reset, never "now".
        val monday = Instant.parse("2026-10-05T00:00:00Z")
        assertEquals("Mon 12 Oct, 00:00", circleResetLabel(monday, ZoneId.of("UTC"), Locale.ENGLISH))
    }
}
