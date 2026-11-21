package com.ironvellum.app.data.cloud

import com.ironvellum.app.domain.WorkoutDraft
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

internal enum class DraftStatus { PENDING, APPROVED, ACCEPTED, REJECTED, EXPIRED }

internal data class DraftEnvelope(
    val id: String, val revision: Int, val contentHash: String, val clientName: String,
    val createdAtMs: Long, val expiresAtMs: Long, val status: DraftStatus, val draft: WorkoutDraft,
) {
    fun sameRevision(other: DraftEnvelope): Boolean = copy(status = other.status) == other
}

@Serializable
internal data class McpConnection(val id: String, val clientName: String, val scopes: List<String>,
    val createdAtMs: Long, val expiresAtMs: Long, val revokedAtMs: Long?)

@Serializable
internal data class McpAccess(val requestId: String, val clientName: String, val tool: String,
    val atMs: Long, val result: String, val draftId: String?)

internal data class McpConnections(val resourceUrl: String?, val connections: List<McpConnection>, val recentAccess: List<McpAccess>)

internal class McpRpcFailure(val classification: String) : Exception(classification)

internal object McpWire {
    private val digest = Regex("[0-9a-f]{64}")

    private fun response(raw: String, vararg fields: String): JsonObject {
        val obj = Json.parseToJsonElement(raw).jsonObject
        require(obj["schemaVersion"] == JsonPrimitive(1))
        if (obj["ok"] == JsonPrimitive(false)) {
            require(obj.keys == setOf("schemaVersion", "ok", "error"))
            throw McpRpcFailure(Json.decodeFromJsonElement<String>(obj.getValue("error")))
        }
        require(obj["ok"] == JsonPrimitive(true) && obj.keys == setOf("schemaVersion", "ok", *fields))
        return obj
    }

    private fun envelope(value: JsonElement): DraftEnvelope {
        val obj = value.jsonObject
        require(obj.keys == setOf("id", "revision", "contentHash", "clientName", "createdAtMs", "expiresAtMs", "status", "draft"))
        require(obj["revision"] == JsonPrimitive(1))
        val hash = Json.decodeFromJsonElement<String>(obj.getValue("contentHash"))
        require(digest.matches(hash))
        return DraftEnvelope(
            Json.decodeFromJsonElement<String>(obj.getValue("id")), 1, hash,
            Json.decodeFromJsonElement<String>(obj.getValue("clientName")),
            Json.decodeFromJsonElement<Long>(obj.getValue("createdAtMs")), Json.decodeFromJsonElement<Long>(obj.getValue("expiresAtMs")),
            DraftStatus.entries.single { it.name.lowercase() == Json.decodeFromJsonElement<String>(obj.getValue("status")) },
            WorkoutDraft.parse(obj.getValue("draft").jsonObject),
        )
    }

    fun connections(raw: String): McpConnections {
        val obj = response(raw, "resourceUrl", "connections", "recentAccess")
        return McpConnections(Json.decodeFromJsonElement(obj.getValue("resourceUrl")),
            Json.decodeFromJsonElement(obj.getValue("connections")), Json.decodeFromJsonElement(obj.getValue("recentAccess")))
    }

    fun drafts(raw: String): List<DraftEnvelope> = response(raw, "drafts").getValue("drafts").jsonArray.map(::envelope)
    fun draft(raw: String): DraftEnvelope = envelope(response(raw, "draft").getValue("draft"))
    fun revoked(raw: String) = require(response(raw, "revoked")["revoked"] == JsonPrimitive(true))
    fun consent(raw: String, approved: Boolean) = require(response(raw, "approved")["approved"] == JsonPrimitive(approved))

    fun listArgs(limit: Int) = buildJsonObject { require(limit in 1..50); put("p_limit", limit) }
    fun draftArgs(id: String) = buildJsonObject { put("p_draft_id", id) }
    fun revokeArgs(id: String) = buildJsonObject { put("p_grant_id", id) }
    fun reviewArgs(id: String, hash: String, action: String) = buildJsonObject {
        put("p_draft_id", id)
        put("p_hash", hash)
        put("p_action", action)
    }
    fun consentArgs(id: String, approved: Boolean, scopes: List<String>, codeHash: String?) = buildJsonObject {
        put("p_request_id", id)
        put("p_approved", approved)
        put("p_scopes", JsonArray(scopes.map(::JsonPrimitive)))
        put("p_code_hash", codeHash)
    }
}
