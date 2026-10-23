package com.ironvellum.app.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The owner's own case, worked through: an experienced lifter does 3 dips (RPE 8, 8, 9) and 3 weighted incline
 * push-ups (RPE 8, assumed: the report did not give one) on Wednesday 19:05, and on Thursday the muscles they hit
 * read fresh by Saturday, not Sunday. The first workout in the app is only two weeks old, which used to make the
 * lifter "new" and slow every recovery by a fifth; the lifter's own answer decides that now.
 */
class WednesdayDipsCheckTest {

    private val zone = ZoneId.of("Europe/London")
    private val DAY = 24 * 3_600_000L

    private fun at(day: Int, hour: Int, minute: Int = 0) =
        ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    private val wednesday = at(21, 19, 5)
    private val thursdayNoon = at(22, 12)
    private val firstWorkout = at(7, 18)

    private fun dips(atMs: Long, vararg rpe: Int) = rpe.map { LoggedSet("Dip", "", null, 8, it, SetKind.WORKING, true, atMs, bodyweight = true) }
    private fun pushUps(atMs: Long, vararg rpe: Int) = rpe.map { LoggedSet("Push-up", "incline", 10.0, 10, it, SetKind.WORKING, true, atMs) }

    /** Two earlier sessions of the same lifts inside the novelty window, so neither lift is new to them on Wednesday. */
    private val sessions = listOf(
        FatigueSession(1, at(14, 18), dips(at(14, 18), 8, 8, 8) + pushUps(at(14, 18), 8, 8, 8)),
        FatigueSession(2, at(17, 11), dips(at(17, 11), 8, 8, 9) + pushUps(at(17, 11), 8, 8, 8)),
        FatigueSession(3, wednesday, dips(wednesday, 8, 8, 9) + pushUps(wednesday, 8, 8, 8)),
    )

    private fun report(experience: LiftingExperience) = FatigueModel.estimate(sessions, thursdayNoon, firstWorkout, experience)

    /** The local day each worked muscle drops under Fresh, for the muscles Wednesday's work reached. */
    private fun freshDays(report: FatigueReport): Map<Muscle, LocalDate> =
        report.contributions.filter { (_, list) -> list.any { it.sessionId == 3L } }.keys
            .mapNotNull { muscle ->
                report.firstBelowMs(muscle, FatigueTuning.FRESH_BELOW)?.let { muscle to Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            }.toMap()

    @Test
    fun `both lifts are in the catalogue's muscle map, so the check cannot pass on nothing`() {
        assertNotNull(MuscleMap.profile("Dip", ""))
        assertNotNull(MuscleMap.profile("Push-up", "incline"))
        assertTrue(report(LiftingExperience.UNANSWERED).mostWorked(zone).isNotEmpty())
    }

    @Test
    fun `an experienced lifter reads fresh by Saturday, not Sunday`() {
        val report = report(LiftingExperience.UNANSWERED)
        assertEquals("a two-week-old first workout is not a new lifter", false, report.newLifter)
        val days = freshDays(report)
        assertTrue("still worked on Thursday: $days", days.isNotEmpty())
        val latest = days.values.max()
        assertTrue("fresh by Saturday at the latest, was $latest ($days)", latest <= LocalDate.of(2026, 10, 24))
        assertTrue("Saturday is a Saturday", latest.dayOfWeek <= DayOfWeek.SATURDAY)
        // The breakdown the owner reads says the same thing, in its own words.
        for (muscle in days.keys) {
            val freshBy = report.reading(muscle, zone).freshBy
            assertTrue("$muscle reads $freshBy", freshBy != LightBy.OverAWeek && freshBy != LightBy.Weekday(DayOfWeek.SUNDAY))
        }
    }

    @Test
    fun `the same week for someone who said they are new reads later`() {
        val experienced = report(LiftingExperience.UNANSWERED)
        val newcomer = report(LiftingExperience.UNDER_TWO_MONTHS)
        assertTrue(newcomer.newLifter)
        // A fifth slower: the last muscle to drop under Fresh does so hours later (the day can be the same Saturday).
        fun lastFreshMs(report: FatigueReport) = Muscle.entries.mapNotNull { report.firstBelowMs(it, FatigueTuning.FRESH_BELOW) }.max()
        assertTrue("slower for a new lifter", lastFreshMs(newcomer) > lastFreshMs(experienced))
    }
}
