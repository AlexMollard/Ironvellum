package com.monarch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdleTest {

    private fun state(
        essence: Long = 0L,
        relic: Double = 1.0,
        lastCollectedAtMs: Long = 0L,
    ) = IdleState(
        essence = essence,
        shadows = 1,
        relicMultiplier = relic,
        lastCollectedAtMs = lastCollectedAtMs,
    )

    private val dayMs = 24 * 3_600_000L

    @Test
    fun `more recent training strictly raises the hourly rate`() {
        val base = Idle.rate(state(), sessionsLast7d = 2, volumeLast7d = 100.0, skillsUnlocked = 0, streakDays = 0).perHour
        val moreSessions = Idle.rate(state(), sessionsLast7d = 4, volumeLast7d = 100.0, skillsUnlocked = 0, streakDays = 0).perHour
        val moreVolume = Idle.rate(state(), sessionsLast7d = 2, volumeLast7d = 400.0, skillsUnlocked = 0, streakDays = 0).perHour
        assertTrue(moreSessions > base)
        assertTrue(moreVolume > base)
    }

    @Test
    fun `zero training decays to a small non-zero floor`() {
        val idle = Idle.rate(state(), sessionsLast7d = 0, volumeLast7d = 0.0, skillsUnlocked = 0, streakDays = 0)
        val trained = Idle.rate(state(), sessionsLast7d = 4, volumeLast7d = 200.0, skillsUnlocked = 0, streakDays = 3)
        // Floor: the army scavenges a trickle, it does not die.
        assertTrue(idle.perHour > 0.0)
        // Untrained earns at most a third of a committed week.
        assertTrue(idle.perHour * 3 < trained.perHour)
    }

    @Test
    fun `skills compound permanently and dominate one week of training`() {
        val noSkills = Idle.rate(state(), sessionsLast7d = 4, volumeLast7d = 300.0, skillsUnlocked = 0, streakDays = 0)
        val skilled = Idle.rate(state(), sessionsLast7d = 4, volumeLast7d = 300.0, skillsUnlocked = 5, streakDays = 0)
        assertTrue(skilled.perHour > noSkills.perHour)
        // Balance intent: ~10 skills outweigh the entire weekly training ceiling.
        val cappedTraining = Idle.rate(state(), sessionsLast7d = 999, volumeLast7d = 1e6, skillsUnlocked = 0, streakDays = 999)
        val skillsOverCeiling = Idle.rate(state(), sessionsLast7d = 0, volumeLast7d = 0.0, skillsUnlocked = 10, streakDays = 0)
        assertTrue(skillsOverCeiling.perHour > cappedTraining.perHour)
        assertEquals(3.5, skilled.skillFactor, 1e-9)
    }

    @Test
    fun `three days away collects only the offline cap`() {
        val s = state(lastCollectedAtMs = 0L)
        val rate = Idle.rate(state(), sessionsLast7d = 4, volumeLast7d = 200.0, skillsUnlocked = 0, streakDays = 0)
        val gained = Idle.accrued(s, rate, nowMs = 3 * dayMs)
        val capped = Idle.accrued(s, rate, nowMs = Idle.OFFLINE_CAP_HOURS * 3_600_000L)
        assertEquals(capped, gained)
        assertTrue(gained > 0L)
    }

    @Test
    fun `time away pays full rate at first then tapers`() {
        val s = state(lastCollectedAtMs = 0L)
        val rate = Idle.rate(state(), sessionsLast7d = 4, volumeLast7d = 200.0, skillsUnlocked = 0, streakDays = 0)
        val hour = 3_600_000L

        // Inside the full-rate window an hour away is worth an hour of rate.
        val sixHours = Idle.accrued(s, rate, nowMs = 6 * hour)
        assertEquals((rate.perHour * 6).toLong(), sixHours)

        // A full day is worth materially more than six hours, but far less than
        // 24x — the marginal rate decays once the full-rate window closes.
        val fullDay = Idle.accrued(s, rate, nowMs = 24 * hour)
        assertTrue(fullDay > sixHours)
        assertTrue(fullDay < (rate.perHour * 24).toLong())
        assertTrue(fullDay > (rate.perHour * 12).toLong())
    }

    @Test
    fun `clock moved backwards collects nothing and stays safe`() {
        val s = state(essence = 500L, lastCollectedAtMs = 10 * dayMs)
        val rate = Idle.rate(state(), sessionsLast7d = 4, volumeLast7d = 200.0, skillsUnlocked = 0, streakDays = 0)
        assertEquals(0L, Idle.accrued(s, rate, nowMs = 10 * dayMs))
        val after = Idle.collect(s, rate, nowMs = 5 * dayMs)
        // Balance unchanged, collection point reset — never a negative balance.
        assertEquals(500L, after.essence)
        assertEquals(5 * dayMs, after.lastCollectedAtMs)
        assertTrue(after.essence >= 0L)
    }

    @Test
    fun `extreme inputs never produce NaN infinity or negative essence`() {
        val rate = Idle.rate(
            state(relic = Double.MAX_VALUE),
            sessionsLast7d = 1_000_000,
            volumeLast7d = 1e300,
            skillsUnlocked = 1_000_000,
            streakDays = 1_000_000,
        )
        assertTrue(rate.perHour.isFinite())
        assertTrue(rate.perHour >= 0.0)
        assertTrue(rate.trainingFactor.isFinite())
        assertTrue(rate.skillFactor.isFinite())

        val hugeEssence = Long.MAX_VALUE - 10L
        val s = state(essence = hugeEssence, relic = Double.MAX_VALUE, lastCollectedAtMs = 0L)
        val collected = Idle.collect(s, rate, nowMs = 100 * dayMs)
        assertTrue(collected.essence >= hugeEssence)
        assertTrue(collected.essence >= 0L)

        val empty = Idle.collect(state(), rate, nowMs = 100 * dayMs)
        assertTrue(empty.essence >= 0L)
    }

    @Test
    fun `collect banks exactly the accrued amount then nothing while time stands still`() {
        val s = state(essence = 1_000L, lastCollectedAtMs = 0L)
        val rate = Idle.rate(state(), sessionsLast7d = 4, volumeLast7d = 200.0, skillsUnlocked = 0, streakDays = 0)
        val now = 6 * 3_600_000L
        val expected = Idle.accrued(s, rate, now)
        assertTrue(expected > 0L)

        val first = Idle.collect(s, rate, now)
        assertEquals(1_000L + expected, first.essence)
        assertEquals(now, first.lastCollectedAtMs)

        // Second collect at the same instant: no elapsed time, no essence.
        val second = Idle.collect(first, rate, now)
        assertEquals(first.essence, second.essence)
        assertEquals(0L, Idle.accrued(first, rate, now))
    }
}
