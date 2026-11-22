package com.ironvellum.app.ui.ai

import com.ironvellum.app.domain.MAX_PROPOSAL_BYTES
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LocalConnectorStreamTest {
    @Test fun exactLimitBytesAreKept() = runBlocking {
        val bytes = ByteArray(MAX_PROPOSAL_BYTES) { (it % 256).toByte() }
        assertArrayEquals(bytes, readDraftStream(ByteArrayInputStream(bytes)))
    }

    @Test fun oversizedStreamReadsOnlyOneBytePastLimit() {
        val input = ByteArrayInputStream(ByteArray(MAX_PROPOSAL_BYTES * 2))
        assertThrows(DraftFileTooLarge::class.java) { runBlocking { readDraftStream(input) } }
        assertEquals(MAX_PROPOSAL_BYTES - 1, input.available())
    }

    @Test fun providerReadErrorIsNotTreatedAsEndOfFile() {
        val input = object : InputStream() {
            private var offset = 0
            override fun read(): Int = if (offset++ < 16) 42 else throw IOException("synthetic provider failure")
        }
        assertThrows(IOException::class.java) { runBlocking { readDraftStream(input) } }
    }
}
