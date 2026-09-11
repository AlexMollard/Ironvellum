package com.monarch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleEngineTest {

    private val ledger = Titles.Ledger(totalXp = 0, workouts = 0, sets = 0, reps = 0)

    @Test
    fun `first workout rule needs one workout`() {
        assertFalse(Titles.satisfied(TitleRule.FirstWorkout, ledger))
        assertTrue(Titles.satisfied(TitleRule.FirstWorkout, ledger.copy(workouts = 1)))
    }

    @Test
    fun `health data alone unlocks step titles`() {
        // The unlock path used to assemble a ledger from sessions only, so a
        // 20k-step day never awarded anything.
        val day = HealthDay(
            date = java.time.LocalDate.now(),
            steps = 21_000,
            distanceKm = 14.0,
            activeKcal = 900,
        )
        val built = Titles.ledgerOf(
            totalXp = 0,
            history = emptyList(),
            healthDays = listOf(day),
            practices = emptyList(),
        )
        assertEquals(21_000, built.stepsBestDay)
        assertEquals(1, built.stepGoalDays)

        val unlocked = Titles.newlyUnlocked(built, emptySet()).map { it.id }
        assertTrue(
            "a 21k-step day must award the 10k and 20k step deeds, got $unlocked",
            unlocked.containsAll(
                Titles.ALL.filter {
                    it.rule is TitleRule.StepsInDay && (it.rule as TitleRule.StepsInDay).count <= 21_000
                }.map { it.id },
            ),
        )
    }

    @Test
    fun `skill practice feeds mastery and attempt counters`() {
        val built = Titles.ledgerOf(
            totalXp = 0,
            history = emptyList(),
            healthDays = emptyList(),
            practices = listOf(
                SkillPractice("Dead Hang", 1L, claimed = true),
                SkillPractice("Pull-up", 2L, claimed = false),
                SkillPractice("Pull-up", 3L, claimed = false),
            ),
        )
        assertEquals(1, built.skillsMastered)
        assertEquals(2, built.practiceAttempts)
    }

    @Test
    fun `workout count rule is inclusive`() {
        val rule = TitleRule.Workouts(10)
        assertTrue(Titles.satisfied(rule, ledger.copy(workouts = 10)))
        assertFalse(Titles.satisfied(rule, ledger.copy(workouts = 9)))
    }

    @Test
    fun `level rule reads from xp curve`() {
        // Level 5 starts at 100+200+300+400 = 1000 xp
        val rule = TitleRule.ReachLevel(5)
        assertTrue(Titles.satisfied(rule, ledger.copy(totalXp = 1000)))
        assertFalse(Titles.satisfied(rule, ledger.copy(totalXp = 999)))
    }

    @Test
    fun `sets and reps rules are inclusive`() {
        assertTrue(Titles.satisfied(TitleRule.SetsLogged(100), ledger.copy(sets = 100)))
        assertFalse(Titles.satisfied(TitleRule.SetsLogged(100), ledger.copy(sets = 99)))
        assertTrue(Titles.satisfied(TitleRule.RepsLogged(1000), ledger.copy(reps = 1000)))
    }

    @Test
    fun `newlyUnlocked skips already held titles`() {
        val held = setOf("awakened")
        val l = ledger.copy(workouts = 1)
        val unlocked = Titles.newlyUnlocked(l, held)
        assertTrue(unlocked.none { it.id == "awakened" })

        val again = Titles.newlyUnlocked(l, emptySet())
        assertTrue(again.any { it.id == "awakened" })
    }

    @Test
    fun `all catalog ids are unique`() {
        assertEquals(Titles.ALL.size, Titles.ALL.map { it.id }.toSet().size)
    }

    @Test
    fun `byId resolves every catalog entry`() {
        Titles.ALL.forEach { def -> assertEquals(def, Titles.byId(def.id)) }
        assertEquals(null, Titles.byId("missing"))
    }

    // ---- every rule type: exact threshold vs one below ----

    private fun full() = ledger.copy(
        workouts = 1,
        sessionStrength = 1_000,
        lifetimeStrength = 50_000,
        stepsBestDay = 10_000,
        stepsLifetime = 100_000,
        distanceKmLifetime = 50.0,
        activeKcalBestDay = 500,
        sleepBestMinutes = 480,
        stepGoalDays = 10,
        skillsMastered = 1,
        practiceAttempts = 25,
        trainingStreakDays = 3,
        bestWeekWorkouts = 3,
    )

    /** Asserts a rule flips exactly at its threshold, not one unit below. */
    private fun check(
        rule: TitleRule,
        value: Long,
        field: (Titles.Ledger, Long) -> Titles.Ledger,
    ) {
        assertTrue("satisfied at threshold: $rule", Titles.satisfied(rule, field(full(), value)))
        assertFalse("not satisfied below: $rule", Titles.satisfied(rule, field(full(), value - 1)))
    }

    @Test
    fun `every rule type is satisfied exactly at threshold and not one below`() {
        assertTrue(Titles.satisfied(TitleRule.FirstWorkout, ledger.copy(workouts = 1)))
        check(TitleRule.Workouts(10), 10) { l, v -> l.copy(workouts = v.toInt()) }
        check(TitleRule.SetsLogged(100), 100) { l, v -> l.copy(sets = v.toInt()) }
        check(TitleRule.RepsLogged(1_000), 1_000) { l, v -> l.copy(reps = v.toInt()) }
        check(TitleRule.SessionStrength(1_000), 1_000) { l, v -> l.copy(sessionStrength = v.toInt()) }
        check(TitleRule.LifetimeStrength(50_000), 50_000) { l, v -> l.copy(lifetimeStrength = v.toInt()) }
        check(TitleRule.StepsInDay(10_000), 10_000) { l, v -> l.copy(stepsBestDay = v.toInt()) }
        check(TitleRule.StepsLifetime(100_000), 100_000) { l, v -> l.copy(stepsLifetime = v) }
        check(TitleRule.DistanceKmLifetime(50.0), 50) { l, v -> l.copy(distanceKmLifetime = v.toDouble()) }
        check(TitleRule.ActiveKcalInDay(500), 500) { l, v -> l.copy(activeKcalBestDay = v.toInt()) }
        check(TitleRule.SleepMinutesInNight(480), 480) { l, v -> l.copy(sleepBestMinutes = v.toInt()) }
        check(TitleRule.StepGoalDays(10), 10) { l, v -> l.copy(stepGoalDays = v.toInt()) }
        check(TitleRule.SkillsMastered(84), 84) { l, v -> l.copy(skillsMastered = v.toInt()) }
        check(TitleRule.PracticeAttempts(500), 500) { l, v -> l.copy(practiceAttempts = v.toInt()) }
        check(TitleRule.TrainingStreak(100), 100) { l, v -> l.copy(trainingStreakDays = v.toInt()) }
        check(TitleRule.WorkoutsInWeek(6), 6) { l, v -> l.copy(bestWeekWorkouts = v.toInt()) }
        // level rule via xp: level 5 starts at 1000 xp
        assertTrue(Titles.satisfied(TitleRule.ReachLevel(5), ledger.copy(totalXp = 1000)))
        assertFalse(Titles.satisfied(TitleRule.ReachLevel(5), ledger.copy(totalXp = 999)))
    }

    @Test
    fun `progress never exceeds 1 and reports correct remaining`() {
        Titles.ALL.forEach { def ->
            val p = Titles.progress(def.rule, full())
            assertTrue("fraction ${def.id}", p.fraction in 0f..1f)
            // remaining must hit zero exactly when the rule is satisfied —
            // full() only sets the lowest tier of each ladder, so higher
            // tiers legitimately still have work left.
            if (Titles.satisfied(def.rule, full())) {
                assertEquals("remaining ${def.id}", 0L, p.remaining)
            } else {
                assertTrue("remaining ${def.id}", p.remaining > 0L)
            }
        }
        val p = Titles.progress(TitleRule.StepsInDay(30_000), full().copy(stepsBestDay = 10_000))
        assertEquals(20_000L, p.remaining)
        assertTrue(p.fraction < 1f)
    }

    @Test
    fun `progress units read correctly`() {
        assertEquals("steps in a day", Titles.progress(TitleRule.StepsInDay(1), full()).unit)
        assertEquals("km lifetime", Titles.progress(TitleRule.DistanceKmLifetime(1.0), full()).unit)
        assertEquals("day streak", Titles.progress(TitleRule.TrainingStreak(3), full()).unit)
        assertEquals("active kcal in a day", Titles.progress(TitleRule.ActiveKcalInDay(500), full()).unit)
        assertEquals("minutes slept in a night", Titles.progress(TitleRule.SleepMinutesInNight(480), full()).unit)
        assertEquals("skills mastered", Titles.progress(TitleRule.SkillsMastered(5), full()).unit)
        assertEquals("practice attempts", Titles.progress(TitleRule.PracticeAttempts(25), full()).unit)
        assertEquals("workouts in a week", Titles.progress(TitleRule.WorkoutsInWeek(3), full()).unit)
    }

    @Test
    fun `every catalog entry has nonblank name and description`() {
        Titles.ALL.forEach { def ->
            assertTrue("name ${def.id}", def.name.isNotBlank())
            assertTrue("description ${def.id}", def.description.isNotBlank())
        }
    }

    @Test
    fun `category is nonblank for every rule in catalog`() {
        Titles.ALL.forEach { def -> assertTrue("category ${def.id}", Titles.category(def.rule).isNotBlank()) }
    }
}
