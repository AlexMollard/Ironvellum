package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MuscleFatigueTest {

    private val H = 3_600_000L
    private val DAY = 24 * H
    private val NOW = 1_000 * DAY
    private val OLD = NOW - 400 * DAY

    private fun set(name: String, rpe: Int? = 9, mods: String = "", kind: SetKind = SetKind.WORKING) =
        LoggedSet(name, mods, 100.0, 5, rpe, kind, true, 0L)

    private fun sets(count: Int, name: String = "Bench Press", rpe: Int? = 9, mods: String = "") =
        Array(count) { set(name, rpe, mods) }

    private fun session(id: Long, hoursAgo: Long, vararg sets: LoggedSet) = FatigueSession(id, NOW - hoursAgo * H, sets.toList())

    private fun estimate(vararg sessions: FatigueSession, first: Long? = OLD) = FatigueModel.estimate(sessions.toList(), NOW, first)

    private fun FatigueReport.only(muscle: Muscle, sessionId: Long) = contributions.getValue(muscle).single { it.sessionId == sessionId }

    @Test
    fun `empty history is all fresh`() {
        val report = FatigueModel.estimate(emptyList(), NOW, null)
        assertEquals(27, report.contributions.size)
        for (muscle in Muscle.entries) {
            assertEquals(0.0, report.residual(muscle), 0.0)
            assertTrue(report.contributions.getValue(muscle).isEmpty())
        }
        assertTrue(report.newLifter)
    }

    @Test
    fun `decay halves at the half life`() {
        val report = estimate(session(1, 0, set("Bench Press")))
        assertEquals(1.26, report.residual(Muscle.MID_CHEST), 1e-9)
        assertEquals(0.882 / 2, report.residual(Muscle.FRONT_DELTS, NOW + 18 * H), 1e-9)
        assertEquals(1.26 / 2, report.residual(Muscle.MID_CHEST, NOW + 24 * H), 1e-9)
    }

    @Test
    fun `classes decay at their own rates`() {
        val report = estimate(session(1, 12, set("Standing Calf Raise"), set("Back Squat")))
        val calves = report.only(Muscle.CALVES, 1).dose
        val quads = report.only(Muscle.QUADS, 1).dose
        assertEquals(calves * 0.5, report.residual(Muscle.CALVES), 1e-9)
        assertEquals(quads * Math.pow(0.5, 12.0 / 24.0), report.residual(Muscle.QUADS), 1e-9)
    }

    @Test
    fun `seven day window is half open and the future is ignored`() {
        val inside = FatigueSession(1, NOW - 7 * DAY + 60_000, listOf(set("Bench Press")))
        val edge = FatigueSession(2, NOW - 7 * DAY, listOf(set("Bench Press")))
        val older = FatigueSession(3, NOW - 8 * DAY, listOf(set("Bench Press")))
        val future = FatigueSession(4, NOW + 1, listOf(set("Bench Press")))
        val report = FatigueModel.estimate(listOf(inside, edge, older, future), NOW, OLD)
        assertEquals(listOf(1L), report.contributions.getValue(Muscle.MID_CHEST).map { it.sessionId })
    }

    @Test
    fun `novelty runs first second third`() {
        val report = estimate(
            session(1, 5 * 24, set("Bench Press")),
            session(2, 3 * 24, set("Bench Press")),
            session(3, 1 * 24, set("Bench Press")),
        )
        assertEquals(0.9 * 1.4, report.only(Muscle.MID_CHEST, 1).dose, 1e-9)
        assertEquals(0.9 * 1.2, report.only(Muscle.MID_CHEST, 2).dose, 1e-9)
        assertEquals(0.9 * 1.0, report.only(Muscle.MID_CHEST, 3).dose, 1e-9)
        assertTrue(report.only(Muscle.MID_CHEST, 1).novel)
        assertFalse(report.only(Muscle.MID_CHEST, 3).novel)
    }

    @Test
    fun `novelty window is twenty one days`() {
        val farther = estimate(session(1, 23 * 24, set("Bench Press")), session(2, 1 * 24, set("Bench Press")))
        assertEquals("22 days apart: still new", 0.9 * 1.4, farther.only(Muscle.MID_CHEST, 2).dose, 1e-9)
        val nearer = estimate(session(1, 21 * 24, set("Bench Press")), session(2, 1 * 24, set("Bench Press")))
        assertEquals("20 days apart: second time", 0.9 * 1.2, nearer.only(Muscle.MID_CHEST, 2).dose, 1e-9)
    }

    @Test
    fun `a light set still makes the lift familiar`() {
        val report = estimate(session(1, 48, set("Bench Press", rpe = 5)), session(2, 24, set("Bench Press")))
        assertEquals(0.9 * 1.2, report.only(Muscle.MID_CHEST, 2).dose, 1e-9)
    }

    @Test
    fun `novelty is variation aware`() {
        val plainThenIncline = estimate(session(1, 48, set("Bench Press")), session(2, 24, set("Bench Press", mods = "incline")))
        assertEquals(0.6 * 0.9 * 1.4, plainThenIncline.only(Muscle.TRICEPS, 2).dose, 1e-9)
        val sameVariation = estimate(
            session(1, 48, set("Bench Press", mods = "deficit,incline")),
            session(2, 24, set("Bench Press", mods = "incline,deficit")),
        )
        assertEquals(0.6 * 0.9 * 1.2, sameVariation.only(Muscle.TRICEPS, 2).dose, 1e-9)
        assertEquals("bench press | deficit,incline", sameVariation.only(Muscle.TRICEPS, 2).liftKey)
    }

    @Test
    fun `big session stretches the half life by a quarter`() {
        val prior = arrayOf(session(1, 10 * 24, set("Bench Press")), session(2, 5 * 24, set("Bench Press")))
        val eleven = estimate(*prior, session(3, 24, *sets(11, rpe = 10)))
        assertEquals(24.0 * 1.25, eleven.only(Muscle.MID_CHEST, 3).halfLifeHours, 1e-9)
        assertEquals("front delts got 7.7, not big", 18.0, eleven.only(Muscle.FRONT_DELTS, 3).halfLifeHours, 1e-9)
        val ten = estimate(*prior, session(3, 24, *sets(10, rpe = 10)))
        assertEquals("exactly 10.0 is not above 10", 24.0, ten.only(Muscle.MID_CHEST, 3).halfLifeHours, 1e-9)
        assertEquals("the other session keeps its own", 24.0, eleven.only(Muscle.MID_CHEST, 2).halfLifeHours, 1e-9)
    }

    @Test
    fun `new lifter stretches the half life by a fifth`() {
        val one = session(1, 24, set("Standing Calf Raise"))
        val fresh = FatigueModel.estimate(listOf(one), NOW, NOW - 7 * 7 * DAY)
        assertTrue(fresh.newLifter)
        assertEquals(12.0 * 1.2, fresh.only(Muscle.CALVES, 1).halfLifeHours, 1e-9)
        val exactly = FatigueModel.estimate(listOf(one), NOW, NOW - 8 * 7 * DAY)
        assertFalse(exactly.newLifter)
        assertEquals(12.0, exactly.only(Muscle.CALVES, 1).halfLifeHours, 1e-9)
    }

    @Test
    fun `big session and new lifter stretches multiply`() {
        val report = FatigueModel.estimate(listOf(session(1, 24, *sets(11, rpe = 10))), NOW, NOW - 7 * DAY)
        assertEquals(24.0 * 1.25 * 1.2, report.only(Muscle.MID_CHEST, 1).halfLifeHours, 1e-9)
    }

    @Test
    fun `an imported only lifter is new`() {
        val imported = session(1, 21 * 24, set("Bench Press"))
        assertTrue(FatigueModel.estimate(listOf(imported), NOW, imported.atMs).newLifter)
    }

    @Test
    fun `contributions keep their source`() {
        val report = estimate(session(7, 24, set("Romanian Deadlift"), set("Romanian Deadlift")))
        val hamstrings = report.contributions.getValue(Muscle.HAMSTRINGS).single()
        assertEquals("romanian deadlift", hamstrings.liftKey)
        assertEquals("Romanian Deadlift", hamstrings.exerciseName)
        assertEquals(7L, hamstrings.sessionId)
        assertEquals(2 * 1.0 * 0.9 * 1.3 * 1.2 * 1.4, hamstrings.dose, 1e-9)
        assertTrue(hamstrings.stretched)
        assertEquals("every dose of a new stretch-heavy lift qualifies", hamstrings.dose, hamstrings.qualifyingDose, 1e-9)
        assertEquals(1, report.contributions.getValue(Muscle.GLUTES).size)
    }

    @Test
    fun `qualifying dose leaves out familiar plain sets`() {
        val report = estimate(session(1, 48, set("Bench Press")), session(2, 24, set("Bench Press")), session(3, 12, set("Bench Press")))
        assertEquals(0.0, report.only(Muscle.MID_CHEST, 3).qualifyingDose, 0.0)
        assertTrue(report.only(Muscle.MID_CHEST, 1).qualifyingDose > 0.0)
    }

    @Test
    fun `prior exposures counts distinct earlier sessions`() {
        val sessions = listOf(
            session(1, 100, set("Bench Press"), set("Bench Press")),
            session(2, 50, set("Bench Press", kind = SetKind.WARMUP)),
            session(3, 20, set("Bench Press")),
        )
        assertEquals(1, FatigueModel.priorExposures(sessions, "bench press", NOW - 20 * H))
        assertEquals(2, FatigueModel.priorExposures(sessions, "bench press", NOW))
    }
}
