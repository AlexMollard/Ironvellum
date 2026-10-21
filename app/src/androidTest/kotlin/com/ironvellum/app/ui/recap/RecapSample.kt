package com.ironvellum.app.ui.recap

import com.ironvellum.app.domain.BestSet
import com.ironvellum.app.domain.LiftRecords
import com.ironvellum.app.domain.LiftWork
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.MuscleRow
import com.ironvellum.app.domain.PeriodStats
import com.ironvellum.app.domain.RecapKind
import com.ironvellum.app.domain.RecapNext
import com.ironvellum.app.domain.RecapStreak
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.WeeklyRecap
import java.time.LocalDate
import java.time.ZoneId

/**
 * The weeks the recap mockups draw (Mon 12 - Sun 18 Oct 2026), as data: the same numbers on screen, in the notification and
 * on the share card, so a capture can be laid beside its mockup.
 */
object RecapSample {
    val monday: LocalDate = LocalDate.of(2026, 10, 12)

    private fun ms(date: LocalDate) = date.atTime(18, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun set(name: String, kg: Double, reps: Int) =
        SessionSet(exerciseId = 1, exerciseName = name, setIndex = 0, reps = reps, weightKg = kg, done = true)

    private fun record(name: String, kg: Double, reps: Int, gain: Double, day: LocalDate, adds: Boolean = false) = LiftRecords.RecordGain(
        name = name, variation = "", e1rmKg = kg * (1 + reps / 30.0), gainKg = gain, set = set(name, kg, reps),
        addsToBody = adds, atMs = ms(day),
    )

    private fun stats(
        from: LocalDate, workouts: Int, sets: Int, volume: Double, minutes: Long,
        records: List<LiftRecords.RecordGain> = emptyList(), days: Set<LocalDate> = emptySet(),
        best: BestSet? = null, lifts: List<LiftWork> = emptyList(), titles: List<String> = emptyList(),
    ) = PeriodStats(from, from.plusDays(7), workouts, sets, volume, minutes, records, best, emptyMap(), days, lifts, titles)

    private fun row(muscle: Muscle, sets: Double, aim: IntRange?): MuscleRow = MuscleRow(muscle, sets, aim, WeeklyRecap.stateOf(sets, aim))

    /** Seven muscles on aim (the mockup's back, chest, quads, triceps, side delts, biceps and glutes), three off it. */
    private val muscles = listOf(
        row(Muscle.LATS, 16.0, 12..18),
        row(Muscle.MID_CHEST, 14.0, 10..14),
        row(Muscle.QUADS, 12.0, 8..12),
        row(Muscle.TRICEPS, 10.0, 8..12),
        row(Muscle.SIDE_DELTS, 9.0, 6..10),
        row(Muscle.BICEPS, 8.0, 6..10),
        row(Muscle.HAMSTRINGS, 7.0, 8..12),
        row(Muscle.GLUTES, 6.0, 4..8),
        row(Muscle.CALVES, 3.0, 6..8),
        row(Muscle.ABS, 0.0, 4..6),
    )

    /** The ordinary week: 4 workouts, 62 sets, 18,420 kg, 4 h 52, three records, a 98-day streak. */
    fun full(): WeeklyRecap = WeeklyRecap(
        weekStart = monday,
        stats = stats(
            monday, 4, 62, 18_420.0, 292,
            records = listOf(
                record("Bench Press", 82.5, 6, 3.0, monday),
                record("Weighted Pull-up", 12.5, 7, 1.2, monday.plusDays(1), adds = true),
                record("Back Squat", 105.0, 5, 2.5, monday.plusDays(3)),
            ),
            days = setOf(monday, monday.plusDays(1), monday.plusDays(3), monday.plusDays(5)),
            best = BestSet("Back Squat", set("Back Squat", 105.0, 5), 122.5, ms(monday.plusDays(3)), addsToBody = false),
        ),
        previous = stats(monday.minusDays(7), 3, 51, 15_960.0, 254),
        kind = RecapKind.FULL,
        muscles = muscles,
        streak = RecapStreak(98, longest = true, atRisk = false, lastWorkout = monday.plusDays(5), trainBy = monday.plusDays(12)),
        next = RecapNext(4, "Push A", 1, listOf(Muscle.CALVES, Muscle.ABS), "Legs", 4),
    )

    /** One workout after a week of four: 14 sets, 5,310 kg, 58 min. */
    fun light(): WeeklyRecap = WeeklyRecap(
        weekStart = monday,
        stats = stats(
            monday, 1, 14, 5_310.0, 58,
            days = setOf(monday.plusDays(1)),
            lifts = listOf(
                LiftWork("Back Squat", 5, 5, 100.0, false),
                LiftWork("Romanian Deadlift", 3, 8, 85.0, false),
                LiftWork("Calf Raise", 3, 15, null, false),
            ),
            titles = listOf("Legs"),
        ),
        previous = stats(monday.minusDays(7), 4, 60, 17_000.0, 240),
        kind = RecapKind.LIGHT,
        muscles = muscles.map { row(it.muscle, it.sets / 3, it.aim) },
        streak = RecapStreak(98, longest = true, atRisk = false, lastWorkout = monday.plusDays(1), trainBy = monday.plusDays(8)),
        next = RecapNext(4, "Push A", 1, emptyList(), null, null),
    )

    /** Nothing logged, after a week of three; the last workout was Sun 11 Oct and the streak stood at 91 days. */
    fun empty(): WeeklyRecap = WeeklyRecap(
        weekStart = monday,
        stats = stats(monday, 0, 0, 0.0, 0),
        previous = stats(monday.minusDays(7), 3, 51, 15_960.0, 254),
        kind = RecapKind.EMPTY,
        muscles = muscles.map { row(it.muscle, 0.0, it.aim) },
        streak = RecapStreak(91, longest = false, atRisk = true, lastWorkout = monday.minusDays(1), trainBy = monday.plusDays(6)),
        next = null,
    )
}
