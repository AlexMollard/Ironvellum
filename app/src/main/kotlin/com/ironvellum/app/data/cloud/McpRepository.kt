package com.ironvellum.app.data.cloud

import com.ironvellum.app.data.Repository
import com.ironvellum.app.data.db.DraftReceipt
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject

/** App-session RPCs only. This adapter holds no OAuth credential or cross-account response cache. */
internal class McpRepository(private val accounts: AccountRepository, repository: Repository) {
    private val store = repository.draftStore
    val scopeChanges get() = accounts.scopeChanges
    fun captureScope() = accounts.captureScope()
    fun isCurrent(scope: AccountRepository.Scope) = accounts.isCurrent(scope)

    /** The UI keeps this handle from the fetch through the explicit review action. */
    sealed interface Review {
        val envelope: DraftEnvelope
    }

    private class ScopedReview(val scope: AccountRepository.Scope, override val envelope: DraftEnvelope) : Review
    data class Saved(val localPresetId: Long, val acknowledgementPending: Boolean)

    private suspend fun <T> rpc(scope: AccountRepository.Scope, name: String, args: JsonObject, decode: (String) -> T): T =
        coroutineScope {
            accounts.withScope(scope) { client ->
                decode(client.postgrest.rpc("ironvellum_mcp_$name", args).data)
            }
        }

    suspend fun connections(scope: AccountRepository.Scope) =
        rpc(scope, "connections", JsonObject(emptyMap()), McpWire::connections)

    suspend fun revoke(scope: AccountRepository.Scope, grantId: String) =
        rpc(scope, "revoke_connection", McpWire.revokeArgs(grantId), McpWire::revoked)

    suspend fun drafts(scope: AccountRepository.Scope, limit: Int = 50): List<Review> =
        rpc(scope, "drafts", McpWire.listArgs(limit)) { raw -> McpWire.drafts(raw).map { ScopedReview(scope, it) } }

    suspend fun draft(scope: AccountRepository.Scope, id: String): Review =
        rpc(scope, "draft", McpWire.draftArgs(id)) { raw ->
            ScopedReview(scope, McpWire.draft(raw).also { require(it.id == id) })
        }

    private suspend fun review(review: ScopedReview, action: String): DraftEnvelope {
        val original = review.envelope
        return rpc(review.scope, "review_draft", McpWire.reviewArgs(original.id, original.contentHash, action)) { raw ->
            McpWire.draft(raw).also { require(original.sameRevision(it)) { "Reviewed revision changed" } }
        }
    }

    suspend fun reject(review: Review) {
        require(review.envelope.status == DraftStatus.PENDING)
        require(review(review as ScopedReview, "reject").status == DraftStatus.REJECTED)
    }

    suspend fun save(review: Review): Saved {
        review as ScopedReview
        val scope = review.scope
        accounts.requireCurrent(scope)
        require(review.envelope.status == DraftStatus.PENDING || review.envelope.status == DraftStatus.APPROVED)
        store.validate(scope, review.envelope)
        val approved = review(review, "approve")
        require(approved.status in setOf(DraftStatus.APPROVED, DraftStatus.ACCEPTED))
        val receipt = store.save(accounts, scope, approved)
        val pending = receipt.ackPending && !tryAcknowledge(scope, receipt)
        accounts.requireCurrent(scope)
        return Saved(receipt.localPresetId, pending)
    }

    /** Reopening Settings/Train can finish old acknowledgements under a fresh scope without reimporting. */
    suspend fun retryAcknowledgements(scope: AccountRepository.Scope): Int {
        accounts.requireCurrent(scope)
        val remaining = store.pending(scope).count { !tryAcknowledge(scope, it) }
        accounts.requireCurrent(scope)
        return remaining
    }

    private suspend fun tryAcknowledge(scope: AccountRepository.Scope, receipt: DraftReceipt): Boolean {
        require(receipt.backend == scope.backend && receipt.userId == scope.userId)
        try {
            rpc(scope, "review_draft", McpWire.reviewArgs(receipt.draftId, receipt.contentHash, "accept")) { raw ->
                val accepted = McpWire.draft(raw)
                require(accepted.id == receipt.draftId && accepted.contentHash == receipt.contentHash && accepted.status == DraftStatus.ACCEPTED)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            accounts.requireCurrent(scope)
            return false
        }
        store.markAcknowledged(accounts, scope, receipt)
        return true
    }
}
