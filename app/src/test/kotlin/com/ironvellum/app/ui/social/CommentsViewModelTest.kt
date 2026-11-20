package com.ironvellum.app.ui.social

import androidx.lifecycle.ViewModelStore

import com.ironvellum.app.data.cloud.Account
import com.ironvellum.app.data.cloud.AllyWorkout
import com.ironvellum.app.data.cloud.Comment
import com.ironvellum.app.data.cloud.ProfileNameDto
import com.ironvellum.app.data.cloud.Liker
import com.ironvellum.app.data.cloud.Reaction
import com.ironvellum.app.data.cloud.ReportReason
import kotlinx.coroutines.CancellationException
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
class CommentsViewModelTest {
    private class Fake : CommentsGateway {
        override val owners = MutableStateFlow(SocialOwner("a", Any()))
        override fun owner() = owners.value
        override fun account(): Account? = null
        val reads = mutableListOf<CompletableDeferred<Result<List<Comment>>>>()
        var post = CompletableDeferred<Result<Comment>>()
        var removal = CompletableDeferred<Result<Unit>>()
        var likerResult: Result<List<Liker>> = Result.success(emptyList())
        var summary: Result<Pair<Map<Reaction, Int>, Reaction?>> = Result.success(emptyMap<Reaction, Int>() to null)
        var reactionWrite: CompletableDeferred<Result<Unit>>? = null
        override suspend fun comments(sessionId: String, force: Boolean): Result<List<Comment>> {
            val response = CompletableDeferred<Result<List<Comment>>>()
            reads += response
            return withContext(NonCancellable) { response.await() }
        }
        override suspend fun addComment(sessionId: String, body: String) = withContext(NonCancellable) { post.await() }
        override suspend fun deleteComment(id: String) = withContext(NonCancellable) { removal.await() }
        override suspend fun workout(sessionId: String, force: Boolean): Result<AllyWorkout> = Result.failure(IllegalStateException("offline"))
        override suspend fun likers(sessionId: String): Result<List<Liker>> = likerResult
        override suspend fun reactionSummary(sessionId: String) = summary
        override suspend fun visibleProfile(userId: String): Result<ProfileNameDto?> = Result.success(null)
        override suspend fun react(sessionId: String, reaction: Reaction?) = reactionWrite?.await() ?: Result.success(Unit)
        override suspend fun report(userId: String, reason: ReportReason, note: String, sessionId: String, commentId: String) = Result.success(Unit)
        fun finish() {
            reads.forEach { it.complete(Result.success(emptyList())) }
            post.complete(Result.failure(CancellationException("test finished")))
            removal.complete(Result.failure(CancellationException("test finished")))
        }
    }

    private val comment = Comment("1", "session", "a", "A", "hello", 1L)
    private val earlier = Comment("earlier", "session", "other", "Other", "Earlier comment", 0L)
    private val models = ViewModelStore()
    private val gateways = mutableListOf<Fake>()
    private fun gateway() = Fake().also { gateways += it }
    private fun model(fake: Fake) = CommentsViewModel(fake, "session").also { models.put("comments", it) }
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() {
        models.clear()
        gateways.forEach { it.finish() }
        Dispatchers.resetMain()
    }

    @Test fun `accepted post survives old read and fresh read remains authoritative`() {
        val fake = gateway()
        val vm = model(fake)
        vm.onDraft("hello")
        vm.send()
        assertTrue(vm.ui.value.sending)
        fake.post.complete(Result.success(comment))
        assertEquals("", vm.ui.value.draft)
        assertEquals(listOf(comment), vm.ui.value.comments)
        fake.reads[0].complete(Result.success(listOf(earlier)))
        assertEquals(listOf(earlier, comment), vm.ui.value.comments)
        vm.load(true)
        fake.reads[1].complete(Result.success(listOf(earlier)))
        assertEquals(listOf(earlier), vm.ui.value.comments)
        assertFalse(vm.ui.value.sending)
    }

    @Test fun `deleted comment cannot return from older snapshot`() {
        val fake = gateway()
        val vm = model(fake)
        vm.onDraft("hello")
        vm.send()
        fake.post.complete(Result.success(comment))
        vm.delete(comment)
        fake.removal.complete(Result.success(Unit))
        fake.reads[0].complete(Result.success(listOf(earlier, comment)))
        assertEquals(listOf(earlier), vm.ui.value.comments)
        assertFalse(vm.ui.value.loading)
        vm.load(true)
        fake.reads[1].complete(Result.success(listOf(earlier)))
        assertEquals(listOf(earlier), vm.ui.value.comments)
    }

    @Test fun `failed mutation keeps draft and does not invalidate read`() {
        val fake = gateway()
        val vm = model(fake)
        vm.onDraft("hello")
        vm.send()
        fake.post.complete(Result.failure(IllegalStateException("refused")))
        assertEquals("hello", vm.ui.value.draft)
        assertFalse(vm.ui.value.sending)
        fake.reads[0].complete(Result.success(listOf(comment)))
        assertEquals(listOf(comment), vm.ui.value.comments)
        vm.load(true)
        vm.delete(comment)
        fake.removal.complete(Result.failure(IllegalStateException("refused")))
        fake.reads[1].complete(Result.success(emptyList()))
        assertTrue(vm.ui.value.comments.isEmpty())
    }

    @Test fun `latest retry wins over older reads`() {
        val fake = gateway()
        val vm = model(fake)
        vm.load(true)
        vm.load(true)
        fake.reads[2].complete(Result.success(listOf(comment)))
        fake.reads[1].complete(Result.success(emptyList()))
        fake.reads[0].complete(Result.success(emptyList()))
        assertEquals(listOf(comment), vm.ui.value.comments)
        assertFalse(vm.ui.value.loading)
    }

    @Test fun `account and backend changes discard late reads and mutations`() {
        val fake = gateway()
        val vm = model(fake)
        vm.onDraft("hello")
        vm.send()
        fake.owners.value = SocialOwner("b", fake.owner().backend)
        fake.reads[1].complete(Result.failure(IllegalStateException("offline")))
        fake.post.complete(Result.success(comment))
        fake.reads[0].complete(Result.success(listOf(comment)))
        assertTrue(vm.ui.value.comments.isEmpty())
        assertEquals("", vm.ui.value.draft)
        assertEquals("b", vm.ui.value.myUserId)
        assertFalse(vm.ui.value.sending)
        val oldRead = fake.reads.size
        vm.load(true)
        fake.owners.value = SocialOwner("b", Any())
        fake.reads[oldRead + 1].complete(Result.failure(IllegalStateException("offline")))
        fake.reads[oldRead].complete(Result.success(listOf(comment)))
        assertTrue(vm.ui.value.comments.isEmpty())
        assertFalse(vm.ui.value.loading)
    }
    @Test fun `new owner failed read clears old thread and late delete cannot affect it`() {
        val fake = gateway()
        val vm = model(fake)
        fake.reads[0].complete(Result.success(listOf(comment)))
        vm.delete(comment)
        fake.owners.value = SocialOwner("b", Any())
        fake.reads[1].complete(Result.failure(IllegalStateException("offline")))
        assertTrue(vm.ui.value.comments.isEmpty())
        vm.load(true)
        fake.reads[2].complete(Result.success(listOf(comment)))
        fake.removal.complete(Result.success(Unit))
        assertEquals(listOf(comment), vm.ui.value.comments)
    }
    @Test fun `fresh snapshot retires earlier changes while preserving a later accepted post`() {
        val fake = gateway()
        val vm = model(fake)
        fake.reads[0].complete(Result.success(emptyList()))
        vm.onDraft("hello")
        vm.send()
        fake.post.complete(Result.success(comment))
        vm.load(true)
        val later = Comment("later", "session", "a", "A", "Later comment", 2L)
        fake.post = CompletableDeferred()
        vm.onDraft("later")
        vm.send()
        fake.post.complete(Result.success(later))
        // The server removed the first post before this fresh read; the second post happened after its snapshot.
        fake.reads[1].complete(Result.success(emptyList()))
        assertEquals(listOf(later), vm.ui.value.comments)
    }

    @Test fun `reaction counts remain unknown on failed first summary and survive failed refresh`() {
        val fake = gateway()
        fake.summary = Result.failure(IllegalStateException("offline"))
        val vm = model(fake)
        assertNull(vm.ui.value.likers)

        vm.react(Reaction.IRON)
        assertNull(vm.ui.value.likers)

        fake.summary = Result.success(mapOf(Reaction.IRON to 1) to null)
        vm.loadReactions()
        assertEquals(emptyList<Liker>(), vm.ui.value.likers)

        fake.summary = Result.failure(IllegalStateException("offline"))
        vm.loadReactions()
        assertEquals(emptyList<Liker>(), vm.ui.value.likers)
        assertEquals(mapOf(Reaction.IRON to 1), vm.ui.value.reactionCounts)
    }

    @Test fun `authoritative counts and older own reaction survive name cap and optimistic failure`() {
        val fake = gateway()
        fake.likerResult = Result.success((1..50).map { Liker("other$it", "Other", it.toLong(), Reaction.IRON) })
        fake.summary = Result.success(mapOf(Reaction.IRON to 50, Reaction.FLAME to 1) to Reaction.FLAME)
        fake.reactionWrite = CompletableDeferred()
        val vm = model(fake)
        assertEquals(51, vm.ui.value.reactionCounts!!.values.sum())
        assertEquals(Reaction.FLAME, vm.ui.value.myReaction)
        vm.react(Reaction.IRON)
        assertEquals(51, vm.ui.value.reactionCounts!![Reaction.IRON])
        assertEquals(0, vm.ui.value.reactionCounts!![Reaction.FLAME])
        assertEquals(Reaction.IRON, vm.ui.value.myReaction)
        fake.reactionWrite!!.complete(Result.failure(IllegalStateException("offline")))
        assertEquals(Reaction.FLAME, vm.ui.value.myReaction)
        assertEquals(50, vm.ui.value.reactionCounts!![Reaction.IRON])
        assertEquals(1, vm.ui.value.reactionCounts!![Reaction.FLAME])
    }

    @Test fun `switching owner clears previous reaction selection when new read fails`() {
        val fake = gateway()
        fake.summary = Result.success(mapOf(Reaction.FLAME to 51) to Reaction.FLAME)
        val vm = model(fake)
        fake.summary = Result.failure(IllegalStateException("denied"))
        fake.owners.value = SocialOwner("b", fake.owner().backend)
        assertNull(vm.ui.value.reactionCounts)
        assertNull(vm.ui.value.myReaction)
        assertNull(vm.ui.value.likers)
    }


}
