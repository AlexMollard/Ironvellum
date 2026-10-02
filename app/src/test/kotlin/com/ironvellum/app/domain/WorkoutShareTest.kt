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
        assertTrue(card, card.contains("2\u00D76 +20\u00A0kg"))
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
        assertTrue(card, card.contains("2\u00D76 \u00B7 top\u00A0+22.5\u00A0kg"))
        assertFalse(card, card.contains("+20\u00A0kg"))
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
        assertFalse(card, card.contains("4\u00D75 +60\u00A0kg"))
        assertTrue(card, card.contains("4\u00D75 \u00B7 top\u00A0+60\u00A0kg"))
    }

    /** The isolation step is 1.25 kg, so a real load can sit on a quarter kilo. */
    @Test
    fun `a quarter-kilo load is printed as loaded, not rounded`() {
        val raise = Exercise(id = 1, name = "Lateral Raise", muscleGroup = MuscleGroup.PUSH, isWeighted = true)
        val card = WorkoutShare.format(
            session(),
            List(3) { set(1, "Lateral Raise", it, 8, weightKg = 8.75) },
            mapOf(1L to raise),
            zone,
        )
        assertTrue(card, card.contains("Lateral Raise \u00B7 3\u00D78 \u00B7 8.75\u00A0kg"))
    }

    /** A dumbbell in the hand is the whole load, not load added on top of the lifter. */
    @Test
    fun `an implement's load is not written as added load`() {
        val press = Exercise(id = 1, name = "Dumbbell Shoulder Press", muscleGroup = MuscleGroup.PUSH, isWeighted = true)
        val card = WorkoutShare.format(
            session(),
            List(3) { set(1, "Dumbbell Shoulder Press", it, 5, weightKg = 18.0) },
            mapOf(1L to press),
            zone,
        )
        assertTrue(card, card.contains("Dumbbell Shoulder Press \u00B7 3\u00D75 \u00B7 18\u00A0kg"))
        assertFalse(card, card.contains("+18"))
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

    /** Unfinished sets stay out of the movement list and the set count. */
    @Test
    fun `skipped sets are not claimed`() {
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
        assertTrue(card, card.contains("2 sets · 24 reps"))
        assertFalse(card, card.contains("3 sets"))
        assertTrue(card, card.contains("Push-up \u00B7 2\u00D712"))
    }

    @Test
    fun `the card opens with the session name and carries no emoji`() {
        val card = WorkoutShare.format(session(), listOf(set(1, "Push-up", 0, 12)), mapOf(1L to exercise(1, "Push-up")), zone)
        assertTrue(card, card.startsWith("Push Day · Ironvellum\n"))
        assertFalse(card, card.codePoints().anyMatch { it >= 0x2190 })
    }

    /** Kilograms moved counts barbell and dumbbell work only, never added load on a bodyweight move. */
    @Test
    fun `kilograms moved sums loaded lifts and skips belts`() {
        val squat = Exercise(id = 2, name = "Back Squat", muscleGroup = MuscleGroup.LEGS, isWeighted = true)
        val card = WorkoutShare.format(
            session(),
            listOf(
                set(2, "Back Squat", 0, 5, weightKg = 100.0),
                set(2, "Back Squat", 1, 5, weightKg = 100.0),
                set(1, "Pull-up", 0, 5, weightKg = 20.0, position = 1),
            ),
            mapOf(1L to exercise(1, "Pull-up"), 2L to squat),
            zone,
        )
        assertTrue(card, card.contains("1,000 kg moved · 1,204 STR"))
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

    @Test
    fun `attempts read as attempts and short durations keep their seconds`() {
        val climb = exercise(1, "Boulder", ExerciseMetric.ATTEMPTS_GRADE)
        val run = exercise(2, "Sprint", ExerciseMetric.DURATION)
        val card = WorkoutShare.format(
            session(),
            listOf(set(1, "Boulder", 0, 5, grade = "V4"), set(2, "Sprint", 0, 0, durationSec = 45, position = 1)),
            mapOf(1L to climb, 2L to run),
            zone,
        )
        assertTrue(card, card.contains("5 attempts · V4"))
        assertTrue(card, card.contains("45 s"))
        assertFalse(card, card.contains("sent"))
    }

    @Test
    fun `peaks get one line and none means no line`() {
        val sets = listOf(set(1, "Push-up", 0, 12), set(2, "Dip", 0, 8, position = 1))
        val catalogue = mapOf(1L to exercise(1, "Push-up"), 2L to exercise(2, "Dip"))
        val card = WorkoutShare.format(session(), sets, catalogue, zone, peaks = listOf("Push-up", "Dip"))
        assertTrue(card, card.contains("New peaks: Push-up, Dip"))
        assertFalse(WorkoutShare.format(session(), sets, catalogue, zone).contains("New peaks"))
    }

    @Test
    fun `totals count done sets and keep held seconds apart from reps`() {
        val plank = exercise(2, "Plank", ExerciseMetric.HOLD)
        val totals = WorkoutShare.totals(
            listOf(
                set(1, "Push-up", 0, 12),
                set(1, "Push-up", 1, 10, done = false),
                set(2, "Plank", 0, 0, durationSec = 40, position = 1),
            ),
            mapOf(1L to exercise(1, "Push-up"), 2L to plank),
        )
        assertEquals(WorkoutShare.Totals(sets = 2, reps = 12, heldSeconds = 40, movedKg = 0), totals)
    }
}
