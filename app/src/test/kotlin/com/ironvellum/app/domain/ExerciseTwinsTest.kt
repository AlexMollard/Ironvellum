package com.ironvellum.app.domain

import com.ironvellum.app.data.Seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/** The table of dedicated exercises that are a base plus a modifier or load, held to the real catalogue. */
class ExerciseTwinsTest {

    private val catalogue: Map<String, Exercise> = Seed.exercises.associate {
        it.name.lowercase() to Exercise(
            name = it.name,
            muscleGroup = MuscleGroup.valueOf(it.muscleGroup),
            isWeighted = it.isWeighted,
            metric = ExerciseMetric.valueOf(it.metric),
            category = it.category,
        )
    }

    private fun exercise(name: String): Exercise = catalogue.getValue(name.lowercase())

    @Test
    fun `every table name is a seeded exercise`() {
        val missing = ExerciseTwins.ALL.values.flatMap { twin -> listOf(twin.dedicated) + twin.bases }
            .filter { it.lowercase() !in catalogue }
        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun `every absorbed modifier is a real token the base offers`() {
        val bad = ExerciseTwins.ALL.values.flatMap { twin ->
            twin.bases.flatMap { base ->
                twin.absorbs.flatten().mapNotNull { token ->
                    val real = MovementDifficulty.pricesModifier(token) || token == WEIGHTED_MODIFIER
                    val base = exercise(base)
                    val reachable = if (token == WEIGHTED_MODIFIER) {
                        !base.isWeighted && base.metric in setOf(ExerciseMetric.REPS, ExerciseMetric.HOLD)
                    } else {
                        token in applicableModifiers(base)
                    }
                    if (real && reachable) null else "${twin.dedicated}: $token on ${base.name}"
                }
            }
        }
        assertEquals(emptyList<String>(), bad)
    }

    @Test
    fun `a base with a token has exactly one dedicated exercise`() {
        val targets = ExerciseTwins.ALL.values.flatMap { twin ->
            twin.bases.flatMap { base -> twin.absorbs.flatten().map { (base.lowercase() to it) to twin.dedicated } }
        }.groupBy({ it.first }, { it.second })
        assertEquals(emptyMap<Pair<String, String>, List<String>>(), targets.filterValues { it.size > 1 })
    }

    @Test
    fun `a twin trains the muscles its base does`() {
        val differing = ExerciseTwins.ALL.values.flatMap { twin ->
            val twinPattern = MuscleMap.profile(twin.dedicated)?.pattern
            twin.bases.mapNotNull { base ->
                val basePattern = MuscleMap.profile(base)?.pattern
                if (twinPattern != null && twinPattern == basePattern) null else "${twin.dedicated}: $twinPattern vs $basePattern"
            }
        }
        assertEquals(emptyList<String>(), differing)
    }

    @Test
    fun `a twin no easier than its base counts toward the boards its base does`() {
        val missing = ExerciseTwins.ALL.values
            .filter { twin -> twin.absorbs.flatten().none { MovementDifficulty.modifierFactor(it) < 1.0 } }
            .flatMap { twin ->
                twin.bases.mapNotNull { base ->
                    val lacks = LiftBoards.boardsFor(base) - LiftBoards.boardsFor(twin.dedicated).toSet()
                    if (lacks.isEmpty()) null else "${twin.dedicated} lacks $lacks of $base"
                }
            }
        assertEquals(emptyList<String>(), missing)
        // An easier twin has its own rung and does not stand in for the base's.
        assertTrue(LiftBoards.boardsFor("Incline Push-up").none { it.lift == Lift.PUSH_UP && it.rung == 2 })
    }

    private fun resolve(base: String, modifiers: String) =
        ExerciseTwins.resolve(base, modifiers, !exercise(base).isWeighted) { it.lowercase() in catalogue }

    @Test
    fun `a base with one absorbed modifier is its twin and keeps the others`() {
        val found = resolve("Push-up", "archer, tempo")!!
        assertEquals("Archer Push-up", found.twin.dedicated)
        assertEquals("tempo", found.modifiers)
        assertEquals("Weighted Pull-up", resolve("Pull-up", "weighted")!!.twin.dedicated)
    }

    @Test
    fun `the shape wins over weighted`() {
        val found = resolve("Push-up", "weighted, incline")!!
        assertEquals("Incline Push-up", found.twin.dedicated)
        assertEquals("weighted", found.modifiers)
    }

    @Test
    fun `feet elevated is a decline push-up`() {
        val found = resolve("Push-up", "weighted, deficit, elevated")!!
        assertEquals("Decline Push-up", found.twin.dedicated)
        assertEquals("weighted, deficit", found.modifiers)
        assertEquals(setOf("elevated"), found.absorbed)
    }

    @Test
    fun `a tie between two shapes takes the first in the table`() {
        assertEquals("Archer Push-up", resolve("Push-up", "incline, archer")!!.twin.dedicated)
    }

    @Test
    fun `no twin means no match`() {
        assertNull(resolve("Pistol Squat", "weighted"))
        assertNull(resolve("Pull-up", "tempo"))
        assertNull(resolve("Pull-up", ""))
        assertNull(ExerciseTwins.resolve("Pull-up", "weighted", exists = { false }))
        // A base that is itself loaded by nature never carries the tag as a modifier.
        assertNull(ExerciseTwins.resolve("Pull-up", "weighted", bodyweightBase = false))
    }

    @Test
    fun `a load with no tag counts as weighted, an assisted one does not`() {
        assertEquals("weighted", ExerciseTwins.effectiveModifiers("", 18.0, true))
        assertEquals("", ExerciseTwins.effectiveModifiers("", 0.0, true))
        assertEquals("assisted", ExerciseTwins.effectiveModifiers("assisted", 20.0, true))
        assertEquals("", ExerciseTwins.effectiveModifiers("", 60.0, false))
    }

    @Test
    fun `adding a modifier steers, a standing one does not, a kept one stays quiet`() {
        val exists = { name: String -> name.lowercase() in catalogue }
        val prompt = ExerciseTwins.steer("Pull-up", "", "weighted", true, emptySet(), exists)
        assertNotNull(prompt)
        assertEquals("Weighted Pull-up", prompt!!.twin.dedicated)
        assertNull(ExerciseTwins.steer("Pull-up", "weighted", "weighted, tempo", true, emptySet(), exists))
        assertNull(ExerciseTwins.steer("Pull-up", "", "weighted", true, setOf(prompt.keepKey("Pull-up")), exists))
        // Keeping weighted does not keep archer.
        assertNotNull(ExerciseTwins.steer("Pull-up", "weighted", "weighted, archer", true, setOf(prompt.keepKey("Pull-up")), exists))
        assertTrue(prompt.keepKey("Pull-up") == "pull-up|weighted")
    }

    @Test
    fun `a twin is never priced below its base with the modifier`() {
        // Prices are whole tiers (a doubling each), so "below" means more than half a tier under the product.
        // An easier twin (incline) is a rung of the skill tree on its own and is priced as that rung.
        val under = ExerciseTwins.ALL.values.flatMap { twin ->
            twin.bases.flatMap { base ->
                twin.absorbs.mapNotNull { tokens ->
                    val factor = MovementDifficulty.modifierFactor(tokens.joinToString(", "))
                    val floor = MovementDifficulty.intensity(base) * factor
                    val priced = MovementDifficulty.intensity(twin.dedicated)
                    if (factor < 1.0 || priced * sqrt(2.0) + 1e-9 >= floor) null else "${twin.dedicated} $priced < $base x $tokens $floor"
                }
            }
        }
        assertEquals(emptyList<String>(), under)
    }
}
