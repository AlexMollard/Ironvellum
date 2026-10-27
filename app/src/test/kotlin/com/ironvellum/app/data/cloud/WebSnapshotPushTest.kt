package com.ironvellum.app.data.cloud

import com.ironvellum.app.domain.WebSnapshotJson
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
        assertEquals(WireLimits.SNAPSHOT_MAX_BYTES + 1, (result as SnapshotDecision.TooLarge).bytes)
    }

    @Test fun sizeCountsUtf8Bytes() {
        val result = snapshotDecision("é".repeat(WireLimits.SNAPSHOT_MAX_BYTES / 2 + 1), null)
        assertTrue(result is SnapshotDecision.TooLarge)
    }
}
