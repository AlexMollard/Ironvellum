package com.ironvellum.app.domain

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The pushed snapshot is built at [WebSnapshotBuilder.stableNowMs] so that an unchanged training state hashes the
 * same all day: the unchanged check in the push never held while fatigue, the rolling window and the 30-day
 * records were built at the real clock.
 */
class WebSnapshotStableClockTest {
    private val base = WebSnapshotFixtures.input()
    private val zone: ZoneId = base.zone
    private val hour = 3_600_000L
    // 10:00 local on a day after every fixture workout.
    private val morning = LocalDate.of(2026, 10, 27).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()

    private fun hashAt(history: List<Pair<WorkoutSession, List<SessionSet>>>, now: Long): String {
        val at = WebSnapshotBuilder.stableNowMs(now, zone, history)
        val json = WebSnapshotJson.write(WebSnapshotBuilder.build(base.copy(history = history, nowMs = at, builtAtMs = now)))
        return WebSnapshotJson.contentHash(json)
    }

    @Test fun builtAtMsIsTheRealClock() {
        val at = WebSnapshotBuilder.stableNowMs(morning, zone, base.history)
        assertNotEquals(morning, at)
        assertEquals(morning, WebSnapshotBuilder.build(base.copy(nowMs = at, builtAtMs = morning)).builtAtMs)
    }

    @Test fun theRealClockWouldHaveChangedTheHashEveryHour() {
        // The bug: every time-derived field moved with the clock.
        fun raw(now: Long) = WebSnapshotJson.contentHash(WebSnapshotJson.write(WebSnapshotBuilder.build(base.copy(nowMs = now))))
        assertNotEquals(raw(morning), raw(morning + hour))
    }

    @Test fun sameTrainingAnHourApartHashesTheSame() {
        assertEquals(hashAt(base.history, morning), hashAt(base.history, morning + hour))
        assertEquals(hashAt(base.history, morning), hashAt(base.history, morning + 12 * hour))
    }

    @Test fun aNewLocalDayChangesTheHashSoFatigueRefreshesDaily() {
        assertNotEquals(hashAt(base.history, morning), hashAt(base.history, morning + 24 * hour))
    }

    @Test fun aNewWorkoutChangesTheHash() {
        val workout = WorkoutSession(id = 99, label = "Today", startedAtMs = morning + hour / 6, completedAtMs = morning + hour / 2)
        val sets = listOf(SessionSet(exerciseId = 1, exerciseName = "Pull-up", setIndex = 0, reps = 8, done = true))
        assertNotEquals(hashAt(base.history, morning + hour), hashAt(base.history + (workout to sets), morning + hour))
    }

    @Test fun clockIsLocalMidnightUnlessAWorkoutIsLater() {
        val midnight = LocalDate.of(2026, 10, 27).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(midnight, WebSnapshotBuilder.stableNowMs(morning, zone, base.history))
        val workout = WorkoutSession(id = 99, label = "Today", startedAtMs = morning - hour, completedAtMs = morning - hour / 2)
        assertEquals(morning - hour / 2, WebSnapshotBuilder.stableNowMs(morning, zone, base.history + (workout to emptyList())))
    }

    @Test fun clockNeverRunsAheadOfTheRealOne() {
        val future = WorkoutSession(id = 99, label = "Later", startedAtMs = morning + 5 * hour, completedAtMs = morning + 6 * hour)
        assertEquals(morning, WebSnapshotBuilder.stableNowMs(morning, zone, listOf(future to emptyList())))
    }
}
