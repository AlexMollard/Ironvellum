package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class WorkoutShareTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val started = 1_758_000_000_000L
    private val finished = started + 47 * 60_000L

    private fun session(
        title: String = "",
        note: String = "",
        privateNote: String = "",
        xp: Int = 248,
        strength: Int = 1204,
    ) = WorkoutSession(
        id = 1,
        label = "Push Day",
        startedAtMs = started,
        completedAtMs = finished,
        xpAwarded = xp,
        strengthScore = strength,
        title = title,
        note = note,
        privateNote = privateNote,
    )

    private fun exercise(id: Long, name: String, metric: ExerciseMetric = ExerciseMetric.REPS) =
        Exercise(id = id, name = name, muscleGroup = MuscleGroup.PUSH, isWeighted = false, metric = metric)

    private fun set(
        exerciseId: Long,
        name: String,
        index: Int,
        reps: Int,
        weightKg: Double? = null,
        position: Int = 0,
        done: Boolean = true,
        durationSec: Int? = null,
        distanceM: Double? = null,
        grade: String? = null,
        modifiers: String = "",
    ) = SessionSet(
        id = index.toLong() + exerciseId * 100,
        exerciseId = exerciseId,
        exerciseName = name,
        exercisePosition = position,
        setIndex = index,
        reps = reps,
        weightKg = weightKg,
        modifiers = modifiers,
        done = done,
        durationSec = durationSec,
        distanceM = distanceM,
        grade = grade,
    )

    /**
     * The private note is the one field the app promises never leaves the
     * device. A share card is the most direct way to break that promise.
     */
    @Test
    fun `the private note never reaches the card`() {
        val card = WorkoutShare.format(
            session(note = "felt strong", privateNote = "shoulder twinge, see physio"),
            listOf(set(1, "Push-up", 0, 12)),
            mapOf(1L to exercise(1, "Push-up")),
            zone,
        )
        assertFalse(card, card.contains("physio"))
        assertTrue(card, card.contains("felt strong"))
    }

    @Test
    fun `identical sets collapse and varied sets are listed`() {
        val card = WorkoutShare.format(
            session(),
            listOf(
                set(1, "Pull-up", 0, 8),
                set(1, "Pull-up", 1, 8),
                set(1, "Pull-up", 2, 8),
                set(2, "Dip", 0, 10, position = 1),
                set(2, "Dip", 1, 8, position = 1),
            ),
            mapOf(1L to exercise(1, "Pull-up"), 2L to exercise(2, "Dip")),
            zone,
        )
        assertTrue(card, card.contains("Pull-up \u00B7 3\u00D78"))
        assertTrue(card, card.contains("Dip \u00B7 10/8"))
    }

    /** A hold reads as seconds, not as a suspiciously heroic rep count. */
    @Test
    fun `holds render in seconds`() {
        val card = WorkoutShare.format(
            session(),
            listOf(set(1, "Hollow Hold", 0, 60), set(1, "Hollow Hold", 1, 60)),
            mapOf(1L to exercise(1, "Hollow Hold")),
            zone,
        )
        assertTrue(card, card.contains("Hollow Hold \u00B7 2\u00D760s"))
    }

    @Test
    fun `a load carried by every set is stated plainly`() {
        val card = WorkoutShare.format(
            session(),
            listOf(set(1, "Weighted Dip", 0, 6, weightKg = 20.0), set(1, "Weighted Dip", 1, 6, weightKg = 20.0)),
            mapOf(1L to exercise(1, "Weighted Dip")),
            zone,
        )
        assertTrue(card, card.contains("2\u00D76 +20 kg"))
        assertFalse(card, card.contains("top"))
    }

    @Test
    fun `a load only the best set carried is labelled as the top set`() {
        val card = WorkoutShare.format(
            session(),
            listOf(set(1, "Weighted Dip", 0, 6, weightKg = 20.0), set(1, "Weighted Dip", 1, 6, weightKg = 22.5)),
            mapOf(1L to exercise(1, "Weighted Dip")),
            zone,
        )
        // "2x6 +22.5 kg" would tell a reader both sets carried 22.5 kg.
        assertTrue(card, card.contains("2\u00D76 \u00B7 top +22.5 kg"))
        assertFalse(card, card.contains("+20 kg"))
    }

    @Test
    fun `one loaded set among bodyweight sets never reads as a loaded block`() {
        val card = WorkoutShare.format(
            session(),
            listOf(
                set(1, "Back Squat", 0, 5, weightKg = 60.0),
                set(1, "Back Squat", 1, 5),
                set(1, "Back Squat", 2, 5),
                set(1, "Back Squat", 3, 5),
            ),
            mapOf(1L to exercise(1, "Back Squat")),
            zone,
        )
        // The defect this pins was read off a real share card on a device.
        assertFalse(card, card.contains("4\u00D75 +60 kg"))
        assertTrue(card, card.contains("4\u00D75 \u00B7 top +60 kg"))
    }

    @Test
    fun `distance work reports pace-readable figures`() {
        val card = WorkoutShare.format(
            session(),
            listOf(set(1, "Running", 0, 0, distanceM = 5000.0, durationSec = 1450)),
            mapOf(1L to exercise(1, "Running", ExerciseMetric.DISTANCE_TIME)),
            zone,
        )
        assertTrue(card, card.contains("Running \u00B7 5 km in 24:10"))
    }

    /** Distance metres are not reps: they must not land in the rep total. */
    @Test
    fun `only counted movements contribute to the rep total`() {
        val card = WorkoutShare.format(
            session(),
            listOf(
                set(1, "Push-up", 0, 12),
                set(2, "Running", 0, 0, distanceM = 5000.0, durationSec = 1450, position = 1),
            ),
            mapOf(1L to exercise(1, "Push-up"), 2L to exercise(2, "Running", ExerciseMetric.DISTANCE_TIME)),
            zone,
        )
        assertTrue(card, card.contains("12 reps"))
    }

    /**
     * A hold's seconds sit in the reps field. Summing them into the rep total
     * printed "140 reps" for a session that was 65 reps and 75 seconds of
     * hollow hold.
     */
    @Test
    fun `hold seconds are reported as time, not added to the rep total`() {
        val card = WorkoutShare.format(
            session(strength = 610),
            listOf(
                set(1, "Push-up", 0, 26),
                set(2, "Hollow Hold", 0, 45, position = 1),
                set(2, "Hollow Hold", 1, 30, position = 1),
            ),
            mapOf(1L to exercise(1, "Push-up"), 2L to exercise(2, "Hollow Hold")),
            zone,
        )
        assertTrue(card, card.contains("26 reps"))
        assertTrue(card, card.contains("75s held"))
        assertFalse(card, card.contains("101 reps"))
    }

    /** Unfinished sets stay out of the movement list but still shape the bar. */
    @Test
    fun `the bar shows completion and skipped sets are not claimed`() {
        val card = WorkoutShare.format(
            session(),
            listOf(
                set(1, "Push-up", 0, 12),
                set(1, "Push-up", 1, 12),
                set(1, "Push-up", 2, 12, done = false),
            ),
            mapOf(1L to exercise(1, "Push-up")),
            zone,
        )
        assertTrue(card, card.contains("2/3 sets"))
        assertTrue(card, card.contains("Push-up \u00B7 2\u00D712"))
    }

    @Test
    fun `the bar fills in proportion and always spans its full width`() {
        assertEquals(WorkoutShare.BAR_CELLS, WorkoutShare.bar(3, 7).codePointCount(0, WorkoutShare.bar(3, 7).length))
        assertEquals("", WorkoutShare.bar(0, 0))
        val full = WorkoutShare.bar(4, 4)
        assertEquals(full, WorkoutShare.bar(9, 9))
    }

    @Test
    fun `a named session is titled by its name, not its preset`() {
        val card = WorkoutShare.format(session(title = "Dungeon Raid"), emptyList(), emptyMap(), zone)
        assertTrue(card, card.contains("Dungeon Raid"))
        assertFalse(card, card.contains("Push Day"))
    }

    @Test
    fun `the card reports duration and the session's own figures`() {
        val card = WorkoutShare.format(session(), listOf(set(1, "Push-up", 0, 12)), mapOf(1L to exercise(1, "Push-up")), zone)
        assertTrue(card, card.contains("47 min"))
        assertTrue(card, card.contains("+248 XP"))
        assertTrue(card, card.contains("1,204 STR"))
    }

    /** Pasted into a chat, a trailing run of blank lines is just noise. */
    @Test
    fun `the card has no trailing blank lines`() {
        val card = WorkoutShare.format(session(), listOf(set(1, "Push-up", 0, 12)), mapOf(1L to exercise(1, "Push-up")), zone)
        assertEquals(card.trimEnd(), card)
    }
}
