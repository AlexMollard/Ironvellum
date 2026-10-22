package com.ironvellum.app.domain

import com.ironvellum.app.domain.WorkoutCsvWriter.Period
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class WorkoutCsvWriterTest {

    // The reader parses Strong dates in the device zone, so the round trip does too.
    private val zone = ZoneId.systemDefault()

    private fun ms(at: String) = LocalDateTime.parse(at).atZone(zone).toInstant().toEpochMilli()

    private fun session(id: Long, start: String, minutes: Long? = 60, title: String = "", note: String = "") =
        WorkoutSession(
            id = id,
            label = "Push",
            startedAtMs = ms(start),
            completedAtMs = minutes?.let { ms(start) + it * 60_000 },
            title = title,
            note = note,
            privateNote = "secret",
        )

    @Test
    fun periodsStartOnMondayTheFirstAndFirstOfJanuary() {
        val sunday = LocalDate.of(2026, 10, 11)
        assertEquals(LocalDate.of(2026, 10, 5), Period.WEEK.startOf(sunday))
        assertEquals(LocalDate.of(2026, 10, 5), Period.WEEK.startOf(LocalDate.of(2026, 10, 5)))
        assertEquals(LocalDate.of(2026, 10, 1), Period.MONTH.startOf(sunday))
        assertEquals(LocalDate.of(2026, 1, 1), Period.YEAR.startOf(sunday))
        assertEquals(LocalDate.of(2026, 1, 1), Period.YEAR.startOf(LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun inPeriodKeepsCompletedTrialsFromThePeriodStart() {
        val ticked = listOf(SessionSet(exerciseId = 1, setIndex = 0, reps = 5, done = true))
        val sessions = listOf(
            session(1, "2026-10-04T23:59:00") to ticked, // Sunday before: out
            session(2, "2026-10-05T00:00:00") to ticked, // Monday midnight: in
            session(3, "2026-10-07T18:00:00", minutes = null) to ticked, // live: out
            session(4, "2026-10-08T18:00:00") to listOf(ticked[0].copy(done = false)), // nothing ticked: out
        )
        val kept = WorkoutCsvWriter.inPeriod(sessions, Period.WEEK, LocalDate.of(2026, 10, 11), zone)
        assertEquals(listOf(2L), kept.map { it.first.id })
    }

    @Test
    fun quotesCommasQuotesAndLineBreaks() {
        assertEquals("plain", WorkoutCsvWriter.quote("plain"))
        assertEquals("\"a, b\"", WorkoutCsvWriter.quote("a, b"))
        assertEquals("\"say \"\"hi\"\"\"", WorkoutCsvWriter.quote("say \"hi\""))
        assertEquals("\"two\nlines\"", WorkoutCsvWriter.quote("two\nlines"))
    }

    @Test
    fun durationReadsLikeStrong() {
        assertEquals("2h 38m", WorkoutCsvWriter.duration(2 * 3600 + 38 * 60 + 12))
        assertEquals("45m", WorkoutCsvWriter.duration(45 * 60))
        assertEquals("40s", WorkoutCsvWriter.duration(40))
        assertEquals("", WorkoutCsvWriter.duration(0))
    }

    @Test
    fun writesTickedSetsOnlyAndNeverThePrivateNote() {
        val sets = listOf(
            SessionSet(exerciseId = 1, exerciseName = "Bench Press", exercisePosition = 0, setIndex = 0, reps = 5, weightKg = 80.0, done = true),
            SessionSet(exerciseId = 1, exerciseName = "Bench Press", exercisePosition = 0, setIndex = 1, reps = 5, weightKg = 80.0, done = false),
        )
        val csv = WorkoutCsvWriter.write(listOf(session(1, "2026-10-05T18:00:00") to sets), zone)
        val lines = csv.removePrefix("\uFEFF").trimEnd().split("\r\n")
        assertEquals(WorkoutCsvWriter.HEADER, lines[0])
        assertEquals(listOf("2026-10-05 18:00:00,Push,1h 0m,Bench Press,1,80,5,,,,,,,"), lines.drop(1))
        assertTrue("secret" !in csv)
    }

    @Test
    fun roundTripsThroughTheStrongReader() {
        val sets = listOf(
            SessionSet(exerciseId = 1, exerciseName = "Bench Press", exercisePosition = 0, setIndex = 0, reps = 5, weightKg = 82.5, done = true),
            SessionSet(exerciseId = 1, exerciseName = "Bench Press", exercisePosition = 0, setIndex = 1, reps = 4, weightKg = 82.5, done = true),
            SessionSet(exerciseId = 2, exerciseName = "Plank", exercisePosition = 1, setIndex = 0, reps = 0, durationSec = 60, done = true),
            SessionSet(exerciseId = 3, exerciseName = "Row, Machine", exercisePosition = 2, setIndex = 0, reps = 0, distanceM = 2000.0, durationSec = 480, done = true),
        )
        val csv = WorkoutCsvWriter.write(
            listOf(session(1, "2026-10-05T18:00:00", minutes = 75, title = "Heavy \"A\"", note = "felt good,\nslept well") to sets),
            zone,
        )

        val parsed = CsvWorkoutReader.read(csv)
        assertEquals(CsvWorkoutReader.Source.STRONG, parsed.source)
        assertTrue(parsed.problems.toString(), parsed.problems.isEmpty())
        val w = parsed.workouts.single()
        assertEquals("Heavy \"A\"", w.label)
        assertEquals(ms("2026-10-05T18:00:00"), w.startedAtMs)
        assertEquals(ms("2026-10-05T19:15:00"), w.completedAtMs)
        assertEquals("felt good,\nslept well", w.notes)
        assertEquals(listOf("Bench Press", "Bench Press", "Plank", "Row, Machine"), w.sets.map { it.exerciseName })
        assertEquals(listOf(1, 2, 1, 1), w.sets.map { it.setIndex })
        assertEquals(listOf(5, 4, 0, 0), w.sets.map { it.reps })
        assertEquals(listOf(82.5, 82.5, null, null), w.sets.map { it.weightKg })
        assertEquals(listOf(null, null, 60, 480), w.sets.map { it.durationSec })
        assertEquals(2000.0, w.sets[3].distanceM!!, 0.001)
    }

    @Test
    fun theStrongColumnsKeepTheirOrderAndModifiersComeLast() {
        val strong = "Date,Workout Name,Duration,Exercise Name,Set Order,Weight,Reps,Distance,Seconds,Notes,Workout Notes,Set Type,RPE"
        assertEquals("$strong,Modifiers", WorkoutCsvWriter.HEADER)
    }

    @Test
    fun aDeficitAndAFlatPushUpComeBackAsTwoLifts() {
        val sets = listOf(
            SessionSet(exerciseId = 1, exerciseName = "Push-up", exercisePosition = 0, setIndex = 0, reps = 20, done = true),
            SessionSet(exerciseId = 1, exerciseName = "Push-up", exercisePosition = 1, setIndex = 0, reps = 12, modifiers = "deficit, tempo", done = true),
            SessionSet(exerciseId = 1, exerciseName = "Push-up", exercisePosition = 1, setIndex = 1, reps = 10, modifiers = "deficit, tempo", done = true),
        )
        val csv = WorkoutCsvWriter.write(listOf(session(1, "2026-10-05T18:00:00") to sets), zone)
        val back = CsvWorkoutReader.read(csv).workouts.single().sets
        assertEquals(listOf("", "deficit, tempo", "deficit, tempo"), back.map { it.modifiers })
        assertEquals(listOf("", "deficit", "deficit"), back.map { Variation.key(it.modifiers) })
        // Flat then deficit are one movement in a row; the variation still splits them into two blocks.
        val placements = CsvWorkoutReader.blockPlacements(listOf(1L, 1L, 1L), back.map { Variation.key(it.modifiers) })
        assertEquals(listOf(0, 1, 1), placements.map { it.first })
        assertEquals(listOf(0, 1, 2), placements.map { it.second })
    }

    @Test
    fun aCombinationKeepsItsStringAndItsKeyAcrossTheRoundTrip() {
        val sets = listOf(
            SessionSet(exerciseId = 1, exerciseName = "Push-up", exercisePosition = 0, setIndex = 0, reps = 8, modifiers = "deficit, archer", done = true),
            SessionSet(exerciseId = 1, exerciseName = "Push-up", exercisePosition = 1, setIndex = 0, reps = 9, modifiers = "archer,tempo,deficit", done = true),
        )
        val csv = WorkoutCsvWriter.write(listOf(session(1, "2026-10-05T18:00:00") to sets), zone)
        val back = CsvWorkoutReader.read(csv).workouts.single().sets.map { it.modifiers }
        assertEquals(listOf("deficit, archer", "archer,tempo,deficit"), back)
        assertEquals(setOf("archer,deficit"), back.map { Variation.key(it) }.toSet())
        // Same key, so no new block between them: one variation, two blocks only if the exercise changes.
        assertEquals(listOf(0, 0), CsvWorkoutReader.blockPlacements(listOf(1L, 1L), back.map { Variation.key(it) }).map { it.first })
    }

    @Test
    fun aStrongFileWithoutTheModifiersColumnStillImports() {
        val csv = "Date,Workout Name,Duration,Exercise Name,Set Order,Weight,Reps,Distance,Seconds,Notes\n" +
            "2024-03-01 18:00:00,Push,1h 0m,Bench Press,1,80,5,0,0,\n"
        val set = CsvWorkoutReader.read(csv).workouts.single().sets.single()
        assertEquals("", set.modifiers)
        assertEquals(80.0, set.weightKg!!, 0.0)
    }
}
