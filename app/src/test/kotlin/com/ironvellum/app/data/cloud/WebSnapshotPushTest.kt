package com.ironvellum.app.data.cloud

import com.ironvellum.app.domain.WebSnapshotJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSnapshotPushTest {
    private val json = "{\"builtAtMs\":1,\"value\":42}"

    @Test fun unchangedContentIsNotUploaded() {
        assertEquals(SnapshotDecision.Unchanged, snapshotDecision(json, WebSnapshotJson.contentHash(json)))
        assertEquals(SnapshotDecision.Unchanged, snapshotDecision("{\"builtAtMs\":2,\"value\":42}", WebSnapshotJson.contentHash(json)))
    }

    @Test fun changedContentUploads() {
        assertEquals(SnapshotDecision.Upload, snapshotDecision("{\"builtAtMs\":1,\"value\":43}", WebSnapshotJson.contentHash(json)))
    }

    @Test fun missingServerRowUploads() {
        assertEquals(SnapshotDecision.Upload, snapshotDecision(json, null))
    }

    @Test fun overCapIsRefusedBeforeUpload() {
        val result = snapshotDecision("x".repeat(WireLimits.SNAPSHOT_MAX_BYTES + 1), null)
        assertTrue(result is SnapshotDecision.TooLarge)
        assertTrue((result as SnapshotDecision.TooLarge).bytes >= WireLimits.SNAPSHOT_MAX_BYTES + 1)
    }

    /** `[1,2,3,4]` entries: 10 compact bytes each, 14 as jsonb prints them. */
    private fun points(entries: Int) = buildString {
        append("{\"points\":[")
        repeat(entries) { if (it > 0) append(','); append("[1,2,3,4]") }
        append("]}")
    }

    @Test fun sizeIsMeasuredInTheTextFormTheServerChecks() {
        // Compact JSON under the cap, jsonb text over it: the case that used to fail with 23514 forever.
        val json = points((WireLimits.SNAPSHOT_MAX_BYTES - 100_000) / 10)
        assertTrue(json.toByteArray(Charsets.UTF_8).size < WireLimits.SNAPSHOT_MAX_BYTES)
        assertTrue(snapshotDecision(json, null) is SnapshotDecision.TooLarge)
    }

    @Test fun aSnapshotWhoseTextFormFitsUploads() {
        assertEquals(SnapshotDecision.Upload, snapshotDecision(points(250_000), null))
    }

    @Test fun theEstimateIsNeverBelowPostgresText() {
        // What jsonb prints: a space after each ':' and ',' outside strings.
        fun printed(element: JsonElement): String = when (element) {
            is JsonObject -> element.entries.joinToString(", ", "{", "}") { "\"${it.key}\": ${printed(it.value)}" }
            is JsonArray -> element.joinToString(", ", "[", "]") { printed(it) }
            else -> element.toString()
        }
        val json = "{\"a\":\"x, y: z\",\"b\":[1,2.5,{\"c\":null}],\"d\":\"q\\\",\\\"r\",\"e\":{}}"
        val real = printed(Json.parseToJsonElement(json)).toByteArray(Charsets.UTF_8).size
        val estimate = WebSnapshotJson.jsonbTextBytes(json)
        assertTrue("estimate $estimate is below the printed $real", estimate >= real)
        assertTrue("estimate $estimate is far above the printed $real", estimate <= real + real / 50 + 1)
    }

    @Test fun sizeCountsUtf8Bytes() {
        val result = snapshotDecision("é".repeat(WireLimits.SNAPSHOT_MAX_BYTES / 2 + 1), null)
        assertTrue(result is SnapshotDecision.TooLarge)
    }
}
