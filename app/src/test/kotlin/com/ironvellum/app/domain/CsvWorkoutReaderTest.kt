package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Fixture-based parser tests. The CSVs under src/test/resources/import are
 * SYNTHETIC, hand-built from the documented formats (Strong's exporter header
 * verified against a real export in github.com/AlexandrosKyriakakis/StrongAppAnalytics,
 * Hevy's against github.com/matanabudy/workout-data-sync's sample export) —
 * they are not anyone's real training data.
 */
class CsvWorkoutReaderTest {

    private fun fixture(name: String): String =
        javaClass.classLoader.getResourceAsStream("import/$name")!!
            .readBytes().toString(Charsets.UTF_8)

    private fun zoneMs(s: String): Long =
        LocalDateTime.parse(s).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun strongFixtureParsesToExpectedWorkoutAndSetCounts() {
        val parsed = CsvWorkoutReader.read(fixture("strong_synthetic.csv"))
        assertEquals(CsvWorkoutReader.Source.STRONG, parsed.source)
        assertEquals(2, parsed.workouts.size)
        assertEquals(4, parsed.totalSets)
        val push = parsed.workouts.first()
        assertEquals("Evening Push", push.label)
        assertEquals(3, push.sets.size)
        assertEquals(zoneMs("2024-03-01T18:00:00"), push.startedAtMs)
        // Duration "1h 10m" lands as completedAt = start + 4200 s.
        assertEquals(zoneMs("2024-03-01T18:00:00") + 4_200_000L, push.completedAtMs)
    }

    @Test
    fun strongQuotedFieldsSurviveCommasAndEmbeddedNewlines() {
        val parsed = CsvWorkoutReader.read(fixture("strong_synthetic.csv"))
        val push = parsed.workouts.first()
        // Set 2's note spans a raw newline inside quotes; set 1's RPE is 7.
        assertEquals(7.0, push.sets[0].rpe!!, 0.0001)
        assertEquals("felt strong\nacross two lines", push.sets[1].notes)
        assertEquals(8.5, push.sets[1].rpe!!, 0.0001)
    }

    @Test
    fun aFileSavedWithWindowsLineEndingsKeepsNotesFreeOfCarriageReturns() {
        val rows = CsvWorkoutReader.parseCsv("a,b\r\n1,\"two\r\nlines\"\r\n")
        assertEquals(listOf(listOf("a", "b"), listOf("1", "two\nlines")), rows)
    }

    @Test
    fun aStrayQuoteMidFieldIsLiteralAndDoesNotSwallowLaterRows() {
        val rows = CsvWorkoutReader.parseCsv("a,b\n1,9\"\n2,3\n")
        assertEquals(listOf(listOf("a", "b"), listOf("1", "9\""), listOf("2", "3")), rows)
    }

    @Test
    fun strongWeightsConvertWhenTheFileWasExportedInPounds() {
        val kg = CsvWorkoutReader.read(fixture("strong_synthetic.csv"), strongWeightIsLbs = false)
        assertEquals(60.0, kg.workouts.first().sets[0].weightKg!!, 0.001)
        val lbs = CsvWorkoutReader.read(fixture("strong_synthetic.csv"), strongWeightIsLbs = true)
        assertEquals(60.0 * 0.45359237, lbs.workouts.first().sets[0].weightKg!!, 0.001)
        assertEquals(135.0 * 0.45359237, lbs.workouts.first().sets[1].weightKg!!, 0.001)
    }

    @Test
    fun hevyFixtureParsesTimestampsUnitsAndSetTypes() {
        val parsed = CsvWorkoutReader.read(fixture("hevy_synthetic.csv"))
        assertEquals(CsvWorkoutReader.Source.HEVY, parsed.source)
        assertNull(parsed.units)
        assertEquals(1, parsed.workouts.size)
        val workout = parsed.workouts.single()
        assertEquals(4, workout.sets.size)
        // "22 Dec 2025, 08:00" with the comma inside the quotes.
        assertEquals(zoneMs("2025-12-22T08:00:00"), workout.startedAtMs)
        assertEquals(zoneMs("2025-12-22T08:37:00"), workout.completedAtMs)
        assertEquals("easy session", workout.notes)
        // kg preferred, untouched.
        assertEquals(21.0, workout.sets[0].weightKg!!, 0.001)
        assertEquals(8.5, workout.sets[0].rpe!!, 0.0001)
        // 5 km becomes 5000 m; 1800 s stays.
        assertEquals(5000.0, workout.sets[2].distanceM!!, 0.001)
        assertEquals(1800, workout.sets[2].durationSec)
        // Warmup and dropset ride through for mergeImported to act on.
        assertEquals("warmup", workout.sets[1].setType)
        assertEquals("dropset", workout.sets[3].setType)
    }

    @Test
    fun hevyPoundColumnsConvertToKilograms() {
        val csv = listOf(
            """"title","start_time","end_time","description","exercise_title","superset_id","exercise_notes","set_index","set_type","weight_lbs","reps","distance_miles","duration_seconds","rpe"""",
            """"W","01 Jan 2025, 10:00","01 Jan 2025, 10:30","","Bench Press (Barbell)",,"",0,"normal",100,5,0,0,8"""",
        ).joinToString("\n")
        val parsed = CsvWorkoutReader.read(csv)
        assertEquals(100.0 * 0.45359237, parsed.workouts.single().sets.single().weightKg!!, 0.001)
    }

    @Test
    fun unrelatedCsvIsRejectedWithAReadableProblem() {
        val parsed = CsvWorkoutReader.read(fixture("unrelated.csv"))
        assertTrue(parsed.workouts.isEmpty())
        assertTrue(parsed.problems.isNotEmpty())
        assertTrue(
            parsed.problems.first().message.contains("not a Strong or Hevy export"),
        )
    }

    @Test
    fun strongDurationParserReadsHoursMinutesAndSeconds() {
        assertEquals(4200L, CsvWorkoutReader.parseStrongDuration("1h 10m"))
        assertEquals(3960L, CsvWorkoutReader.parseStrongDuration("1h 6m"))
        assertEquals(2700L, CsvWorkoutReader.parseStrongDuration("45m"))
        assertNull(CsvWorkoutReader.parseStrongDuration(""))
    }
}
