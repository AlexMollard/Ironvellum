package com.ironvellum.app.domain

import com.ironvellum.app.RETIRED_WORDS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class WorkoutShareTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val started = 1_758_000_000_000L
    private val finished = started + 47 * 60_000L

    private val g = WorkoutShare.HIT
    private val y = WorkoutShare.UNDER
    private val b = WorkoutShare.SKIPPED
    private val p = WorkoutShare.PEAK

    /** A movement's peak, with the set positions that set it. */
    private fun peak(name: String, vararg setIndexes: Int) = SessionPeaks.Peak(
        exerciseName = name,
        count = setIndexes.size,
        setIndex = setIndexes.firstOrNull() ?: 0,
        figure = 5,
        weightKg = 80.0,
        isHold = false,
        was = SetRecords.Record(name, setIndexes.firstOrNull() ?: 0, 0.0, 4, 80.0, 0L, 0L),
        deltaScore = 1.0,
        setIndexes = setIndexes.toSet(),
    )

    private fun session(
        title: String = "",
        note: String = "",
        privateNote: String = "",
        xp: Int = 248,
        strength: Int = 1204,
        minutes: Long? = 47,
    ) = WorkoutSession(
        id = 1,
        label = "Push Day",
        startedAtMs = started,
        completedAtMs = minutes?.let { started + it * 60_000L },
        xpAwarded = xp,
        strengthScore = strength,
        title = title,
        note = note,
        privateNote = privateNote,
    )

    private fun exercise(
        id: Long,
        name: String,
        metric: ExerciseMetric = ExerciseMetric.REPS,
        weighted: Boolean = false,
    ) = Exercise(id = id, name = name, muscleGroup = MuscleGroup.PUSH, isWeighted = weighted, metric = metric)

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
        warmup: Boolean = false,
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
        warmup = warmup,
    )

    private fun card(
        sets: List<SessionSet>,
        catalogue: List<Exercise>,
        session: WorkoutSession = session(),
        peaks: List<SessionPeaks.Peak> = emptyList(),
        targets: Map<Long, Int> = emptyMap(),
        notes: Map<Long, String> = emptyMap(),
        includeNotes: Boolean = false,
    ): String = WorkoutShare.format(
        session,
        sets,
        catalogue.associateBy { it.id },
        zone,
        peaks = peaks,
        targets = targets,
        exerciseNotes = notes,
        includeNotes = includeNotes,
    )

    /** The approved preview, line for line. */
    @Test
    fun `the card is the approved grid`() {
        val bench = exercise(1, "Bench Press", weighted = true)
        val ohp = exercise(2, "Overhead Press", weighted = true)
        val dip = exercise(3, "Dip")
        val raise = exercise(4, "Lateral Raise", weighted = true)
        val plank = exercise(5, "Plank", ExerciseMetric.HOLD)
        val sets = List(5) { set(1, "Bench Press", it, 5, 80.0) } +
            List(4) { set(2, "Overhead Press", it, if (it == 3) 4 else if (it == 2) 7 else 6, 45.0, position = 1) } +
            List(3) { set(3, "Dip", it, 10, 10.0, position = 2) } +
            List(3) { set(4, "Lateral Raise", it, 15, 8.0, position = 3, done = it < 2) } +
            listOf(60, 45, 60).mapIndexed { i, s -> set(5, "Plank", i, 0, durationSec = s, position = 4) }
        val text = card(
            sets,
            listOf(bench, ohp, dip, raise, plank),
            session(title = "Push day", xp = 214, strength = 380, minutes = 52),
            peaks = listOf(peak("Bench Press", 4)),
            targets = mapOf(1L to 5, 2L to 6, 3L to 10, 4L to 15, 5L to 60),
        )
        assertEquals(
            "Ironvellum · Push day · 16 Sep\n" +
                "\n" +
                "Bench    $g$g$g$g$p 5×5 @ 80kg\n" +
                "OHP      $g$g$g$y 6/6/7/4 @ 45kg\n" +
                "Dips     $g$g$g 3×10 @ +10kg\n" +
                "Laterals $g$g$b 15/15 @ 8kg\n" +
                "Plank    $g$y$g 60/45/60s\n" +
                "\n" +
                "52 min · 17 sets · +214 XP · 380 STR",
            text,
        )
    }

    @Test
    fun `squares are green when hit, yellow when under, black when not done`() {
        val text = card(
            listOf(
                set(1, "Push-up", 0, 12),
                set(1, "Push-up", 1, 9),
                set(1, "Push-up", 2, 12, done = false),
                set(1, "Push-up", 3, 15),
            ),
            listOf(exercise(1, "Push-up")),
            targets = mapOf(1L to 12),
        )
        assertTrue(text, text.contains("Push-up $g$y$b$g 12/9/15"))
    }

    @Test
    fun `warm-up sets get no square`() {
        val text = card(
            listOf(
                set(1, "Bench Press", 0, 8, 40.0, done = false, warmup = true),
                set(1, "Bench Press", 1, 5, 80.0),
                set(1, "Bench Press", 2, 5, 80.0),
            ),
            listOf(exercise(1, "Bench Press", weighted = true)),
            targets = mapOf(1L to 5),
        )
        assertTrue(text, text.contains("Bench $g$g 5/5 @ 80kg"))
        assertFalse(text, text.contains(b))
    }

    @Test
    fun `a movement with only warm-ups has no line`() {
        val text = card(
            listOf(set(1, "Bench Press", 0, 8, 40.0, done = false, warmup = true), set(2, "Push-up", 0, 12, position = 1)),
            listOf(exercise(1, "Bench Press", weighted = true), exercise(2, "Push-up")),
        )
        assertFalse(text, text.contains("Bench"))
        assertTrue(text, text.contains("Push-up $g 12"))
    }

    @Test
    fun `with no target known a done set is green`() {
        val text = card(listOf(set(1, "Push-up", 0, 3), set(1, "Push-up", 1, 1)), listOf(exercise(1, "Push-up")))
        assertTrue(text, text.contains("Push-up $g$g 3/1"))
    }

    @Test
    fun `an unticked set is prescribed and not done`() {
        val text = card(
            listOf(set(1, "Push-up", 0, 12), set(1, "Push-up", 1, 12, done = false)),
            listOf(exercise(1, "Push-up")),
            targets = mapOf(1L to 12),
        )
        assertTrue(text, text.contains("Push-up $g$b 12"))
    }

    @Test
    fun `added load on bodyweight reads with a plus and a lifted weight reads plain`() {
        val dip = exercise(1, "Dip")
        val press = exercise(2, "Dumbbell Shoulder Press", weighted = true)
        val text = card(
            List(3) { set(1, "Dip", it, 8, 10.0) } + List(3) { set(2, "Dumbbell Shoulder Press", it, 5, 18.0, position = 1) },
            listOf(dip, press),
        )
        assertTrue(text, text.contains("Dips   $g$g$g 3×8 @ +10kg"))
        assertTrue(text, text.contains("DB OHP $g$g$g 3×5 @ 18kg"))
        assertFalse(text, text.contains("+18"))
    }

    @Test
    fun `pure bodyweight prints no load and the top load wins`() {
        val bare = card(List(3) { set(1, "Push-up", it, 12) }, listOf(exercise(1, "Push-up")))
        assertTrue(bare, bare.lines().any { it == "Push-up $g$g$g 3×12" })
        assertFalse(bare, bare.contains("kg"))

        val ramp = card(
            listOf(set(1, "Dip", 0, 6, 20.0), set(1, "Dip", 1, 6, 22.5), set(1, "Dip", 2, 6)),
            listOf(exercise(1, "Dip")),
        )
        assertTrue(ramp, ramp.contains("Dips $g$g$g 3×6 @ top +22.5kg"))
    }

    /** The isolation step is 1.25 kg, so a real load can sit on a quarter kilo. */
    @Test
    fun `a quarter-kilo load is printed, not rounded`() {
        val text = card(
            List(3) { set(1, "Lateral Raise", it, 8, 8.75) },
            listOf(exercise(1, "Lateral Raise", weighted = true)),
        )
        assertTrue(text, text.contains("Laterals $g$g$g 3×8 @ 8.75kg"))
    }

    @Test
    fun `a hold scores its seconds against the target seconds`() {
        val plank = exercise(1, "Plank", ExerciseMetric.HOLD)
        val text = card(
            listOf(
                set(1, "Plank", 0, 0, durationSec = 60),
                set(1, "Plank", 1, 0, durationSec = 45),
                set(1, "Plank", 2, 0, durationSec = 60, done = false),
            ),
            listOf(plank),
            targets = mapOf(1L to 60),
        )
        assertTrue(text, text.contains("Plank $g$y$b 60/45s"))
    }

    @Test
    fun `a weighted hold carries its added load`() {
        val text = card(
            listOf(set(1, "L-sit", 0, 0, 5.0, durationSec = 20), set(1, "L-sit", 1, 0, 5.0, durationSec = 20)),
            listOf(exercise(1, "L-sit", ExerciseMetric.HOLD)),
            targets = mapOf(1L to 20),
        )
        assertTrue(text, text.contains("L-sit $g$g 20/20s @ +5kg"))
    }

    @Test
    fun `a run and a climb read as one compact figure`() {
        val run = exercise(1, "Run", ExerciseMetric.DISTANCE_TIME)
        val climb = exercise(2, "Climb", ExerciseMetric.ATTEMPTS_GRADE)
        val text = card(
            listOf(
                set(1, "Run", 0, 0, distanceM = 5200.0, durationSec = 1690),
                set(2, "Climb", 0, 6, grade = "V4", position = 1),
            ),
            listOf(run, climb),
        )
        assertTrue(text, text.contains("Run   5.2km in 28:10"))
        assertTrue(text, text.contains("Climb 6 attempts · V4"))
        assertFalse(text, text.contains(g))
    }

    @Test
    fun `a short timed activity keeps its seconds and a long one rounds to minutes`() {
        val sprint = exercise(1, "Sprint", ExerciseMetric.DURATION)
        val swim = exercise(2, "Swim", ExerciseMetric.DURATION)
        val text = card(
            listOf(set(1, "Sprint", 0, 0, durationSec = 45), set(2, "Swim", 0, 0, durationSec = 1800, position = 1)),
            listOf(sprint, swim),
        )
        assertTrue(text, text.contains("Sprint 45s"))
        assertTrue(text, text.contains("Swim   30 min"))
    }

    @Test
    fun `an activity nobody did has no line`() {
        val text = card(
            listOf(set(1, "Run", 0, 0, distanceM = 5000.0, durationSec = 1500, done = false), set(2, "Push-up", 0, 12, position = 1)),
            listOf(exercise(1, "Run", ExerciseMetric.DISTANCE_TIME), exercise(2, "Push-up")),
        )
        assertFalse(text, text.contains("Run"))
    }

    @Test
    fun `exercise notes appear under their movement only when asked for`() {
        val sets = listOf(set(1, "Push-up", 0, 12), set(2, "Dip", 0, 8, position = 1))
        val catalogue = listOf(exercise(1, "Push-up"), exercise(2, "Dip"))
        val notes = mapOf(1L to "  left wrist clicked\non the last rep  ")

        val on = card(sets, catalogue, notes = notes, includeNotes = true)
        assertTrue(on, on.contains("Push-up $g 12\n  ↳ \"left wrist clicked on the last rep\"\nDips"))
        assertEquals(1, on.lines().count { it.contains("↳") })

        val off = card(sets, catalogue, notes = notes, includeNotes = false)
        assertFalse(off, off.contains("↳"))
        assertFalse(off, off.contains("wrist"))
    }

    @Test
    fun `a long note is cut to eighty characters with an ellipsis`() {
        val long = "x".repeat(200)
        val text = card(
            listOf(set(1, "Push-up", 0, 12)),
            listOf(exercise(1, "Push-up")),
            notes = mapOf(1L to long),
            includeNotes = true,
        )
        val line = text.lines().first { it.contains("↳") }
        assertEquals("  ↳ \"" + "x".repeat(79) + "…\"", line)
    }

    @Test
    fun `a blank note adds no line`() {
        val text = card(
            listOf(set(1, "Push-up", 0, 12)),
            listOf(exercise(1, "Push-up")),
            notes = mapOf(1L to "   "),
            includeNotes = true,
        )
        assertFalse(text, text.contains("↳"))
    }

    @Test
    fun `common lifts take their short name and long ones are cut`() {
        assertEquals("Bench", WorkoutShare.shortName("Bench Press"))
        assertEquals("OHP", WorkoutShare.shortName("overhead press"))
        assertEquals("Laterals", WorkoutShare.shortName("Lateral Raise"))
        assertEquals("Pull-up", WorkoutShare.shortName("Pull-up"))
        val cut = WorkoutShare.shortName("Single-Leg Romanian Deadlift")
        assertEquals(WorkoutShare.NAME_MAX, cut.length)
        assertTrue(cut, cut.endsWith("…"))
    }

    @Test
    fun `names are padded so the squares start in one column`() {
        val text = card(
            listOf(set(1, "Bench Press", 0, 5, 80.0), set(2, "Dip", 0, 8, position = 1)),
            listOf(exercise(1, "Bench Press", weighted = true), exercise(2, "Dip")),
        )
        val columns = text.lines().filter { it.contains(g) }.map { it.indexOf(g) }.distinct()
        assertEquals(text, 1, columns.size)
    }

    @Test
    fun `a record is marked on its own movement only`() {
        val sets = listOf(set(1, "Push-up", 0, 12), set(2, "Dip", 0, 8, position = 1))
        val catalogue = listOf(exercise(1, "Push-up"), exercise(2, "Dip"))
        val text = card(sets, catalogue, peaks = listOf(peak("push-up", 0)))
        assertEquals(text, 1, text.split(p).size - 1)
        assertTrue(text, text.lines().first { it.startsWith("Push-up") }.contains(p))
        assertFalse(card(sets, catalogue).contains(p))
    }

    @Test
    fun `the footer drops its zero parts`() {
        val sets = listOf(set(1, "Push-up", 0, 12))
        val catalogue = listOf(exercise(1, "Push-up"))
        assertTrue(card(sets, catalogue, session(xp = 0, strength = 0)).endsWith("\n\n47 min · 1 set"))
        assertTrue(card(sets, catalogue, session(xp = 120, strength = 0)).endsWith("47 min · 1 set · +120 XP"))
        assertTrue(card(sets, catalogue, session(xp = 0, strength = 910)).endsWith("47 min · 1 set · 910 STR"))
        assertTrue(card(sets, catalogue, session(xp = 248, strength = 1204, minutes = null)).endsWith("1 set · +248 XP · 1,204 STR"))
        val bare = card(sets, catalogue, session(xp = 0, strength = 0, minutes = null))
        assertFalse(bare, bare.contains("min"))
        assertFalse(bare, bare.contains("XP"))
        assertEquals(bare.trimEnd(), bare)
    }

    @Test
    fun `a record set is a trophy even when it is under the target`() {
        val text = card(
            listOf(set(1, "Push-up", 0, 12), set(1, "Push-up", 1, 15), set(1, "Push-up", 2, 9), set(1, "Push-up", 3, 9)),
            listOf(exercise(1, "Push-up")),
            peaks = listOf(peak("Push-up", 2)),
            targets = mapOf(1L to 12),
        )
        assertTrue(text, text.contains("Push-up $g$g$p$y 12/15/9/9"))
    }

    @Test
    fun `a trophy needs a done set at that position`() {
        val text = card(
            listOf(set(1, "Push-up", 0, 12), set(1, "Push-up", 1, 12, done = false)),
            listOf(exercise(1, "Push-up")),
            peaks = listOf(peak("Push-up", 1)),
        )
        assertTrue(text, text.contains("Push-up $g$b 12"))
        assertFalse(text, text.contains(p))
    }

    @Test
    fun `beating the target by reps or seconds is still green`() {
        val reps = card(
            listOf(set(1, "Push-up", 0, 12), set(1, "Push-up", 1, 15)),
            listOf(exercise(1, "Push-up")),
            targets = mapOf(1L to 12),
        )
        assertTrue(reps, reps.contains("Push-up $g$g 12/15"))
        val hold = card(
            listOf(set(1, "Plank", 0, 0, durationSec = 60), set(1, "Plank", 1, 0, durationSec = 75)),
            listOf(exercise(1, "Plank", ExerciseMetric.HOLD)),
            targets = mapOf(1L to 60),
        )
        assertTrue(hold, hold.contains("Plank $g$g 60/75s"))
    }

    @Test
    fun `a varied load reads as the top one`() {
        val bench = exercise(1, "Bench Press", weighted = true)
        val text = card(
            listOf(set(1, "Bench Press", 0, 5, 80.0), set(1, "Bench Press", 1, 5, 82.5), set(1, "Bench Press", 2, 4, 85.0)),
            listOf(bench),
        )
        assertTrue(text, text.contains("Bench $g$g$g 5/5/4 @ top 85kg"))
    }

    @Test
    fun `uniform sets read as a product from three and as a list below`() {
        val text = card(
            List(3) { set(1, "Push-up", it, 10) } +
                List(2) { set(2, "Dip", it, 8, position = 1) } +
                listOf(set(3, "Row", 0, 12, position = 2)) +
                listOf(set(4, "Curl", 0, 10, position = 3), set(4, "Curl", 1, 9, position = 3), set(4, "Curl", 2, 10, position = 3)),
            listOf(exercise(1, "Push-up"), exercise(2, "Dip"), exercise(3, "Row"), exercise(4, "Curl")),
        )
        assertTrue(text, text.contains("Push-up $g$g$g 3×10"))
        assertTrue(text, text.contains("Dips    $g$g 8/8"))
        assertTrue(text, text.contains("Row     $g 12"))
        assertTrue(text, text.contains("Curl    $g$g$g 10/9/10"))
    }

    @Test
    fun `a uniform hold reads as a product of seconds`() {
        val text = card(
            List(3) { set(1, "Plank", it, 0, durationSec = 60) },
            listOf(exercise(1, "Plank", ExerciseMetric.HOLD)),
        )
        assertTrue(text, text.contains("Plank $g$g$g 3×60s"))
    }

    @Test
    fun `no key line and no star ever ride along`() {
        val text = card(
            listOf(set(1, "Push-up", 0, 15), set(1, "Push-up", 1, 12)),
            listOf(exercise(1, "Push-up")),
            peaks = listOf(peak("Push-up", 1)),
            targets = mapOf(1L to 12),
        )
        assertTrue(text, text.contains("Push-up $g$p 15/12"))
        assertTrue(text, text.endsWith("47 min · 2 sets · +248 XP · 1,204 STR"))
        assertFalse(text, text.contains("★") || text.contains("peak") || text.contains("rite"))
    }

    @Test
    fun `the footer counts done working sets only`() {
        val text = card(
            listOf(
                set(1, "Bench Press", 0, 8, 40.0, warmup = true),
                set(1, "Bench Press", 1, 5, 80.0),
                set(1, "Bench Press", 2, 5, 80.0),
                set(1, "Bench Press", 3, 5, 80.0, done = false),
            ),
            listOf(exercise(1, "Bench Press", weighted = true)),
        )
        assertTrue(text, text.endsWith("47 min · 2 sets · +248 XP · 1,204 STR"))
    }

    /**
     * The private note is the one field the app promises never leaves the
     * device. A share card is the most direct way to break that promise.
     */
    @Test
    fun `the private note never reaches the card`() {
        val text = card(
            listOf(set(1, "Push-up", 0, 12)),
            listOf(exercise(1, "Push-up")),
            session(note = "felt strong", privateNote = "shoulder twinge, see physio"),
        )
        assertFalse(text, text.contains("physio"))
        assertTrue(text, text.endsWith("“felt strong”"))
    }

    @Test
    fun `a named trial is titled by its name, not its rite`() {
        val text = card(emptyList(), emptyList(), session(title = "Dungeon Raid"))
        assertTrue(text, text.startsWith("Ironvellum · Dungeon Raid · 16 Sep"))
        assertFalse(text, text.contains("Push Day"))
        assertTrue(card(emptyList(), emptyList()).startsWith("Ironvellum · Push Day · 16 Sep"))
    }

    @Test
    fun `no retired glossary word reaches the card`() {
        val sets = listOf(
            set(1, "Bench Press", 0, 5, 80.0),
            set(2, "Run", 0, 0, distanceM = 5000.0, durationSec = 1500, position = 1),
            set(3, "Climb", 0, 6, grade = "V4", position = 2),
        )
        val catalogue = listOf(
            exercise(1, "Bench Press", weighted = true),
            exercise(2, "Run", ExerciseMetric.DISTANCE_TIME),
            exercise(3, "Climb", ExerciseMetric.ATTEMPTS_GRADE),
        )
        val text = card(
            sets,
            catalogue,
            session(title = "Push day", note = "good one"),
            peaks = listOf(peak("Bench Press", 0)),
            notes = mapOf(1L to "felt light"),
            includeNotes = true,
        )
        val hits = RETIRED_WORDS.filter { it.containsMatchIn(text) }
        assertTrue("$text\n$hits", hits.isEmpty())
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

    /** A hold's seconds sit in the reps field of an old archive; they are time, not reps. */
    @Test
    fun `hold seconds are reported as time, not added to the rep total`() {
        val totals = WorkoutShare.totals(
            listOf(
                set(1, "Push-up", 0, 26),
                set(2, "Hollow Hold", 0, 45, position = 1),
                set(2, "Hollow Hold", 1, 30, position = 1),
            ),
            mapOf(1L to exercise(1, "Push-up"), 2L to exercise(2, "Hollow Hold")),
        )
        assertEquals(26, totals.reps)
        assertEquals(75, totals.heldSeconds)
    }

    /** Kilograms moved counts barbell and dumbbell work only, never added load on a bodyweight move. */
    @Test
    fun `kilograms moved sums loaded lifts and skips belts`() {
        val squat = Exercise(id = 2, name = "Back Squat", muscleGroup = MuscleGroup.LEGS, isWeighted = true)
        val totals = WorkoutShare.totals(
            listOf(
                set(2, "Back Squat", 0, 5, weightKg = 100.0),
                set(2, "Back Squat", 1, 5, weightKg = 100.0),
                set(1, "Pull-up", 0, 5, weightKg = 20.0, position = 1),
            ),
            mapOf(1L to exercise(1, "Pull-up"), 2L to squat),
        )
        assertEquals(1000, totals.movedKg)
    }

    /** Distance metres are not reps: they must not land in the rep total. */
    @Test
    fun `only counted movements contribute to the rep total`() {
        val totals = WorkoutShare.totals(
            listOf(
                set(1, "Push-up", 0, 12),
                set(2, "Running", 0, 0, distanceM = 5000.0, durationSec = 1450, position = 1),
            ),
            mapOf(1L to exercise(1, "Push-up"), 2L to exercise(2, "Running", ExerciseMetric.DISTANCE_TIME)),
        )
        assertEquals(12, totals.reps)
    }
}
