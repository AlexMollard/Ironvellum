package com.ironvellum.app.data

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncomingWorkoutShareTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val file get() = File(context.cacheDir, "exports/incoming-share-test.csv")
    private fun uri(): Uri {
        file.parentFile!!.mkdirs()
        return FileProvider.getUriForFile(context, context.packageName + ".exports", file)
    }
    @After fun clean() { file.delete() }

    @Test fun streamAndClipDataAcceptOnlySupportedContentShares() {
        val uri = uri()
        val sent = Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_STREAM, uri)
        assertEquals(uri.toString(), IncomingWorkoutShare.csvUri(sent))
        val clipped = Intent(Intent.ACTION_SEND).setType("application/octet-stream").apply {
            clipData = ClipData.newRawUri("workout", uri)
        }
        assertEquals(uri.toString(), IncomingWorkoutShare.csvUri(clipped))
        assertNull(IncomingWorkoutShare.csvUri(Intent(sent).setType("image/png")))
        assertNull(IncomingWorkoutShare.csvUri(Intent(sent).setAction(Intent.ACTION_VIEW)))
        assertEquals("", IncomingWorkoutShare.csvUri(Intent(sent).putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file))))
        assertEquals("", IncomingWorkoutShare.csvUri(Intent(Intent.ACTION_SEND).setType("text/csv")))
    }

    @Test fun sharedTextIsReadWithoutLosingUnicodeOrQuotedRows() = runBlocking {
        uri()
        val text = "title,exercise_title\n\"Entrenamiento de pierna\",\"Sentadilla, pausa\"\n"
        file.writeText(text, Charsets.UTF_8)
        assertEquals(text, IncomingWorkoutShare.readText(context, uri()))
    }

    @Test fun oversizedContentAndFileUrisAreRejectedBeforeParsing() = runBlocking {
        val uri = uri()
        RandomAccessFile(file, "rw").use { it.setLength(IncomingWorkoutShare.MAX_BYTES.toLong() + 1) }
        assertTrue(runCatching { IncomingWorkoutShare.readText(context, uri) }.exceptionOrNull() is IOException)
        assertTrue(runCatching { IncomingWorkoutShare.readText(context, Uri.fromFile(file)) }.exceptionOrNull() is IOException)
    }
}
