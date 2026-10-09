package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdleTest {

    /**
     * A real collection baseline. 0 is the "never collected" sentinel, so the
     * curve tests must measure from a stamped moment or they are asserting the
     * fresh-account guard instead of the accrual curve.
     */
    private val base = 1_700_000_000_000L

    private fun state(
        essence: Long = 0L,
        relic: Double = 1.0,
        lastCollectedAtMs: Long = base,
        echoes: Int = 0,
    ) = IdleState(
        essence = essence,
        figures = echoes,
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
    fun `skills help permanently but never out-earn training`() {
        val noSkills = Idle.rate(state(), sessionsLast7d = 4, volumeLast7d = 300.0, skillsUnlocked = 0, streakDays = 0)
        val skilled = Idle.rate(state(), sessionsLast7d = 4, volumeLast7d = 300.0, skillsUnlocked = 5, streakDays = 0)
        // 5 skills on the asymptote: 1 + ceiling * (1 - e^(-rate * 5)).
        assertEquals(1.0 + (1.0 - kotlin.math.exp(-Idle.SKILL_RATE * 5)), skilled.skillFactor, 1e-9)
        // Every unlock still adds something, however deep into the tree, but
        // the bonus is bounded — the whole tree stays under x2.
        val deeper = Idle.rate(state(), sessionsLast7d = 4, volumeLast7d = 300.0, skillsUnlocked = 50, streakDays = 0)
        assertTrue(deeper.skillFactor > skilled.skillFactor)
        assertTrue(deeper.skillFactor < 2.0)

        // Balance intent, inverted from the first draft: the WHOLE skill tree
        // must not beat a trained week. Training is the engine; skills are trim.
        val everySkill = Idle.rate(state(), sessionsLast7d = 0, volumeLast7d = 0.0, skillsUnlocked = 95, streakDays = 0)
        val trainedWeek = Idle.rate(state(), sessionsLast7d = 5, volumeLast7d = 400.0, skillsUnlocked = 0, streakDays = 5)
        assertTrue(everySkill.perHour < trainedWeek.perHour)
        // Asymptotic means x2 is a LIMIT, never reached — the 95-tree is close
        // but below it, and the catalogue growing cannot inflate it further.
        assertTrue(everySkill.skillFactor < 2.0)
        assertTrue(everySkill.skillFactor > 1.9)
    }
    @Test
    fun `a full day away pays the full rate`() {
        val s = state()
        val rate = Idle.rate(state(), sessionsLast7d = 4, volumeLast7d = 200.0, skillsUnlocked = 0, streakDays = 0)
        // 24h is the whole point: a lifter who opens the app once a day loses nothing.
        val day = Idle.accrued(s, rate, nowMs = base + dayMs)
        assertEquals((rate.perHour * 24).toLong(), day)
    }

    @Test
    fun `output tapers after a day and keeps paying through a rest week`() {
        val s = state()
        val rate = Idle.rate(state(), sessionsLast7d = 4, volumeLast7d = 200.0, skillsUnlocked = 0, streakDays = 0)

        val oneDay = Idle.accrued(s, rate, nowMs = base + dayMs)
        val threeDays = Idle.accrued(s, rate, nowMs = base + 3 * dayMs)
        val sevenDays = Idle.accrued(s, rate, nowMs = base + 7 * dayMs)

        // Still growing through a normal rest week.
        assertTrue(threeDays > oneDay)
        assertTrue(sevenDays > threeDays)
        // But the extra days are worth far less than the first: 48h of taper
        // adds ~26.4 effective hours, while the next 96h add only ~9.6.
        assertTrue(threeDays - oneDay < 2 * oneDay)
        assertTrue(sevenDays - threeDays < threeDays - oneDay)
    }

    @Test
    fun `past the taper a day pays the ten percent floor`() {
        val s = state()
        val rate = Idle.rate(state(), sessionsLast7d = 4, volumeLast7d = 200.0, skillsUnlocked = 0, streakDays = 0)
        // Days 5 and 6 sit past the taper (72h) and short of the cap (12 days).
        val five = Idle.accrued(s, rate, nowMs = base + 5 * dayMs)
        val six = Idle.accrued(s, rate, nowMs = base + 6 * dayMs)
        // Each accrual truncates independently, so allow a single unit of slack.
        val floorDay = (rate.perHour * 24 * Idle.MIN_EFFICIENCY).toLong()
        assertTrue(kotlin.math.abs((six - five) - floorDay) <= 1L)
    }

    @Test
    fun `no absence pays more than the cap, so a year away cannot climb the board`() {
        val s = state()
        val rate = Idle.rate(state(), sessionsLast7d = 4, volumeLast7d = 200.0, skillsUnlocked = 0, streakDays = 0)
        val cap = (rate.perHour * Idle.MAX_EFFECTIVE_HOURS).toLong()
        // The floor reaches 72 effective hours at day 12 (50.4 + 0.1 * 216h).
        val elevenDays = Idle.accrued(s, rate, nowMs = base + 11 * dayMs)
        assertTrue("the cap must not bite before day 12", elevenDays < cap)
        assertTrue(kotlin.math.abs(Idle.accrued(s, rate, nowMs = base + 13 * dayMs) - cap) <= 1L)
        assertTrue(kotlin.math.abs(Idle.accrued(s, rate, nowMs = base + 365 * dayMs) - cap) <= 1L)
    }

    @Test
    fun `clock moved backwards collects nothing and stays safe`() {
        val s = state(essence = 500L, lastCollectedAtMs = base + 10 * dayMs)
        val rate = Idle.rate(state(), sessionsLast7d = 4, volumeLast7d = 200.0, skillsUnlocked = 0, streakDays = 0)
        assertEquals(0L, Idle.accrued(s, rate, nowMs = base + 10 * dayMs))
        val after = Idle.collect(s, rate, nowMs = base + 5 * dayMs)
        // Balance unchanged, collection point reset — never a negative balance.
        assertEquals(500L, after.essence)
        assertEquals(base + 5 * dayMs, after.lastCollectedAtMs)
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
        val s = state(essence = hugeEssence, relic = Double.MAX_VALUE, lastCollectedAtMs = base)
        val collected = Idle.collect(s, rate, nowMs = base + 100 * dayMs)
        assertTrue(collected.essence >= hugeEssence)
        assertTrue(collected.essence >= 0L)

        val empty = Idle.collect(state(), rate, nowMs = base + 100 * dayMs)
        assertTrue(empty.essence >= 0L)
    }

    @Test
    fun `collect banks exactly the accrued amount then nothing while time stands still`() {
        val s = state(essence = 1_000L, lastCollectedAtMs = base)
        val rate = Idle.rate(state(), sessionsLast7d = 4, volumeLast7d = 200.0, skillsUnlocked = 0, streakDays = 0)
        val now = base + 6 * 3_600_000L
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

    @Test
    fun `a fresh account with no baseline banks nothing`() {
        // A new idle_state row carries lastCollectedAtMs = 0. The curve never
        // stops paying, so the epoch read as a 56-year absence and a brand new
        // lifter opened the Muster screen to ~497,000 banked essence.
        val fresh = state(lastCollectedAtMs = 0L)
        val rate = Idle.rate(fresh, sessionsLast7d = 0, volumeLast7d = 0.0, skillsUnlocked = 0, streakDays = 0)
        val now = 1_800_000_000_000L
        assertEquals(0L, Idle.accrued(fresh, rate, nowMs = now))
        assertEquals(0.0, Idle.accruedExact(fresh, rate, nowMs = now), 1e-9)
        // Collecting stamps the baseline, so accrual starts from that moment.
        val stamped = Idle.collect(fresh, rate, nowMs = now)
        assertEquals(0L, stamped.essence)
        assertEquals(now, stamped.lastCollectedAtMs)
        assertTrue(Idle.accrued(stamped, rate, nowMs = now + 3_600_000L) > 0L)
    }

    @Test
    fun `efficiency holds at full for a day, falls in a line over two more, then holds at a tenth`() {
        assertEquals(1.0, Idle.efficiencyAtHours(0.0), 1e-9)
        assertEquals(1.0, Idle.efficiencyAtHours(24.0), 1e-9)
        assertEquals(0.55, Idle.efficiencyAtHours(48.0), 1e-9)
        assertEquals(Idle.MIN_EFFICIENCY, Idle.efficiencyAtHours(72.0), 1e-9)
        assertEquals(Idle.MIN_EFFICIENCY, Idle.efficiencyAtHours(500.0), 1e-9)
    }

    @Test
    fun `the full-strength fraction fills over the first day and then stays full`() {
        assertEquals(0.0, Idle.fullStrengthFraction(0L), 1e-9)
        assertEquals(0.5, Idle.fullStrengthFraction(12 * 3_600_000L), 1e-9)
        assertEquals(1.0, Idle.fullStrengthFraction(30 * 3_600_000L), 1e-9)
        assertEquals(0.0, Idle.fullStrengthFraction(-5L), 1e-9)
    }

    @Test
    fun `echoes lift the rate one percent per hundred, continuously`() {
        fun factorAt(echoes: Int) = Idle.rate(state(echoes = echoes), 4, 200.0, 0, 0).echoFactor
        assertEquals(1.0, factorAt(0), 1e-12)
        assertEquals(1.01, factorAt(100), 1e-12)
        // 681 echoes is +6.81%: no steps at the hundreds.
        assertEquals(1.0681, factorAt(681), 1e-12)
    }

    @Test
    fun `echoes stop at twenty-five percent, reached at 2500`() {
        fun factorAt(echoes: Int) = Idle.rate(state(echoes = echoes), 4, 200.0, 0, 0).echoFactor
        assertEquals(1.25, factorAt(2_500), 1e-12)
        assertEquals(1.25, factorAt(10_000), 1e-12)
        assertEquals(1.25, factorAt(Int.MAX_VALUE), 1e-12)
        assertEquals(Idle.MAX_ECHO_FACTOR, factorAt(2_500), 1e-12)
    }

    @Test
    fun `a negative echo count reads as none`() {
        assertEquals(1.0, Idle.echoFactor(-40), 1e-12)
        assertEquals(1.0, Idle.rate(state(echoes = -40), 4, 200.0, 0, 0).echoFactor, 1e-12)
    }

    @Test
    fun `the echo factor multiplies into the hourly rate and nothing else`() {
        val none = Idle.rate(state(), 4, 200.0, 3, 2)
        val held = Idle.rate(state(echoes = 681), 4, 200.0, 3, 2)
        assertEquals(none.perHour * 1.0681, held.perHour, 1e-9)
        assertEquals(none.trainingFactor, held.trainingFactor, 1e-12)
        assertEquals(none.skillFactor, held.skillFactor, 1e-12)
    }
}
