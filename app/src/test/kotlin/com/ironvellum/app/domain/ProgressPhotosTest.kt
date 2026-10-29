package com.ironvellum.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressPhotosTest {
    private val monday = LocalDate.of(2026, 10, 26)
    private val sunday = LocalDate.of(2026, 11, 1)

    @Test fun theFirstWorkoutOfADayIsOfferedAPhotoAndTheSecondIsNot() {
        val day = monday.toEpochDay()
        assertTrue(PhotoRules.workoutPhotoOffered(emptySet(), day))
        assertFalse(PhotoRules.workoutPhotoOffered(setOf(day), day))
    }

    @Test fun aPhotoOnAnotherDayDoesNotBlockToday() {
        assertTrue(PhotoRules.workoutPhotoOffered(setOf(monday.toEpochDay() - 1), monday.toEpochDay()))
    }

    @Test fun aWeekIsNamedByItsMondayWhateverDayYouAskFrom() {
        assertEquals(monday, PhotoRules.weekStart(monday))
        assertEquals(monday, PhotoRules.weekStart(sunday.minusDays(1)))
        assertEquals(monday, PhotoRules.weekStart(sunday))
        assertEquals(sunday.plusDays(1), PhotoRules.weekStart(sunday.plusDays(1)))
    }

    @Test fun thePosePromptShowsOnlyOnThePickedDay() {
        assertTrue(PhotoRules.poseDue(sunday, DayOfWeek.SUNDAY, null, emptySet()))
        assertFalse(PhotoRules.poseDue(sunday.minusDays(1), DayOfWeek.SUNDAY, null, emptySet()))
        assertTrue(PhotoRules.poseDue(monday, DayOfWeek.MONDAY, null, emptySet()))
    }

    @Test fun aDismissOnlyHushesThePromptForThatDay() {
        assertFalse(PhotoRules.poseDue(sunday, DayOfWeek.SUNDAY, sunday.toEpochDay(), emptySet()))
        assertTrue(PhotoRules.poseDue(sunday.plusDays(7), DayOfWeek.SUNDAY, sunday.toEpochDay(), emptySet()))
    }

    @Test fun aCompleteSetNoLongerPrompts() {
        assertTrue(PhotoRules.poseDue(sunday, DayOfWeek.SUNDAY, null, setOf(Pose.FRONT, Pose.SIDE)))
        assertFalse(PhotoRules.poseDue(sunday, DayOfWeek.SUNDAY, null, Pose.entries.toSet()))
    }

    private fun pose(week: LocalDate, pose: Pose) = PhotoSnapshot(PhotoKind.POSE, week.toEpochDay(), null, pose, "p.jpg", 0)

    @Test fun weeksWithPosesAreNewestFirstDistinctAndIgnoreWorkoutPhotos() {
        val rows = listOf(
            pose(monday, Pose.FRONT), pose(monday, Pose.SIDE), pose(monday.minusWeeks(2), Pose.BACK),
            PhotoSnapshot(PhotoKind.WORKOUT, monday.plusDays(3).toEpochDay(), 1L, null, "w.jpg", 0),
        )
        assertEquals(listOf(monday, monday.minusWeeks(2)), PhotoRules.weeksWithPoses(rows))
    }

    @Test fun theComparisonOpensOnTheLastTwoWeeksWithPosesAndCopesWithFewer() {
        val a = monday
        val b = monday.minusWeeks(1)
        assertEquals(b to a, PhotoRules.defaultComparison(listOf(a, b, monday.minusWeeks(5))))
        assertEquals(null to a, PhotoRules.defaultComparison(listOf(a)))
        assertEquals(null to null, PhotoRules.defaultComparison(emptyList()))
    }

    @Test fun aBigPhotoIsScaledToTheLongEdgeAndASmallOneIsLeftAlone() {
        assertEquals(2048 to 1536, PhotoRules.targetSize(4000, 3000))
        assertEquals(1536 to 2048, PhotoRules.targetSize(3000, 4000))
        assertEquals(1000 to 800, PhotoRules.targetSize(1000, 800))
        assertEquals(2048 to 1, PhotoRules.targetSize(100_000, 2))
    }

    @Test fun fileNamesAreOursAndCarryTheirDayAndPose() {
        assertEquals("w-20000-ab12.jpg", PhotoRules.workoutFileName(20000, "ab12"))
        assertEquals("p-19996-side-ab12.jpg", PhotoRules.poseFileName(19996, Pose.SIDE, "ab12"))
    }

    @Test fun onlyNamesThisAppWritesAreOurs() {
        assertTrue(PhotoRules.isOurName(PhotoRules.workoutFileName(20000, "ab12cd")))
        assertTrue(PhotoRules.isOurName(PhotoRules.poseFileName(20000, Pose.FRONT, "ab12cd")))
        assertFalse(PhotoRules.isOurName("../x.jpg"))
        assertFalse(PhotoRules.isOurName("a/b.jpg"))
        assertFalse(PhotoRules.isOurName("x.png"))
        assertFalse(PhotoRules.isOurName(""))
        assertNull(Pose.fromKey("left"))
    }
}
