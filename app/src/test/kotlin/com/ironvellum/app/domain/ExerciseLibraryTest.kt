package com.ironvellum.app.domain

import com.ironvellum.app.data.Seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The library's growth of the catalogue: how many movements, and that each one is complete in every table. */
class ExerciseLibraryTest {

    @Test
    fun `the catalogue holds at least 500 movements, all with steps, and most of the lifting ones are drawn`() {
        assertTrue("catalogue size ${Seed.exercises.size}", Seed.exercises.size >= 500)
        val unguided = Seed.exercises.map { it.name }.filter { ExerciseGuides.forName(it) == null }
        assertEquals("movements without steps", emptyList<String>(), unguided)
        val drawn = Seed.exercises.count { ExerciseArtIndex.forName(it.name) != null }
        // Upstream draws 497 distinct records; after merging duplicates 448 catalogue movements carry a drawing.
        assertTrue("drawn movements: $drawn", drawn >= 440)
    }

    @Test
    fun `every library movement is seeded exactly once with its declared row`() {
        ExerciseLibrary.rows.forEach { row ->
            val seeded = Seed.exercises.filter { it.name == row.name }
            assertEquals("${row.name} seeded ${seeded.size} times", 1, seeded.size)
            assertEquals(row.group.name, seeded.single().muscleGroup)
            assertEquals(row.metric.name, seeded.single().metric)
            assertEquals(row.weighted, seeded.single().isWeighted)
            assertEquals(row.category, seeded.single().category)
        }
        assertTrue(ExerciseLibrary.rows.size >= 300)
    }

    @Test
    fun `an existing install gains the library by name and keeps what it has`() {
        // Repository.ensureSeeded inserts the Seed rows whose name is not already stored: an old catalogue (what shipped
        // before the library) is topped up with exactly the library rows, and nothing it holds is replaced.
        val old = Seed.exercises.filterNot { ExerciseLibrary.isLibraryOnly(it.name) }.map { it.name }.toSet()
        val gained = Seed.exercises.filterNot { it.name in old }.map { it.name }
        assertEquals(ExerciseLibrary.rows.map { it.name }.sorted(), gained.sorted())
        assertTrue(old.size >= 256)
    }

    @Test
    fun `every library movement has a guide, a profile, a tier and a metric the scoring understands`() {
        val bad = ExerciseLibrary.rows.flatMap { row ->
            buildList {
                if (ExerciseGuides.forName(row.name) == null) add("${row.name}: no guide")
                if (MuscleMap.profile(row.name) == null) add("${row.name}: no profile")
                if (!MovementDifficulty.isClassified(row.name)) add("${row.name}: not classified")
                if ((row.metric == ExerciseMetric.HOLD) != MovementDifficulty.isHoldByName(row.name)) add("${row.name}: hold flag")
                if (row.loadFactor != MovementDifficulty.loadFactor(row.name)) add("${row.name}: load factor")
            }
        }
        assertEquals(emptyList<String>(), bad)
    }

    @Test
    fun `library movements are never prescribed by the generator`() {
        val pool = Seed.exercises.map {
            Exercise(
                name = it.name,
                muscleGroup = MuscleGroup.valueOf(it.muscleGroup),
                isWeighted = it.isWeighted,
                metric = ExerciseMetric.valueOf(it.metric),
                category = it.category,
            )
        }
        for (focus in TrainingFocus.entries) {
            val offered = ProgramGenerator.eligible(pool, Equipment.FULL_GYM, focus).filter { ExerciseLibrary.isLibraryOnly(it.name) }
            assertEquals("library movements offered for $focus", emptyList<Exercise>(), offered)
        }
    }
}
