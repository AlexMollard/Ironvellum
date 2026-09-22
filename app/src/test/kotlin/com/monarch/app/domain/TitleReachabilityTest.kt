package com.monarch.app.domain

import com.monarch.app.data.Seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A title whose threshold is bounded by the size of a shipped catalogue must
 * never exceed that catalogue: Fifty Paths once asked for 50 distinct
 * activities against a 43-activity catalogue, could never be earned, and
 * padded the codex denominator forever. These tests derive the catalogue size
 * from the seed itself, so either side moving breaks the build here first.
 */
class TitleReachabilityTest {

    /** Activity movements are exactly the catalogue rows with a non-strength metric. */
    private val catalogueActivities: Int =
        Seed.exercises.count { !ExerciseMetric.valueOf(it.metric).isStrength }

    @Test
    fun `every distinct-activities threshold fits the activity catalogue`() {
        val thresholds = Titles.ALL
            .map { it.rule }
            .filterIsInstance<TitleRule.DistinctActivities>()
            .map { it.count }
        assertTrue("no DistinctActivities titles found", thresholds.isNotEmpty())
        val tooBig = thresholds.filter { it > catalogueActivities }
        assertTrue(
            "DistinctActivities thresholds $tooBig exceed the $catalogueActivities " +
                "activities the catalogue ships — unearnable deeds again",
            tooBig.isEmpty(),
        )
    }

    @Test
    fun `every skills-mastered threshold fits the skill tree`() {
        val thresholds = Titles.ALL
            .map { it.rule }
            .filterIsInstance<TitleRule.SkillsMastered>()
            .map { it.count }
        assertTrue("no SkillsMastered titles found", thresholds.isNotEmpty())
        val tooBig = thresholds.filter { it > Skills.ALL.size }
        assertTrue(
            "SkillsMastered thresholds $tooBig exceed the ${Skills.ALL.size} " +
                "skills the tree ships — unearnable deeds again",
            tooBig.isEmpty(),
        )
    }

    /**
     * The codex draws progress as "current of target". If an unearned deed
     * can report current == target the line says you are done when you are
     * not, which is a worse lie than reading a kilometre short — so progress
     * floors, and this pins that it can never reach the target unearned.
     */
    @Test
    fun `an unearned distance deed never reports itself complete`() {
        listOf(4.9, 0.1, 4.999).forEach { km ->
            val ledger = Titles.Ledger(totalXp = 0, workouts = 0, sets = 0, reps = 0, bestRunKm = km)
            val rule = TitleRule.LongestRun(5.0)
            assertFalse("$km km must not satisfy a 5 km deed", Titles.satisfied(rule, ledger))
            val progress = Titles.progress(rule, ledger)
            assertTrue(
                "$km km reported ${progress.current} of ${progress.target} on an unearned deed",
                progress.current < progress.target,
            )
        }
        // Earned, and only then, may the two meet.
        val done = Titles.Ledger(totalXp = 0, workouts = 0, sets = 0, reps = 0, bestRunKm = 5.0)
        val rule = TitleRule.LongestRun(5.0)
        assertTrue(Titles.satisfied(rule, done))
        assertEquals(Titles.progress(rule, done).target, Titles.progress(rule, done).current)
    }

    // ---- strength milestones ----

    private fun liftRules() = Titles.ALL.map { it.rule }.filterIsInstance<TitleRule.LiftMultiple>()
    private fun repRules() = Titles.ALL.map { it.rule }.filterIsInstance<TitleRule.SessionReps>()

    /** Catalogue names, normalised the way the ledger keys them. */
    private val catalogueNames: Set<String> =
        Seed.exercises.map { Titles.normaliseName(it.name) }.toSet()

    /**
     * The load/rep deeds name exercises literally (domain cannot read Seed).
     * A typo there would make the deed permanently unearnable - the Fifty
     * Paths failure mode - so every name is pinned against the catalogue here.
     */
    @Test
    fun `every strength deed names a real catalogue exercise`() {
        val liftNames = liftRules().flatMap { it.names }
        val repNames = repRules().flatMap { it.names }
        assertTrue("no LiftMultiple titles found", liftNames.isNotEmpty())
        assertTrue("no SessionReps titles found", repNames.isNotEmpty())
        val unknown = (liftNames + repNames).filter { it !in catalogueNames }
        assertTrue(
            "strength deeds name exercises the catalogue does not ship: $unknown",
            unknown.isEmpty(),
        )
    }

    /**
     * A woman and a man wearing the same title must have done equivalently
     * hard things, so the female bar sits strictly below the male one on
     * every load deed. Equal or inverted bars would strip the title's reach
     * from exactly the hunters the sex factor exists to credit.
     */
    @Test
    fun `every lift deed sets the female bar below the male bar`() {
        val bad = liftRules().filter { it.female <= 0.0 || it.female >= it.male }
        assertTrue(
            "lift deeds whose female bar is not strictly below the male bar: $bad",
            bad.isEmpty(),
        )
    }

    /** A 5-rep bench at 40 kg against an 80 kg hunter estimates a 0.583x 1RM. */
    private fun benchLedger(sex: Sex): Titles.Ledger {
        val session = WorkoutSession(id = 1, label = "push day", startedAtMs = 1_000L, completedAtMs = 2_000L)
        val set = SessionSet(exerciseId = 7L, setIndex = 0, reps = 5, weightKg = 40.0, done = true)
        val exercises = mapOf(
            7L to Exercise(name = "Bench Press", muscleGroup = MuscleGroup.PUSH, isWeighted = true),
        )
        return Titles.ledgerOf(
            totalXp = 0,
            history = listOf(session to listOf(set)),
            healthDays = emptyList(),
            practices = emptyList(),
            exercises = exercises,
            bodyweightAt = { 80.0 },
            sex = sex,
        )
    }

    @Test
    fun `the same lift earns a female hunter the title a male hunter has not yet earned`() {
        val hers = benchLedger(Sex.FEMALE)
        val his = benchLedger(Sex.MALE)
        val rule = Titles.byId("bench_mark")!!.rule as TitleRule.LiftMultiple
        assertTrue(rule.female <= 0.583)
        assertTrue(hers.bestLiftMultiple["bench press"]!! > 0.55)
        assertTrue(Titles.satisfied(rule, hers))
        assertFalse("the male bar must still be out of reach at 0.583x", Titles.satisfied(rule, his))
        // The progress lines must agree with the awarder on both sides.
        val hersProgress = Titles.progress(rule, hers)
        assertTrue(hersProgress.current >= hersProgress.target)
        val hisProgress = Titles.progress(rule, his)
        assertTrue(hisProgress.current < hisProgress.target)
    }

    /**
     * The deed reads the bodyweight IN FORCE at the session. The same
     * absolute bench (93.3 kg e1RM) clears the 1.0x male bar at the 80 kg
     * she weighed then, and must NOT clear it against a later 100 kg
     * weigh-in - or gaining weight would strip a title already earned.
     */
    @Test
    fun `a load deed is judged against the bodyweight at the session`() {
        val session = WorkoutSession(id = 1, label = "push day", startedAtMs = 1_000L)
        val set = SessionSet(exerciseId = 7L, setIndex = 0, reps = 5, weightKg = 80.0, done = true)
        val exercises = mapOf(
            7L to Exercise(name = "Bench Press", muscleGroup = MuscleGroup.PUSH, isWeighted = true),
        )
        fun ledger(bw: Double) = Titles.ledgerOf(
            totalXp = 0,
            history = listOf(session to listOf(set)),
            healthDays = emptyList(),
            practices = emptyList(),
            exercises = exercises,
            bodyweightAt = { bw },
        )
        val rule = TitleRule.LiftMultiple(setOf("bench press"), male = 1.0, female = 0.5)
        assertTrue(Titles.satisfied(rule, ledger(80.0)))
        assertFalse(Titles.satisfied(rule, ledger(100.0)))
    }

    /**
     * The e1RM estimate caps its rep term at 12, so a 50-rep set cannot
     * extrapolate into an imaginary 1RM: 20 kg * (1 + 12/30) = 28 kg stays
     * far under a real bar. Without the cap the factor grows with reps
     * forever and volume work farms load deeds.
     */
    @Test
    fun `a high-rep set cannot extrapolate past the rep-term cap`() {
        val session = WorkoutSession(id = 1, label = "pump", startedAtMs = 1_000L)
        val exercises = mapOf(
            7L to Exercise(name = "Bench Press", muscleGroup = MuscleGroup.PUSH, isWeighted = true),
        )
        val ledger = Titles.ledgerOf(
            totalXp = 0,
            history = listOf(
                session to listOf(
                    SessionSet(exerciseId = 7L, setIndex = 0, reps = 50, weightKg = 20.0, done = true),
                ),
            ),
            healthDays = emptyList(),
            practices = emptyList(),
            exercises = exercises,
            bodyweightAt = { 80.0 },
        )
        val multiple = ledger.bestLiftMultiple["bench press"]!!
        assertEquals(20.0 * (1.0 + 12 / 30.0) / 80.0, multiple, 1e-9)
    }

    /**
     * Rep and hold deeds, end to end: 100 pull-ups inside one session and a
     * 240-second hold each earn their deed, and counts split across two
     * sessions do not. Unassisted bodyweight moves mark no kilo, so the same
     * history leaves every load deed untouched - pull-up volume can never
     * masquerade as a weighted pull-up 1RM.
     */
    @Test
    fun `rep and hold deeds are earned within a single session`() {
        val exercises = mapOf(
            1L to Exercise(name = "Pull-up", muscleGroup = MuscleGroup.PULL, isWeighted = false),
            2L to Exercise(name = "Plank", muscleGroup = MuscleGroup.CORE, isWeighted = false, metric = ExerciseMetric.HOLD),
        )
        fun history(sessions: List<List<SessionSet>>) = sessions.mapIndexed { i, sets ->
            WorkoutSession(id = i.toLong() + 1, label = "s$i", startedAtMs = i * 10_000L + 1) to sets
        }
        val big = Titles.ledgerOf(
            totalXp = 0,
            history = history(
                listOf(
                    listOf(
                        SessionSet(exerciseId = 1L, setIndex = 0, reps = 60, done = true),
                        SessionSet(exerciseId = 1L, setIndex = 1, reps = 40, done = true),
                        SessionSet(exerciseId = 2L, setIndex = 2, reps = 0, durationSec = 240, done = true),
                    ),
                ),
            ),
            healthDays = emptyList(),
            practices = emptyList(),
            exercises = exercises,
        )
        val split = Titles.ledgerOf(
            totalXp = 0,
            history = history(
                listOf(
                    listOf(SessionSet(exerciseId = 1L, setIndex = 0, reps = 60, done = true)),
                    listOf(SessionSet(exerciseId = 1L, setIndex = 0, reps = 40, done = true)),
                ),
            ),
            healthDays = emptyList(),
            practices = emptyList(),
            exercises = exercises,
        )
        val pullRule = Titles.byId("century_of_rungs")!!.rule
        val holdRule = Titles.byId("unshaking")!!.rule
        assertTrue(Titles.satisfied(pullRule, big))
        assertTrue(Titles.satisfied(holdRule, big))
        assertFalse("100 pull-ups split over two sessions is not a 100-rep session", Titles.satisfied(pullRule, split))
        assertFalse(Titles.satisfied(holdRule, split))
        // Bodyweight-only work never marks a load multiple.
        assertTrue(big.bestLiftMultiple.isEmpty())
    }

    /**
     * Sweeps every new deed with a ledger just short of its bar: the awarder
     * must refuse, and the codex line must read short of the target. The
     * LongestRun rounding bug - 4.9 km drawn as "5 of 5 km" - must not be
     * reborn in percent form for the load deeds.
     */
    @Test
    fun `an unearned strength deed never reports itself complete`() {
        liftRules().forEach { rule ->
            listOf(Sex.MALE to rule.male, Sex.FEMALE to rule.female).forEach { (sex, bar) ->
                val almost = bar * 0.999
                val ledger = Titles.Ledger(
                    totalXp = 0, workouts = 0, sets = 0, reps = 0,
                    sex = sex,
                    bestLiftMultiple = rule.names.associateWith { almost },
                )
                assertFalse("$sex lift at $almost must not satisfy ${rule.names}", Titles.satisfied(rule, ledger))
                val progress = Titles.progress(rule, ledger)
                assertTrue(
                    "progress drew ${progress.current} of ${progress.target} on an unearned lift",
                    progress.current < progress.target,
                )
            }
        }
        repRules().forEach { rule ->
            val ledger = Titles.Ledger(
                totalXp = 0, workouts = 0, sets = 0, reps = 0,
                bestSessionReps = rule.names.associateWith { rule.count - 1 },
            )
            assertFalse(Titles.satisfied(rule, ledger))
            val progress = Titles.progress(rule, ledger)
            assertTrue(progress.current < progress.target)
        }
        listOf(Titles.byId("unshaking")!!).forEach { def ->
            val rule = def.rule as TitleRule.LongestHold
            val ledger = Titles.Ledger(
                totalXp = 0, workouts = 0, sets = 0, reps = 0,
                bestHoldSeconds = rule.seconds - 1,
            )
            assertFalse(Titles.satisfied(rule, ledger))
            val progress = Titles.progress(rule, ledger)
            assertTrue(progress.current < progress.target)
        }
    }
}
