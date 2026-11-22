package com.ironvellum.app.domain

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class LocalConnectorFilesTest {
    private val body = """{"schemaVersion":1,"name":"🏋 café","entries":[{"exerciseKey":"bench press","sets":3,"reps":8,"loadKg":12.3456789}],"warmup":"AUTO","cooldown":"NONE"}"""
    private val id = "caad118d-ff23-4028-a618-114bc2cf4992"
    private val proposal = """{"type":"ironvellum.workout-draft","schemaVersion":1,"datasetId":"$id","snapshotId":"$id","contextSha256":"${"0".repeat(64)}","draftId":"$id","sender":"My PC","createdAtMs":100,"expiresAtMs":200,"draft":$body}"""
    private fun rejected(bytes: ByteArray) = assertThrows(IllegalArgumentException::class.java) { LocalConnectorFiles.proposal(bytes) }

    @Test fun preservesUnicodeDecimalAndHashesExactInputBytes() {
        val input = " $proposal\r\n".encodeToByteArray()
        val result = LocalConnectorFiles.proposal(input)
        assertEquals("🏋 café", result.draft.name)
        assertEquals("12.3456789", result.draft.entries.single().loadKg.toString())
        assertEquals(MessageDigest.getInstance("SHA-256").digest(input).joinToString("") { "%02x".format(it) }, result.sha256)
        assertNotEquals(result.sha256, LocalConnectorFiles.proposal(proposal.encodeToByteArray()).sha256)
    }

    @Test fun duplicateEscapedNestedMembersAndPrivateUnknownFieldsAreRejected() {
        for (bad in listOf(
            proposal.replace("\"sets\":3", "\"sets\":3,\"s\\u0065ts\":4"),
            proposal.replace("\"sender\"", "\"privateNote\":\"hidden\",\"sender\""),
            proposal.replace("\"name\"", "\"health\":{},\"name\""),
            proposal.replace("\"sets\":3", "\"sets\":true"),
            proposal.replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
            proposal.replace("12.3456789", "1e9999"),
            proposal.replace("12.3456789", "1e-9999"),
            proposal.replace("12.3456789", "NaN"),
            proposal.replace("12.3456789", "01"),
            proposal.replace("12.3456789", "true"),
            proposal.replace("🏋 café", "\\uD800"),
            proposal.replace("🏋 café", "broken\\q"),
            proposal.replace("🏋 café", "raw\nline"),
            proposal.replace("\"sets\":3", "\"sets\":3,"),
            proposal.replace("\"createdAtMs\":100", "\"createdAtMs\":\"100\""),
        )) rejected(bad.encodeToByteArray())
    }

    @Test fun refusesOversizedMalformedAndDeepDocuments() {
        rejected(ByteArray(65537) { 32 })
        rejected(byteArrayOf(0xc3.toByte(), 0x28))
        rejected(("[".repeat(17) + "0" + "]".repeat(17)).encodeToByteArray())
        rejected((proposal + "{}").encodeToByteArray())
        rejected(proposal.replace("200", "2592000101").encodeToByteArray())
        val valid = proposal.encodeToByteArray()
        assertEquals("My PC", LocalConnectorFiles.proposal(valid + ByteArray(65536 - valid.size) { 32 }).sender)
    }

    @Test fun outerVersionAndTimestampsRequireIntegerTokensButDraftTargetsAcceptIntegralDecimals() {
        val bodyVariants = proposal.replace("\"sets\":3", "\"sets\":3.0").replace("\"reps\":8", "\"reps\":8e0")
        val entry = LocalConnectorFiles.proposal(bodyVariants.encodeToByteArray()).draft.entries.single()
        assertEquals(3, entry.sets)
        assertEquals(8, entry.reps)
        for ((field, original, exponent) in listOf(
            Triple("schemaVersion", "1", "1e0"), Triple("createdAtMs", "100", "1e2"), Triple("expiresAtMs", "200", "2E+2")
        )) {
            for (spelling in listOf(exponent, original + ".0")) {
                val bytes = proposal.replaceFirst("\"$field\":$original", "\"$field\":$spelling").encodeToByteArray()
                assertThrows("$field accepts $spelling", IllegalArgumentException::class.java) { LocalConnectorFiles.proposal(bytes) }
            }
        }
    }
}
