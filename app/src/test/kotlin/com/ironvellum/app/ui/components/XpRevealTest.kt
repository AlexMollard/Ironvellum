package com.ironvellum.app.ui.components

import com.ironvellum.app.domain.Xp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XpRevealTest {
    private fun lift(name: String, xp: Int, hard: Int = 3, easy: Int = 0, load: Double? = null, hold: Boolean = false) =
        Xp.Line(Xp.Kind.EXERCISE, name, xp, hardSets = hard, easySets = easy, topLoadKg = load, hold = hold)

    private fun bonus(kind: Xp.Kind, xp: Int, detail: String = "") = Xp.Line(kind, kind.label, xp, detail = detail)

    private val trial = Xp.Breakdown(
        listOf(
            lift("Pull-up", 86, hard = 4, load = 17.7),
            lift("Advanced Tuck Front Lever", 44, hold = true),
            lift("Chin-up", 38, load = 7.7),
            bonus(Xp.Kind.PEAK, 15, "Pull-up"),
            bonus(Xp.Kind.COMPLETION, 25),
        ),
    )

    @Test
    fun theSlicesOfTheBarsAddUpToEverySegmentExactly() {
        for (totalAfter in listOf(208L, 1_000L, 1_590L, 5_000L)) {
            val bars = XpReveal.bars(trial, totalAfter)
            for ((i, line) in trial.lines.withIndex()) {
                assertEquals("segment $i at $totalAfter", line.xp.toLong(), bars.sumOf { b -> b.slices.filter { it.segment == i }.sumOf { it.xp } })
            }
            assertEquals(trial.total.toLong(), bars.sumOf { b -> b.slices.sumOf { it.xp } })
        }
    }

    @Test
    fun aTrialInsideOneLevelFillsOneBarFromWhereTheLifterWas() {
        val before = 785L
        val bars = XpReveal.bars(trial, before + trial.total)
        assertEquals(1, bars.size)
        assertEquals(Xp.progress(before).intoLevel, bars.single().startXp)
        assertEquals(Xp.progress(before).level, bars.single().level)
        assertEquals(Xp.progress(before).needed, bars.single().needed)
    }

    @Test
    fun aSegmentThatCrossesALevelContinuesInAFreshBar() {
        // 20 XP short of the level end: the 86 XP pull-up segment crosses it, 66 into the next.
        val levelEnd = Xp.progress(0).needed
        val totalBefore = levelEnd - 20
        val bars = XpReveal.bars(trial, totalBefore + trial.total)
        assertEquals(2, bars.size)
        assertEquals(bars[0].level + 1, bars[1].level)
        assertEquals(0L, bars[1].startXp)
        assertEquals(20L, bars[0].slices.last().xp)
        assertEquals(0, bars[0].slices.last().segment)
        assertEquals(66L, bars[1].slices.first().xp)
        assertEquals(0, bars[1].slices.first().segment)
        // The first bar ends exactly full.
        assertEquals(bars[0].needed, bars[0].startXp + bars[0].slices.sumOf { it.xp })
    }

    @Test
    fun segmentsFillInTurnAndSettleOnTheTotal() {
        val lines = trial.lines
        assertEquals(0f, XpReveal.filled(lines, 0), 0.001f)
        // Mid first segment: only the first is partly filled.
        val mid = XpReveal.filled(lines, XpReveal.SEG_MS / 2)
        assertTrue(mid > 0f && mid < lines[0].xp)
        assertEquals(0, XpReveal.current(lines, XpReveal.SEG_MS / 2))
        assertEquals(2, XpReveal.current(lines, XpReveal.SEG_MS * 2 + 10))
        // After the last, everything is in and no segment is current.
        val end = lines.size * XpReveal.SEG_MS
        assertEquals(trial.total.toFloat(), XpReveal.filled(lines, end), 0.001f)
        assertEquals(-1, XpReveal.current(lines, end))
        assertEquals(-1, XpReveal.current(lines, 0))
    }

    @Test
    fun theSublinesSayWhatEachMovementWasMadeOf() {
        assertEquals("4 hard sets · +" + com.ironvellum.app.data.Units.withUnit(17.7) + " added", XpReveal.subline(trial.lines[0]))
        // A lifted load is not "added" to anything: it is the top one.
        assertEquals(
            "3 hard sets · top " + com.ironvellum.app.data.Units.withUnit(60.0),
            XpReveal.subline(Xp.Line(Xp.Kind.EXERCISE, "Back Squat", 32, hardSets = 3, topLoadKg = 60.0, externalLoad = true)),
        )
        assertEquals("3 hard holds", XpReveal.subline(trial.lines[1]))
        assertEquals("2 hard · 1 easy", XpReveal.subline(lift("Hanging Knee Raise", 26, hard = 2, easy = 1)))
        assertEquals("1 hard set", XpReveal.subline(lift("Dip", 10, hard = 1)))
        assertEquals("", XpReveal.subline(Xp.Line(Xp.Kind.ACTIVITY, "Run", 40)))
    }

    @Test
    fun everyRowIsSpokenWholeWhateverTheAnimationHasReached() {
        assertTrue(XpReveal.spoken(trial.lines[0]).startsWith("Pull-up, 4 hard sets"))
        assertTrue(XpReveal.spoken(trial.lines[0]).endsWith(", 86 XP"))
        assertEquals("New peak, Pull-up, 15 XP", XpReveal.spoken(trial.lines[3]))
        assertEquals("Trial complete, 25 XP", XpReveal.spoken(trial.lines[4]))
        assertEquals("Earned 208 XP across 5 segments, level 16", XpReveal.headline(trial, 16))
    }

    @Test
    fun onlyMovementsAreEffortAndEverythingElseIsABonus() {
        assertTrue(XpReveal.isLift(trial.lines[0]))
        assertTrue(XpReveal.isLift(Xp.Line(Xp.Kind.ACTIVITY, "Run", 30)))
        assertTrue(!XpReveal.isLift(trial.lines[3]))
        assertTrue(!XpReveal.isLift(trial.lines[4]))
    }
}
