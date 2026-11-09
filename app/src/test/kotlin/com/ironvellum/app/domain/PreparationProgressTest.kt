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
    @Test fun restoredExpiredTimerAdvancesOnceAndGivesTheNextDirectionItsFullTime() {
        val running = PreparationProgress().advance(snapshot, PreparationAction.START, 1000)
        val restored = PreparationCodec.decodeProgress(PreparationCodec.encodeProgress(running)).getOrThrow().recovered(snapshot, 360000)
        val next = restored.advance(snapshot, PreparationAction.TIMER_EXPIRED, 360000, running.currentUnitKey)
        assertEquals("0:0:1", next.currentUnitKey)
        assertEquals(PreparationStatus.RUNNING, next.status)
        assertEquals(360000L, next.readyUntilMs)
        assertEquals(390000L, next.deadlineMs)
        assertEquals(30000L, next.remainingMs(360000))
        assertEquals(next, next.advance(snapshot, PreparationAction.TIMER_EXPIRED, 360001, running.currentUnitKey))
        assertEquals(next, next.advance(snapshot, PreparationAction.COMPLETE_UNIT, 360001, running.currentUnitKey))
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

    @Test fun expiryRequiresRunningTimedUnitCurrentKeyAndReachedDeadline() {
        val open = PreparationProgress()
        assertEquals(open, open.advance(snapshot, PreparationAction.TIMER_EXPIRED, 36000, open.currentUnitKey))
        val running = open.advance(snapshot, PreparationAction.START, 1000)
        assertEquals(6000L, running.readyUntilMs)
        assertEquals(36000L, running.deadlineMs)
        assertEquals(30000L, running.remainingMs(2000))
        assertEquals(20000L, running.remainingMs(16000))
        assertEquals(running, running.advance(snapshot, PreparationAction.TIMER_EXPIRED, 35999, running.currentUnitKey))
        assertEquals(running, running.advance(snapshot, PreparationAction.TIMER_EXPIRED, 36000))
        assertEquals(running, running.advance(snapshot, PreparationAction.TIMER_EXPIRED, 36000, "wrong"))
        val paused = running.advance(snapshot, PreparationAction.PAUSE, 16000)
        assertEquals(paused, paused.advance(snapshot, PreparationAction.TIMER_EXPIRED, 36000, paused.currentUnitKey))
    }
    @Test fun automaticRoundsStayContinuousAndRepStepsWaitForManualCompletion() {
        var p = PreparationProgress().advance(snapshot, PreparationAction.START, 1000)
        listOf("0:0:1", "0:1:0", "0:1:1", "1:0:0").forEach { key ->
            val end = p.deadlineMs!!
            p = p.advance(snapshot, PreparationAction.TIMER_EXPIRED, end, p.currentUnitKey)
            assertEquals(key, p.currentUnitKey)
            assertEquals(PreparationStatus.RUNNING, p.status)
            if (key != "1:0:0") assertEquals(end, p.readyUntilMs)
        }
        assertEquals(4, p.completedUnitKeys.size)
        assertNull(p.deadlineMs)
        assertEquals(p, p.advance(snapshot, PreparationAction.TIMER_EXPIRED, 999999, p.currentUnitKey))
        p = p.advance(snapshot, PreparationAction.COMPLETE_UNIT, 999999, p.currentUnitKey)
        assertEquals(PreparationStatus.DONE, p.status)
        assertEquals(5, p.completedUnitKeys.size)
    }
    @Test fun sidesSwitchImmediatelyButDifferentExercisesGetPreparationTimeAndFinalExpiryFinishes() {
        PreparationKind.entries.forEach { kind ->
            val routine = snapshot.copy(kind = kind, steps = listOf(
                snapshot.steps.first().copy(rounds = 1, repeat = PreparationRepeat.SIDES),
                snapshot.steps.first().copy(exerciseId = 3, exerciseName = "Calf Stretch", rounds = 1, repeat = PreparationRepeat.ONCE),
            ))
            var p = PreparationProgress().advance(routine, PreparationAction.START, 1000)
            p = p.advance(routine, PreparationAction.TIMER_EXPIRED, 36000, p.currentUnitKey)
            assertEquals("0:0:1", p.currentUnitKey)
            assertEquals(36000L, p.readyUntilMs)
            p = p.advance(routine, PreparationAction.TIMER_EXPIRED, 66000, p.currentUnitKey)
            assertEquals("1:0:0", p.currentUnitKey)
            assertEquals(71000L, p.readyUntilMs)
            assertEquals(101000L, p.deadlineMs)
            p = p.advance(routine, PreparationAction.TIMER_EXPIRED, 101000, p.currentUnitKey)
            assertEquals(PreparationStatus.DONE, p.status)
            assertEquals(setOf("0:0:0", "0:0:1", "1:0:0"), p.completedUnitKeys)
            assertTrue(p.skippedUnitKeys.isEmpty())
            assertNull(p.deadlineMs)
        }
    }
    @Test fun manualRepCompletionStartsFollowingTimerButPausedCompletionDoesNotRun() {
        val routine = snapshot.copy(steps = snapshot.steps.reversed())
        val running = PreparationProgress().advance(routine, PreparationAction.START, 1000)
        val next = running.advance(routine, PreparationAction.COMPLETE_UNIT, 2000, running.currentUnitKey)
        assertEquals(PreparationStatus.RUNNING, next.status)
        assertEquals(7000L, next.readyUntilMs)
        assertEquals(37000L, next.deadlineMs)
        val paused = running.advance(routine, PreparationAction.PAUSE, 2000)
        assertEquals(PreparationStatus.OPEN, paused.advance(routine, PreparationAction.COMPLETE_UNIT, 2000, paused.currentUnitKey).status)
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
