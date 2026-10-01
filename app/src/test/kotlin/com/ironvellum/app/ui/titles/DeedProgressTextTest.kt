package com.ironvellum.app.ui.titles

import com.ironvellum.app.domain.TitleDef
import com.ironvellum.app.domain.TitleRule
import com.ironvellum.app.domain.Titles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The words on a deed's progress line: the figures, and the unit after "to go". */
class DeedProgressTextTest {

    private fun ledger(
        sleepBestMinutes: Int = 0,
        bestRunKm: Double = 0.0,
        bestSwimKm: Double = 0.0,
    ) = Titles.Ledger(
        totalXp = 0, workouts = 0, sets = 0, reps = 0,
        sleepBestMinutes = sleepBestMinutes, bestRunKm = bestRunKm, bestSwimKm = bestSwimKm,
    )

    private fun text(def: TitleDef, ledger: Titles.Ledger, earned: Boolean = false) =
        deedProgressText(def, Titles.progress(def.rule, ledger), ledger, earned)

    private inline fun <reified R : TitleRule> deed(pick: (R) -> Boolean = { true }): TitleDef =
        Titles.ALL.first { (it.rule as? R)?.let(pick) == true }

    @Test
    fun `a sleep deed counts what is left in minutes`() {
        val def = deed<TitleRule.SleepMinutesInNight>()
        val rule = def.rule as TitleRule.SleepMinutesInNight
        val line = text(def, ledger(sleepBestMinutes = rule.minutes - 32))
        assertEquals("32 min to go", line.toGo)
    }

    @Test
    fun `no deed ever says to go without a unit`() {
        val bareNumber = Regex("""^[\d,.]+ to go$""")
        Titles.ALL.forEach { def ->
            val line = text(def, ledger())
            assertFalse("${def.id} reads \"${line.toGo}\"", bareNumber.matches(line.toGo))
            assertTrue("${def.id} reads \"${line.toGo}\"", line.toGo.endsWith(" to go"))
        }
    }

    @Test
    fun `a count is singular when one is left`() {
        val def = deed<TitleRule.Workouts>()
        val rule = def.rule as TitleRule.Workouts
        val one = Titles.Ledger(totalXp = 0, workouts = rule.count - 1, sets = 0, reps = 0)
        assertEquals("1 trial to go", text(def, one).toGo)
    }

    @Test
    fun `a half marathon just short never reads complete`() {
        val def = Titles.byId("half_gate_marathon")!!
        // 21.05 floored to a whole km read "21 / 21" while the deed was unearned.
        listOf(21.0, 21.05, 21.0999).forEach { km ->
            val line = text(def, ledger(bestRunKm = km))
            val (current, target) = line.counts.split(" / ")
            assertNotEquals("$km km shows the target reached", target, current)
            assertEquals("21.0 / 21.1", line.counts)
            assertEquals("0.1 km to go", line.toGo)
        }
    }

    @Test
    fun `distance figures keep one decimal`() {
        val half = Titles.byId("half_gate_marathon")!!
        assertEquals("12.3 / 21.1", text(half, ledger(bestRunKm = 12.34)).counts)
        assertEquals("8.8 km to go", text(half, ledger(bestRunKm = 12.34)).toGo)
        val swim = Titles.byId("deep_current")!!
        // 2.4 km floored to 2 read "2 / 2" against a 2.5 km bar.
        assertEquals("2.4 / 2.5", text(swim, ledger(bestSwimKm = 2.4)).counts)
        assertEquals("0.1 km to go", text(swim, ledger(bestSwimKm = 2.4)).toGo)
    }

    @Test
    fun `an earned distance deed shows its full bar`() {
        val half = Titles.byId("half_gate_marathon")!!
        val line = text(half, ledger(bestRunKm = 21.1), earned = true)
        assertEquals("21.1 / 21.1", line.counts)
        assertEquals("complete", line.toGo)
    }
}
