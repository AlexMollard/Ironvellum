package com.ironvellum.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class HealthResumeSyncTest {

    private val today = LocalDate.of(2026, 10, 22)
    private var now = 1_000_000L
    private val reads = mutableListOf<Int>()
    private val source = FakeSource()
    private val store = FakeStore()
    private lateinit var sync: HealthResumeSync

    private class FakeSource : HealthResumeSync.Source {
        var ready = true
        var types = "Steps,Weight"
        var minted = 0
        var onChanges: (String) -> ChangeBatch = { ChangeBatch(0, null, false, "next", false) }
        val asked = mutableListOf<String>()
        override suspend fun ready() = ready
        override suspend fun readTypes() = types
        override suspend fun newToken() = "minted-${++minted}"
        override suspend fun changes(token: String): ChangeBatch {
            asked += token
            return onChanges(token)
        }
    }

    private class FakeStore : HealthResumeSync.TokenStore {
        var value: StoredToken? = null
        override fun load() = value
        override fun save(value: StoredToken) { this.value = value }
        override fun clear() { value = null }
    }

    @Before
    fun build() {
        sync = HealthResumeSync(source, store, { reads += it }, { now }, { today })
    }

    private fun pastThrottle() { now += HealthResumeSync.THROTTLE_MS }

    @Test
    fun `a resume right after process start is covered by the start-up sync`() = runBlocking {
        sync.onResume()
        assertEquals(emptyList<Int>(), reads)
    }

    @Test
    fun `start-up reads the full window and stores a token`() = runBlocking {
        sync.startup(90)
        assertEquals(listOf(90), reads)
        assertEquals(StoredToken("Steps,Weight", "minted-1"), store.value)
    }

    @Test
    fun `throttle allows one resume per five minutes`() = runBlocking {
        store.value = StoredToken("Steps,Weight", "t0")
        source.onChanges = { ChangeBatch(1, today, false, "t1", false) }
        pastThrottle()
        sync.onResume()
        now += HealthResumeSync.THROTTLE_MS - 1
        sync.onResume()
        assertEquals(listOf(1), reads)
        now += 1
        sync.onResume()
        assertEquals(listOf(1, 1), reads)
        assertEquals(listOf("t0", "t1"), source.asked)
    }

    @Test
    fun `start-up inside the window blocks the first resume`() = runBlocking {
        sync.startup(90)
        now += 60_000
        sync.onResume()
        assertEquals(listOf(90), reads)
        assertEquals(emptyList<String>(), source.asked)
    }

    @Test
    fun `changes read only back to the oldest changed day`() = runBlocking {
        store.value = StoredToken("Steps,Weight", "t0")
        source.onChanges = { ChangeBatch(3, today.minusDays(4), false, "t1", false) }
        pastThrottle()
        sync.onResume()
        assertEquals(listOf(5), reads)
        assertEquals("t1", store.value?.token)
    }

    @Test
    fun `no changes reads nothing but advances the token`() = runBlocking {
        store.value = StoredToken("Steps,Weight", "t0")
        pastThrottle()
        sync.onResume()
        assertEquals(emptyList<Int>(), reads)
        assertEquals("next", store.value?.token)
    }

    @Test
    fun `a deletion cannot be dated so it reads the fallback window`() = runBlocking {
        store.value = StoredToken("Steps,Weight", "t0")
        source.onChanges = { ChangeBatch(1, null, true, "t1", false) }
        pastThrottle()
        sync.onResume()
        assertEquals(listOf(HealthResumeSync.FALLBACK_DAYS), reads)
    }

    @Test
    fun `an old change is capped at ninety days`() = runBlocking {
        store.value = StoredToken("Steps,Weight", "t0")
        source.onChanges = { ChangeBatch(1, today.minusDays(400), false, "t1", false) }
        pastThrottle()
        sync.onResume()
        assertEquals(listOf(90), reads)
    }

    @Test
    fun `an expired token falls back to fourteen days and a new token`() = runBlocking {
        store.value = StoredToken("Steps,Weight", "old")
        source.onChanges = { ChangeBatch(0, null, false, null, expired = true) }
        pastThrottle()
        sync.onResume()
        assertEquals(listOf(14), reads)
        assertEquals("minted-1", store.value?.token)
    }

    @Test
    fun `a rejected token behaves like an expired one`() = runBlocking {
        store.value = StoredToken("Steps,Weight", "bad")
        source.onChanges = { throw IllegalStateException("invalid token") }
        pastThrottle()
        sync.onResume()
        assertEquals(listOf(14), reads)
        assertEquals("minted-1", store.value?.token)
    }

    @Test
    fun `a token for other granted types is discarded`() = runBlocking {
        store.value = StoredToken("Steps", "old")
        pastThrottle()
        sync.onResume()
        assertEquals(listOf(14), reads)
        assertEquals(emptyList<String>(), source.asked)
        assertEquals(StoredToken("Steps,Weight", "minted-1"), store.value)
    }

    @Test
    fun `no token yet reads the fallback window and mints one`() = runBlocking {
        pastThrottle()
        sync.onResume()
        assertEquals(listOf(14), reads)
        assertEquals("minted-1", store.value?.token)
    }

    @Test
    fun `not ready skips the read and leaves the token alone`() = runBlocking {
        store.value = StoredToken("Steps,Weight", "keep")
        source.ready = false
        pastThrottle()
        sync.onResume()
        assertEquals(emptyList<Int>(), reads)
        assertEquals("keep", store.value?.token)
    }

    @Test
    fun `start-up without Health Connect still imports and stores no token`() = runBlocking {
        source.ready = false
        sync.startup(90)
        assertEquals(listOf(90), reads)
        assertNull(store.value)
    }
}
