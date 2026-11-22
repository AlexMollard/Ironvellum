package com.ironvellum.app.domain

import com.ironvellum.app.data.localconnector.*
import java.io.File
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class LocalConnectorVectorTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "docs/training-contracts").isDirectory }
    private val contracts = File(root, "docs/training-contracts")

    @Test fun packagedLocalDraftPolicyExcludesExactlyTheSeedAssistanceMachines() {
        val rules = Json.parseToJsonElement(File(contracts,"draft-movement-policy-v1.json").readText()).jsonObject
        assertEquals(setOf("schemaVersion","requiredMetric","excludedKeys"), rules.keys)
        assertEquals(1,rules.getValue("schemaVersion").jsonPrimitive.int)
        assertEquals("REPS",rules.getValue("requiredMetric").jsonPrimitive.content)
        val actual = LocalCatalogue.movements.filter { (key, row) -> row.metric == "REPS" && BodyShare.isAssisted(key) }.keys
        assertEquals(setOf("assisted chin-up","assisted dip","assisted pull-up"),actual)
        val excluded = rules.getValue("excludedKeys").jsonArray.map { it.jsonPrimitive.content }
        assertEquals(actual.sorted(),excluded)
        for ((key,row) in LocalCatalogue.movements) {
            assertEquals(key, row.metric == "REPS" && key !in excluded, LocalCatalogue.acceptsDraft(key))
        }
    }

    @Test fun contextEncoderRefusesToReturnAnOversizedDocument() {
        val context = LocalContext("ironvellum.training-context", 1, "d", "s", 0, LocalCatalogue.identity,
            LocalCoverage(0,0,0,0,0,0,0,0), emptyList(),
            listOf(LocalPreset("p", "x".repeat(MAX_CONTEXT_BYTES), "AUTO", "NONE", emptyList())), progress(emptyList()))
        val error = assertThrows(LocalExportException::class.java) { context.encode() }
        assertEquals(LocalFailure.FILE_LIMIT, error.reason)
    }

    @Test fun completeV2FixtureMatchesEncoderDecoderAndIndependentMetricTotals() {
        val bytes = File(contracts,"fixtures/v2/context.json").readBytes()
        val context = Json.decodeFromString<ReadContext>(bytes.decodeToString())
        assertArrayEquals(bytes, context.encode())
        val actual = readProgress(context.workouts, context.movements.associateBy { it.key })
        assertEquals(context.progress, actual)
        assertEquals(12L, actual.completedReps)
        assertEquals(20.0, actual.externalVolumeKg, 0.0)
        assertEquals(18L, actual.holdSeconds)
        assertEquals(105L, actual.activitySeconds)
        assertEquals(103.5, actual.distanceM, 0.0)
        assertEquals(3L, actual.attempts)
        val assisted = actual.lifts.single { it.exerciseKey == "assisted dip" }
        assertNull(assisted.maxExternalLoadKg)
        assertEquals(12.5, assisted.maxAssistanceKg!!, 0.0)
        val entry = context.savedWorkouts.single().entries.single()
        assertEquals(entry.scheme, readScheme(entry.schemeRaw))
        val invalid = Json.decodeFromString<ReadContext>(File(contracts,"fixtures/v2/invalid-progress.json").readText())
        assertNotEquals(readProgress(invalid.workouts, invalid.movements.associateBy { it.key }), invalid.progress)
    }

    @Test fun sharedWireVectorsMatchProductionSerializerProgressAndEntireCatalogue() {
        val dataset = "b7553464-9b0a-47ea-bd63-94f1f2c5d171"
        val snapshot = "14a23d60-061f-4cd1-bd5e-dae345f37231"
        val key = "bench press"
        val workouts = listOf(LocalWorkout("local:09e0f524-efa5-4d65-9c29-b940ad7a2751", 1700000000000, 1700000900000,
            listOf(LocalSet(0, key, 8, 12.3456789), LocalSet(1, "push-up", 5, null))))
        val presets = listOf(LocalPreset("local:f8266819-bde5-4ecb-9f66-5f006027c11c", "Café 🏋", "AUTO", "NONE",
            listOf(LocalEntry(key, 3, 8, 12.3456789))))
        val time = 1700001000000L
        val context = LocalContext("ironvellum.training-context", 1, dataset, snapshot, time, LocalCatalogue.identity,
            LocalCoverage(1,0,2,0,0,1,0,0), workouts, presets, progress(workouts))
        val bytes = context.encode()
        val sha = LocalConnectorFiles.sha256(bytes)
        val proposal = buildJsonObject {
            put("type", "ironvellum.workout-draft"); put("schemaVersion", 1)
            put("datasetId", dataset); put("snapshotId", snapshot); put("contextSha256", sha)
            put("draftId", "caad118d-ff23-4028-a618-114bc2cf4992"); put("sender", "Personal connector")
            put("createdAtMs", time + 1); put("expiresAtMs", time + 86400000L)
            put("draft", buildJsonObject {
                put("schemaVersion", 1); put("name", "Café 🏋"); put("warmup", "AUTO"); put("cooldown", "NONE")
                put("entries", buildJsonArray {
                    add(buildJsonObject { put("exerciseKey", key); put("sets", 3); put("reps", 8); put("loadKg", 12.3456789) })
                })
            })
        }
        val original = (" " + proposal + "\r\n").encodeToByteArray()
        val proposalSha = LocalConnectorFiles.proposal(original).sha256
        val manifest = Json.parseToJsonElement(File(contracts, "movements-v1.json").readText()).jsonObject
        val expected = LocalCatalogue.movements.map { (k, e) -> buildJsonObject {
            put("key", k); put("name", e.name); put("metric", e.metric)
        } }
        assertEquals(expected, manifest.getValue("movements").jsonArray.toList())
        assertEquals(584, LocalCatalogue.identity.count)
        assertEquals("6b8bea45f8781884088d99fe8fc76c0df4c5755a8c2d1e9914629710bfa18a66", LocalCatalogue.identity.sha256)
        val vectors = mapOf("context.json" to bytes, "proposal.json" to original,
            "SHA256SUMS" to ("$sha  context.json\n$proposalSha  proposal.json\n").encodeToByteArray())
        vectors.forEach { (name, content) ->
            val file = File(contracts, "fixtures/$name")
            if (System.getenv("IRONVELLUM_EXPORT_LOCAL_VECTORS") == "1") {
                file.parentFile.mkdirs()
                file.writeBytes(content)
            }
            assertArrayEquals(name, content, file.readBytes())
        }
    }
}
