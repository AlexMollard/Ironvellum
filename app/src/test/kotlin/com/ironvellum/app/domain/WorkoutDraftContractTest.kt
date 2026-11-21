package com.ironvellum.app.domain

import com.ironvellum.app.data.Seed
import com.ironvellum.app.data.DraftMovements
import com.ironvellum.app.data.DraftMovementCandidate
import com.ironvellum.app.data.db.ExerciseEntity
import java.io.File
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class WorkoutDraftContractTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "server/production_mcp/contracts").isDirectory }
    private val contracts = File(root, "server/production_mcp/contracts")
    private val example = Json.parseToJsonElement(File(contracts, "fixtures.json").readText()).jsonObject
        .getValue("app").jsonObject.getValue("pending").jsonObject.getValue("draft").jsonObject.getValue("draft").jsonObject
    private val entry get() = example.getValue("entries").jsonArray.single().jsonObject
    private fun entries(vararg items: JsonObject) = JsonObject(example + ("entries" to JsonArray(items.toList())))
    private fun fails(body: JsonObject) = assertThrows(IllegalArgumentException::class.java) { WorkoutDraft.parse(body) }

    @Test fun fixtureAndBoundaryValuesSurviveReview() {
        assertEquals("Upper A", WorkoutDraft.parse(example).name)
        for (load in listOf(JsonNull, JsonPrimitive(0), JsonPrimitive(1000))) {
            val max = JsonObject(entry + mapOf("sets" to JsonPrimitive(50), "reps" to JsonPrimitive(1000), "loadKg" to load))
            val draft = WorkoutDraft.parse(entries(*Array(20) { max }))
            assertEquals(20, draft.entries.size)
            assertEquals(1000, draft.entries.last().reps)
        }
        val longName = "🏋".repeat(80)
        assertEquals(longName, WorkoutDraft.parse(JsonObject(example + ("name" to JsonPrimitive(longName)))).name)
    }

    @Test fun unknownMissingAndFutureFieldsRefuseTheWholePayload() {
        example.keys.forEach { fails(JsonObject(example - it)) }
        entry.keys.forEach { fails(entries(JsonObject(entry - it))) }
        fails(JsonObject(example + ("schedule" to JsonNull)))
        fails(entries(JsonObject(entry + ("scheme" to JsonPrimitive("straight")))))
        for (version in listOf(JsonPrimitive(2), JsonPrimitive("1"))) fails(JsonObject(example + ("schemaVersion" to version)))
        for (name in listOf("", " A", "B ", "a\nb", "x".repeat(81))) fails(JsonObject(example + ("name" to JsonPrimitive(name))))
        for (key in listOf("warmup", "cooldown")) fails(JsonObject(example + (key to JsonPrimitive("CUSTOM"))))
        fails(entries())
        fails(entries(*Array(21) { entry }))
    }

    @Test fun targetsAreStrictAndNeverCoerced() {
        val invalid = mapOf(
            "sets" to listOf(JsonPrimitive(0), JsonPrimitive(51), JsonPrimitive(1.5), JsonPrimitive("3")),
            "reps" to listOf(JsonPrimitive(0), JsonPrimitive(1001), JsonPrimitive(false)),
            "loadKg" to listOf(JsonPrimitive(-0.1), JsonPrimitive(1000.001), JsonPrimitive("40")),
            "exerciseKey" to listOf(JsonPrimitive(""), JsonPrimitive("Bench Press"), JsonPrimitive("x".repeat(129))),
        )
        for ((key, values) in invalid) for (value in values) fails(entries(JsonObject(entry + (key to value))))
    }

    @Test fun fullSeedIsTheTrackedManifest() {
        val catalogue = DraftMovements.catalogue(Seed.exercises)
        val manifest = buildJsonObject {
            put("schemaVersion", 1)
            put("movements", JsonArray(catalogue.map { (key, row) -> buildJsonObject {
                put("key", key)
                put("name", row.name)
                put("metric", row.metric)
            } }))
        }
        val file = File(contracts, "movements-v1.json")
        if (System.getenv("IRONVELLUM_EXPORT_MOVEMENTS") == "1") {
            file.writeText(Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), manifest) + "\n")
        }
        assertEquals(ExerciseMetric.entries.map { it.name }.toSet(), catalogue.values.map { it.metric }.toSet())
        assertEquals(Seed.exercises.size, catalogue.size)
        assertEquals(manifest, Json.parseToJsonElement(file.readText()))
    }

    private fun resolve(draft: WorkoutDraft, rows: List<ExerciseEntity>) = DraftMovements.resolve(draft, rows.asSequence().map {
        DraftMovementCandidate(it.id, it.name, it.custom, it.metric)
    })

    @Test fun customTimedAmbiguousAndUnknownMovementsCannotResolve() {
        val bench = Seed.exercises.single { it.name == "Bench Press" }
        val draft = WorkoutDraft.parse(example)
        val withId = bench.copy(id = 72)
        assertEquals(listOf(72L), resolve(draft, listOf(withId)))
        for (rows in listOf(emptyList(), listOf(withId.copy(custom = true)), listOf(withId.copy(metric = "HOLD")),
            listOf(withId, withId.copy(id = 73, name = " Bench Press ")))) {
            assertThrows(IllegalArgumentException::class.java) { resolve(draft, rows) }
        }
        val hold = Seed.exercises.first { it.metric == "HOLD" }
        val timed = WorkoutDraft.parse(entries(JsonObject(entry + ("exerciseKey" to JsonPrimitive(Titles.normaliseName(hold.name))))))
        assertThrows(IllegalArgumentException::class.java) { resolve(timed, listOf(hold.copy(id = 2, metric = "REPS"))) }
        assertThrows(IllegalArgumentException::class.java) { DraftMovements.catalogue(listOf(withId, withId)) }
        assertThrows(IllegalArgumentException::class.java) { DraftMovements.catalogue(listOf(withId.copy(custom = true))) }
    }
}
