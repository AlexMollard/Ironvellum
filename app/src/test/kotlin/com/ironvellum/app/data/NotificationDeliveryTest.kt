package com.ironvellum.app.data

import androidx.work.ListenableWorker
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class NotificationDeliveryTest {
    @Test fun temporaryFailuresKeepTheCurrentOccurrenceQueuedForTwoRetries() = runBlocking {
        for (attempt in 0..1) {
            val result = notificationDelivery(
                attempt = attempt,
                deliver = { throw IOException("database temporarily unavailable") },
                failed = {},
                scheduleNext = { error("booking the next occurrence would cancel this retry") },
            )
            assertEquals(ListenableWorker.Result.retry(), result)
        }
    }

    @Test fun exhaustedRetriesBookTheNextOccurrenceRatherThanLoopingForever() = runBlocking {
        var nextBooked = false
        val result = notificationDelivery(
            attempt = 2,
            deliver = { throw IOException("still unavailable") },
            failed = {},
            scheduleNext = { nextBooked = true },
        )
        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue("exhaustion must not lose the daily or weekly chain", nextBooked)
    }

    @Test fun cancellationDoesNotRetryOrBookAnotherOccurrence() = runBlocking {
        val cancelled = CancellationException("notification disabled")
        for (attempt in listOf(0, 2)) {
            try {
                notificationDelivery(
                    attempt = attempt,
                    deliver = { throw cancelled },
                    failed = { error("cancellation is not a delivery failure") },
                    scheduleNext = { error("cancelled work must not replace the current schedule") },
                )
                fail("the worker's cancellation must propagate")
            } catch (actual: CancellationException) {
                assertSame(cancelled, actual)
            }
        }
    }
}
