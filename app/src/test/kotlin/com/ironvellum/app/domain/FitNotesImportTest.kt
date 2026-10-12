package com.ironvellum.app.domain

import com.ironvellum.app.data.Seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * FitNotes import. The samples are SYNTHETIC but copy the shape of real exports
 * (header, `kgs`/`lbs` per row, `0:00:30` times, `""` for an empty comment,
 * blank cells for fields a movement does not use); see [CsvWorkoutReader].
 */
class FitNotesImportTest {

    private fun csv(vararg lines: String) = lines.joinToString("\n")

    private fun zoneMs(s: String): Long =
        LocalDateTime.parse(s).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val header = "Date,Exercise,Category,Weight,Weight Unit,Reps,Distance,Distance Unit,Time,Comment"

    private val kgs = csv(
        header,
        "2026-08-04,Flat Barbell Bench Press,Chest,60.0,kgs,8,,,,\"\"",
        "2026-08-04,Flat Barbell Bench Press,Chest,67.5,kgs,5,,,,\"felt heavy, bar slow\"",
        "2026-08-04,Plank,Abs,0.0,kgs,,,,0:00:45,\"\"",
        "2026-08-05,Running (Outdoor),Cardio,,,,5.0,km,0:25:30,\"easy pace\"",
        "2026-08-05,Cycling,Cardio,,,,3.0,mi,0:12:00,\"\"",
        "2026-08-05,Barbell Squat,Legs,100.0,kgs,5,,,,\"\"",
    )

    @Test
    fun kgExportBecomesOneTrialPerDateWithSetsInFileOrder() {
        val parsed = CsvWorkoutReader.read(kgs)
        assertEquals(CsvWorkoutReader.Source.FITNOTES, parsed.source)
        assertNull(parsed.units)
        assertTrue(parsed.problems.isEmpty())
        assertEquals(2, parsed.workouts.size)
        assertEquals(6, parsed.totalSets)

        val first = parsed.workouts[0]
        assertEquals(zoneMs("2026-08-04T12:00:00"), first.startedAtMs)
        assertNull(first.completedAtMs)
        assertEquals(
            listOf("Flat Barbell Bench Press", "Flat Barbell Bench Press", "Plank"),
            first.sets.map { it.exerciseName },
        )
        assertEquals(listOf(1, 2, 1), first.sets.map { it.setIndex })
        assertEquals(60.0, first.sets[0].weightKg!!, 0.0001)
        assertEquals(8, first.sets[0].reps)
        assertEquals(67.5, first.sets[1].weightKg!!, 0.0001)
        // A comment rides its set; an empty one stays empty.
        assertEquals("", first.sets[0].notes)
        assertEquals("felt heavy, bar slow", first.sets[1].notes)
        assertEquals(zoneMs("2026-08-05T12:00:00"), parsed.workouts[1].startedAtMs)
    }

    @Test
    fun cardioRowsKeepDistanceInMetresAndTimeInSeconds() {
        val sets = CsvWorkoutReader.read(kgs).workouts[1].sets
        val run = sets[0]
        assertEquals(5000.0, run.distanceM!!, 0.001)
        assertEquals(1530, run.durationSec)
        assertNull(run.weightKg)
        assertEquals(0, run.reps)
        assertEquals("easy pace", run.notes)
        assertEquals(3 * 1609.344, sets[1].distanceM!!, 0.001)
        assertEquals(720, sets[1].durationSec)
        // A hold: time and no reps.
        val plank = CsvWorkoutReader.read(kgs).workouts[0].sets[2]
        assertEquals(45, plank.durationSec)
        assertEquals(0, plank.reps)
        assertNull(plank.distanceM)
    }

    @Test
    fun poundsRowsConvertToKilograms() {
        val parsed = CsvWorkoutReader.read(
            csv(
                header,
                "2022-09-14,Flat Barbell Bench Press,Chest,45.0,lbs,10,,,,\"\"",
                "2022-09-14,Flat Barbell Bench Press,Chest,135.0,lbs,5,,,,\"\"",
            ),
        )
        assertEquals(CsvWorkoutReader.Source.FITNOTES, parsed.source)
        assertEquals(45.0 * CsvWorkoutReader.LB_TO_KG, parsed.workouts.single().sets[0].weightKg!!, 0.001)
        assertEquals(135.0 * CsvWorkoutReader.LB_TO_KG, parsed.workouts.single().sets[1].weightKg!!, 0.001)
    }

    @Test
    fun theUnitIsReadPerRowSoMixedExportsStayCorrect() {
        val sets = CsvWorkoutReader.read(
            csv(
                header,
                "2026-08-04,Barbell Squat,Legs,100.0,kgs,5,,,,",
                "2026-08-04,Lat Pulldown,Back,100.0,lbs,10,,,,",
            ),
        ).workouts.single().sets
        assertEquals(100.0, sets[0].weightKg!!, 0.001)
        assertEquals(100.0 * CsvWorkoutReader.LB_TO_KG, sets[1].weightKg!!, 0.001)
    }

    @Test
    fun theOlderHeaderCarriesTheUnitAndAnExportWithoutCommentsStillReads() {
        val old = CsvWorkoutReader.read(
            csv(
                "Date,Exercise,Category,Weight (lbs),Reps,Distance,Distance Unit,Time,Comment",
                "2015-06-07,Seated Rope Cable Face Pull,Posterior Deltoid,50.0,10,,,,\"\"",
            ),
        )
        assertEquals(CsvWorkoutReader.Source.FITNOTES, old.source)
        assertEquals(50.0 * CsvWorkoutReader.LB_TO_KG, old.workouts.single().sets.single().weightKg!!, 0.001)

        // "Include comments in export" unticked: no Comment column at all.
        val noComments = CsvWorkoutReader.read(
            csv(
                "Date,Exercise,Category,Weight,Weight Unit,Reps,Distance,Distance Unit,Time",
                "2019-07-01,Flat Barbell Bench Press,Chest,40.0,kgs,8,,,",
            ),
        )
        assertEquals(40.0, noComments.workouts.single().sets.single().weightKg!!, 0.001)
        assertEquals("", noComments.workouts.single().sets.single().notes)
    }

    @Test
    fun theIosHeaderWithPairedWeightColumnsAndNotesReads() {
        val sets = CsvWorkoutReader.read(
            csv(
                "Date,Exercise,Category,Weight (kg),Weight (lbs),Reps,Distance,Distance Unit,Time,Notes,Kind",
                "2024-01-30,Squat,Legs,100,,5,,,,solid,wr",
                "2024-01-30,Bench,Chest,,135,5,,,,,wr",
                "2024-01-30,Sprint,Cardio,,,,200,yd,00:30,,dt",
            ),
        ).workouts.single().sets
        assertEquals(100.0, sets[0].weightKg!!, 0.001)
        assertEquals("solid", sets[0].notes)
        assertEquals(135.0 * CsvWorkoutReader.LB_TO_KG, sets[1].weightKg!!, 0.001)
        assertEquals(200 * 0.9144, sets[2].distanceM!!, 0.001)
        assertEquals(30, sets[2].durationSec)
    }

    @Test
    fun unreadableRowsAndUnitsAreReportedNotGuessed() {
        val parsed = CsvWorkoutReader.read(
            csv(
                header,
                "not a date,Deadlift,Legs,100.0,kgs,5,,,,",
                "2026-08-04,Deadlift,Legs,100.0,st,5,,,,",
                "2026-08-04,Walking,Cardio,,,,2.0,furlong,0:10:00,",
            ),
        )
        assertEquals(1, parsed.workouts.size)
        val sets = parsed.workouts.single().sets
        assertNull(sets[0].weightKg)
        assertNull(sets[1].distanceM)
        assertEquals(600, sets[1].durationSec)
        assertTrue(parsed.problems.any { it.message.contains("unreadable date") })
        assertTrue(parsed.problems.any { it.message.contains("2 weight or distance values") })
    }

    @Test
    fun aBomAndWindowsLineEndingsDoNotHideTheHeader() {
        val parsed = CsvWorkoutReader.read("﻿" + kgs.replace("\n", "\r\n"))
        assertEquals(CsvWorkoutReader.Source.FITNOTES, parsed.source)
        assertEquals(6, parsed.totalSets)
    }

    @Test
    fun headerDetectionDoesNotMisfireOnStrongHevyOrOtherFitNotesFiles() {
        fun fixture(name: String) = javaClass.classLoader.getResourceAsStream("import/$name")!!
            .readBytes().toString(Charsets.UTF_8)
        assertEquals(CsvWorkoutReader.Source.STRONG, CsvWorkoutReader.read(fixture("strong_synthetic.csv")).source)
        assertEquals(CsvWorkoutReader.Source.HEVY, CsvWorkoutReader.read(fixture("hevy_synthetic.csv")).source)
        // The FitNotes Body Tracker export also starts with Date but is not a workout log.
        val body = CsvWorkoutReader.read(
            csv("Date,Time,Measurement,Value,Unit,Comment", "2024-03-20,08:00,Body Weight,75.5,kg,"),
        )
        assertTrue(body.workouts.isEmpty())
        assertTrue(body.problems.first().message.contains("not a Strong, Hevy or FitNotes export"))
        // Strong's own header mentions Exercise Name and Weight but never a bare Exercise column.
        assertEquals(
            CsvWorkoutReader.Source.STRONG,
            CsvWorkoutReader.read(
                csv(
                    "Date,Workout Name,Duration,Exercise Name,Set Order,Weight,Reps,Distance,Seconds,Notes,Workout Notes,RPE",
                    "2024-03-01 18:00:00,Push,1h,Bench Press (Barbell),1,60,8,0,0,,,",
                ),
            ).source,
        )
    }

    @Test
    fun timeParsingTakesClockAndSecondForms() {
        assertEquals(30, CsvWorkoutReader.parseFitNotesTime("0:00:30"))
        assertEquals(3600 + 61, CsvWorkoutReader.parseFitNotesTime("1:01:01"))
        assertEquals(1530, CsvWorkoutReader.parseFitNotesTime("25:30"))
        assertEquals(90, CsvWorkoutReader.parseFitNotesTime("90"))
        assertNull(CsvWorkoutReader.parseFitNotesTime(""))
        assertNull(CsvWorkoutReader.parseFitNotesTime("0:00:00"))
        assertNull(CsvWorkoutReader.parseFitNotesTime("abc"))
    }

    @Test
    fun fitNotesDefaultNamesResolveToRealCatalogueMovements() {
        val catalogue = Seed.exercises.map { it.name }
        val expected = mapOf(
            "Flat Barbell Bench Press" to "Bench Press",
            "Flat Dumbbell Bench Press" to "Dumbbell Bench Press",
            "Incline Barbell Bench Press" to "Incline Bench Press",
            "Incline Dumbbell Bench Press" to "Incline Dumbbell Press",
            "Barbell Squat" to "Back Squat",
            "Barbell Front Squat" to "Front Squat",
            "Deadlift" to "Deadlift",
            "Overhead Press" to "Overhead Press",
            "Pull Up" to "Pull-up",
            "Chin Up" to "Chin-up",
            "Seated Dumbbell Press" to "Dumbbell Shoulder Press",
            "Lateral Dumbbell Raise" to "Lateral Raise",
            "Cable Face Pull" to "Face Pull",
            // Its own movement since the exercise library landed, so no alias is needed.
            "Barbell Curl" to "Barbell Curl",
            "Dumbbell Hammer Curl" to "Hammer Curl",
            "Cable Overhead Triceps Extension" to "Overhead Cable Extension",
            "Parallel Bar Triceps Dip" to "Dip",
            "Leg Extension Machine" to "Leg Extension",
            "Seated Leg Curl Machine" to "Seated Leg Curl",
            "Standing Calf Raise Machine" to "Standing Calf Raise",
            "Seated Machine Fly" to "Pec Deck",
            "Hammer Strength Row" to "Machine Row",
            "Running (Outdoor)" to "Running",
            "Running (Treadmill)" to "Treadmill",
            "Rowing Machine" to "Rowing",
            "Stationary Bike" to "Indoor Cycling",
            "Elliptical Trainer" to "Elliptical",
            "Cycling" to "Cycling",
        )
        expected.forEach { (fitNotes, ours) ->
            assertEquals(fitNotes, ours, ImportAliases.resolve(fitNotes, catalogue))
        }
        assertNull(ImportAliases.resolve("Log Press", catalogue))
    }
}
