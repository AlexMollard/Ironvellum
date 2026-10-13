package com.ironvellum.app.domain

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The developer rescore over a synthetic history: deltas, the level clamp, idempotency and no double pay. */
class XpRescoreTest {

    private val zone = ZoneOffset.UTC

    private val exercises = mapOf(
        1L to Exercise(id = 1, name = "Pull-up", muscleGroup = MuscleGroup.PULL, isWeighted = false),
        2L to Exercise(id = 2, name = "Bench Press", muscleGroup = MuscleGroup.PUSH, isWeighted = true),
    )

    private fun day(date: LocalDate, hour: Int = 9): Long = date.atStartOfDay(zone).toInstant().toEpochMilli() + hour * 3_600_000L

    private fun set(exerciseId: Long, index: Int, reps: Int, kg: Double?) = SessionSet(
        exerciseId = exerciseId,
        exerciseName = exercises.getValue(exerciseId).name,
        exercisePosition = if (exerciseId == 1L) 0 else 1,
        setIndex = index,
        reps = reps,
        weightKg = kg,
        done = true,
    )

    private fun trial(
        id: Long,
        date: LocalDate,
        xp: Int,
        reps: Int = 8,
        kg: Double? = 5.0,
        presetId: Long? = null,
        imported: Boolean = false,
        sealedXp: Int? = null,
        editedAtMs: Long? = null,
    ): Pair<WorkoutSession, List<SessionSet>> = WorkoutSession(
        id = id,
        presetId = presetId,
        label = "Pull $id",
        startedAtMs = day(date),
        completedAtMs = day(date) + 3_000_000L,
        xpAwarded = xp,
        imported = imported,
        sealedXp = sealedXp,
        editedAtMs = editedAtMs,
    ) to listOf(set(1, 0, reps, kg), set(1, 1, reps, kg), set(1, 2, reps, kg), set(2, 0, 5, 60.0))

    // Four trials over a month, each stored at a figure the old rules paid (made up, not the owner's).
    private val monday = LocalDate.of(2026, 9, 7)
    private val history = listOf(
        trial(1, monday, xp = 300),
        trial(2, monday.plusDays(7), xp = 300, reps = 9),
        trial(3, monday.plusDays(14), xp = 300, reps = 9, kg = 7.5),
        trial(4, monday.plusDays(21), xp = 300, reps = 10, kg = 7.5),
    )

    private fun reprice(
        h: List<Pair<WorkoutSession, List<SessionSet>>> = history,
        planned: (Long) -> Map<Long, Int>? = { null },
        scheduled: (Long) -> Int? = { null },
    ) = XpRescore.reprice(h, exercises, { 80.0 }, planned, scheduled, zone)

    /** What a seal would have paid trial [index] of [h]: the same pricer, fed only what came before it. */
    private fun priced(h: List<Pair<WorkoutSession, List<SessionSet>>>, index: Int, quest: Boolean = false): Int {
        val (session, sets) = h[index]
        val book = SessionXp(exercises, h, { 80.0 }, excludeSessionId = session.id, beforeMs = session.startedAtMs)
        val facts = book.factsFor(session, sets, null, quest, zone)
        return book.breakdown(sets, 80.0, facts, doneSetCount = sets.size).total
    }

    @Test
    fun `each trial is priced against only the history before it, oldest first`() {
        val changes = reprice(history.reversed())
        assertEquals(listOf(1L, 2L, 3L, 4L), changes.map { it.sessionId })
        history.indices.forEach { assertEquals("trial ${it + 1}", priced(history, it), changes[it].newXp) }
        // The first trial has nothing before it; its own sets are not its benchmark.
        assertEquals(300, changes[0].oldXp)
    }

    @Test
    fun `a later trial cannot change what an earlier one is worth`() {
        val with = reprice(history)
        val without = reprice(history.take(2))
        assertEquals(without.map { it.newXp }, with.take(2).map { it.newXp })
    }

    @Test
    fun `an imported trial keeps what its import paid`() {
        val withImport = history + trial(9, monday.plusDays(28), xp = 111, imported = true)
        val changes = reprice(withImport)
        assertFalse(changes.any { it.sessionId == 9L })
    }

    @Test
    fun `the quest bonus is kept for the trial that earned it, once a day`() {
        val monday1 = trial(1, monday, xp = 0, presetId = 7)
        val monday2 = trial(2, monday, xp = 0, presetId = 7).let { (s, sets) -> s.copy(startedAtMs = s.startedAtMs + 4_000_000L, completedAtMs = s.completedAtMs!! + 4_000_000L) to sets }
        val h = listOf(monday1, monday2)
        // Rite 7 is scheduled for Monday (1): only the first Monday trial of it earns the bonus.
        val changes = reprice(h, scheduled = { if (it == 7L) 1 else null })
        assertEquals(priced(h, 0, quest = true), changes[0].newXp)
        assertEquals(priced(h, 1, quest = false), changes[1].newXp)
        assertEquals(Xp.QUEST_BONUS, changes[0].newXp - reprice(h, scheduled = { null })[0].newXp)
        // The wrong day of the week pays none.
        assertEquals(reprice(h, scheduled = { null })[0].newXp, reprice(h, scheduled = { 2 })[0].newXp)
    }

    @Test
    fun `a recorded sealed figure moves with the delta and an unrecorded one stays unrecorded`() {
        val recorded = XpRescore.Change(1, "a", 0, oldXp = 100, newXp = 130, amended = true, sealedXp = 90)
        assertEquals(120, recorded.newSealedXp)
        assertEquals(0, XpRescore.Change(1, "a", 0, oldXp = 100, newXp = 10, amended = true, sealedXp = 50).newSealedXp)
        assertNull(XpRescore.Change(1, "a", 0, oldXp = 100, newXp = 130, amended = false, sealedXp = null).newSealedXp)
    }

    private fun changes(vararg deltas: Int) = deltas.mapIndexed { i, d ->
        XpRescore.Change(i.toLong(), "t$i", 0, oldXp = 200, newXp = 200 + d, amended = false, sealedXp = null)
    }

    @Test
    fun `the total moves by the sum of the deltas only`() {
        val plan = XpRescore.plan(changes(30, -10, 0), totalXp = 5_000, rollLevelMark = 0)
        assertEquals(20L, plan.delta)
        assertEquals(5_020L, plan.totalAfter)
        assertEquals(20L, plan.applied)
        assertEquals(2, plan.changed.size)
        assertFalse(plan.clamped)
    }

    @Test
    fun `a rescore that would drop the level holds the total at the floor of the current level`() {
        val total = 5_000L
        val level = Xp.levelFor(total)
        val floor = total - Xp.progress(total).intoLevel
        val plan = XpRescore.plan(changes(-400, -400, -400), totalXp = total, rollLevelMark = level)
        assertTrue(plan.clamped)
        assertEquals(floor, plan.totalAfter)
        assertEquals(level, plan.levelAfter)
        assertEquals(0, plan.inscriptionsDue)
        // Inside the level it may still fall, down to the floor.
        val inside = XpRescore.plan(changes(-30), totalXp = total, rollLevelMark = level)
        assertFalse(inside.clamped)
        assertEquals(total - 30, inside.totalAfter)
    }

    @Test
    fun `a rise pays only the levels that never paid`() {
        val total = 5_000L
        val level = Xp.levelFor(total)
        val toNext = Xp.progress(total).needed - Xp.progress(total).intoLevel
        val up = changes(toNext.toInt() + 5)
        // Everything up to the current level has paid: the new level is owed once.
        assertEquals(1, XpRescore.plan(up, total, rollLevelMark = level).inscriptionsDue)
        // That level already paid once (a refund dropped the lifter below it earlier): nothing is minted again.
        assertEquals(0, XpRescore.plan(up, total, rollLevelMark = level + 1).inscriptionsDue)
        assertEquals(level + 1, XpRescore.plan(up, total, rollLevelMark = level + 1).levelAfter)
    }

    @Test
    fun `running it twice changes nothing the second time`() {
        val before = history.map { (s, sets) -> s.copy(xpAwarded = s.xpAwarded) to sets }
        val first = XpRescore.plan(reprice(before), totalXp = 4_000, rollLevelMark = 0)
        assertTrue(first.changed.isNotEmpty())
        // Apply it: every trial takes its new figure, the total takes the sum.
        val applied = before.map { (s, sets) -> s.copy(xpAwarded = first.changes.first { it.sessionId == s.id }.newXp) to sets }
        val second = XpRescore.plan(reprice(applied), totalXp = first.totalAfter, rollLevelMark = first.levelAfter)
        assertTrue(second.changed.isEmpty())
        assertEquals(0L, second.applied)
        assertEquals(first.totalAfter, second.totalAfter)
        assertEquals(0, second.inscriptionsDue)
    }

    @Test
    fun `an amended trial is flagged so the preview can say its raise was granted`() {
        val h = listOf(trial(1, monday, xp = 10, editedAtMs = 5L))
        assertTrue(reprice(h).single().amended)
        assertFalse(reprice(listOf(trial(1, monday, xp = 10))).single().amended)
    }
}
