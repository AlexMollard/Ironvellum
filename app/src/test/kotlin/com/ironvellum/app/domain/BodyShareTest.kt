package com.ironvellum.app.domain

import com.ironvellum.app.data.Seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The body share and the total-load 1RM it feeds: the owner's real numbers, the whole catalogue, the orderings records rely on. */
class BodyShareTest {

    private val catalogue: List<Exercise> = Seed.exercises.map { e ->
        Exercise(
            name = e.name,
            muscleGroup = MuscleGroup.entries.firstOrNull { it.name == e.muscleGroup } ?: MuscleGroup.CORE,
            isWeighted = e.isWeighted,
            metric = ExerciseMetric.valueOf(e.metric),
            category = e.category,
        )
    }

    private fun named(name: String) = catalogue.first { it.name == name }

    private val pushUp get() = named("Push-up")

    // The owner: 79.8 kg, push-ups with +18 kg for 12 and with +55 kg for 3.
    private val body = 79.8

    private fun pushUpE1rm(addedKg: Double, reps: Int) =
        BodyShare.e1rmAddedKg(addedKg, reps, BodyShare.of(pushUp), body)

    @Test
    fun `the owner's weighted push-ups are judged on the total load not the plates`() {
        val share = BodyShare.of(pushUp)
        assertEquals(0.65, share, 1e-9)
        // (0.65 x 79.8 + 18) x (1 + 12/30) - 0.65 x 79.8 = 45.95: "+46 kg", not the old 25.2.
        assertEquals(45.95, pushUpE1rm(18.0, 12), 0.01)
        // +55 x 3 : 106.87 x 1.1 - 51.87 = 65.69
        assertEquals(65.69, pushUpE1rm(55.0, 3), 0.01)
        assertTrue("+55 x 3 must beat +18 x 12", pushUpE1rm(55.0, 3) > pushUpE1rm(18.0, 12))
        // Both sit above their plates and in a plausible range for a lifter with a 55 kg push-up.
        assertTrue(pushUpE1rm(18.0, 12) in 40.0..55.0)
        assertTrue(pushUpE1rm(55.0, 3) in 55.0..80.0)
    }

    @Test
    fun `without a body weight the estimate is the old added-load figure`() {
        assertEquals(ProgramRules.epley(18.0, 12), BodyShare.e1rmAddedKg(18.0, 12, 0.65, null), 1e-9)
        assertEquals(ProgramRules.epley(18.0, 12), BodyShare.e1rmAddedKg(18.0, 12, 0.65, 0.0), 1e-9)
        // A barbell lift has no share: the marked load is the whole load whoever lifts it.
        assertEquals(ProgramRules.epley(100.0, 5), BodyShare.e1rmAddedKg(100.0, 5, 0.0, body), 1e-9)
    }

    @Test
    fun `heavier at fewer reps and lighter at more reps order by the total load`() {
        assertTrue(pushUpE1rm(50.0, 2) > pushUpE1rm(10.0, 12))
        // More reps at the same plate always wins; more plate at the same reps always wins.
        assertTrue(pushUpE1rm(20.0, 10) > pushUpE1rm(20.0, 8))
        assertTrue(pushUpE1rm(25.0, 8) > pushUpE1rm(20.0, 8))
        // The plates alone put +14 x 3 (15.4) above +10 x 12 (14.0): the old estimate would have called the lighter
        // set a lighter lift. Counting the body, twelve reps with 10 kg on a 52 kg push-up is the bigger effort.
        assertTrue(ProgramRules.epley(14.0, 3) > ProgramRules.epley(10.0, 12))
        assertTrue(pushUpE1rm(10.0, 12) > pushUpE1rm(14.0, 3))
    }

    @Test
    fun `incline and elevated move the push-up share and a deficit does not`() {
        val plain = BodyShare.of("Push-up")
        assertTrue(BodyShare.of("Push-up", "incline") < plain)
        assertTrue(BodyShare.of("Push-up", "elevated") > plain)
        assertTrue(BodyShare.of("Push-up", "decline") > plain)
        assertEquals(plain, BodyShare.of("Push-up", "deficit"), 1e-9)
        assertEquals(plain, BodyShare.of("Push-up", "weighted"), 1e-9)
        // Hands up and feet up cancel, as they do in the muscle profile.
        assertEquals(plain, BodyShare.of("Push-up", "incline, elevated"), 1e-9)
        // A harder variation at the same load and reps never scores lower.
        fun e1rm(modifiers: String) = BodyShare.e1rmAddedKg(20.0, 8, BodyShare.of(pushUp, modifiers), body)
        assertTrue(e1rm("incline") < e1rm("") && e1rm("") < e1rm("deficit, elevated"))
    }

    @Test
    fun `the anchors are the documented values`() {
        assertEquals(0.49, BodyShare.of("Knee Push-up"), 1e-9)
        assertEquals(1.0, BodyShare.of("Pull-up"), 1e-9)
        assertEquals(1.0, BodyShare.of("Chin-up"), 1e-9)
        assertEquals(1.0, BodyShare.of("Dip"), 1e-9)
        assertEquals(0.5, BodyShare.of("Inverted Row"), 1e-9)
        assertEquals(0.65, BodyShare.of("Inverted Row", "elevated"), 1e-9)
        assertEquals(0.65, BodyShare.of("Australian Pull-up", "elevated"), 1e-9)
        assertEquals(0.94, BodyShare.of("Pistol Squat"), 1e-9)
        assertEquals(0.8, BodyShare.of("Forward Lunge"), 1e-9)
        assertEquals(1.0, BodyShare.of("One-Arm Push-up"), 1e-9)
    }

    @Test
    fun `an external-load or assisted set has no share`() {
        assertEquals(0.0, BodyShare.of(named("Bench Press")), 0.0)
        assertEquals(0.0, BodyShare.of(named("Back Squat")), 0.0)
        assertEquals(0.0, BodyShare.of(named("Lat Pulldown")), 0.0)
        assertEquals(0.0, BodyShare.of(named("Pull-up"), "assisted"), 0.0)
        assertEquals(1.0, BodyShare.of(named("Pull-up")), 1e-9)
        assertEquals(0.65, BodyShare.of(named("Weighted Push-up")), 1e-9)
    }

    @Test
    fun `free-standing single-leg work counts the body even when catalogued as weighted`() {
        // The owner loads split squats with a vest: the working leg moves the body too.
        val bss = named("Bulgarian Split Squat")
        assertTrue(BodyShare.addsToBody(bss))
        assertEquals(0.85, BodyShare.of(bss), 1e-9)
        assertTrue(BodyShare.addsToBody(named("Dumbbell Lunge")))
        assertTrue(BodyShare.addsToBody(named("Dumbbell Step-Up")))
        assertTrue(!BodyShare.addsToBody(named("Barbell Lunge")))
        assertEquals(0.0, BodyShare.of(named("Barbell Lunge")), 0.0)
        assertEquals(0.0, BodyShare.of(named("Smith Machine Bulgarian Split Squat")), 0.0)
    }

    @Test
    fun `every bodyweight movement in the catalogue has a share between 0_1 and 1_0`() {
        val bad = ArrayList<String>()
        var counted = 0
        for (e in catalogue) {
            val adds = BodyShare.addsToBody(e)
            val strength = e.metric.isStrength
            val share = BodyShare.of(e)
            if (adds && strength) {
                counted++
                if (share < 0.1 - 1e-9 || share > 1.0 + 1e-9) bad += "${e.name}: $share"
                // Each modifier keeps it inside the band too.
                for (m in listOf("incline", "decline", "deficit", "elevated", "incline, deficit", "decline, elevated")) {
                    val s = BodyShare.of(e, m)
                    if (s < 0.1 - 1e-9 || s > 1.0 + 1e-9) bad += "${e.name} [$m]: $s"
                }
            } else if (!adds) {
                if (share != 0.0) bad += "${e.name}: external load but share $share"
            }
        }
        assertTrue("shares out of band:\n" + bad.joinToString("\n"), bad.isEmpty())
        assertTrue("the walk saw only $counted bodyweight movements", counted > 100)
    }

    @Test
    fun `the share table never misses a movement with a pattern`() {
        // Every bodyweight strength movement is derived from a rule, its pattern or its group: never a silent 1.0.
        val groupOnly = catalogue.filter { BodyShare.addsToBody(it) && it.metric.isStrength }
            .filter { BodyShare.derive(it.name, it.muscleGroup).basis == BodyShare.Basis.GROUP }
            .map { it.name }
        assertTrue("movements with no profile and no rule: $groupOnly", groupOnly.isEmpty())
    }

    @Test
    fun `an added-load e1rm counts reps past twelve as twelve`() {
        assertEquals(BodyShare.e1rmAddedKg(10.0, 12, 0.6, 80.0), BodyShare.e1rmAddedKg(10.0, 14, 0.6, 80.0), 1e-9)
        assertEquals(BodyShare.e1rmAddedKg(10.0, 12, 0.0, null), BodyShare.e1rmAddedKg(10.0, 30, 0.0, null), 1e-9)
        assertTrue(BodyShare.e1rmAddedKg(7.5, 14, 0.0, null) > BodyShare.e1rmAddedKg(5.0, 12, 0.0, null))
    }
}
