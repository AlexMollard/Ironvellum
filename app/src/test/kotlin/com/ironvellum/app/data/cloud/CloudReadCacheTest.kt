package com.ironvellum.app.data.cloud

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudReadCacheTest {
    @Test fun sameUserDifferentBackendDoesNotReuseCachedOrInflightData() = runBlocking {
        val cache = CloudReadCache()
        val firstBackend = Any()
        val secondBackend = Any()
        val release = CompletableDeferred<Unit>()
        val old = async(start = CoroutineStart.UNDISPATCHED) {
            cache.getOrFetch("inbox", 30000, false, "user", firstBackend) {
                release.await()
                "old backend"
            }
        }
        assertEquals("new backend", cache.getOrFetch("inbox", 30000, false, "user", secondBackend) { "new backend" })
        release.complete(Unit)
        assertEquals("old backend", old.await())
        assertEquals("new backend", cache.getOrFetch("inbox", 30000, false, "user", secondBackend) { "wrong" })
    }

    @Test fun ordinaryReadsJoinButForcedReadReplacesSlot() = runBlocking {
        val cache = CloudReadCache()
        val backend = Any()
        val release = CompletableDeferred<Unit>()
        var fetches = 0
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            cache.getOrFetch("inbox", 30000, false, "user", backend) {
                fetches++
                release.await()
                "old"
            }
        }
        val joined = async(start = CoroutineStart.UNDISPATCHED) {
            cache.getOrFetch("inbox", 30000, false, "user", backend) { fetches++; "unexpected" }
        }
        assertEquals("fresh", cache.getOrFetch("inbox", 30000, true, "user", backend) { fetches++; "fresh" })
        release.complete(Unit)
        assertEquals("old", first.await())
        assertEquals("old", joined.await())
        assertEquals("fresh", cache.getOrFetch("inbox", 30000, false, "user", backend) { "unexpected" })
        assertEquals(2, fetches)
    }

    @Test fun cancellationReleasesWaitersAndDoesNotCacheFailure() = runBlocking {
        val cache = CloudReadCache()
        val backend = Any()
        val release = CompletableDeferred<Unit>()
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            cache.getOrFetch("inbox", 30000, false, "user", backend) { release.await(); "old" }
        }
        val joined = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { cache.getOrFetch("inbox", 30000, false, "user", backend) { "wrong" } }
        }
        first.cancelAndJoin()
        assertEquals(true, joined.await().isFailure)
        assertEquals("fresh", cache.getOrFetch("inbox", 30000, false, "user", backend) { "fresh" })
    }
}
