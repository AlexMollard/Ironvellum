package com.ironvellum.app.domain

import com.ironvellum.app.data.cloud.WireLimits
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class WebSnapshotJsonTest {
    @Test fun writingTwiceIsByteIdentical() {
        val snapshot = WebSnapshotBuilder.build(WebSnapshotFixtures.input())
        assertEquals(WebSnapshotJson.write(snapshot), WebSnapshotJson.write(snapshot))
    }
    @Test fun contentHashIgnoresBuiltAt() {
        val snapshot = WebSnapshotBuilder.build(WebSnapshotFixtures.input())
        val original = WebSnapshotJson.contentHash(WebSnapshotJson.write(snapshot))
        assertEquals(original, WebSnapshotJson.contentHash(WebSnapshotJson.write(snapshot.copy(builtAtMs = snapshot.builtAtMs + 1))))
        assertNotEquals(original, WebSnapshotJson.contentHash(WebSnapshotJson.write(snapshot.copy(profile = snapshot.profile.copy(totalXp = snapshot.profile.totalXp + 1)))))
    }
    @Test fun writerEnforcesWirePrecision() {
        val snapshot = WebSnapshotBuilder.build(WebSnapshotFixtures.input())
        val session = snapshot.sessions.values.first().copy(e1rm = mapOf("Pull-up#1" to 31.234))
        val lift = snapshot.lifts.first()
        val variation = lift.variations.first()
        val point = JsonArray(listOf(JsonPrimitive(1L), JsonPrimitive(2L), JsonPrimitive(7.891), JsonPrimitive(0)))
        val changed = snapshot.copy(sessions = mapOf("1" to session),
            lifts = listOf(lift.copy(variations = listOf(variation.copy(points = listOf(point))))))
        val json = Json.parseToJsonElement(WebSnapshotJson.write(changed)).jsonObject
        val estimate = json.getValue("sessions").jsonObject.getValue("1").jsonObject.getValue("e1rm")
            .jsonObject.getValue("Pull-up#1").jsonPrimitive.double
        val chartValue = json.getValue("lifts").jsonArray[0].jsonObject.getValue("variations")
            .jsonArray[0].jsonObject.getValue("points").jsonArray[0].jsonArray[2].jsonPrimitive.double
        assertEquals(31.2, estimate, 0.0)
        assertEquals(7.89, chartValue, 0.0)
    }
    @Test fun emptySnapshotIsValidAndTiny() {
        val input = WebSnapshotFixtures.input().copy(history = emptyList(), exercises = emptyMap(), stats = emptyList())
        val json = WebSnapshotJson.write(WebSnapshotBuilder.build(input))
        val value = Json.parseToJsonElement(json).jsonObject
        assertTrue(json.toByteArray(Charsets.UTF_8).size < 8192)
        assertTrue(value.getValue("sessions").jsonObject.isEmpty())
        assertTrue(value.getValue("lifts").jsonArray.isEmpty())
    }
    @Test fun everyGroupCoversEveryMuscle() {
        assertEquals(Muscle.entries.toSet(), WebSnapshotTuning.GROUPS.keys)
    }
    @Test fun fiveYearSnapshotFitsUnderCap() {
        val input = WebSnapshotFixtures.input()
        val exercises = (1L..60L).associateWith { Exercise(it, "Synthetic lift $it", MuscleGroup.PULL, true) }
        val history = (0 until 5 * 52 * 4).map { n ->
            val at = input.nowMs - (5L * 52 * 4 - n) * 7 * 86_400_000L / 4
            val session = WorkoutSession(id = n + 1L, label = "Training", startedAtMs = at, completedAtMs = at + 3_600_000)
            val sets = (0 until 8).flatMap { exercise ->
                val id = ((n * 8 + exercise) % 60 + 1).toLong()
                (0 until 4).map { index -> SessionSet(exerciseId = id, exerciseName = exercises.getValue(id).name,
                    exercisePosition = exercise, setIndex = index, reps = 8, weightKg = 60.0, done = true) }
            }
            session to sets
        }
        val size = WebSnapshotJson.write(WebSnapshotBuilder.build(input.copy(history = history, exercises = exercises))).toByteArray(Charsets.UTF_8).size
        assertTrue("$size bytes", size < WireLimits.SNAPSHOT_MAX_BYTES / 2)
    }
}
