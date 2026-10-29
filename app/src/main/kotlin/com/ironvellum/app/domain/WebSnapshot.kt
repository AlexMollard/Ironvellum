package com.ironvellum.app.domain

import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.math.round

/** Versioned, device-derived public viewer data. Ordered collections preserve the wire order. */
@Serializable
data class WebSnapshot(
    val v: Int = 1, val builtAtMs: Long, val zone: String, val unit: String, val effort: String, val words: String,
    val rir: Map<String, Int>, val appearance: Appearance, val profile: Profile, val deeds: List<Deed>,
    val muscles: Map<String, String>, val week: Week, val months: Map<String, Month>,
    val sessions: Map<String, SessionEntry>, val lifts: List<Lift>, val fatigue: Fatigue, val trends: Trends,
    /** Additive to v1: a web that does not know it ignores it. */
    val routines: Routines = Routines(),
) {
    @Serializable data class AccentSet(val preset: String?, val primary: String, val secondary: String, val bright: String,
        val deep: String, val muted: String, val panelTint: String, val panelTintBorder: String, val fill: String,
        val text: String, val tint: String, val border: String, val reward: String, val onPrimary: String, val onSecondary: String)
    @Serializable data class Appearance(val mode: String, val themed: Boolean, val dark: AccentSet, val light: AccentSet)
    @Serializable data class Profile(val name: String, val level: Int, val totalXp: Long, val intoLevel: Long, val needed: Long,
        val streakDays: Int, val longestStreakDays: Int, val deedsEarned: Int, val deedsTotal: Int, val figure: String,
        val titleId: String?, val crestId: String?, val workouts: Int, val firstMs: Long?)
    @Serializable data class Deed(val id: String, val name: String, val rarity: String, val category: String, val description: String, val atMs: Long)
    @Serializable data class Week(val startDay: String, val endDay: String, val workouts: Int, val hardSets: Double,
        val volumeKg: Double, val prevVolumeKg: Double, val xp: Int, val plannedDays: Int?, val days: List<String>)
    @Serializable data class Month(val workouts: Int, val perWeek: Double, val weeks: List<MonthWeek>, val topLifts: List<TopLift>)
    @Serializable data class MonthWeek(val start: String, val hardSets: Double)
    @Serializable data class TopLift(val name: String, val art: String?, val sessions: Int)
    @Serializable data class SessionEntry(val origin: String, val min: Int, val exercises: Int, val hardSets: Int, val volumeKg: Double, val peaks: Int,
        val xp: Int, val strength: Int, val muscleLevels: Map<String, Int>, val groupLevels: Map<String, Int>,
        val prs: List<PrTag>, val peakSets: List<String>, val e1rm: Map<String, Double>)
    @Serializable data class PrTag(val label: String, val reps: Int, val weightKg: Double?, val durationSec: Int?)
    @Serializable data class Lift(val name: String, val art: String?, val metric: String, val addsToBody: Boolean,
        val byE1rm: Boolean, val muscleSummary: String, val muscleLevels: Map<String, Int>, val variations: List<LiftVariation>)
    @Serializable data class LiftVariation(val key: String, val label: String, val sessions: Int, val sets: Int, val lastMs: Long?,
        val perWeek: Double?, val volumeKg: Double?, val peak: LiftPeak?, val heaviest: Heaviest?,
        val chartMetric: String, val chartUnit: String,
        val points: List<kotlinx.serialization.json.JsonArray>, val setRecords: List<SetRecord>)
    @Serializable data class LiftPeak(val reps: Int, val weightKg: Double?, val durationSec: Int?, val atMs: Long, val e1rmKg: Double?)
    @Serializable data class Heaviest(val reps: Int, val weightKg: Double, val atMs: Long)
    @Serializable data class SetRecord(val position: Int, val reps: Int, val weightKg: Double?, val atMs: Long)
    @Serializable data class Fatigue(val atMs: Long, val newLifter: Boolean, val hasHistory: Boolean,
        val levels: Map<String, Int>, val worked: List<FatigueWorked>)
    @Serializable data class FatigueWorked(val muscle: String, val level: Int, val cause: String?, val freshBy: String?)
    @Serializable data class Trends(val floor: Double, val heavy: Double, val windows: Map<String, TrendWindow>,
        val weeks: List<TrendWeek>, val days: List<String>, val daysPerWeek: Double,
        val recordsPerMonth: List<RecordsMonth>, val records30d: Int, val recent: List<RecentRecord>)
    @Serializable data class TrendWindow(val from: Long, val to: Long, val perWeek: Map<String, Double>, val state: Map<String, String>)
    @Serializable data class TrendWeek(val start: String, val hardSets: Double, val volumeKg: Double, val workouts: Int, val groups: Map<String, Double>)
    @Serializable data class RecordsMonth(val month: String, val count: Int)
    /** The saved routines as the phone's Train screen words them, the week they cover, and the custom exercises. */
    @Serializable data class Routines(val presets: List<RoutinePreset> = emptyList(), val week: RoutineWeek = RoutineWeek(),
        val custom: List<CustomExercise> = emptyList())
    @Serializable data class RoutinePreset(val id: Long, val name: String, val note: String, val day: Int?, val minutes: Int,
        val hardSets: Int, val levels: Map<String, Int>, val entries: List<RoutineEntry>)
    @Serializable data class RoutineEntry(val position: Int, val name: String, val art: String?, val label: String, val sets: Int,
        val reps: Int, val loadKg: Double?, val figure: String, val scheme: String?, val modifiers: String, val supersetGroup: Int?, val warmup: Boolean)
    @Serializable data class RoutineWeek(val days: Int = 0, val hardSets: Int = 0, val covers: Map<String, String> = emptyMap(), val headline: String = "")
    @Serializable data class CustomExercise(val name: String, val muscles: Map<String, String>, val needs: List<String>)
    @Serializable data class RecentRecord(val atMs: Long, val localId: Long, val name: String, val label: String,
        val art: String?, val e1rmKg: Double, val gainKg: Double)
}

object WebSnapshotTuning {
    const val LEVEL_MOST = 3.0
    const val LEVEL_SOME = 1.5
    const val WEEKS = 52
    const val DAYS = 182
    const val MONTHS = 24
    enum class Group { CHEST, BACK, SHOULDERS, ARMS, CORE, LEGS }
    val GROUPS: Map<Muscle, Group> = Muscle.entries.associateWith { muscle -> when (muscle) {
        Muscle.UPPER_CHEST, Muscle.MID_CHEST, Muscle.LOWER_CHEST, Muscle.SERRATUS -> Group.CHEST
        Muscle.LATS, Muscle.RHOMBOIDS, Muscle.TRAPS, Muscle.NECK -> Group.BACK
        Muscle.FRONT_DELTS, Muscle.SIDE_DELTS, Muscle.REAR_DELTS, Muscle.ROTATOR_CUFF -> Group.SHOULDERS
        Muscle.BICEPS, Muscle.BRACHIALIS, Muscle.TRICEPS, Muscle.FOREARMS -> Group.ARMS
        Muscle.ABS, Muscle.OBLIQUES, Muscle.LOWER_BACK -> Group.CORE
        Muscle.HIP_FLEXORS, Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.ADDUCTORS,
        Muscle.ABDUCTORS, Muscle.CALVES, Muscle.TIBIALIS -> Group.LEGS
    } }
}

object WebSnapshotJson {
    private val json = Json { encodeDefaults = true; explicitNulls = true }
    fun write(snapshot: WebSnapshot): String {
        // Builders normally supply rounded figures already. Preserve their collections
        // without copying; normalize only externally constructed snapshots.
        fun rounded(value: Double, scale: Double): Double =
            if (!value.isFinite() || abs(value) > Double.MAX_VALUE / scale) value
            else round(value * scale) / scale
        fun pointValue(point: JsonArray): Double {
            require(point.size == 4) { "A lift point must have four entries" }
            val value = point[2].jsonPrimitive
            require(!value.isString) { "A lift point value must be numeric" }
            return value.double.also { require(it.isFinite()) { "A lift point value must be finite" } }
        }
        fun needsPoints(variation: WebSnapshot.LiftVariation): Boolean =
            variation.points.any { point -> pointValue(point).let { rounded(it, 100.0) != it } }
        val sessionsChanged = snapshot.sessions.values.any { entry ->
            entry.e1rm.values.any { rounded(it, 10.0) != it }
        }
        val liftsChanged = snapshot.lifts.any { lift -> lift.variations.any(::needsPoints) }
        if (!sessionsChanged && !liftsChanged) return json.encodeToString(WebSnapshot.serializer(), snapshot)
        val sessions = if (sessionsChanged) snapshot.sessions.mapValues { (_, entry) ->
            if (entry.e1rm.values.any { rounded(it, 10.0) != it })
                entry.copy(e1rm = entry.e1rm.mapValues { (_, value) -> rounded(value, 10.0) })
            else entry
        } else snapshot.sessions
        val lifts = if (liftsChanged) snapshot.lifts.map { lift ->
            if (lift.variations.none(::needsPoints)) lift else lift.copy(variations = lift.variations.map { variation ->
                if (!needsPoints(variation)) variation else variation.copy(points = variation.points.map { point ->
                    val value = pointValue(point)
                    val normalized = rounded(value, 100.0)
                    if (normalized == value) point else JsonArray(point.toMutableList().also { it[2] = JsonPrimitive(normalized) })
                })
            })
        } else snapshot.lifts
        return json.encodeToString(WebSnapshot.serializer(), snapshot.copy(sessions = sessions, lifts = lifts))
    }
    /**
     * The length in bytes of [value] as Postgres prints it back from a jsonb column: the compact UTF-8 plus the
     * space jsonb adds after every `:` and `,` outside a string, and 1% on top for number spellings that differ.
     * The server's size check reads that printed form, so an estimate from the compact text alone runs short.
     */
    fun jsonbTextBytes(value: String): Int {
        var spaces = 0L
        var inString = false
        var escaped = false
        for (c in value) {
            when {
                escaped -> escaped = false
                inString -> if (c == '\\') escaped = true else if (c == '"') inString = false
                c == '"' -> inString = true
                c == ',' || c == ':' -> spaces++
            }
        }
        val exact = value.toByteArray(Charsets.UTF_8).size + spaces
        return (exact + exact / 100).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun contentHash(value: String): String {
        val body = JsonObject(json.parseToJsonElement(value).let { it as JsonObject }.filterKeys { it != "builtAtMs" })
        return MessageDigest.getInstance("SHA-256").digest(body.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
