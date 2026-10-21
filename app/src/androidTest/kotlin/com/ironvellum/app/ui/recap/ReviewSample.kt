package com.ironvellum.app.ui.recap

import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.PeriodReview
import com.ironvellum.app.domain.PeriodStats
import com.ironvellum.app.domain.ReviewKind
import com.ironvellum.app.domain.ReviewLift
import com.ironvellum.app.domain.ReviewPeriod
import com.ironvellum.app.domain.ReviewSummary
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.SkillUnlock
import com.ironvellum.app.domain.WeekTotal
import java.time.LocalDate
import java.time.ZoneId

/**
 * The periods the review mockups draw, as data: September 2026 (16 workouts, 74.9 t), a year of 94 workouts and 423 t, and
 * the same year for a lifter who started in March. Laid beside the mockups, the screen, the card and the Today card read the
 * same numbers.
 */
object ReviewSample {
    val september = ReviewPeriod(ReviewKind.MONTH, LocalDate.of(2026, 9, 1))
    val year = ReviewPeriod(ReviewKind.YEAR, LocalDate.of(2026, 1, 1))

    private fun ms(date: LocalDate) = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun set(name: String, kg: Double, reps: Int) =
        SessionSet(exerciseId = 1, exerciseName = name, setIndex = 0, reps = reps, weightKg = kg, done = true)

    private fun lift(name: String, before: Double?, after: Double, series: List<Double>, day: LocalDate, adds: Boolean = false) =
        ReviewLift(name, "", before, after, series, set(name, after / 1.17, 5), adds, ms(day))

    private fun stats(from: LocalDate, to: LocalDate, workouts: Int, sets: Int, kg: Double, minutes: Long, muscles: Map<Muscle, Double>, days: Set<LocalDate>) =
        PeriodStats(from, to, workouts, sets, kg, minutes, emptyList(), null, muscles, days, emptyList(), emptyList())

    private val muscles = listOf(
        Muscle.LATS to 61.0, Muscle.MID_CHEST to 58.0, Muscle.QUADS to 47.0, Muscle.TRICEPS to 41.0, Muscle.SIDE_DELTS to 36.0,
        Muscle.BICEPS to 30.0,
    )

    /** September 2026: 16 workouts on 16 days, +18% volume on August, a streak of 80 days that is still going. */
    fun month(): PeriodReview {
        val days = listOf(3, 5, 7, 8, 10, 12, 14, 15, 17, 19, 21, 22, 24, 26, 28, 29).map { LocalDate.of(2026, 9, it) }.toSet()
        return PeriodReview(
            period = september,
            stats = stats(september.from, september.to, 16, 233, 74_860.0, 1_104, muscles.take(5).toMap(), days),
            previous = stats(LocalDate.of(2026, 8, 1), september.from, 14, 210, 63_200.0, 960, emptyMap(), emptySet()),
            since = null,
            weeks = listOf(
                WeekTotal(LocalDate.of(2026, 8, 31), 2, 9_400.0),
                WeekTotal(LocalDate.of(2026, 9, 7), 4, 16_880.0),
                WeekTotal(LocalDate.of(2026, 9, 14), 4, 17_980.0),
                WeekTotal(LocalDate.of(2026, 9, 21), 4, 16_020.0),
            ),
            lifts = listOf(
                lift("Back Squat", 112.5, 120.0, listOf(112.5, 114.0, 116.5, 118.0, 120.0), LocalDate.of(2026, 9, 26)),
                lift("Barbell Row", 82.0, 88.0, listOf(82.0, 84.0, 85.5, 88.0), LocalDate.of(2026, 9, 24)),
                lift("Bench Press", 91.5, 96.0, listOf(91.5, 93.0, 92.5, 95.0, 96.0), LocalDate.of(2026, 9, 22)),
                lift("Overhead Press", 52.0, 55.5, listOf(52.0, 53.0, 54.5, 55.5), LocalDate.of(2026, 9, 19)),
                lift("Weighted Pull-up", 11.5, 14.2, listOf(11.5, 12.4, 13.3, 14.2), LocalDate.of(2026, 9, 17), adds = true),
                lift("Front Squat", null, 84.0, listOf(80.0, 84.0), LocalDate.of(2026, 9, 12)),
            ),
            muscles = muscles,
            monthWorkouts = emptyList(),
            streakDays = 80,
            streakGoing = true,
            streakLongest = true,
            longestStreakDays = 80,
            skills = listOf(
                SkillUnlock("Parallel Bar Support Hold", 2, 240, ms(LocalDate.of(2026, 9, 9))),
                SkillUnlock("Parallel Bar Dip", 3, 360, ms(LocalDate.of(2026, 9, 27))),
            ),
            perWeek = 3.7,
        )
    }

    private fun yearLifts() = listOf(
        lift("Back Squat", 105.0, 130.0, listOf(105.0, 112.5, 120.0, 130.0), LocalDate.of(2026, 11, 20)),
        lift("Barbell Row", 74.0, 93.5, listOf(74.0, 82.0, 90.0, 93.5), LocalDate.of(2026, 10, 30)),
        lift("Bench Press", 88.0, 104.5, listOf(88.0, 95.0, 100.0, 104.5), LocalDate.of(2026, 12, 4)),
    )

    /** The year in full: 94 workouts, 423 t, +18% on 2025, the longest streak 172 days. */
    fun fullYear(): PeriodReview = PeriodReview(
        period = year,
        stats = stats(year.from, year.to, 94, 1_460, 423_000.0, 6_700, muscles.take(5).toMap(), emptySet()),
        previous = stats(LocalDate.of(2025, 1, 1), year.from, 80, 1_240, 358_500.0, 5_700, emptyMap(), emptySet()),
        since = null,
        weeks = listOf(WeekTotal(LocalDate.of(2026, 10, 12), 5, 21_400.0), WeekTotal(LocalDate.of(2026, 6, 1), 4, 17_100.0)),
        lifts = yearLifts(),
        muscles = muscles,
        monthWorkouts = listOf(8, 7, 9, 8, 8, 7, 8, 7, 8, 9, 8, 7),
        streakDays = 98,
        streakGoing = true,
        streakLongest = false,
        longestStreakDays = 172,
        skills = listOf(SkillUnlock("Parallel Bar Dip", 3, 360, ms(LocalDate.of(2026, 9, 27)))),
        perWeek = 1.8,
    )

    /** The same year for a lifter who started on 2 March: no comparison, and the pace over the ten months. */
    fun partialYear(): PeriodReview = fullYear().copy(
        previous = null,
        since = LocalDate.of(2026, 3, 2),
        stats = stats(year.from, year.to, 94, 1_460, 423_000.0, 6_700, muscles.take(5).toMap(), emptySet()),
        monthWorkouts = listOf(0, 0, 8, 9, 10, 11, 9, 10, 9, 9, 10, 9),
        perWeek = 2.2,
    )

    fun monthSummary() = ReviewSummary(september, workouts = 16, volumeKg = 74_860.0, liftsGained = 5)

    fun yearSummary() = ReviewSummary(year, workouts = 94, volumeKg = 423_000.0, liftsGained = 11)
}
