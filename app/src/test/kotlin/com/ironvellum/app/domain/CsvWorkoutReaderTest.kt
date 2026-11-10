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
    private fun exercise(metric: ExerciseMetric = ExerciseMetric.REPS, name: String = "Bench Press", weighted: Boolean = true) =
        Exercise(name = name, muscleGroup = MuscleGroup.CORE, isWeighted = weighted, metric = metric)

    @Test
    fun candidatesUseLatestNormalisedLabelRegardlessOfOrder() {
        val parsed = CsvWorkoutReader.read(fixture("strong_synthetic.csv"))
        val workout = parsed.workouts.first()
        val older = workout.copy(label = "  PUSH   day ", startedAtMs = 1)
        val newer = workout.copy(label = "push day", startedAtMs = 2)
        val first = ImportedWorkouts.candidates(parsed.copy(workouts = listOf(older, newer))).single()
        val second = ImportedWorkouts.candidates(parsed.copy(workouts = listOf(newer, older))).single()
        assertEquals(first, second)
        assertEquals("csv:push day", first.key)
        assertEquals(newer, first.workout)
    }

    @Test
    fun candidatesExcludeEmptyAndWarmupOnlySessions() {
        val parsed = CsvWorkoutReader.read(fixture("strong_synthetic.csv"))
        val workout = parsed.workouts.first()
        val empty = workout.copy(label = "Empty", sets = emptyList())
        val warmup = workout.copy(label = "Warmup", sets = listOf(workout.sets.first().copy(setType = "warmup")))
        assertTrue(ImportedWorkouts.candidates(parsed.copy(workouts = listOf(empty, warmup))).isEmpty())
    }

    @Test
    fun draftKeepsRepeatedBlocksActivityAndWarmupWithoutCountingRamps() {
        val rows = listOf(
            SessionSet(exerciseId = 1, exercisePosition = 0, setIndex = 0, reps = 5, kind = SetKind.WARMUP),
            SessionSet(exerciseId = 1, exercisePosition = 0, setIndex = 1, reps = 8, done = true, modifiers = "paused", supersetGroup = 1),
            SessionSet(exerciseId = 2, exercisePosition = 1, setIndex = 0, reps = 0, durationSec = 120, done = true, supersetGroup = 1),
            SessionSet(exerciseId = 1, exercisePosition = 2, setIndex = 0, reps = 6, done = true),
            SessionSet(exerciseId = 3, exercisePosition = 3, setIndex = 0, reps = 10, kind = SetKind.WARMUP),
        )
        val draft = ImportedWorkouts.draft("Day", "", rows) { exercise(if (it == 2L) ExerciseMetric.DURATION else ExerciseMetric.REPS) }!!
        assertEquals(listOf(3L, 1L, 2L, 1L), draft.entries.map { it.exerciseId })
        assertTrue(draft.entries.first().warmup)
        assertEquals(1, draft.entries[1].targetSets)
        assertTrue(draft.entries[1].scheme.warmupRamp)
        assertEquals("paused", draft.entries[1].modifiers)
        assertEquals(2, draft.entries[2].targetReps)
        assertEquals(listOf(null, 1, 1, null), draft.entries.map { it.supersetGroup })
        assertNull(ImportedWorkouts.draft("Warmup", "", listOf(rows.last())) { exercise() })
    }

    @Test
    fun copiedAttemptsUseMedianAndKeepMinimumForBlankFigures() {
        fun target(vararg reps: Int): Int {
            val rows = reps.mapIndexed { index, count ->
                SessionSet(exerciseId = 1, setIndex = index, reps = count, done = true)
            }
            return ImportedWorkouts.draft("Climb", "", rows) { exercise(ExerciseMetric.ATTEMPTS_GRADE) }!!.entries.single().targetReps
        }
        assertEquals(5, target(5))
        assertEquals(4, target(3, 5))
        assertEquals(1, target(0))
        assertEquals(500, target(700))
    }

    @Test
    fun copiedTopAndBackoffUseRepresentableRecordedSummaries() {
        val rows = listOf(
            SessionSet(exerciseId = 1, setIndex = 0, reps = 5, weightKg = 100.0, kind = SetKind.TOP, done = true),
            SessionSet(exerciseId = 1, setIndex = 1, reps = 8, weightKg = 50.0, kind = SetKind.BACKOFF, done = true),
            SessionSet(exerciseId = 1, setIndex = 2, reps = 6, weightKg = 40.0, kind = SetKind.BACKOFF, done = true),
        )
        val entry = ImportedWorkouts.draft("Lift", "", rows) { exercise() }!!.entries.single()
        val scheme = SetScheme.decode(entry.scheme.normalised().encode())
        val plan = SchemePlan.plan(scheme, entry.targetSets, entry.targetReps, entry.targetWeightKg, LiftLoading(stepKg = 2.5))
        assertEquals(listOf(SetKind.TOP, SetKind.BACKOFF, SetKind.BACKOFF), plan.map { it.kind })
        assertEquals(listOf(5, 7, 7), plan.map { it.reps })
        assertEquals(listOf(100.0, 50.0, 50.0), plan.map { it.weightKg })
        val plain = ImportedWorkouts.draft("Plain", "", listOf(rows.first().copy(kind = SetKind.WORKING))) { exercise() }!!
        assertEquals(SchemeType.STRAIGHT, plain.entries.single().scheme.type)
    }

    @Test
    fun copiedBodyweightBackoffsCombineNullAndZeroBeforeChoosingTheMode() {
        fun fixed(name: String, weighted: Boolean, vararg loads: Double?): Double? {
            val rows = listOf(SessionSet(exerciseId = 1, setIndex = 0, reps = 5, kind = SetKind.TOP, done = true)) +
                loads.mapIndexed { index, load ->
                    SessionSet(exerciseId = 1, setIndex = index + 1, reps = 8, weightKg = load, kind = SetKind.BACKOFF, done = true)
                }
            return ImportedWorkouts.draft("Copy", "", rows) { exercise(name = name, weighted = weighted) }!!
                .entries.single().scheme.backoffFixedKg
        }
        assertEquals(0.0, fixed("Pull-up", false, null, 0.0, 10.0)!!, 0.0)
        assertEquals(10.0, fixed("Pull-up", false, null, 10.0)!!, 0.0)
        assertNull(fixed("Pull-up", false, -1.0))
        assertNull(fixed("Pull-up", false, Double.NaN))
        assertNull(fixed("Assisted Pull-up", true, null))
        assertEquals(0.0, fixed("Assisted Pull-up", true, 0.0)!!, 0.0)
    }


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
            parsed.problems.first().message.contains("not a Strong, Hevy or FitNotes export"),
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
