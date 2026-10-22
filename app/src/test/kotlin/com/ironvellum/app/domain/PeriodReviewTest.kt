package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class PeriodReviewTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val bench = Exercise(id = 1, name = "Bench Press", muscleGroup = MuscleGroup.PUSH, isWeighted = true)
    private val squat = Exercise(id = 2, name = "Back Squat", muscleGroup = MuscleGroup.LEGS, isWeighted = true)
    private val row = Exercise(id = 3, name = "Barbell Row", muscleGroup = MuscleGroup.PULL, isWeighted = true)
    private val catalogue = listOf(bench, squat, row).associateBy { it.id }

    private var nextId = 1L

    private fun set(ex: Exercise, kg: Double, reps: Int = 5, index: Int = 0) =
        SessionSet(exerciseId = ex.id, exerciseName = ex.name, setIndex = index, reps = reps, weightKg = kg, done = true)

    private fun workout(
        day: LocalDate,
        vararg sets: SessionSet,
        time: LocalTime = LocalTime.of(18, 0),
        at: ZoneId = zone,
    ): Pair<WorkoutSession, List<SessionSet>> {
        // [time] is when the workout was completed: that is the moment a workout's day is read from.
        val done = day.atTime(time).atZone(at).toInstant().toEpochMilli()
        return WorkoutSession(id = nextId++, label = "Workout", startedAtMs = done - 45 * 60_000L, completedAtMs = done) to sets.toList()
    }

    private val september = ReviewPeriod.of(ReviewKind.MONTH, LocalDate.of(2026, 9, 15))
    private val year2026 = ReviewPeriod.of(ReviewKind.YEAR, LocalDate.of(2026, 6, 1))
    private val today = LocalDate.of(2026, 10, 3)

    private fun review(
        history: List<Pair<WorkoutSession, List<SessionSet>>>,
        period: ReviewPeriod = september,
        practices: List<SkillPractice> = emptyList(),
        now: LocalDate = today,
        at: ZoneId = zone,
    ) = PeriodReview.build(history, catalogue, { 0.0 }, practices, period, now, at)

    // ---- periods ----

    @Test
    fun `a month and a year are half open from their first day`() {
        assertEquals(LocalDate.of(2026, 9, 1), september.from)
        assertEquals(LocalDate.of(2026, 10, 1), september.to)
        assertEquals(LocalDate.of(2026, 9, 30), september.last)
        assertEquals(LocalDate.of(2026, 1, 1), year2026.from)
        assertEquals(LocalDate.of(2027, 1, 1), year2026.to)
        assertEquals("2026-09", september.key)
        assertEquals("2026", year2026.key)
    }

    @Test
    fun `the period before January is December of the year before, and February 2028 has 29 days`() {
        val january = ReviewPeriod.of(ReviewKind.MONTH, LocalDate.of(2027, 1, 20))
        assertEquals(LocalDate.of(2026, 12, 1), january.previous.from)
        assertEquals(LocalDate.of(2026, 1, 1), ReviewPeriod.of(ReviewKind.YEAR, LocalDate.of(2027, 3, 3)).previous.from)
        val leap = ReviewPeriod.of(ReviewKind.MONTH, LocalDate.of(2028, 2, 10))
        assertEquals(LocalDate.of(2028, 2, 29), leap.last)
    }

    @Test
    fun `a period must start on its first day`() {
        val bad = runCatching { ReviewPeriod(ReviewKind.MONTH, LocalDate.of(2026, 9, 2)) }
        assertTrue(bad.isFailure)
    }

    @Test
    fun `New Year's Eve at 23-30 is the old year and 00-10 on New Year's Day is the new one`() {
        val december = ReviewPeriod.of(ReviewKind.MONTH, LocalDate.of(2026, 12, 1))
        val history = listOf(
            workout(LocalDate.of(2026, 12, 31), set(bench, 80.0), time = LocalTime.of(23, 30)),
            workout(LocalDate.of(2027, 1, 1), set(bench, 80.0), time = LocalTime.of(0, 10)),
        )
        assertEquals(1, review(history, december, now = LocalDate.of(2027, 1, 5)).stats.workouts)
        assertEquals(1, review(history, ReviewPeriod.of(ReviewKind.MONTH, LocalDate.of(2027, 1, 1)), now = LocalDate.of(2027, 1, 5)).stats.workouts)
        assertEquals(1, review(history, year2026, now = LocalDate.of(2027, 1, 5)).stats.workouts)
    }

    @Test
    fun `a clock change inside a month does not move its edges`() {
        // Berlin goes back on Sun 25 Oct 2026: October is 31 calendar days, one of them 25 hours long.
        val october = ReviewPeriod.of(ReviewKind.MONTH, LocalDate.of(2026, 10, 1))
        val history = listOf(
            workout(LocalDate.of(2026, 9, 30), set(bench, 80.0), time = LocalTime.of(23, 59)),
            workout(LocalDate.of(2026, 10, 1), set(bench, 80.0), time = LocalTime.of(0, 0)),
            workout(LocalDate.of(2026, 10, 25), set(bench, 80.0), time = LocalTime.of(2, 30)),
            workout(LocalDate.of(2026, 10, 31), set(bench, 80.0), time = LocalTime.of(23, 59)),
            workout(LocalDate.of(2026, 11, 1), set(bench, 80.0), time = LocalTime.of(0, 0)),
        )
        assertEquals(3, review(history, october, now = LocalDate.of(2026, 11, 3)).stats.workouts)
    }

    @Test
    fun `southern hemisphere zones read the same calendar days`() {
        val nz = ZoneId.of("Pacific/Auckland") // summer time starts Sun 27 Sep 2026 at 02:00
        val history = listOf(
            workout(LocalDate.of(2026, 9, 27), set(bench, 80.0), time = LocalTime.of(1, 59), at = nz),
            workout(LocalDate.of(2026, 9, 30), set(bench, 80.0), time = LocalTime.of(23, 30), at = nz),
            workout(LocalDate.of(2026, 10, 1), set(bench, 80.0), time = LocalTime.of(0, 5), at = nz),
        )
        assertEquals(2, review(history, now = today, at = nz).stats.workouts)
    }

    // ---- best week ----

    @Test
    fun `the best week is the one that moved the most, with its whole week's figures`() {
        val history = listOf(
            workout(LocalDate.of(2026, 9, 8), set(bench, 100.0, 5)),
            workout(LocalDate.of(2026, 9, 15), set(bench, 100.0, 5)),
            workout(LocalDate.of(2026, 9, 17), set(squat, 120.0, 5)),
            workout(LocalDate.of(2026, 9, 22), set(bench, 60.0, 5)),
        )
        val best = review(history).bestWeek!!
        assertEquals(LocalDate.of(2026, 9, 14), best.weekStart)
        assertEquals(LocalDate.of(2026, 9, 20), best.weekEnd)
        assertEquals(2, best.workouts)
        assertEquals(1100.0, best.volumeKg, 1e-9)
    }

    @Test
    fun `equal volume goes to the week with more workouts, then to the earlier week`() {
        val moreWorkouts = listOf(
            workout(LocalDate.of(2026, 9, 8), set(bench, 100.0, 5)),
            workout(LocalDate.of(2026, 9, 15), set(bench, 50.0, 5)),
            workout(LocalDate.of(2026, 9, 16), set(bench, 50.0, 5)),
        )
        assertEquals(LocalDate.of(2026, 9, 14), review(moreWorkouts).bestWeek!!.weekStart)
        val tied = listOf(
            workout(LocalDate.of(2026, 9, 8), set(bench, 100.0, 5)),
            workout(LocalDate.of(2026, 9, 22), set(bench, 100.0, 5)),
        )
        assertEquals(LocalDate.of(2026, 9, 7), review(tied).bestWeek!!.weekStart)
    }

    @Test
    fun `a week that straddles two months belongs to the one holding its Thursday`() {
        // Mon 31 Aug to Sun 6 Sep has its Thursday on 3 Sep: September's. Mon 28 Sep to Sun 4 Oct has it on 1 Oct: October's.
        val history = listOf(
            workout(LocalDate.of(2026, 8, 31), set(bench, 100.0, 5)),
            workout(LocalDate.of(2026, 9, 1), set(bench, 100.0, 5)),
            workout(LocalDate.of(2026, 9, 29), set(squat, 200.0, 5)),
        )
        val september = review(history).bestWeek!!
        assertEquals(LocalDate.of(2026, 8, 31), september.weekStart)
        assertEquals(2, september.workouts)
        val october = review(history, ReviewPeriod.of(ReviewKind.MONTH, LocalDate.of(2026, 10, 1)), now = LocalDate.of(2026, 10, 20)).bestWeek!!
        assertEquals(LocalDate.of(2026, 9, 28), october.weekStart)
    }

    @Test
    fun `no workouts, no best week`() {
        assertNull(review(emptyList()).bestWeek)
    }

    // ---- lifts ----

    @Test
    fun `a lift trained before the period gains, a lift first trained in it is new, and none is both`() {
        val history = listOf(
            workout(LocalDate.of(2026, 8, 10), set(bench, 80.0, 5)),
            workout(LocalDate.of(2026, 9, 10), set(bench, 90.0, 5), set(row, 60.0, 8)),
            workout(LocalDate.of(2026, 9, 20), set(bench, 95.0, 5), set(row, 65.0, 8)),
        )
        val review = review(history)
        assertEquals(listOf("Bench Press"), review.gained.map { it.label })
        assertEquals(listOf("Barbell Row"), review.newLifts.map { it.label })
        val bench = review.gained.single()
        assertEquals(ProgramRules.epley(95.0, 5) - ProgramRules.epley(80.0, 5), bench.gainKg!!, 1e-9)
        assertEquals(ProgramRules.epley(80.0, 5), bench.beforeKg!!, 1e-9)
        // The sparkline starts at the best before the period, then one point per trial inside it.
        assertEquals(3, bench.series.size)
        val row = review.newLifts.single()
        assertNull(row.beforeKg)
        assertNull(row.gainKg)
        assertEquals(2, row.series.size)
    }

    @Test
    fun `the biggest gain comes first and a lift that did not improve is left out`() {
        val history = listOf(
            workout(LocalDate.of(2026, 8, 10), set(bench, 80.0), set(squat, 100.0), set(row, 70.0)),
            workout(LocalDate.of(2026, 9, 10), set(bench, 82.5), set(squat, 120.0), set(row, 60.0)),
        )
        assertEquals(listOf("Back Squat", "Bench Press"), review(history).gained.map { it.label })
    }

    @Test
    fun `a lift last trained after the period is not new in it`() {
        val history = listOf(
            workout(LocalDate.of(2026, 9, 10), set(bench, 80.0)),
            workout(LocalDate.of(2026, 10, 2), set(squat, 100.0)),
        )
        assertEquals(listOf("Bench Press"), review(history).newLifts.map { it.label })
    }

    // ---- partial periods and comparison ----

    @Test
    fun `a full period names no start and compares with the period before`() {
        val history = listOf(
            workout(LocalDate.of(2026, 8, 1), set(bench, 100.0)),
            workout(LocalDate.of(2026, 9, 7), set(bench, 100.0), set(bench, 100.0, index = 1)),
        )
        val review = review(history)
        assertNull(review.since)
        assertEquals(1, review.previous!!.workouts)
        assertEquals(100, review.volumeChangePercent)
    }

    @Test
    fun `a lifter who started in the period is told since when and compared with nothing`() {
        val history = listOf(
            workout(LocalDate.of(2026, 9, 14), set(bench, 100.0)),
            workout(LocalDate.of(2026, 9, 21), set(bench, 100.0)),
        )
        val review = review(history)
        assertEquals(LocalDate.of(2026, 9, 14), review.since)
        assertNull(review.previous)
        assertNull(review.volumeChangePercent)
    }

    @Test
    fun `a lifter who started part way through the month before is not compared with it either`() {
        val history = listOf(
            workout(LocalDate.of(2026, 8, 24), set(bench, 100.0)),
            workout(LocalDate.of(2026, 9, 7), set(bench, 100.0)),
        )
        val review = review(history)
        assertNull(review.since)
        assertNull(review.previous)
    }

    @Test
    fun `a year that began in March says since March, and its pace counts only the months training`() {
        val history = (0 until 20).map { workout(LocalDate.of(2026, 3, 2).plusDays(it * 7L), set(bench, 100.0)) }
        val review = review(history, year2026, now = LocalDate.of(2027, 1, 2))
        assertEquals(LocalDate.of(2026, 3, 2), review.since)
        assertNull(review.previous)
        // 20 workouts over 2 Mar to 31 Dec: 305 days, 43.6 weeks.
        assertEquals(20 / (305 / 7.0), review.perWeek, 1e-9)
    }

    @Test
    fun `a year that began on the first of January is not partial`() {
        val history = listOf(workout(LocalDate.of(2026, 1, 1), set(bench, 100.0)))
        assertNull(review(history, year2026, now = LocalDate.of(2027, 1, 2)).since)
    }

    @Test
    fun `a year counts its workouts in each month`() {
        val history = listOf(
            workout(LocalDate.of(2026, 1, 31), set(bench, 100.0), time = LocalTime.of(23, 30)),
            workout(LocalDate.of(2026, 2, 1), set(bench, 100.0), time = LocalTime.of(0, 30)),
            workout(LocalDate.of(2026, 2, 2), set(bench, 100.0)),
            workout(LocalDate.of(2026, 12, 31), set(bench, 100.0), time = LocalTime.of(23, 59)),
        )
        val months = review(history, year2026, now = LocalDate.of(2027, 1, 2)).monthWorkouts
        assertEquals(12, months.size)
        assertEquals(listOf(1, 2), months.take(2))
        assertEquals(1, months[11])
        assertTrue(review(history).monthWorkouts.isEmpty())
    }

    // ---- streak, skills, pace ----

    @Test
    fun `the streak is read at the period's end and says whether it still runs`() {
        val history = (0 until 10).map { workout(LocalDate.of(2026, 9, 21).plusDays(it * 5L), set(bench, 100.0)) }
        val review = review(history, now = LocalDate.of(2026, 10, 7))
        // Last workout of September is 26 Sep (21, 26, then 1 Oct...): chain from 21 Sep to 30 Sep.
        assertEquals(10, review.streakDays)
        assertTrue(review.streakGoing)
        assertTrue(review.streakLongest)
        val ended = review(history, now = LocalDate.of(2026, 12, 1))
        assertFalse(ended.streakGoing)
    }

    @Test
    fun `skills claimed inside the period are listed, unclaimed practice and other months are not`() {
        val skill = Skills.ALL.first()
        fun at(day: LocalDate) = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val practices = listOf(
            SkillPractice(skill.name, at(LocalDate.of(2026, 9, 9)), claimed = true, value = skill.xp),
            SkillPractice(skill.name, at(LocalDate.of(2026, 9, 12)), claimed = false, value = 30),
            SkillPractice(Skills.ALL[1].name, at(LocalDate.of(2026, 10, 1)), claimed = true, value = 100),
        )
        val unlocked = review(listOf(workout(LocalDate.of(2026, 9, 9), set(bench, 100.0))), practices = practices).skills
        assertEquals(listOf(skill.name), unlocked.map { it.name })
        assertEquals(skill.tier, unlocked.single().tier)
    }

    @Test
    fun `the weekly pace is workouts over the weeks of the month`() {
        val history = (0 until 16).map { workout(LocalDate.of(2026, 9, 1).plusDays(it.toLong()), set(bench, 100.0)) }
        assertEquals(16 / (30 / 7.0), review(history).perWeek, 1e-9)
    }

    @Test
    fun `the muscles are ordered by hard sets and the summary agrees with the review`() {
        val history = listOf(
            workout(LocalDate.of(2026, 8, 10), set(bench, 80.0)),
            workout(LocalDate.of(2026, 9, 10), set(bench, 90.0), set(row, 60.0, 8)),
            workout(LocalDate.of(2026, 9, 17), set(bench, 95.0)),
        )
        val review = review(history)
        assertTrue(review.muscles.zipWithNext().all { (a, b) -> a.second >= b.second })
        val summary = PeriodReview.summary(history, catalogue, { 0.0 }, september, zone)
        assertEquals(review.stats.workouts, summary.workouts)
        assertEquals(review.stats.volumeKg, summary.volumeKg, 1e-9)
        assertEquals(review.lifts.size, summary.liftsGained)
    }

    // ---- Today and Stats ----

    private val some: (ReviewPeriod) -> Int = { 4 }

    @Test
    fun `Today offers last month on days 1 to 7 and not on the 8th`() {
        assertEquals(september, ReviewSchedule.offeredOn(LocalDate.of(2026, 10, 1), some))
        assertEquals(september, ReviewSchedule.offeredOn(LocalDate.of(2026, 10, 7), some))
        assertNull(ReviewSchedule.offeredOn(LocalDate.of(2026, 10, 8), some))
        assertNull(ReviewSchedule.offeredOn(LocalDate.of(2026, 9, 30), some))
    }

    @Test
    fun `in the first week of January Today offers the year, and only the year`() {
        assertEquals(year2026, ReviewSchedule.offeredOn(LocalDate.of(2027, 1, 1), some))
        assertEquals(year2026, ReviewSchedule.offeredOn(LocalDate.of(2027, 1, 7), some))
        assertNull(ReviewSchedule.offeredOn(LocalDate.of(2027, 1, 8), some))
        // February's card is January's month, a plain month again.
        assertEquals(ReviewPeriod.of(ReviewKind.MONTH, LocalDate.of(2027, 1, 1)), ReviewSchedule.offeredOn(LocalDate.of(2027, 2, 3), some))
    }

    @Test
    fun `Today offers nothing for a period with no workouts or one the lifter dismissed`() {
        assertNull(ReviewSchedule.offeredOn(LocalDate.of(2026, 10, 2), { 0 }))
        assertNull(ReviewSchedule.offeredOn(LocalDate.of(2026, 10, 2), some, dismissed = setOf("2026-09")))
        assertNotNull(ReviewSchedule.offeredOn(LocalDate.of(2026, 10, 2), some, dismissed = setOf("2026-08", "2026")))
        // Dismissing the year does not bring December back in its place.
        assertNull(ReviewSchedule.offeredOn(LocalDate.of(2027, 1, 2), some, dismissed = setOf("2026")))
    }

    @Test
    fun `the Stats list holds finished periods with workouts, newest first, the year before its December`() {
        val days = listOf(
            LocalDate.of(2025, 12, 3), LocalDate.of(2026, 7, 9), LocalDate.of(2026, 9, 2),
            LocalDate.of(2026, 10, 12), LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 2),
        )
        val list = ReviewSchedule.listed(days, today = LocalDate.of(2027, 1, 5))
        assertEquals(
            listOf("2026", "2026-12", "2026-10", "2026-09", "2025"),
            list.map { it.key },
        )
    }

    @Test
    fun `the running month and year are not listed`() {
        val days = listOf(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 9, 2))
        assertEquals(listOf("2026-09"), ReviewSchedule.listed(days, today = LocalDate.of(2026, 10, 20)).map { it.key })
    }
}
