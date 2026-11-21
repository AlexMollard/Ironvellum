package com.ironvellum.app.ui.ai

import com.ironvellum.app.R
import com.ironvellum.app.data.DraftCatalogueTooLarge
import com.ironvellum.app.data.DraftMovementUnavailable
import com.ironvellum.app.data.cloud.DraftStatus
import com.ironvellum.app.data.cloud.McpRpcFailure
import com.ironvellum.app.data.cloud.UnsupportedMcpBackend
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class McpUiPolicyTest {
    @Test fun approvedIsResumableAndTerminalFetchesAreReadOnly() {
        for (state in DraftStatus.entries) {
            assertEquals(state in setOf(DraftStatus.PENDING, DraftStatus.APPROVED), state.canSave)
            assertEquals(state == DraftStatus.PENDING, state.canReject)
        }
    }
    @Test fun fullDraftPageReportsALowerBound() {
        for (count in listOf(0, 1, 49)) {
            assertEquals(com.ironvellum.app.domain.UiText.Plural(R.plurals.ai_ready_count, count), readyCount(count))
        }
        assertEquals(com.ironvellum.app.domain.UiText.Plural(R.plurals.ai_ready_at_least, 50), readyCount(50))
    }

    @Test fun failuresMapToDistinctMessagesWithoutDisplayingBackendText() {
        assertEquals(R.string.ai_stale, problem(McpRpcFailure("conflict")))
        assertEquals(R.string.ai_unavailable, problem(UnsupportedMcpBackend()))
        assertEquals(R.string.ai_movement_missing, problem(DraftMovementUnavailable()))
        assertEquals(R.string.ai_catalogue_limit, problem(DraftCatalogueTooLarge()))
        assertEquals(R.string.ai_network_error, problem(IOException("sensitive diagnostics")))
    }
}
