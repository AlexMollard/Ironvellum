package com.ironvellum.app.domain

import com.ironvellum.app.data.Seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sixteen variation deeds: each rule earned and not yet earned, what is easier and harder, sets that carry
 * several modifiers at once, and that every name and modifier a rule asks for exists in the shipped catalogue.
 */
class VariationDeedsTest {

    private val pullUp = Exercise(id = 1, name = "Pull-up", muscleGroup = MuscleGroup.PULL, isWeighted = false)
    private val chinUp = Exercise(id = 2, name = "Chin-up", muscleGroup = MuscleGroup.PULL, isWeighted = false)
    private val dip = Exercise(id = 3, name = "Dip", muscleGroup = MuscleGroup.PUSH, isWeighted = false)
    private val pushUp = Exercise(id = 4, name = "Push-up", muscleGroup = MuscleGroup.PUSH, isWeighted = false)
    private val deadlift = Exercise(id = 5, name = "Deadlift", muscleGroup = MuscleGroup.LEGS, isWeighted = true)
    private val archerPushUp = Exercise(id = 6, name = "Archer Push-up", muscleGroup = MuscleGroup.PUSH, isWeighted = false)
    private val archerPullUp = Exercise(id = 7, name = "Archer Pull-up", muscleGroup = MuscleGroup.PULL, isWeighted = false)
    private val plank = Exercise(id = 8, name = "Plank", muscleGroup = MuscleGroup.CORE, isWeighted = false, metric = ExerciseMetric.HOLD)
    private val catalogue = listOf(pullUp, chinUp, dip, pushUp, deadlift, archerPushUp, archerPullUp, plank).associateBy { it.id }

    /** One set, in the order it is logged: [position] is the block, [index] the set inside it. */
    private fun set(
        exercise: Exercise,
        reps: Int,
        modifiers: String = "",
        weightKg: Double? = null,
        position: Int = 0,
        index: Int = 0,
        done: Boolean = true,
    ) = SessionSet(
        exerciseId = exercise.id, exercisePosition = position, setIndex = index, reps = reps,
        weightKg = weightKg, modifiers = modifiers, done = done,
    )

    /** Sessions in the order given, a day apart. */
    private fun ledger(vararg sessions: List<SessionSet>): Titles.Ledger = Titles.ledgerOf(
        totalXp = 0,
        history = sessions.mapIndexed { i, sets ->
            WorkoutSession(id = i + 1L, label = "s$i", startedAtMs = 1_000L + i * 86_400_000L) to sets
        },
        healthDays = emptyList(),
        practices = emptyList(),
        exercises = catalogue,
        bodyweightAt = { 80.0 },
    )

    private fun earned(id: String, ledger: Titles.Ledger) = Titles.satisfied(Titles.byId(id)!!.rule, ledger)

    private fun ids(ledger: Titles.Ledger) = Titles.newlyUnlocked(ledger, emptySet()).map { it.id }.toSet()

    // ------------------------------------------------------------ the catalogue

    private val newIds = listOf(
        "cast_off_the_crutch", "level_ground", "off_the_leash", "no_hand_beneath",
        "below_the_floor", "headlong", "bowstring", "half_the_hands", "the_long_way_down",
        "curious_iron", "many_roads", "cartographer", "every_face_of_the_stone",
        "twofold_burden", "threefold_burden", "the_unkind_angle",
    )

    @Test
    fun `sixteen deeds join the catalogue at the rarities the owner approved`() {
        assertEquals(121, Titles.ALL.size)
        assertEquals(121, Titles.ALL.map { it.id }.toSet().size)
        val rarities = newIds.map { Titles.byId(it)!!.rarity }.groupingBy { it }.eachCount()
        assertEquals(5, rarities[TitleRarity.Common])
        assertEquals(5, rarities[TitleRarity.Rare])
        assertEquals(4, rarities[TitleRarity.Epic])
        assertEquals(2, rarities[TitleRarity.Masterwork])
        assertEquals("the older 105 are untouched at the front", 105, Titles.ALL.indexOfFirst { it.id == newIds.first() })
        // Unlocks are stored by id and the cloud column holds 64 characters.
        assertTrue(newIds.all { it.length <= 64 })
        assertTrue(newIds.all { Titles.byId(it)!!.description.isNotBlank() })
    }

    @Test
    fun `harder is exactly archer one-arm decline elevated and deficit`() {
        assertEquals(setOf("archer", "one-arm", "decline", "elevated", "deficit"), Variation.HARDER)
        assertTrue(Variation.HARDER.none { Variation.eases(it) })
    }

    @Test
    fun `an unearned variation deed never reports itself complete`() {
        val empty = ledger()
        newIds.forEach { id ->
            val rule = Titles.byId(id)!!.rule
            assertFalse("$id earned by nothing", Titles.satisfied(rule, empty))
            val progress = Titles.progress(rule, empty)
            assertTrue("$id draws ${progress.current} of ${progress.target}", progress.current < progress.target)
        }
    }

    // ------------------------------------------------------------ moving off the easier version

    @Test
    fun `cast off the crutch needs a standard set after an assisted one of the same lift`() {
        assertTrue(earned("cast_off_the_crutch", ledger(listOf(set(pullUp, 5, "assisted"), set(pullUp, 3, index = 1)))))
        assertTrue("a later trial counts", earned("cast_off_the_crutch", ledger(listOf(set(dip, 8, "assisted")), listOf(set(dip, 1)))))
        assertTrue("tempo does not split the lift", earned("cast_off_the_crutch", ledger(listOf(set(chinUp, 5, "assisted")), listOf(set(chinUp, 1, "tempo")))))
        assertFalse("before is not after", earned("cast_off_the_crutch", ledger(listOf(set(pullUp, 3), set(pullUp, 5, "assisted", index = 1)))))
        assertFalse("assisted only", earned("cast_off_the_crutch", ledger(listOf(set(pullUp, 5, "assisted")))))
        assertFalse("a standard set of the same lift before an assisted one does not count once assisted is logged",
            earned("cast_off_the_crutch", ledger(listOf(set(chinUp, 3), set(chinUp, 5, "assisted", index = 1)))))
        assertFalse("a harder variation is not the standard lift", earned("cast_off_the_crutch", ledger(listOf(set(pullUp, 5, "assisted"), set(pullUp, 3, "archer", index = 1)))))
        assertFalse("not done", earned("cast_off_the_crutch", ledger(listOf(set(pullUp, 5, "assisted"), set(pullUp, 3, index = 1, done = false)))))
        assertFalse("a push-up is not on that list", earned("cast_off_the_crutch", ledger(listOf(set(pushUp, 5, "incline"), set(pushUp, 3, index = 1)))))
    }

    @Test
    fun `an assisted set later in the same trial is ordered by block then set`() {
        val inOrder = ledger(listOf(set(pullUp, 5, "assisted", position = 0), set(pullUp, 3, position = 1)))
        val reversedInList = ledger(listOf(set(pullUp, 3, position = 1), set(pullUp, 5, "assisted", position = 0)))
        assertTrue(earned("cast_off_the_crutch", inOrder))
        assertTrue("list order is not time order", earned("cast_off_the_crutch", reversedInList))
    }

    @Test
    fun `level ground needs a flat push-up after an incline one`() {
        assertTrue(earned("level_ground", ledger(listOf(set(pushUp, 10, "incline"), set(pushUp, 8, index = 1)))))
        assertFalse(earned("level_ground", ledger(listOf(set(pushUp, 8), set(pushUp, 10, "incline", index = 1)))))
        assertFalse("decline is harder, not easier, so it does not make incline logged", earned("level_ground", ledger(listOf(set(pushUp, 10, "incline"), set(pushUp, 8, "incline", index = 1)))))
        assertFalse("incline alone", earned("level_ground", ledger(listOf(set(pushUp, 10, "incline")))))
        assertFalse("a pull-up is not a push-up", earned("level_ground", ledger(listOf(set(pullUp, 5, "assisted"), set(pullUp, 3, index = 1)))))
    }

    @Test
    fun `off the leash is three different lifts moved off the easier version`() {
        val two = listOf(
            set(pullUp, 5, "assisted"), set(pullUp, 3, index = 1),
            set(dip, 5, "assisted", position = 1), set(dip, 3, position = 1, index = 1),
        )
        assertFalse(earned("off_the_leash", ledger(two)))
        val three = two + listOf(set(pushUp, 9, "incline", position = 2), set(pushUp, 6, position = 2, index = 1))
        assertTrue(earned("off_the_leash", ledger(three)))
        val chinUpInstead = two + listOf(set(chinUp, 5, "assisted", position = 2), set(chinUp, 3, position = 2, index = 1))
        assertTrue("any lift counts, not only the four the other deeds name", earned("off_the_leash", ledger(chinUpInstead)))
        // The same lift twice is still one lift.
        val repeated = two + listOf(set(pullUp, 5, "assisted", position = 3), set(pullUp, 3, position = 3, index = 1))
        assertFalse(earned("off_the_leash", ledger(repeated)))
        val progress = Titles.progress(Titles.byId("off_the_leash")!!.rule, ledger(two))
        assertEquals(2L to 3L, progress.current to progress.target)
    }

    @Test
    fun `no hand beneath needs standard reps past the best assisted set`() {
        assertTrue(earned("no_hand_beneath", ledger(listOf(set(pullUp, 8, "assisted"), set(pullUp, 9, index = 1)))))
        assertTrue("order does not matter", earned("no_hand_beneath", ledger(listOf(set(pullUp, 9), set(pullUp, 8, "assisted", index = 1)))))
        assertTrue("the best assisted set is the bar", earned("no_hand_beneath", ledger(listOf(set(pullUp, 5, "assisted"), set(pullUp, 8, "assisted", index = 1), set(pullUp, 9, index = 2)))))
        assertFalse("a tie is not beating it", earned("no_hand_beneath", ledger(listOf(set(pullUp, 8, "assisted"), set(pullUp, 8, index = 1)))))
        assertFalse("fewer", earned("no_hand_beneath", ledger(listOf(set(pullUp, 8, "assisted"), set(pullUp, 5, index = 1)))))
        assertFalse("assisted logged and not beaten on its own lift", earned("no_hand_beneath", ledger(listOf(set(pullUp, 8, "assisted"), set(pullUp, 5, index = 1), set(chinUp, 12, "assisted", position = 1)))))
    }

    // ------------------------------------------------------------ an easier version never logged is moved past

    @Test
    fun `standard dips with no assisted history earn both dip deeds dated to the first standard dip`() {
        val l = ledger(listOf(set(dip, 6)), listOf(set(dip, 8)))
        assertTrue(earned("cast_off_the_crutch", l))
        assertTrue(earned("no_hand_beneath", l))
        assertEquals(1_000L, Titles.earnedAtMs(Titles.byId("cast_off_the_crutch")!!, l))
        assertEquals(1_000L, Titles.earnedAtMs(Titles.byId("no_hand_beneath")!!, l))
        assertFalse("one lift is not three", earned("off_the_leash", l))
        assertNull("a deed with no history to date it", Titles.earnedAtMs(Titles.byId("below_the_floor")!!, l))
    }

    @Test
    fun `weighted and harder sets count as standard or harder`() {
        assertTrue(earned("cast_off_the_crutch", ledger(listOf(set(dip, 5, weightKg = 20.0)))))
        assertTrue(earned("cast_off_the_crutch", ledger(listOf(set(pullUp, 3, "archer")))))
        assertTrue(earned("level_ground", ledger(listOf(set(pushUp, 8, "decline")))))
        assertTrue(earned("no_hand_beneath", ledger(listOf(set(pullUp, 3, "one-arm")))))
        val l = ledger(listOf(set(pullUp, 5, "tempo")), listOf(set(pullUp, 3, "archer")))
        assertEquals("the first such set: tempo is the standard lift", 1_000L, Titles.earnedAtMs(Titles.byId("cast_off_the_crutch")!!, l))
    }

    @Test
    fun `a lifter with assisted history but no standard set earns neither`() {
        val l = ledger(listOf(set(dip, 8, "assisted")), listOf(set(dip, 10, "assisted")))
        assertFalse(earned("cast_off_the_crutch", l))
        assertFalse(earned("no_hand_beneath", l))
        assertFalse("incline logged: a flat push-up is still owed", earned("level_ground", ledger(listOf(set(pushUp, 10, "incline")))))
        assertFalse("an easier set is never the standard one", earned("level_ground", ledger(listOf(set(pushUp, 10, "incline"), set(pushUp, 12, "assisted", index = 1)))))
    }

    @Test
    fun `a lifter who logged the easier version keeps the old dated rule`() {
        val l = ledger(listOf(set(pullUp, 5, "assisted")), listOf(set(pullUp, 3)), listOf(set(pullUp, 9)))
        assertTrue(earned("cast_off_the_crutch", l))
        assertEquals("the first standard set after the assisted one", 1_000L + 86_400_000L, Titles.earnedAtMs(Titles.byId("cast_off_the_crutch")!!, l))
        assertEquals("the first standard set above the assisted best", 1_000L + 2 * 86_400_000L, Titles.earnedAtMs(Titles.byId("no_hand_beneath")!!, l))
        val standardFirst = ledger(listOf(set(pullUp, 9)), listOf(set(pullUp, 5, "assisted")))
        assertFalse("assisted logged later still means the old rule", earned("cast_off_the_crutch", standardFirst))
    }

    @Test
    fun `a lift with no easier version never earns the easier-version deeds`() {
        val l = ledger(listOf(set(deadlift, 5, weightKg = 100.0), set(plank, 30, index = 1)))
        assertFalse(earned("cast_off_the_crutch", l))
        assertFalse(earned("no_hand_beneath", l))
        assertFalse(earned("off_the_leash", l))
        val three = ledger(listOf(set(pullUp, 3), set(dip, 5, position = 1), set(pushUp, 8, position = 2)))
        assertTrue("three lifts with an easier version, none logged easier", earned("off_the_leash", three))
        assertEquals(1_000L, Titles.earnedAtMs(Titles.byId("off_the_leash")!!, three))
    }

    // ------------------------------------------------------------ the harder road

    @Test
    fun `below the floor is the first deficit set of any lift`() {
        assertTrue(earned("below_the_floor", ledger(listOf(set(deadlift, 5, "deficit", weightKg = 100.0)))))
        assertTrue(earned("below_the_floor", ledger(listOf(set(pushUp, 10, "deficit, tempo")))))
        assertFalse(earned("below_the_floor", ledger(listOf(set(pushUp, 10, "elevated")))))
        assertFalse("not done", earned("below_the_floor", ledger(listOf(set(pushUp, 10, "deficit", done = false)))))
    }

    @Test
    fun `headlong is a decline or feet-elevated push-up`() {
        assertTrue(earned("headlong", ledger(listOf(set(pushUp, 10, "decline")))))
        assertTrue(earned("headlong", ledger(listOf(set(pushUp, 10, "elevated")))))
        assertFalse("incline is the other way", earned("headlong", ledger(listOf(set(pushUp, 10, "incline")))))
        assertFalse("not a push-up", earned("headlong", ledger(listOf(set(pullUp, 10, "elevated")))))
        assertFalse(earned("headlong", ledger(listOf(set(pushUp, 10)))))
    }

    @Test
    fun `bowstring is five reps of an archer pull-up or push-up in one set`() {
        assertTrue(earned("bowstring", ledger(listOf(set(pullUp, 5, "archer")))))
        assertTrue(earned("bowstring", ledger(listOf(set(pushUp, 8, "archer")))))
        assertTrue("the catalogue's Archer Push-up says archer in its name", earned("bowstring", ledger(listOf(set(archerPushUp, 5)))))
        assertTrue(earned("bowstring", ledger(listOf(set(archerPullUp, 6)))))
        assertFalse("four reps", earned("bowstring", ledger(listOf(set(pullUp, 4, "archer")))))
        assertFalse("two sets of three are not one set of five", earned("bowstring", ledger(listOf(set(pullUp, 3, "archer"), set(pullUp, 3, "archer", index = 1)))))
        assertFalse("a plain pull-up", earned("bowstring", ledger(listOf(set(pullUp, 10)))))
        assertFalse("a chin-up is not on the list", earned("bowstring", ledger(listOf(set(chinUp, 6, "archer")))))
        assertFalse("one-arm is not archer", earned("bowstring", ledger(listOf(set(pullUp, 6, "one-arm")))))
    }

    @Test
    fun `half the hands is the first one-arm set`() {
        assertTrue(earned("half_the_hands", ledger(listOf(set(pullUp, 1, "one-arm")))))
        assertTrue(earned("half_the_hands", ledger(listOf(set(pushUp, 1, "one-arm, tempo")))))
        assertFalse(earned("half_the_hands", ledger(listOf(set(pullUp, 5, "archer")))))
    }

    @Test
    fun `the long way down needs a deficit best above a loaded standard best`() {
        val standard = set(deadlift, 5, weightKg = 100.0)
        assertTrue(earned("the_long_way_down", ledger(listOf(standard, set(deadlift, 5, "deficit", weightKg = 110.0, index = 1)))))
        assertTrue("across trials", earned("the_long_way_down", ledger(listOf(standard), listOf(set(deadlift, 5, "deficit", weightKg = 110.0)))))
        assertFalse("equal", earned("the_long_way_down", ledger(listOf(standard, set(deadlift, 5, "deficit", weightKg = 100.0, index = 1)))))
        assertFalse("lower", earned("the_long_way_down", ledger(listOf(standard, set(deadlift, 5, "deficit", weightKg = 90.0, index = 1)))))
        assertFalse("nothing standard to beat", earned("the_long_way_down", ledger(listOf(set(deadlift, 5, "deficit", weightKg = 110.0)))))
        assertFalse("an easier variation is not the deficit lift", earned("the_long_way_down", ledger(listOf(standard, set(deadlift, 5, "deficit, assisted", weightKg = 140.0, index = 1)))))
        assertFalse("another lift", earned("the_long_way_down", ledger(listOf(standard, set(pushUp, 5, "deficit", weightKg = 140.0, index = 1)))))
    }

    // ------------------------------------------------------------ many roads

    /** Distinct variations: a lift each, the same modifier on every one. */
    private fun variationsOf(count: Int): Titles.Ledger {
        val lifts = (1..count).map { Exercise(id = 100L + it, name = "Lift $it", muscleGroup = MuscleGroup.PUSH, isWeighted = false) }
        val sets = lifts.map { set(it, 5, "deficit") }
        return Titles.ledgerOf(
            totalXp = 0,
            history = listOf(WorkoutSession(id = 1, label = "s", startedAtMs = 1_000L) to sets),
            healthDays = emptyList(),
            practices = emptyList(),
            exercises = lifts.associateBy { it.id },
        )
    }

    @Test
    fun `curious iron many roads and cartographer count distinct variations`() {
        assertEquals(setOf<String>(), ids(variationsOf(4)).intersect(setOf("curious_iron", "many_roads", "cartographer")))
        assertEquals(setOf("curious_iron"), ids(variationsOf(5)).intersect(setOf("curious_iron", "many_roads", "cartographer")))
        assertEquals(setOf("curious_iron"), ids(variationsOf(14)).intersect(setOf("curious_iron", "many_roads", "cartographer")))
        assertEquals(setOf("curious_iron", "many_roads"), ids(variationsOf(15)).intersect(setOf("curious_iron", "many_roads", "cartographer")))
        assertEquals(setOf("curious_iron", "many_roads"), ids(variationsOf(39)).intersect(setOf("curious_iron", "many_roads", "cartographer")))
        assertEquals(setOf("curious_iron", "many_roads", "cartographer"), ids(variationsOf(40)).intersect(setOf("curious_iron", "many_roads", "cartographer")))
    }

    @Test
    fun `a variation counts once however often it is logged and tempo is not a variation`() {
        val sets = listOf(
            set(pushUp, 5, "deficit"), set(pushUp, 5, "deficit", index = 1), set(pushUp, 5, "Deficit, tempo", index = 2),
            set(pushUp, 5, "tempo", index = 3), set(pushUp, 5, "paused, weighted", index = 4), set(pushUp, 5, "banded", index = 5),
            set(pullUp, 5, "deficit", position = 1),
            set(pushUp, 5, "deficit, elevated", position = 2),
        )
        // push-up deficit, pull-up deficit, push-up deficit+elevated.
        assertEquals(3, ledger(sets).variations.distinctVariations)
    }

    @Test
    fun `every face of the stone needs all seven single-modifier push-ups`() {
        val faces = Variation.TOKENS.toList()
        val all = faces.mapIndexed { i, token -> set(pushUp, 5, token, index = i) }
        assertTrue(earned("every_face_of_the_stone", ledger(all)))
        assertTrue("across trials", earned("every_face_of_the_stone", ledger(all.take(3), all.drop(3))))
        assertFalse("six of seven", earned("every_face_of_the_stone", ledger(all.drop(1))))
        // A combined set is its own variation, not any of its faces.
        val combined = all.drop(2) + listOf(set(pushUp, 5, "assisted, archer", index = 9))
        assertFalse(earned("every_face_of_the_stone", ledger(combined)))
        assertFalse("the faces of another lift", earned("every_face_of_the_stone", ledger(faces.mapIndexed { i, t -> set(pullUp, 5, t, index = i) })))
        val progress = Titles.progress(Titles.byId("every_face_of_the_stone")!!.rule, ledger(all.drop(1)))
        assertEquals(6L to 7L, progress.current to progress.target)
    }

    // ------------------------------------------------------------ burdens

    @Test
    fun `twofold and threefold burden count harder modifiers on one set`() {
        fun burdens(modifiers: String, exercise: Exercise = pushUp) =
            ids(ledger(listOf(set(exercise, 3, modifiers)))).intersect(setOf("twofold_burden", "threefold_burden"))
        assertEquals(emptySet<String>(), burdens("deficit"))
        assertEquals(emptySet<String>(), burdens("deficit, tempo, paused, weighted"))
        assertEquals(setOf("twofold_burden"), burdens("archer, deficit"))
        assertEquals(setOf("twofold_burden"), burdens("deficit, elevated, tempo"))
        assertEquals(setOf("twofold_burden", "threefold_burden"), burdens("archer, deficit, elevated"))
        assertEquals("an easier modifier is not a burden", emptySet<String>(), burdens("assisted, deficit"))
        assertEquals("incline does not add to archer", emptySet<String>(), burdens("incline, archer"))
        assertEquals("the name carries archer", setOf("twofold_burden"), burdens("deficit", archerPushUp))
    }

    @Test
    fun `burdens are counted on one set not across sets`() {
        val sets = listOf(set(pushUp, 5, "archer"), set(pushUp, 5, "deficit", index = 1), set(pushUp, 5, "elevated", index = 2))
        assertEquals(emptySet<String>(), ids(ledger(sets)).intersect(setOf("twofold_burden", "threefold_burden")))
    }

    @Test
    fun `the unkind angle is five reps of an archer deficit feet-elevated push-up`() {
        assertTrue(earned("the_unkind_angle", ledger(listOf(set(pushUp, 5, "archer, deficit, elevated")))))
        assertTrue(earned("the_unkind_angle", ledger(listOf(set(pushUp, 8, "elevated, archer, tempo, deficit")))))
        assertTrue("the name carries archer", earned("the_unkind_angle", ledger(listOf(set(archerPushUp, 5, "deficit, elevated")))))
        assertFalse("four reps", earned("the_unkind_angle", ledger(listOf(set(pushUp, 4, "archer, deficit, elevated")))))
        assertFalse("decline is not elevated", earned("the_unkind_angle", ledger(listOf(set(pushUp, 5, "archer, deficit, decline")))))
        assertFalse("two of three", earned("the_unkind_angle", ledger(listOf(set(pushUp, 9, "archer, deficit")))))
        assertFalse("three sets are not one", earned("the_unkind_angle", ledger(listOf(set(pushUp, 9, "archer"), set(pushUp, 9, "deficit", index = 1), set(pushUp, 9, "elevated", index = 2)))))
        assertFalse("a pull-up", earned("the_unkind_angle", ledger(listOf(set(pullUp, 5, "archer, deficit, elevated")))))
    }

    @Test
    fun `one hard set earns every deed it satisfies at once`() {
        val one = ledger(listOf(set(pushUp, 5, "archer, deficit, elevated")))
        assertEquals(
            // A harder push-up with no easier one ever logged is moved past it, so the two easier-version deeds join.
            setOf("level_ground", "no_hand_beneath", "below_the_floor", "headlong", "bowstring", "twofold_burden", "threefold_burden", "the_unkind_angle"),
            ids(one).intersect(newIds.toSet()),
        )
    }

    // ------------------------------------------------------------ reachable from the shipped catalogue

    private val shipped: List<Exercise> = Seed.exercises.map {
        Exercise(
            name = it.name,
            muscleGroup = MuscleGroup.entries.firstOrNull { g -> g.name == it.muscleGroup } ?: MuscleGroup.CORE,
            isWeighted = it.isWeighted,
            metric = ExerciseMetric.entries.firstOrNull { m -> m.name == it.metric } ?: ExerciseMetric.REPS,
            category = it.category,
        )
    }

    /** The splitting modifiers the catalogue offers the movement called [name], plus the ones its name carries. */
    private fun reachable(name: String): Set<String> {
        val exercise = shipped.firstOrNull { Titles.normaliseName(it.name) == name } ?: return emptySet()
        return applicableModifiers(exercise).filter { it in Variation.TOKENS }.toSet() + VariationDeeds.carried(exercise.name, "")
    }

    @Test
    fun `every movement a variation deed names is in the catalogue and can carry what the deed asks`() {
        val rules = newIds.map { Titles.byId(it)!!.rule }
        val named = rules.flatMap {
            when (it) {
                is TitleRule.MovedOffEasier -> it.names
                is TitleRule.VariationSet -> it.names
                is TitleRule.EveryFace -> setOf(it.name)
                else -> emptySet()
            }
        }
        assertEquals(emptyList<String>(), named.filter { n -> shipped.none { Titles.normaliseName(it.name) == n } })
        rules.filterIsInstance<TitleRule.MovedOffEasier>().forEach { rule ->
            rule.names.forEach { name ->
                assertTrue("$name offers no easier version", reachable(name).any { it in Variation.TOKENS - Variation.HARDER })
            }
        }
        rules.filterIsInstance<TitleRule.VariationSet>().forEach { rule ->
            val needed = rule.allOf + rule.anyOf
            val lifts = rule.names.ifEmpty { shipped.map { Titles.normaliseName(it.name) }.toSet() }
            if (rule.allOf.isNotEmpty()) {
                assertTrue("no named lift can carry ${rule.allOf}", lifts.any { reachable(it).containsAll(rule.allOf) })
            } else {
                assertTrue("no named lift can carry any of ${rule.anyOf}", rule.anyOf.all { token -> lifts.any { token in reachable(it) } })
            }
            assertTrue(needed.all { it in Variation.TOKENS })
        }
    }

    @Test
    fun `the push-up offers every face the deed asks for`() {
        val rule = Titles.byId("every_face_of_the_stone")!!.rule as TitleRule.EveryFace
        assertEquals(rule.tokens, reachable(rule.name))
        assertEquals("the description says seven", 7, rule.tokens.size)
        assertTrue(Titles.byId("every_face_of_the_stone")!!.description.contains("7"))
        // No other lift offers as many: the deed would otherwise be one lift of many.
        val widest = shipped.maxOf { applicableModifiers(it).count { t -> t in Variation.TOKENS } }
        assertEquals(7, widest)
    }
}
