package com.ironvellum.app.data.cloud

import com.ironvellum.app.data.MessageException
import com.ironvellum.app.domain.UiText
import io.github.jan.supabase.exceptions.HttpRequestException
import io.ktor.client.request.HttpRequestBuilder
import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class InboxPollingTest {
    @Test fun networkFailuresRetryTwiceThenReturnToTheNormalPoll() {
        val failures = listOf(
            IOException("connection reset"),
            HttpRequestException("connect timed out", HttpRequestBuilder()),
            MessageException(UiText.Raw("offline"), IOException("unreachable")),
        )
        failures.forEach { error ->
            assertTrue(InboxPolling.shouldRetry(error, 0))
            assertTrue(InboxPolling.shouldRetry(error, 1))
            assertFalse(InboxPolling.shouldRetry(error, 2))
            assertFalse(InboxPolling.shouldRetry(error, 9))
        }
    }

    @Test fun permanentFailuresAreNotRetriedAsNetworkProblems() {
        assertFalse(InboxPolling.shouldRetry(IllegalArgumentException("bad configuration"), 0))
        assertFalse(InboxPolling.shouldRetry(IllegalStateException("not signed in"), 0))
    }

    @Test fun cancellationIsPropagatedEvenWhenWrapped() {
        val cancelled = CancellationException("account changed")
        try {
            InboxPolling.shouldRetry(IllegalStateException(cancelled), 0)
            fail("cancelled work must not turn into a successful or retried delivery")
        } catch (actual: CancellationException) {
            assertSame(cancelled, actual)
        }
    }
}
