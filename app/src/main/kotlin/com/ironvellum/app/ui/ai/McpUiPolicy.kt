package com.ironvellum.app.ui.ai

import com.ironvellum.app.R
import com.ironvellum.app.data.DraftCatalogueTooLarge
import com.ironvellum.app.data.DraftMovementUnavailable
import com.ironvellum.app.data.cloud.*
import com.ironvellum.app.domain.UiText

internal val DraftStatus.canSave get() = this == DraftStatus.PENDING || this == DraftStatus.APPROVED
internal val DraftStatus.canReject get() = this == DraftStatus.PENDING

internal fun readyCount(count: Int): UiText =
    if (count >= MCP_DRAFT_PAGE_LIMIT) UiText.plural(R.plurals.ai_ready_at_least, count)
    else UiText.plural(R.plurals.ai_ready_count, count)

internal fun problem(error: Exception): Int = when {
    error is DraftCatalogueTooLarge -> R.string.ai_catalogue_limit
    error is DraftMovementUnavailable -> R.string.ai_movement_missing
    error is UnsupportedMcpBackend || Cloud.isNotSetUp(error) -> R.string.ai_unavailable
    error is StaleDraftReview -> R.string.ai_stale
    error is McpRpcFailure && error.classification in setOf("conflict", "not_found") -> R.string.ai_stale
    error is McpRpcFailure && error.classification in setOf("invalid_draft", "unsupported_movement") -> R.string.ai_invalid
    error is IllegalArgumentException -> R.string.ai_invalid
    else -> R.string.ai_network_error
}
