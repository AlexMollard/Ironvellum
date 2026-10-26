package com.ironvellum.app.domain

/**
 * "Lighten" for one routine entry: one working set fewer (never below one) and one more rep in reserve. The app has
 * no stored target effort, so a rep in reserve is a rep off the target at the same load. A scheme with back-offs
 * loses its last back-off; a lift counted in anything but reps (a hold) only loses the set.
 */
object Lighten {

    /** The working sets [entry] has, whatever its scheme. */
    fun workingSets(entry: PresetEntry): Int = entry.scheme.workingSets(entry.targetSets).coerceAtLeast(1)

    fun canTrim(entry: PresetEntry): Boolean = workingSets(entry) > 1

    /** [entry] lightened; unchanged when it has a single set and nothing to shift. [repsCounted] is false for a hold. */
    fun apply(entry: PresetEntry, repsCounted: Boolean): PresetEntry {
        val trimmed = trim(entry)
        return if (repsCounted) shiftReps(trimmed) else trimmed
    }

    private fun trim(entry: PresetEntry): PresetEntry {
        if (!canTrim(entry)) return entry
        val scheme = entry.scheme
        return when (scheme.type) {
            SchemeType.TOP_BACKOFF ->
                if (scheme.backoffSets > 1) entry.copy(scheme = scheme.copy(backoffSets = scheme.backoffSets - 1))
                else entry.copy(targetSets = 1, scheme = SetScheme(warmupRamp = scheme.warmupRamp))
            SchemeType.CUSTOM -> {
                val kinds = scheme.custom
                val drop = kinds.indexOfLast { it == SetKind.BACKOFF }.takeIf { it >= 0 } ?: kinds.indexOfLast { it != SetKind.DROP }
                entry.copy(scheme = scheme.copy(custom = kinds.filterIndexed { i, _ -> i != drop }))
            }
            else -> entry.copy(targetSets = entry.targetSets - 1, scheme = scheme.fitTo(entry.targetSets - 1))
        }
    }

    private fun shiftReps(entry: PresetEntry): PresetEntry {
        val scheme = entry.scheme
        fun down(range: IntRange) = (range.first - 1).coerceAtLeast(1)..range.last.coerceAtLeast((range.first - 1).coerceAtLeast(1))
        return when (scheme.type) {
            SchemeType.TOP_BACKOFF -> entry.copy(
                scheme = scheme.copy(
                    topReps = down(scheme.topReps),
                    backoffReps = down(scheme.backoffReps),
                    topRir = (scheme.topRir.first + 1)..(scheme.topRir.last + 1),
                ),
            )
            else -> entry.copy(targetReps = (entry.targetReps - 1).coerceAtLeast(1))
        }
    }
}

/** What one routine entry becomes for one workout: swapped for another lift, or lightened. */
data class EntryAdjust(val swap: Swap? = null, val lighten: Boolean = false) {
    /** The lift that stands in, with the reps and load it starts at (never the old lift's figures). */
    data class Swap(val exerciseId: Long, val exerciseName: String, val modifiers: String, val sets: Int, val reps: Int, val weightKg: Double?)

    val isEmpty: Boolean get() = swap == null && !lighten
}

/**
 * Changes to one workout begun from a routine, by routine entry id. The routine is never edited: the workout starts
 * from [applyTo] of each entry, and the same override read back later says what the routine's entries were for that
 * workout, so the end-of-workout "update the routine?" offer compares the workout with what it was meant to be
 * ([RiteSwap], [RoutineUpdate]) and stays quiet about these changes.
 */
class SessionOverride(val entries: Map<Long, EntryAdjust>) {

    val isEmpty: Boolean get() = entries.values.all { it.isEmpty }

    /** [entry] as the workout starts it. [repsCounted] says whether the lift (after any swap) is counted in reps. */
    fun applyTo(entry: PresetEntry, repsCounted: Boolean = true): PresetEntry {
        val adjust = entries[entry.id] ?: return entry
        var out = entry
        adjust.swap?.let {
            out = out.copy(
                exerciseId = it.exerciseId, exerciseName = it.exerciseName, modifiers = it.modifiers,
                targetSets = it.sets, targetReps = it.reps, targetWeightKg = it.weightKg, scheme = SetScheme(),
            )
        }
        return if (adjust.lighten) Lighten.apply(out, repsCounted) else out
    }

    fun encode(): String = entries.filterValues { !it.isEmpty }.entries.joinToString("\n") { (id, a) ->
        val s = a.swap
        listOf(
            id.toString(), if (a.lighten) "1" else "0",
            s?.exerciseId?.toString().orEmpty(), s?.exerciseName?.clean().orEmpty(), s?.modifiers?.clean().orEmpty(),
            s?.sets?.toString().orEmpty(), s?.reps?.toString().orEmpty(), s?.weightKg?.toString().orEmpty(),
        ).joinToString("\t")
    }

    private fun String.clean() = replace('\t', ' ').replace('\n', ' ')

    companion object {
        val NONE = SessionOverride(emptyMap())

        /** Anything unreadable reads as no override. */
        fun decode(text: String?): SessionOverride {
            if (text.isNullOrBlank()) return NONE
            val map = text.lines().mapNotNull { line ->
                val f = line.split('\t')
                if (f.size < 8) return@mapNotNull null
                val id = f[0].toLongOrNull() ?: return@mapNotNull null
                val swap = f[2].toLongOrNull()?.let { exerciseId ->
                    EntryAdjust.Swap(
                        exerciseId, f[3], f[4], f[5].toIntOrNull() ?: return@mapNotNull null,
                        f[6].toIntOrNull() ?: return@mapNotNull null, f[7].toDoubleOrNull(),
                    )
                }
                id to EntryAdjust(swap, f[1] == "1")
            }.toMap()
            return SessionOverride(map)
        }
    }
}
