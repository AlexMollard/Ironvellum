package com.ironvellum.app.data.cloud

import java.io.File
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class McpWireContractTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "server/production_mcp/contracts").isDirectory }
    private val fixture = Json.parseToJsonElement(File(root, "server/production_mcp/contracts/fixtures.json").readText()).jsonObject
    private val app = fixture.getValue("app").jsonObject
    private val args = fixture.getValue("appArgs").jsonObject

    @Test fun everyAppResponseHasAnExplicitSuccessOrFailureShape() {
        val overview = McpWire.connections(app.getValue("connections").toString())
        assertEquals("Example", overview.connections.single().clientName)
        assertEquals("create_workout_draft", overview.recentAccess.single().tool)
        assertNull(McpWire.connections(app.getValue("emptyConnections").toString()).resourceUrl)
        assertTrue(McpWire.drafts(app.getValue("emptyDrafts").toString()).isEmpty())
        assertEquals(1, McpWire.drafts(app.getValue("drafts").toString()).size)
        McpWire.revoked(app.getValue("revoked").toString())
        McpWire.consent(app.getValue("consentApproved").toString(), true)
        McpWire.consent(app.getValue("consentDenied").toString(), false)
        val approval = args.getValue("ironvellum_mcp_consent").jsonObject
        assertEquals(approval, McpWire.consentArgs(approval.getValue("p_request_id").jsonPrimitive.content, true,
            approval.getValue("p_scopes").jsonArray.map { it.jsonPrimitive.content }, approval.getValue("p_code_hash").jsonPrimitive.content))
        for (status in DraftStatus.entries) {
            val envelope = McpWire.draft(app.getValue(status.name.lowercase()).toString())
            assertEquals(status, envelope.status)
            assertEquals("Upper A", envelope.draft.name)
        }
        for (key in listOf("conflict", "notFound")) {
            val response = app.getValue(key)
            val error = assertThrows(McpRpcFailure::class.java) { McpWire.draft(response.toString()) }
            assertEquals(response.jsonObject["error"]!!.jsonPrimitive.content, error.classification)
        }
        for (invalid in listOf("{}", """{"schemaVersion":2,"ok":true,"drafts":[]}""",
            """{"schemaVersion":1,"ok":"true","drafts":[]}""", """{"schemaVersion":1,"ok":true}""")) {
            assertThrows(Exception::class.java) { McpWire.drafts(invalid) }
        }
    }

    @Test fun receiptBackendIncludesSchemeAndPreservesProjectPathWithoutCredentials() {
        assertEquals("https://example.invalid/project", draftBackendIdentity("https://EXAMPLE.invalid:443/project/"))
        assertEquals("http://example.invalid:54321", draftBackendIdentity("http://example.invalid:54321/"))
        assertNull(draftBackendIdentity("https://user:secret@example.invalid"))
        assertNull(draftBackendIdentity("https://example.invalid?key=test"))
    }

    @Test fun reviewArgumentsEchoOpaqueHashAndApprovalBindsFullPayload() {
        val reviewed = McpWire.draft(app.getValue("pending").toString())
        val approved = McpWire.draft(app.getValue("approved").toString())
        assertTrue(reviewed.sameRevision(approved))
        val changed = app.getValue("approved").toString().replace("Upper A", "Upper B")
        assertFalse(reviewed.sameRevision(McpWire.draft(changed)))
        assertEquals(args["ironvellum_mcp_review_draft"], McpWire.reviewArgs(reviewed.id, reviewed.contentHash, "approve"))
        assertEquals(args["ironvellum_mcp_draft"], McpWire.draftArgs(reviewed.id))
        assertEquals(args["ironvellum_mcp_drafts"], McpWire.listArgs(50))
        assertEquals(args["ironvellum_mcp_revoke_connection"], McpWire.revokeArgs("8a87c86b-f636-43a1-b000-b4cb865524a3"))
    }
}
