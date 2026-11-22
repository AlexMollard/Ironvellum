package com.ironvellum.app.data

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

    @Test fun csvSniffingRecognisesTheThreeExistingExportHeaders() {
        assertTrue(IncomingWorkoutShare.isCsvHeader("\"Date\",\"Workout Name\",\"Set Order\"\n"))
        assertTrue(IncomingWorkoutShare.isCsvHeader("\"title\",\"exercise_title\"\r\n"))
        assertTrue(IncomingWorkoutShare.isCsvHeader("Date,Exercise,Category,Weight (kg)\n"))
        assertFalse(IncomingWorkoutShare.isCsvHeader("name,sets,reps\n"))
        assertFalse(IncomingWorkoutShare.isCsvHeader("{\"type\":\"ironvellum.workout\"}"))
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
