package com.ironvellum.app.data.cloud

import com.ironvellum.app.domain.SessionSet
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WireClampTest {
    @Test fun aShortTextIsLeftAlone() {
        assertEquals("heavy singles", WireLimits.clamp("heavy singles", WireLimits.SET_MODIFIERS_MAX))
    }

    @Test fun anOverlongSetNoteIsCutToTheCloudsLimit() {
        val note = "tempo 3-1-1 ".repeat(10)
        val cut = WireLimits.clamp(note, WireLimits.SET_MODIFIERS_MAX)
        assertEquals(WireLimits.SET_MODIFIERS_MAX, cut.length)
        assertTrue(note.startsWith(cut))
    }

    @Test fun anOverlongLabelIsCutToEighty() {
        assertEquals(80, WireLimits.clamp("L".repeat(200), WireLimits.IMPORTED_LABEL_MAX).length)
    }

    @Test fun aCutNeverSplitsASurrogatePair() {
        val emoji = "💪"
        val cut = WireLimits.clamp("a".repeat(63) + emoji + "tail", 64)
        assertEquals("a".repeat(63), cut)
        assertFalse(Character.isHighSurrogate(cut.last()))
        assertEquals("a".repeat(62) + emoji, WireLimits.clamp("a".repeat(62) + emoji + "tail", 64))
    }

    @Test fun importedNamesTheCloudRefusesDoNotFit() {
        fun set(name: String) = SessionSet(exerciseId = 1, exerciseName = name, setIndex = 0, reps = 1)
        assertTrue(CloudSync.importedSetFits(set("x".repeat(64))))
        assertFalse(CloudSync.importedSetFits(set("x".repeat(65))))
    }

    @Test fun attemptKeepsAFailureAsAResultButNeverSwallowsACancellation() {
        assertTrue(attempt { error("boom") }.isFailure)
        assertEquals(3, attempt { 3 }.getOrThrow())
        try {
            attempt { throw CancellationException("stop") }
            fail("a cancellation was swallowed")
        } catch (expected: CancellationException) {
            assertEquals("stop", expected.message)
        }
    }
}
