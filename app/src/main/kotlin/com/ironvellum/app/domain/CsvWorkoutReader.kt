package com.ironvellum.app.domain

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Reads a Strong or Hevy CSV export (one row per set, workout fields repeated
 * on every row) into [ParsedImport].
 *
 * Formats are evidence-based, not guessed:
 * - Strong header `Date,Workout Name,Duration,Exercise Name,Set Order,Weight,
 *   Reps,Distance,Seconds,Notes[,Workout Notes][,RPE]`, Date
 *   `2020-12-30 18:51:52`, Duration `2h 38m` — as shipped by the app's own
 *   exporter (verified against a real export published in
 *   github.com/AlexandrosKyriakakis/StrongAppAnalytics). Strong's Weight has
 *   NO unit column: it follows the exporter's app setting, so the review step
 *   asks; [strongWeightIsLbs] converts on the way in and no guess is silent.
 * - Hevy header `"title","start_time","end_time","description","exercise_title",
 *   "superset_id","exercise_notes","set_index","set_type",
 *   "weight_kg"|"weight_lbs","reps","distance_km"|"distance_miles",
 *   "duration_seconds","rpe"`, timestamps like `"22 Dec 2025, 08:00"` —
 *   verified against github.com/matanabudy/workout-data-sync's sample export
 *   and the Hevy help centre. Hevy's paired kg/lb columns are BOTH parsed,
 *   preferring the kg one.
 *
 * Pure Kotlin: parsing runs off the main thread and must stay unit-testable.
 */
object CsvWorkoutReader {

    enum class Source { STRONG, HEVY }

    enum class WeightUnit { KG, LB }

    /** A thing that went wrong the lifter can act on. Never a stack trace. */
    data class Problem(val message: String)

    data class ParsedSet(
        val exerciseName: String,
        /** Order within one exercise block, as the exporting app numbered it. */
        val setIndex: Int,
        val reps: Int,
        /** Already in kg; Strong rows converted when [ParsedImport] says so. */
        val weightKg: Double?,
        val distanceM: Double?,
        val durationSec: Int?,
        /** normal / warmup / dropset / failure; Strong exports carry none. */
        val setType: String,
        val rpe: Double?,
        /** Per-set note (Strong `Notes`, Hevy `exercise_notes`). */
        val notes: String,
    )

    data class ParsedWorkout(
        val label: String,
        val startedAtMs: Long,
        /** End time when the format carries one; start + Duration for Strong. */
        val completedAtMs: Long?,
        /** Workout-level notes (Strong `Workout Notes`, Hevy `description`). */
        val notes: String,
        val sets: List<ParsedSet>,
    )

    data class ParsedImport(
        val source: Source,
        val workouts: List<ParsedWorkout>,
        /**
         * Strong only: the unit the file's Weight column was read as. Null for
         * Hevy, whose files self-describe. The review step shows the guess.
         */
        val units: WeightUnit?,
        val problems: List<Problem>,
    ) {
        val totalSets: Int get() = workouts.sumOf { it.sets.size }

        val dateRange: Pair<Long, Long>?
            get() = workouts.minOfOrNull { it.startedAtMs }?.let { min ->
                maxOf(workouts.maxOf { it.completedAtMs ?: it.startedAtMs }, min) to min
            }
    }

    /** RFC 4180: quoted commas, quoted newlines, doubled quotes. Hand-rolled. */
    fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<MutableList<String>>(mutableListOf())
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        val n = text.length
        // A bare CR is a line terminator in classic Mac/Excel exports; CRLF and
        // LF are handled by the same branch below.
        while (i < n) {
            val c = text[i]
            when {
                inQuotes -> when (c) {
                    '"' -> if (i + 1 < n && text[i + 1] == '"') {
                        field.append('"'); i++
                    } else {
                        inQuotes = false
                    }
                    // A line break inside a quoted note is kept, but always as
                    // LF: a file re-saved on Windows would otherwise carry a
                    // stray CR into the lifter's note.
                    '\r' -> {
                        if (i + 1 < n && text[i + 1] == '\n') i++
                        field.append('\n')
                    }
                    else -> field.append(c)
                }
                // A quote opens a quoted field only at the field's start
                // (RFC 4180, and how Excel reads it). Mid-field it is a literal
                // character, so one stray quote in a note cannot swallow every
                // row after it.
                c == '"' && field.isEmpty() -> inQuotes = true
                c == ',' -> {
                    rows.last().add(field.toString()); field.setLength(0)
                }
                c == '\r' || c == '\n' -> {
                    if (c == '\r' && i + 1 < n && text[i + 1] == '\n') i++
                    rows.last().add(field.toString()); field.setLength(0)
                    rows.add(mutableListOf())
                }
                else -> field.append(c)
            }
            i++
        }
        rows.last().add(field.toString())
        return rows.filter { it.any { f -> f.isNotBlank() } }
    }

    fun read(text: String, strongWeightIsLbs: Boolean = false): ParsedImport {
        val rows = parseCsv(text)
        val header = rows.firstOrNull()?.map { it.trim().trimStart('\uFEFF') }
            ?: return ParsedImport(Source.STRONG, emptyList(), null, listOf(unrelated("The file is empty.")))
        return when {
            header.firstOrNull()?.equals("Date", true) == true &&
                header.any { it.equals("Set Order", true) } ->
                readStrong(rows, strongWeightIsLbs)
            header.firstOrNull()?.equals("title", true) == true &&
                header.any { it.equals("exercise_title", true) } ->
                readHevy(rows)
            else -> ParsedImport(
                Source.STRONG,
                emptyList(),
                null,
                listOf(
                    unrelated(
                        "This file is not a Strong or Hevy export. Expected a header row " +
                            "starting with \"Date\" or \"title\" — found " +
                            "\"${header.take(3).joinToString(", ")}\".",
                    ),
                ),
            )
        }
    }

    private fun unrelated(message: String) = Problem(message)

    // ------------------------------------------------------------------ Strong

    private val STRONG_DURATION = Regex("(?:(\\d+)\\s*h)?\\s*(?:(\\d+)\\s*m)?\\s*(?:(\\d+)\\s*s)?\\s*")

    private fun readStrong(rows: List<List<String>>, lbs: Boolean): ParsedImport {
        val header = rows.first().map { it.trim() }
        val col = header.associate { it.lowercase() to header.indexOf(it) }
        val problems = mutableListOf<Problem>()
        val factor = if (lbs) LB_TO_KG else 1.0
        val unit = if (lbs) WeightUnit.LB else WeightUnit.KG

        val workouts = LinkedHashMap<String, MutableList<ParsedSet>>()
        val meta = LinkedHashMap<String, Pair<Long, Long?>>() // key -> (start, completed)
        val setNotes = LinkedHashMap<String, MutableMap<Int, String>>()
        val workoutNotes = LinkedHashMap<String, String>()
        val rpes = LinkedHashMap<String, MutableMap<Int, Double>>()
        // setIndex restarts at 1 per exercise block; a raw row key disambiguates.
        val rowKey = LinkedHashMap<String, MutableList<Int>>()

        rows.drop(1).forEachIndexed { rowNo, row ->
            fun str(name: String) = col[name.lowercase()]?.let { row.getOrNull(it) }?.trim() ?: ""
            val date = str("Date")
            val name = str("Workout Name")
            val start = parseStrongDate(date) ?: run {
                problems += Problem("Row ${rowNo + 2}: unreadable date \"$date\" — skipped.")
                return@forEachIndexed
            }
            val durationSec = parseStrongDuration(str("Duration"))
            val key = "$date\u0000$name"
            val sets = workouts.getOrPut(key) { mutableListOf() }
            meta.putIfAbsent(key, start to durationSec?.let { start + it * 1000L })
            rowKey.getOrPut(key) { mutableListOf() }
            val idx = str("Set Order").toIntOrNull() ?: (sets.size + 1)
            str("Workout Notes").takeIf { it.isNotBlank() }?.let { workoutNotes.putIfAbsent(key, it) }
            str("Notes").takeIf { it.isNotBlank() }?.let { setNotes.getOrPut(key) { mutableMapOf() }[idx] = it }
            str("RPE").toDoubleOrNull()?.let { rpes.getOrPut(key) { mutableMapOf() }[idx] = it }
            sets += ParsedSet(
                exerciseName = str("Exercise Name"),
                setIndex = idx,
                reps = str("Reps").toDoubleOrNull()?.toInt() ?: 0,
                weightKg = str("Weight").toDoubleOrNull()?.let { round3(it * factor) },
                distanceM = str("Distance").toDoubleOrNull()?.let { round3(it * 1000.0) },
                durationSec = str("Seconds").toDoubleOrNull()?.toInt(),
                setType = "normal",
                rpe = null,
                notes = "",
            )
        }

        val parsed = workouts.map { (key, sets) ->
            val (start, completed) = meta.getValue(key)
            val label = key.substringAfter('\u0000')
            val notes = setNotes[key]
            val rpeMap = rpes[key]
            ParsedWorkout(
                label = label.ifBlank { "Imported workout" },
                startedAtMs = start,
                completedAtMs = completed,
                // Workout Notes is the workout-level column; it becomes the
                // session's PRIVATE note at merge time. Per-set Notes ride
                // their set.
                notes = workoutNotes[key] ?: "",
                sets = sets.map { s ->
                    s.copy(rpe = rpeMap?.get(s.setIndex), notes = notes?.get(s.setIndex) ?: "")
                },
            )
        }
        return ParsedImport(Source.STRONG, parsed, unit, problems)
    }

    /**
     * Evidence: `2020-12-30 18:51:52` (local wall clock, no zone — read in the
     * device zone per the plan). Some locale exports use `30/12/2020 18:51`;
     * both are accepted and the fallback is reported in the final report, not
     * silently.
     */
    fun parseStrongDate(raw: String): Long? {
        val s = raw.trim()
        return runCatching {
            LocalDateTime.parse(s).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.recoverCatching {
            val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            LocalDateTime.parse(s, fmt).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.recoverCatching {
            val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            LocalDateTime.parse(s, fmt).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()
    }

    /** `2h 38m`, `1h 6m`, `45m`, `30s`. */
    fun parseStrongDuration(raw: String): Long? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        val m = STRONG_DURATION.matchEntire(s) ?: return null
        val (h, min, sec) = m.destructured
        val total = (h.toIntOrNull() ?: 0) * 3600L + (min.toIntOrNull() ?: 0) * 60L + (sec.toIntOrNull() ?: 0)
        return if (total > 0) total else null
    }

    // -------------------------------------------------------------------- Hevy

    private val HEVY_MONTHS = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
    )

    private fun readHevy(rows: List<List<String>>): ParsedImport {
        val header = rows.first().map { it.trim() }
        val col = header.associate { it.lowercase() to header.indexOf(it) }
        val problems = mutableListOf<Problem>()

        data class Row(val start: Long, val end: Long?, val title: String, val desc: String, val parsed: ParsedSet)

        val parsedRows = mutableListOf<Row>()
        rows.drop(1).forEachIndexed { rowNo, row ->
            fun str(name: String) = col[name.lowercase()]?.let { row.getOrNull(it) }?.trim() ?: ""
            fun dbl(name: String) = str(name).toDoubleOrNull()
            val startStr = str("start_time")
            val start = parseHevyDate(startStr) ?: run {
                problems += Problem("Row ${rowNo + 2}: unreadable start_time \"$startStr\" — skipped.")
                return@forEachIndexed
            }
            // Weight: prefer the kg column, fall back to lb.
            val weightKg = dbl("weight_kg") ?: dbl("weight_lbs")?.let { round3(it * LB_TO_KG) }
            val distanceM = dbl("distance_km")?.let { round3(it * 1000.0) }
                ?: dbl("distance_miles")?.let { round3(it * 1609.344) }
            parsedRows += Row(
                start = start,
                end = parseHevyDate(str("end_time")),
                title = str("title").ifBlank { "Imported workout" },
                desc = str("description"),
                parsed = ParsedSet(
                    exerciseName = str("exercise_title"),
                    setIndex = str("set_index").toIntOrNull() ?: 0,
                    reps = dbl("reps")?.toInt() ?: 0,
                    weightKg = weightKg,
                    distanceM = distanceM,
                    durationSec = dbl("duration_seconds")?.toInt(),
                    setType = str("set_type").lowercase().ifBlank { "normal" },
                    rpe = dbl("rpe"),
                    notes = str("exercise_notes"),
                ),
            )
        }

        val workouts = parsedRows
            .groupBy { it.start to it.title.lowercase().trim() }
            .map { (_, group) ->
                val first = group.first()
                ParsedWorkout(
                    label = first.title,
                    startedAtMs = first.start,
                    completedAtMs = group.mapNotNull { it.end }.maxOrNull(),
                    // Hevy's description is the workout-level note; PRIVATE only.
                    notes = first.desc,
                    sets = group.map { it.parsed },
                )
            }
        return ParsedImport(Source.HEVY, workouts, null, problems)
    }

    /**
     * Evidence: `"22 Dec 2025, 08:00"` (d MMM yyyy, HH:mm — note the comma
     * inside the quoted field). Older exports used ISO `2025-12-22 08:00`;
     * both are accepted.
     */
    fun parseHevyDate(raw: String): Long? {
        val s = raw.trim()
        runCatching { return OffsetDateTime.parse(s).toInstant().toEpochMilli() }
        runCatching {
            return LocalDateTime.parse(s).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        val m = Regex("(\\d{1,2})\\s+([A-Za-z]{3,})\\s+(\\d{4}),\\s*(\\d{1,2}):(\\d{2})").matchEntire(s)
            ?: return null
        val (d, monName, y, hh, mm) = m.destructured
        val mon = HEVY_MONTHS[monName.take(3).lowercase()] ?: return null
        return LocalDateTime.of(y.toInt(), mon, d.toInt(), hh.toInt(), mm.toInt())
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    const val LB_TO_KG = 0.45359237

    private fun round3(v: Double) = Math.round(v * 1000.0) / 1000.0
}
