package com.ironvellum.app.ui.recap

import com.ironvellum.app.domain.AimState
import com.ironvellum.app.domain.LiftRecords
import com.ironvellum.app.domain.MuscleRow
import com.ironvellum.app.domain.PeriodStats
import com.ironvellum.app.domain.RecapKind
import com.ironvellum.app.domain.RecapNext
import com.ironvellum.app.domain.RecapStreak
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.WeeklyRecap
import com.ironvellum.app.plainText
import com.ironvellum.app.themedText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class RecapCopyTest {
    private val monday = LocalDate.of(2026, 10, 12)

    private fun record(name: String, kg: Double, reps: Int, gain: Double, adds: Boolean = false) = LiftRecords.RecordGain(
        name = name, variation = "", e1rmKg = kg * (1 + reps / 30.0), gainKg = gain,
        set = SessionSet(exerciseId = 1, exerciseName = name, setIndex = 0, reps = reps, weightKg = kg, done = true),
        addsToBody = adds, atMs = 0,
    )

    private fun stats(from: LocalDate, workouts: Int, volume: Double, records: List<LiftRecords.RecordGain> = emptyList()) = PeriodStats(
        from = from, to = from.plusDays(7), workouts = workouts, sets = 62, volumeKg = volume, minutes = 292, records = records,
        bestSet = null, muscleSets = emptyMap(), days = emptySet(), lifts = emptyList(), titles = emptyList(),
    )

    private fun recap(
        records: List<LiftRecords.RecordGain> = listOf(
            record("Bench Press", 82.5, 6, 3.0), record("Weighted Pull-up", 12.5, 7, 1.2, adds = true), record("Back Squat", 105.0, 5, 2.5),
        ),
        previousVolume: Double = 15_960.0,
        streakDays: Int = 98,
        next: RecapNext? = RecapNext(4, "Push A", 1, emptyList(), null, null),
    ) = WeeklyRecap(
        weekStart = monday,
        stats = stats(monday, 4, 18_420.0, records),
        previous = stats(monday.minusDays(7), 3, previousVolume),
        kind = RecapKind.FULL,
        muscles = emptyList<MuscleRow>(),
        streak = RecapStreak(streakDays, longest = true, atRisk = false, lastWorkout = monday.plusDays(5), trainBy = monday.plusDays(12)),
        next = next,
    )

    private fun copy(recap: WeeklyRecap = recap()) = RecapCopy.of(recap, Locale.US)

    @Test
    fun `the title counts workouts and records, in either vocabulary`() {
        assertEquals("Your week: 4 workouts, 3 records", copy().title.plainText())
        assertEquals("Your week: 4 trials, 3 records", copy().title.themedText())
    }

    @Test
    fun `a week with no records says only the workouts`() {
        assertEquals("Your week: 4 workouts", copy(recap(records = emptyList())).title.plainText())
    }

    @Test
    fun `one record is singular`() {
        assertEquals("Your week: 4 workouts, 1 record", copy(recap(records = listOf(record("Bench Press", 80.0, 5, 2.5)))).title.plainText())
    }

    @Test
    fun `the text gives the volume with its change, then the streak`() {
        assertEquals("18,420 kg lifted, up 15%. Streak at 98 days.", copy().text.plainText())
        assertEquals("18,420 kg lifted, up 15%. Oath at 98 days.", copy().text.themedText())
    }

    @Test
    fun `a lighter week says down, and the first week with nothing to compare says only the volume`() {
        assertEquals("18,420 kg lifted, down 8%. Streak at 98 days.", copy(recap(previousVolume = 20_000.0)).text.plainText())
        assertEquals("18,420 kg lifted. Streak at 98 days.", copy(recap(previousVolume = 0.0)).text.plainText())
        assertEquals("18,420 kg lifted. Streak at 98 days.", copy(recap(previousVolume = 18_420.0)).text.plainText())
    }

    @Test
    fun `no streak, no streak sentence`() {
        assertEquals("18,420 kg lifted, up 15%.", copy(recap(streakDays = 0)).text.plainText())
        assertEquals("Streak at 1 day.", copy(recap(streakDays = 1, previousVolume = 0.0)).text.plainText().substringAfter("lifted. "))
    }

    @Test
    fun `the expanded text carries the range, the totals, the records and what is next`() {
        val lines = copy().expanded.plainText().lines()
        assertEquals("Mon 12 – Sun 18 Oct 2026", lines[0])
        assertEquals("62 sets · 4 h 52 min", lines[1])
        assertEquals("Records: Bench Press, Weighted Pull-up, Back Squat", lines[2])
        assertEquals("Next: Push A on Monday", lines[3])
        assertEquals(4, lines.size)
    }

    @Test
    fun `without records or a plan the expanded text is shorter`() {
        assertEquals(2, copy(recap(records = emptyList(), next = null)).expanded.plainText().lines().size)
    }

    @Test
    fun `the lock screen version says the recap is ready and shows no figure`() {
        val copy = copy()
        assertEquals("Your weekly recap is ready", copy.publicTitle.plainText())
        assertEquals("Tap to see how the week went.", copy.publicText.plainText())
        val shown = copy.publicTitle.themedText() + copy.publicText.themedText()
        assertFalse("a figure on the lock screen: $shown", shown.any { it.isDigit() })
        assertTrue("Bench" !in shown && "kg" !in shown)
    }
}
