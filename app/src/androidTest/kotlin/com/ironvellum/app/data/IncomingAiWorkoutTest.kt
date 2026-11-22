package com.ironvellum.app.data

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.domain.MAX_PROPOSAL_BYTES
import com.ironvellum.app.domain.PortableWorkout
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException

class IncomingAiWorkoutTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun contentsDecideRegardlessOfMimeOrSendView() = runBlocking {
        SharedWorkoutFixture(context).use { sender ->
            val uri = sender.uri
            sender.grant()
            for (mime in listOf("application/json", "text/plain", "application/octet-stream")) {
                for (action in listOf(Intent.ACTION_SEND, Intent.ACTION_VIEW)) {
                    val intent = Intent(action).apply {
                        if (action == Intent.ACTION_SEND) {
                            type = mime
                            putExtra(Intent.EXTRA_STREAM, uri)
                        } else setDataAndType(uri, mime)
                        clipData = ClipData.newRawUri("test workout", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    sender.write("""{"type":"ironvellum.workout"}""")
                    assertTrue(IncomingAiWorkout.read(context, intent) is IncomingAiWorkout.Payload.Portable)
                    sender.write("""{"type":"ironvellum.workout-draft"}""")
                    assertTrue(IncomingAiWorkout.read(context, intent) is IncomingAiWorkout.Payload.SourceBound)
                    sender.write("title,exercise_title\nTraining,Bench Press\n")
                    assertEquals(IncomingAiWorkout.Payload.Csv(uri), IncomingAiWorkout.read(context, intent))
                    sender.write("Unrelated text")
                    assertEquals(IncomingAiWorkout.Payload.Unsupported, IncomingAiWorkout.read(context, intent))
                    sender.write("""{"type":"ironvellum.workout","name":"""" + "x".repeat(MAX_PROPOSAL_BYTES))
                    assertEquals(IncomingAiWorkout.Payload.TooLarge, IncomingAiWorkout.read(context, intent))
                }
            }
            sender.delete()
            val missing = view(uri)
            assertEquals(IncomingAiWorkout.Payload.Unreadable, IncomingAiWorkout.read(context, missing))
        }
    }

    @Test fun onlyAnEffectiveExternalGrantCanProduceAReviewPayload() = runBlocking {
        SharedWorkoutFixture(context).use { sender ->
            sender.write("""{"type":"ironvellum.workout"}""")
            val intent = view(sender.uri)
            assertEquals(IncomingAiWorkout.Payload.Unreadable, IncomingAiWorkout.read(context, intent))
            sender.grant()
            assertTrue(IncomingAiWorkout.read(context, intent) is IncomingAiWorkout.Payload.Portable)
            sender.revoke()
            assertEquals(IncomingAiWorkout.Payload.Unreadable, IncomingAiWorkout.read(context, intent))
            sender.write("title,exercise_title\nTraining,Bench Press\n")
            sender.grant()
            assertEquals(IncomingAiWorkout.Payload.Csv(sender.uri), IncomingAiWorkout.read(context, intent))
            sender.revoke()
            assertTrue(runCatching { IncomingWorkoutShare.readSharedText(context, sender.uri) }.exceptionOrNull() is IOException)
        }
        val ownFile = File.createTempFile("ambient-readable", ".json", File(context.cacheDir, "exports").apply { mkdirs() })
        try {
            ownFile.writeText("""{"type":"ironvellum.workout"}""")
            val uri = FileProvider.getUriForFile(context, context.packageName + ".exports", ownFile)
            assertEquals(IncomingAiWorkout.Payload.Unreadable, IncomingAiWorkout.read(context, view(uri)))
            assertTrue(IncomingAiWorkout.readPickedFile(context.contentResolver, uri)
                is IncomingAiWorkout.Payload.Portable)
        } finally {
            ownFile.delete()
        }
    }

    @Test fun directTextRequiresAnExplicitWorkoutAndRejectsFileUris() = runBlocking {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        assertNull(IncomingAiWorkout.read(context, Intent(send).putExtra(Intent.EXTRA_TEXT, "ordinary shared text")))
        val code = "```json\n{\"type\":\"ironvellum.workout\"}\n```"
        assertTrue(IncomingAiWorkout.read(context, Intent(send).putExtra(Intent.EXTRA_TEXT, code))
            is IncomingAiWorkout.Payload.Portable)
        val noGrant = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("file:///not-opened"), "application/json")
        assertEquals(IncomingAiWorkout.Payload.Unreadable, IncomingAiWorkout.read(context, noGrant))
    }

    @Test fun escapedTypeIsIdenticalThroughShareTextFileAndPaste() = runBlocking {
        val code = """{"typ\u0065":"ironvellum.work\u006fut","schemaVersion":1,"name":"Escaped type",
            "warmup":"AUTO","cooldown":"NONE","entries":[{"exerciseKey":"bench press","sets":3,"reps":8,"loadKg":40}]}"""
        val shared = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, code)
        val expected = PortableWorkout.parse(code.encodeToByteArray()).fingerprint
        SharedWorkoutFixture(context).use { sender ->
            sender.write(code)
            sender.grant()
            for (payload in listOf(IncomingAiWorkout.read(context, shared), IncomingAiWorkout.read(context, view(sender.uri)),
                IncomingAiWorkout.paste(code))) {
                assertTrue(payload is IncomingAiWorkout.Payload.Portable)
                assertEquals(expected, PortableWorkout.parse((payload as IncomingAiWorkout.Payload.Portable).bytes).fingerprint)
            }
        }
        val unrelated = """{"note":{"type":"ironvellum.workout"}}"""
        assertNull(IncomingAiWorkout.read(context, Intent(shared).putExtra(Intent.EXTRA_TEXT, unrelated)))
    }

    @Test fun explicitlyMarkedOversizedSharedTextReportsTheLimit() = runBlocking {
        val padding = "é".repeat(MAX_PROPOSAL_BYTES / 2)
        val marked = """{"type":"ironvellum.workout","name":"$padding"}"""
        assertTrue(marked.length < MAX_PROPOSAL_BYTES)
        assertEquals(IncomingAiWorkout.Payload.TooLarge, readSharedText(marked))
        assertEquals(IncomingAiWorkout.Payload.TooLarge, readSharedText(
            """{"type":"ironvellum.workout-draft","name":"${"x".repeat(MAX_PROPOSAL_BYTES)}"}"""))
        assertNull(readSharedText("""{"note":{"type":"ironvellum.workout"},"name":"$padding"}"""))
        assertNull(readSharedText("ordinary text " + "x".repeat(MAX_PROPOSAL_BYTES)))
    }

    @Test fun explicitlyMarkedDamagedSharedTextReachesTheMatchingParser() = runBlocking {
        val portable = """{"type":"ironvellum.workout","entries":["""
        val sourceBound = """{"schemaVersion":1,"type":"ironvellum.workout-draft","entries":["""
        val portablePayload = readSharedText(portable)
        val sourcePayload = readSharedText(sourceBound)
        assertTrue(portablePayload is IncomingAiWorkout.Payload.Portable)
        assertTrue(sourcePayload is IncomingAiWorkout.Payload.SourceBound)
        assertArrayEquals(portable.encodeToByteArray(), (portablePayload as IncomingAiWorkout.Payload.Portable).bytes)
        assertArrayEquals(sourceBound.encodeToByteArray(), (sourcePayload as IncomingAiWorkout.Payload.SourceBound).bytes)
        assertNull(readSharedText("""{"note":{"type":"ironvellum.workout"},"missing":["""))
    }

    @Test fun textIngressRejectsUnpairedSurrogatesAndPreservesSupplementaryScalars() = runBlocking {
        val template = """{"type":"ironvellum.workout","schemaVersion":1,"name":"NAME","warmup":"AUTO","cooldown":"NONE","entries":[{"exerciseKey":"bench press","sets":3,"reps":8,"loadKg":40}]}"""
        for (name in listOf("\uD800", "\uDC00", "🏋")) {
            val code = template.replace("NAME", name)
            for (payload in listOf(IncomingAiWorkout.paste(code), readSharedText(code))) {
                if (name == "🏋") {
                    val bytes = (payload as IncomingAiWorkout.Payload.Portable).bytes
                    assertArrayEquals(code.encodeToByteArray(), bytes)
                    assertEquals(name, PortableWorkout.parse(bytes).draft.name)
                } else {
                    assertEquals(IncomingAiWorkout.Payload.InvalidCode, payload)
                }
            }
        }
    }

    private suspend fun readSharedText(text: String) = IncomingAiWorkout.read(
        context, Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
    )

    private fun view(uri: Uri) = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/json")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
