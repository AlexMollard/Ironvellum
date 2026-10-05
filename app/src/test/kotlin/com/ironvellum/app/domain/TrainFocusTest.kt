package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** The Train lead card's one rule: continue, begin, sealed, respite or no cycle. */
class TrainFocusTest {

    private val midnight = 1_000_000L
    private val mon = WorkoutPreset(id = 1, name = "Full Body A", scheduledDay = 1)
    private val wed = WorkoutPreset(id = 2, name = "Full Body B", scheduledDay = 3)
    private val loose = WorkoutPreset(id = 3, name = "Arms")
    private val cycle = listOf(mon, wed, loose)

    private fun sealed(presetId: Long?, atMs: Long = midnight + 10) =
        WorkoutSession(id = 9, presetId = presetId, label = "x", startedAtMs = atMs - 5, completedAtMs = atMs)

    private val day = 86_400_000L

    // Midnight opens [today]; the week opened at the Monday before it.
    private fun resolve(today: Int, sessions: List<WorkoutSession> = emptyList(), live: WorkoutSession? = null) =
        TrainFocus.resolve(cycle, sessions, live, today, midnight, weekStartMs = midnight - (today - 1) * day)

    @Test
    fun `a live trial is continued whatever the day`() {
        val live = WorkoutSession(id = 5, presetId = null, label = "Open Trial", startedAtMs = midnight)
        assertEquals(TrainFocus.Live(live, null), resolve(today = 1, live = live))
    }

    @Test
    fun `a scheduled day offers its rite until a trial from it is sealed today`() {
        assertEquals(TrainFocus.Begin(mon), resolve(today = 1))
        assertEquals(TrainFocus.Begin(mon), resolve(today = 1, sessions = listOf(sealed(mon.id, atMs = midnight - 1))))
        assertEquals(TrainFocus.Begin(mon), resolve(today = 1, sessions = listOf(sealed(wed.id))))
        val seal = sealed(mon.id)
        assertEquals(TrainFocus.Sealed(mon, seal), resolve(today = 1, sessions = listOf(seal)))
    }

    @Test
    fun `a rite taken early in its week is sealed on its own day, but not one from last week`() {
        val early = sealed(wed.id, atMs = midnight - 2 * day + 10)
        assertEquals(TrainFocus.Sealed(wed, early), resolve(today = 3, sessions = listOf(early)))
        val lastWeek = sealed(wed.id, atMs = midnight - 3 * day)
        assertEquals(TrainFocus.Begin(wed), resolve(today = 3, sessions = listOf(lastWeek)))
    }

    @Test
    fun `a rest day offers the next rite early, wrapping the week, until anything is sealed today`() {
        assertEquals(TrainFocus.Respite(wed, 3, canTakeEarly = true), resolve(today = 2))
        assertEquals(TrainFocus.Respite(mon, 1, canTakeEarly = true), resolve(today = 6))
        assertEquals(TrainFocus.Respite(wed, 3, canTakeEarly = false), resolve(today = 2, sessions = listOf(sealed(null))))
    }

    @Test
    fun `nothing scheduled is a respite with no next rite, and no rites is no cycle`() {
        assertEquals(TrainFocus.Respite(null, null, canTakeEarly = false), TrainFocus.resolve(listOf(loose), emptyList(), null, 2, midnight, midnight))
        assertEquals(TrainFocus.NoCycle, TrainFocus.resolve(emptyList(), emptyList(), null, 2, midnight, midnight))
    }
}
