package com.ironvellum.app.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedCardTest {
    private val pullUp = Exercise(1, "Pull-up", MuscleGroup.PULL, false)
    private val squat = Exercise(3, "Barbell Squat", MuscleGroup.LEGS, true)
    private val exercises = listOf(pullUp, squat).associateBy { it.id }

    private fun session(id: Long = 1, imported: Boolean = false) =
        WorkoutSession(id = id, label = "Pull", startedAtMs = 1_000_000, completedAtMs = 1_000_000 + 40 * 60_000L, imported = imported)

    private val weighted = listOf(
        SessionSet(exerciseId = 1, exerciseName = "Pull-up", setIndex = 0, reps = 6, weightKg = 12.5, modifiers = "Weighted", done = true),
        SessionSet(exerciseId = 3, exerciseName = "Barbell Squat", exercisePosition = 1, setIndex = 0, reps = 5, weightKg = 120.0, done = true),
    )

    private val day = 86_400_000L
    private val earlier = listOf(
        SessionSet(exerciseId = 1, exerciseName = "Pull-up", setIndex = 0, reps = 6, weightKg = 5.0, modifiers = "Weighted", done = true),
        SessionSet(exerciseId = 3, exerciseName = "Barbell Squat", exercisePosition = 1, setIndex = 0, reps = 5, weightKg = 100.0, done = true),
    )

    /** The card of [sets] done a day after a lighter session, so its lifts really are records. */
    private fun card(sets: List<SessionSet> = weighted, imported: Boolean = false): FeedCard? {
        val before = session(1)
        val now = session(2, imported).let { it.copy(startedAtMs = before.startedAtMs + day, completedAtMs = before.completedAtMs!! + day) }
        return FeedCardJson.cards(listOf(before to earlier, now to sets), exercises)[2L]
    }

    @Test
    fun cardHasOnlyFriendSafeKeys() {
        val parsed = Json.parseToJsonElement(FeedCardJson.write(card()!!)).jsonObject
        assertEquals(listOf("v", "hardSets", "volumeKg", "prs", "levels"), parsed.keys.toList())
        val prs = parsed.getValue("prs").jsonArray
        assertTrue(prs.isNotEmpty())
        for (pr in prs) assertEquals(listOf("label", "reps", "weightKg", "durationSec"), pr.jsonObject.keys.toList())
    }

    @Test
    fun feedCardHasNoBodyweightDerivedField() {
        // An archer session at 80 kg body weight beside a weighted pull-up: nothing of the 80 may appear.
        val archer = SessionSet(exerciseId = 1, exerciseName = "Pull-up", setIndex = 1, reps = 5, modifiers = "Archer", done = true)
        val sets = weighted + archer
        val built = card(sets)!!
        val text = FeedCardJson.write(built).lowercase()
        assertFalse("e1rm" in text)
        assertFalse("body" in text)
        // Loaded work only: the pull-up's belt is not a whole load, so the squat alone counts.
        assertEquals(600.0, built.volumeKg, 0.0)
        assertEquals(12.5, built.prs.first { it.label.startsWith("Pull-up") }.weightKg!!, 0.0)
    }

    @Test
    fun cardUnderServerCap() {
        val text = FeedCardJson.write(
            FeedCard(40, 12345.0, (1..12).map { WebSnapshot.PrTag("x".repeat(80) + it, 5, 100.0, null) }, Muscle.entries.associateWith { 3 }),
        )
        assertTrue(text.length <= FeedCardJson.MAX_CHARS)
    }

    @Test
    fun prsCapAtSix() {
        val prs = (1..9).map { WebSnapshot.PrTag("Lift $it", 5, 50.0, null) }
        val parsed = Json.parseToJsonElement(FeedCardJson.write(FeedCard(3, 1.0, prs, emptyMap()))).jsonObject
        assertEquals(6, parsed.getValue("prs").jsonArray.size)
    }

    @Test
    fun oversizedCardDropsPrsFromTheEndNotTheFigures() {
        val prs = (1..6).map { WebSnapshot.PrTag("L".repeat(500) + it, 5, 50.0, null) }
        val text = FeedCardJson.write(FeedCard(9, 100.0, prs, mapOf(Muscle.entries.first() to 2)))
        assertTrue(text.length <= FeedCardJson.MAX_CHARS)
        val parsed = Json.parseToJsonElement(text).jsonObject
        assertEquals("9", parsed.getValue("hardSets").jsonPrimitive.content)
        assertTrue(parsed.getValue("prs").jsonArray.size < 6)
    }

    @Test
    fun importedSessionHasNoCard() {
        assertNull(card(imported = true))
    }

    @Test
    fun emptyWorkoutHasNoCard() {
        assertNull(card(weighted.map { it.copy(done = false) }))
        assertNull(card(emptyList()))
    }

    @Test
    fun holdPrCarriesSecondsAndNoReps() {
        val plank = Exercise(4, "Plank", MuscleGroup.CORE, false, ExerciseMetric.HOLD)
        val sets = listOf(SessionSet(exerciseId = 4, exerciseName = "Plank", setIndex = 0, reps = 0, durationSec = 60, done = true))
        val before = session(1) to listOf(sets.single().copy(durationSec = 30))
        val after = session(2).copy(startedAtMs = 1_000_000 + day, completedAtMs = 1_000_000 + day + 60_000) to sets
        val built = FeedCardJson.cards(listOf(before, after), mapOf(4L to plank)).getValue(2L)
        val hold = built.prs.single()
        assertEquals(0, hold.reps)
        assertEquals(60, hold.durationSec)
        val json = FeedCardJson.element(built).getValue("prs").jsonArray.single().jsonObject
        assertEquals(JsonNull, json.getValue("weightKg"))
    }

    @Test
    fun levelsUseTheTuningThresholds() {
        assertEquals(0, FeedCardJson.level(0.0))
        assertEquals(1, FeedCardJson.level(0.5))
        assertEquals(1, FeedCardJson.level(WebSnapshotTuning.LEVEL_SOME - 0.1))
        assertEquals(2, FeedCardJson.level(WebSnapshotTuning.LEVEL_SOME))
        assertEquals(3, FeedCardJson.level(WebSnapshotTuning.LEVEL_MOST))
        val levels = card()!!.levels
        assertTrue(levels.isNotEmpty() && levels.values.all { it in 1..3 })
    }

    @Test
    fun cardsCoverEveryCompletedWorkoutOnceAndSkipImported() {
        val history = listOf(session(1) to weighted, session(2, imported = true) to weighted, session(3) to weighted.map { it.copy(done = false) })
        val cards = FeedCardJson.cards(history, exercises)
        assertEquals(setOf(1L), cards.keys)
        assertNotNull(cards[1L])
    }

    @Test
    fun aRepeatedLiftIsNotAPrTheSecondTime() {
        val first = session(1)
        val again = session(2).copy(startedAtMs = first.startedAtMs + day, completedAtMs = first.completedAtMs!! + day)
        val cards = FeedCardJson.cards(listOf(first to weighted, again to weighted), exercises)
        assertTrue(cards.getValue(2L).prs.isEmpty())
        assertNull((Json.parseToJsonElement(FeedCardJson.write(cards.getValue(2L))) as JsonObject)["e1rm"]?.jsonPrimitive?.contentOrNull)
    }
}
