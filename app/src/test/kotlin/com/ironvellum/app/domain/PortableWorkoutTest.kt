package com.ironvellum.app.domain

import com.ironvellum.app.data.localconnector.LocalCatalogue
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class PortableWorkoutTest {
    private val entry = """{"exerciseKey":"bench press","sets":3,"reps":8,"loadKg":40}"""
    private val otherEntry = """{"exerciseKey":"push-up","sets":2,"reps":10,"loadKg":null}"""
    private val document = """{"type":"ironvellum.workout","schemaVersion":1,"name":"Upper body","warmup":"AUTO","cooldown":"NONE","entries":[$entry,$otherEntry]}"""

    private fun parse(source: String) = PortableWorkout.parse(source.encodeToByteArray())

    @Test fun readerAllowsSixteenContainerLevelsAndRejectsSeventeen() {
        fun nested(levels: Int) = "[".repeat(levels) + "0" + "]".repeat(levels)
        assertEquals(nested(16), LocalJsonReader(nested(16)).document().toString())
        assertThrows(IllegalArgumentException::class.java) { LocalJsonReader(nested(17)).document() }
    }

    @Test fun fileAndPasteHaveSameIdentity() {
        val file = parse(document)
        val paste = parse(" \r\n```json\r\n$document\r\n```\r\n ")
        assertEquals(file.fingerprint, paste.fingerprint)
        assertEquals(file.draft, paste.draft)
        assertEquals(64, file.fingerprint.length)
        assertEquals(file.draft, WorkoutDraft.parse(file.draft.toJson()))
    }

    @Test fun canonicalDecimalsAndObjectOrder() {
        val digest = parse(document).fingerprint
        val reordered = """{"entries":[{"loadKg":4e1,"reps":8,"sets":3,"exerciseKey":"bench press"},$otherEntry],"cooldown":"NONE","warmup":"AUTO","name":"Upper body","schemaVersion":1,"type":"ironvellum.workout"}"""
        assertEquals(digest, parse(reordered).fingerprint)
        assertEquals(digest, parse(document.replace(":40", ":40.0")).fingerprint)
        assertNotEquals(digest, parse(document.replace("Upper body", "Push day")).fingerprint)
        assertNotEquals(digest, parse(document.replace("$entry,$otherEntry", "$otherEntry,$entry")).fingerprint)
        assertNotEquals(digest, parse(document.replace(":40", ":41")).fingerprint)

        val weight = "40.00000000000000000000000000000000001"
        val precise = parse(document.replace(":40", ":$weight"))
        assertEquals(weight.toBigDecimal(), precise.draft.entries.first().loadKg)
        assertEquals(weight.toBigDecimal(), WorkoutDraft.parse(precise.draft.toJson()).entries.first().loadKg)
        assertNotEquals(digest, precise.fingerprint)
    }

    @Test fun rejectUntrustedMalformedInput() {
        val invalid = listOf(
            "Explanation\n```json\n$document\n```",
            "```json\n$document\n```\nExplanation",
            "```json\n$document\n```\n```json\n$document\n```",
            "```json$document```",
            document + document,
            document.replace("\"sets\":3", "\"sets\":3,\"se\\u0074s\":2"),
            document.replace("\"name\":", "\"sender\":\"AI\",\"name\":"),
            document.replace("Upper body", "\\uD800"),
            document.replace("Upper body", " Upper body"),
            document.replace("bench press", "Bench Press"),
            document.replace(":40", ":\"40\""),
            document.replace(":40", ":1001"),
            document.replace(":40", ":1e-400"),
            document.replace(":40", ":1.${"0".repeat(127)}"),
            document.replace(":40", ":${"[".repeat(16)}0${"]".repeat(16)}"),
            "```json\n$document\n```".padEnd(MAX_PROPOSAL_BYTES + 1),
        )
        invalid.forEach { source ->
            assertThrows(source.take(240), IllegalArgumentException::class.java) { parse(source) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            PortableWorkout.parse(document.encodeToByteArray() + byteArrayOf(0xc0.toByte(), 0xaf.toByte()))
        }
    }

    @Test fun unknownMovementRemainsUnresolved() {
        val key = "unlisted plate press"
        assertFalse(LocalCatalogue.acceptsDraft(key))
        assertEquals(key, parse(document.replace("bench press", key)).draft.entries.first().exerciseKey)
    }

    @Test fun publishedExampleAndInstructionsUseThePortableContract() {
        val contracts = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "docs/training-contracts") }
            .first(File::isDirectory)
        val example = PortableWorkout.parse(File(contracts, "portable-workout-v1.example.json").readBytes())
        val fencedSample = Regex("```json\\R[\\s\\S]+?\\R```").find(PortableWorkout.instructions())
        val sample = parse(requireNotNull(fencedSample).value)
        assertEquals(example.fingerprint, sample.fingerprint)
        assertTrue(example.draft.entries.all { LocalCatalogue.acceptsDraft(it.exerciseKey) })
    }
}
