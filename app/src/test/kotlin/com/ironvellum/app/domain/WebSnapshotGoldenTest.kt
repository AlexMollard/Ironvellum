package com.ironvellum.app.domain

import com.ironvellum.app.data.cloud.CloudSync
import com.ironvellum.app.data.cloud.Inbox
import com.ironvellum.app.data.cloud.InboxItem
import java.time.Instant
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSnapshotGoldenTest {
    private val oldThemed = com.ironvellum.app.data.Words.themed
    @org.junit.Before fun plainWords() = com.ironvellum.app.data.Words.useForTest(false)
    @org.junit.After fun restoreWords() = com.ironvellum.app.data.Words.useForTest(oldThemed)

    private val root: File = run {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "supabase/migrations").isDirectory) dir = dir.parentFile
        requireNotNull(dir)
    }

    private fun fixture(name: String, actual: String) {
        val file = File(root, "web/test/fixtures/$name")
        if (System.getProperty("golden.update") == "true") {
            file.parentFile.mkdirs()
            file.writeText(actual, Charsets.UTF_8)
        } else {
            assertEquals("Run with -Dgolden.update=true to regenerate $name", file.readText(Charsets.UTF_8), actual)
        }
    }

    @Test fun goldenFixtureIsCurrent() {
        val input = WebSnapshotFixtures.input()
        val snapshot = WebSnapshotBuilder.build(input)
        fixture("snapshot-v1.json", WebSnapshotJson.write(snapshot))
        val rows = buildJsonObject {
            put("workouts", buildJsonArray {
                input.history.forEach { (session, _) ->
                    add(buildJsonObject {
                        put("localId", session.id)
                        put("origin", if (session.imported) "imported" else "synced")
                        put("id", "fixture-${session.id}")
                        put("label", session.label)
                        put("title", session.title)
                        put("note", session.note)
                        put("startedAt", Instant.ofEpochMilli(session.startedAtMs).toString())
                        put("completedAt", session.completedAtMs?.let { Instant.ofEpochMilli(it).toString() })
                        put("editedAt", session.editedAtMs?.let { Instant.ofEpochMilli(it).toString() })
                        put("xp", session.xpAwarded)
                    })
                }
            })
            put("sets", buildJsonObject {
                input.history.forEach { (session, sets) ->
                    val origin = if (session.imported) "imported" else "synced"
                    val indexes = CloudSync.wireSetIndexes(sets)
                    put("$origin:fixture-${session.id}", buildJsonArray {
                        sets.forEachIndexed { index, set ->
                            add(buildJsonObject {
                                put("exerciseName", set.exerciseName)
                                put("exercisePosition", set.exercisePosition)
                                put("setIndex", indexes[index])
                                put("reps", set.reps)
                                put("weightKg", set.weightKg)
                                put("modifiers", set.modifiers)
                                put("done", set.done)
                                put("durationSec", set.durationSec)
                                put("distanceM", set.distanceM)
                                put("grade", set.grade)
                                put("kind", set.kind.wire)
                                put("rpe", set.rpe)
                                put("supersetGroup", set.supersetGroup)
                            })
                        }
                    })
                }
            })
        }
        assertEquals(14, rows.getValue("workouts").jsonArray.size)
        assertEquals(input.history.map { it.first.id.toString() }.toSet(), snapshot.sessions.keys)
        val byWorkout = rows.getValue("sets").jsonObject
        input.history.forEach { (session, _) ->
            val origin = if (session.imported) "imported" else "synced"
            val keys = byWorkout.getValue("$origin:fixture-${session.id}").jsonArray.map { row ->
                val set = row.jsonObject
                "${set.getValue("exerciseName").jsonPrimitive.content}#${set.getValue("setIndex").jsonPrimitive.content}"
            }.toSet()
            val entry = snapshot.sessions.getValue(session.id.toString())
            assertTrue(keys.containsAll(entry.e1rm.keys))
            assertTrue(keys.containsAll(entry.peakSets))
        }
        fixture("rows.json", Json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), rows))
        val format = WorkoutShare::class.java.getDeclaredMethod("figures", List::class.java, Boolean::class.javaPrimitiveType)
        format.isAccessible = true
        val parity = buildJsonObject {
            put("loadCases", buildJsonArray {
                for (kg in listOf(102.058, 0.0, 0.45359237, 1000.5)) for (unit in LoadUnit.entries) add(buildJsonObject {
                    put("kg", kg); put("unit", unit.name.lowercase()); put("text", Loads.format(kg, unit))
                })
            })
            // The phone's Inbox.unread rule, for the web inbox badge: unread when never seen or newer than the last visit.
            put("inboxUnreadCases", buildJsonArray {
                val cases = listOf(
                    listOf(100L, 200L, 300L) to null, listOf(100L, 200L, 300L) to 200L, listOf(100L, 200L, 300L) to 300L,
                    listOf(100L, 200L, 300L) to 50L, emptyList<Long>() to null, emptyList<Long>() to 10L,
                )
                for ((times, seen) in cases) add(buildJsonObject {
                    put("itemsAtMs", buildJsonArray { times.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
                    put("seenAtMs", seen?.let { kotlinx.serialization.json.JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
                    put("unread", Inbox(times.map { InboxItem.FriendRequest(it, "a", "n") }, seen).unread)
                })
            })
            // The lifter folio's figures (LifterSummary), pinned for the web port: XP sum, per-week rate, last-workout days, strength series.
            put("lifterSummaryCases", buildJsonArray {
                val day = 24L * 60 * 60 * 1000
                val now = 200 * day
                val cases = listOf(
                    emptyList<LifterSummary.Entry>(),
                    listOf(LifterSummary.Entry(now - day / 2, 120, 700)),
                    listOf(LifterSummary.Entry(now - day, 100, 900), LifterSummary.Entry(now - 20 * day, 150, 700), LifterSummary.Entry(now - 9 * day, 80, 950)),
                    listOf(LifterSummary.Entry(now - 3 * day, 90, 500), LifterSummary.Entry(now - 28 * day, 90, 480), LifterSummary.Entry(now - 29 * day, 90, 470), LifterSummary.Entry(null, 10, 800)),
                )
                for (entries in cases) add(buildJsonObject {
                    put("sessions", buildJsonArray {
                        entries.forEach { e -> add(buildJsonObject { put("completedAtMs", e.completedAtMs); put("xp", e.xpAwarded); put("strength", e.strengthScore) }) }
                    })
                    put("nowMs", now)
                    val f = LifterSummary.of(entries, now)
                    put("count", f.count); put("xp", f.xp); put("perWeek", String.format(java.util.Locale.ENGLISH, "%.1f", f.perWeek))
                    put("daysSince", f.daysSince)
                    put("series", buildJsonArray { f.strengthSeries.forEach { add(kotlinx.serialization.json.JsonPrimitive(it.toInt())) } })
                    put("peak", f.peak)
                })
            })
            put("formatSetsCases", buildJsonArray {
                for (values in listOf(emptyList(), listOf(5), listOf(5, 5, 5), listOf(5, 4, 3))) add(buildJsonObject {
                    put("sets", buildJsonArray { values.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
                    put("text", format.invoke(WorkoutShare, values, false) as String)
                })
            })
        }
        fixture("parity.json", Json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), parity))
    }
}
