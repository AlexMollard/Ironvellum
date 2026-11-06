package com.ironvellum.app.domain

import org.junit.Assert.*
import org.junit.Test

class PreparationProgressTest {
    @Test fun legacyOnlyScopeRoundTripsAndOldJsonDefaultsToFullScope() {
        val original = PreparationProgress(legacyOnly = true)
        assertEquals(original, PreparationCodec.decodeProgress(PreparationCodec.encodeProgress(original)).getOrThrow())
        assertEquals(PreparationProgress(), PreparationCodec.decodeProgress("{\"status\":\"OPEN\"}").getOrThrow())
    }
    @Test fun importedNonContiguousLegacyTicksAreNotRepeated() {
        val legacy = snapshot.copy(legacyStepCount = 2)
        var p = legacyPreparationProgress(legacy, WarmupStep.State.OPEN, setOf(2))
        assertEquals(setOf("1:0:0"), p.completedUnitKeys)
        repeat(4) { p = p.advance(legacy, PreparationAction.COMPLETE_UNIT, 0, p.currentUnitKey) }
        assertEquals(PreparationStatus.DONE, p.status)
        assertEquals(5, p.completedUnitKeys.size)
    }
    @Test fun restoredTimerAtTargetStillNeedsAnExplicitCurrentTap() {
        val open = PreparationProgress()
        val running = open.advance(snapshot, PreparationAction.START, 1000)
        val restored = PreparationCodec.decodeProgress(PreparationCodec.encodeProgress(running)).getOrThrow().recovered(snapshot, 36000)
        assertEquals(running.currentUnitKey, restored.currentUnitKey)
        val next = restored.advance(snapshot, PreparationAction.COMPLETE_UNIT, 36000, running.currentUnitKey)
        assertEquals("0:0:1", next.currentUnitKey)
        assertEquals(next, next.advance(snapshot, PreparationAction.COMPLETE_UNIT, 36001, running.currentUnitKey))
    }

    @Test fun pausingReadinessRetainsWholeTargetAndResumeGetsFreshReadiness() {
        val paused = PreparationProgress().advance(snapshot, PreparationAction.START, 1000)
            .advance(snapshot, PreparationAction.PAUSE, 3000)
        assertEquals(30000L, paused.remainingMs)
        assertNull(paused.readyUntilMs)
        val resumed = paused.advance(snapshot, PreparationAction.RESUME, 100000)
        assertEquals(105000L, resumed.readyUntilMs)
        assertEquals(135000L, resumed.deadlineMs)
    }
    private val snapshot = PreparationSnapshot(PreparationKind.WARMUP, "test", listOf(
        PreparationStep(1, "Arm Circles", PreparationMeasure.SECONDS, 30, rounds = 2, repeat = PreparationRepeat.DIRECTIONS),
        PreparationStep(2, "Squat", PreparationMeasure.REPS, 10),
    ))

    @Test fun timerHasFiveSecondReadinessAndExpiryNeverAdvances() {
        val running = PreparationProgress().advance(snapshot, PreparationAction.START, 1000)
        assertEquals(6000L, running.readyUntilMs)
        assertEquals(36000L, running.deadlineMs)
        assertEquals(30000L, running.remainingMs(2000))
        assertEquals(20000L, running.remainingMs(16000))
        assertEquals(0L, running.remainingMs(99999))
        assertEquals("0:0:0", running.currentUnitKey)
    }

    @Test fun pauseAndResumePreserveDurationAndClearDeadlines() {
        val paused = PreparationProgress().advance(snapshot, PreparationAction.START, 1000)
            .advance(snapshot, PreparationAction.PAUSE, 16000)
        assertEquals(PreparationStatus.PAUSED, paused.status)
        assertEquals(20000L, paused.remainingMs(99999))
        assertNull(paused.deadlineMs)
        val resumed = paused.advance(snapshot, PreparationAction.RESUME, 100000)
        assertEquals(125000L, resumed.deadlineMs)
    }

    @Test fun duplicateMissingAndStaleUnitKeysCannotSkipAnotherUnit() {
        val first = PreparationProgress()
        assertEquals(first, first.advance(snapshot, PreparationAction.COMPLETE_UNIT, 0))
        val next = first.advance(snapshot, PreparationAction.COMPLETE_UNIT, 0, first.currentUnitKey)
        assertEquals("0:0:1", next.currentUnitKey)
        assertEquals(next, next.advance(snapshot, PreparationAction.COMPLETE_UNIT, 1, first.currentUnitKey))
        assertEquals(next, next.advance(snapshot, PreparationAction.SKIP_UNIT, 1, "wrong"))
        var p = next
        val expected = listOf("0:1:0", "0:1:1", "1:0:0")
        expected.forEach { key ->
            p = p.advance(snapshot, PreparationAction.COMPLETE_UNIT, 0, p.currentUnitKey)
            assertEquals(key, p.currentUnitKey)
        }
        p = p.advance(snapshot, PreparationAction.SKIP_UNIT, 0, p.currentUnitKey)
        assertEquals(PreparationStatus.DONE, p.status)
        assertEquals(setOf("1:0:0"), p.skippedUnitKeys)
        assertEquals(4, p.completedUnitKeys.size)
        val sides = snapshot.copy(steps = snapshot.steps.take(1).map { it.copy(repeat = PreparationRepeat.SIDES) })
        val right = first.advance(sides, PreparationAction.COMPLETE_UNIT, 0, first.currentUnitKey)
        assertEquals("0:0:1", right.currentUnitKey)
        assertEquals("0:1:0", right.advance(sides, PreparationAction.COMPLETE_UNIT, 0, right.currentUnitKey).currentUnitKey)
    }

    @Test fun editorBoundsRejectInvalidTargetsButCodecsRetainLegacyPositiveTargets() {
        val legacy = snapshot.steps.first().copy(target = 900)
        assertFalse(legacy.validForEditor())
        assertEquals(listOf(legacy), PreparationCodec.decodeSteps(PreparationCodec.encodeSteps(listOf(legacy))).getOrThrow())
        assertTrue(snapshot.steps.first().validForEditor())
        assertFalse(legacy.copy(target = 0).validForEditor())
        assertFalse(legacy.copy(target = 30, rounds = 0).validForEditor())
        assertFalse(legacy.copy(target = 30, note = "x".repeat(1001)).validForEditor())
    }
    @Test fun repUnitsStayRunningWithoutTimerDeadlinesAndRebootPausesTimedUnits() {
        val reps = snapshot.copy(steps = snapshot.steps.takeLast(1))
        val running = PreparationProgress().advance(reps, PreparationAction.START, 1000)
        assertEquals(running, running.recovered(2000))
        val timed = PreparationProgress().advance(snapshot, PreparationAction.START, 100000)
        assertEquals(PreparationStatus.PAUSED, timed.recovered(0).status)
        assertNull(timed.recovered(0).deadlineMs)
    }
}
