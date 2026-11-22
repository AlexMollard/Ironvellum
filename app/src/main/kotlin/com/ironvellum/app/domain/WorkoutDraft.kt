package com.ironvellum.app.domain

import java.math.BigDecimal
import java.util.Collections
import kotlinx.serialization.json.*

/** The exact v1 targets the lifter reviewed. Preserve decimal precision until the local Room write. */
internal data class WorkoutDraft private constructor(
    val name: String,
    val warmup: Preparation,
    val cooldown: Preparation,
    val entries: List<Entry>,
) {
    enum class Preparation { AUTO, NONE }
    data class Entry(val exerciseKey: String, val sets: Int, val reps: Int, val loadKg: BigDecimal?)

    fun toJson(): JsonObject = buildJsonObject {
        put("schemaVersion", 1)
        put("name", name)
        put("warmup", warmup.name)
        put("cooldown", cooldown.name)
        put("entries", buildJsonArray {
            for (entry in entries) {
                add(buildJsonObject {
                    put("exerciseKey", entry.exerciseKey)
                    put("sets", entry.sets)
                    put("reps", entry.reps)
                    put("loadKg", JsonPrimitive(entry.loadKg?.stripTrailingZeros()))
                })
            }
        })
    }

    class EditSizeLimit : IllegalArgumentException()

    companion object {
        fun parseEdited(body: JsonObject): WorkoutDraft {
            val json = body.toString()
            if (json.length > MAX_PROPOSAL_BYTES || json.encodeToByteArray().size > MAX_PROPOSAL_BYTES) throw EditSizeLimit()
            return parse(LocalJsonReader(json).document() as JsonObject)
        }

        fun parse(body: JsonObject): WorkoutDraft {
            require(body.keys == setOf("schemaVersion", "name", "warmup", "cooldown", "entries"))
            body.integer("schemaVersion", 1..1)
            val name = body.string("name")
            require(name.codePointCount(0, name.length) in 1..80 && name.trim(' ') == name && name.none(Char::isISOControl))
            val array = body.getValue("entries") as? JsonArray ?: throw IllegalArgumentException("entries")
            require(array.size in 1..20)
            val entries = array.map { value ->
                val entry = value as? JsonObject ?: throw IllegalArgumentException("entry")
                require(entry.keys == setOf("exerciseKey", "sets", "reps", "loadKg"))
                val key = entry.string("exerciseKey")
                require(key.codePointCount(0, key.length) in 1..128 && Titles.normaliseName(key) == key)
                val load = entry.getValue("loadKg").takeUnless { it == JsonNull }?.let {
                    val weight = entry.decimal("loadKg")
                    require(weight >= BigDecimal.ZERO && weight <= BigDecimal.valueOf(1000))
                    weight.stripTrailingZeros()
                }
                Entry(key, entry.integer("sets", 1..50), entry.integer("reps", 1..1000), load)
            }
            return WorkoutDraft(name, Preparation.valueOf(body.string("warmup")), Preparation.valueOf(body.string("cooldown")),
                Collections.unmodifiableList(entries))
        }

        private fun JsonObject.string(key: String): String {
            val value = getValue(key) as? JsonPrimitive
            require(value != null && value.isString)
            return value.content
        }

        private fun JsonObject.decimal(key: String): BigDecimal {
            val value = getValue(key) as? JsonPrimitive
            require(value != null && !value.isString)
            return value.content.toBigDecimal()
        }

        private fun JsonObject.integer(key: String, range: IntRange): Int {
            val value = decimal(key)
            require(value >= range.first.toBigDecimal() && value <= range.last.toBigDecimal() && value.stripTrailingZeros().scale() <= 0)
            return value.toInt()
        }
    }
}
