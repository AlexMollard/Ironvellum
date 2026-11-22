package com.ironvellum.app.domain

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A workout for local review, identified by its original normalized targets. */
internal class PortableWorkout private constructor(val draft: WorkoutDraft, val fingerprint: String) {
    companion object {
        private const val TYPE = "ironvellum.workout"
        private const val FENCE = "```"

        fun parse(bytes: ByteArray): PortableWorkout {
            require(bytes.size in 1..MAX_PROPOSAL_BYTES)
            val source = try {
                Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
            } catch (_: CharacterCodingException) {
                throw IllegalArgumentException("Invalid UTF-8")
            }
            val root = LocalJsonReader(unwrap(source)).document() as? JsonObject
                ?: throw IllegalArgumentException("Expected a workout object")
            require(root["type"] == JsonPrimitive(TYPE))
            val draft = WorkoutDraft.parse(JsonObject(root - "type"))
            val canonical = buildJsonObject {
                put("type", TYPE)
                for ((key, value) in draft.toJson()) put(key, value)
            }
            return PortableWorkout(draft, LocalConnectorFiles.sha256(canonical.toString().encodeToByteArray()))
        }

        private fun unwrap(source: String): String {
            val trimmed = source.trim(' ', '\t', '\r', '\n')
            if (!trimmed.startsWith(FENCE)) return trimmed

            val start = trimmed.indexOf('\n')
            val end = trimmed.lastIndexOf('\n')
            require(start >= 0 && end > start)
            require(trimmed.substring(0, start).removeSuffix("\r") == "${FENCE}json")
            require(trimmed.substring(end + 1) == FENCE)
            return trimmed.substring(start + 1, end)
        }

        fun instructions(): String = """
            Return one Ironvellum workout as JSON in a UTF-8 .json file or a single fenced json block. Keep the entire return within 64 KiB and omit surrounding prose and extra fields.
            Use type "ironvellum.workout", schemaVersion 1, and a workout name of 1 to 80 characters without control characters or leading or trailing spaces.
            Set warmup and cooldown to "AUTO" for the app's default preparation or "NONE" to skip it.
            Include 1 to 20 entries in workout order. Use explicit Ironvellum movement keys (lowercase, trimmed, 1 to 128 characters). Choose built-in movements with fixed repetition targets and no machine assistance. Use whole numbers for sets (1 to 50) and reps per set (1 to 1000). Provide loadKg as a number from 0 to 1000 for external load in kilograms, or null when no load is specified.
            Import the workout in Ironvellum and review the movements and targets before you choose Save. Saving creates an unscheduled workout.

            ${FENCE}json
            {
              "type": "ironvellum.workout",
              "schemaVersion": 1,
              "name": "Upper body",
              "warmup": "AUTO",
              "cooldown": "NONE",
              "entries": [
                {"exerciseKey": "bench press", "sets": 3, "reps": 8, "loadKg": 40},
                {"exerciseKey": "push-up", "sets": 2, "reps": 10, "loadKg": null}
              ]
            }
            $FENCE
        """.trimIndent()
    }
}
