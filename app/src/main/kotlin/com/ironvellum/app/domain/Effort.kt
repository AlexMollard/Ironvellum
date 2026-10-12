package com.ironvellum.app.domain

/** How effort is read and shown: RPE (10 is your limit) or RIR (the reps you had left). The same stored figure either way. */
enum class EffortScale(val label: String) {
    RPE("RPE"),
    RIR("RIR"),
    ;

    companion object {
        /** The stored name, read defensively: anything unknown is RPE, the default. */
        fun fromWire(name: String?): EffortScale = entries.firstOrNull { it.name == name } ?: RPE
    }
}

/**
 * Effort per set, optional: one whole number RPE on the set, 5 to 10. RIR is not stored, it is
 * 10 minus the RPE, so a lifter who changes scale loses nothing. Anything at or under 5 is stored as 5
 * (the "5 or less" chip): below that the number says nothing a rep count would not. Pure, so the live strip,
 * the amend editor, the trial detail, the share card, the CSV and the Strong/Hevy import all say the same thing.
 */
object Effort {
    const val MIN = 5
    const val MAX = 10

    /** The chips, in the order they sit: the easiest first. */
    val CHIPS: List<Int> = (MIN..MAX).toList()

    /** A stored value as it must be: 5 to 10, or null for none. Out of range clamps; use [fromImported] for outside figures. */
    fun clamp(rpe: Int?): Int? = rpe?.coerceIn(MIN, MAX)

    /**
     * An RPE from another app's file, as the whole number stored. Halves round up (8.5 reads 9), anything
     * at or under 5 reads 5, and nothing, zero, a negative or a figure past 10 reads as no effort: a bad
     * cell must not become a hard set.
     */
    fun fromImported(value: Double?): Int? {
        if (value == null || value.isNaN() || value <= 0.0 || value > MAX + 0.5) return null
        return Math.round(value).toInt().coerceIn(MIN, MAX)
    }

    /** Reps left, 10 minus the RPE: 0 at RPE 10 up to 5 at RPE 5. */
    fun rir(rpe: Int): Int = MAX - clamp(rpe)!!

    /** The stored RPE for [rir] reps left (5 or more all read 5). */
    fun rpeOfRir(rir: Int): Int = clamp(MAX - rir)!!

    /** A chip's face: "≤5", "6" ... "10" on RPE; "5+", "4" ... "0" on RIR. */
    fun chip(rpe: Int, scale: EffortScale): String = when (scale) {
        EffortScale.RPE -> if (rpe <= MIN) "≤5" else rpe.toString()
        EffortScale.RIR -> rir(rpe).let { if (rpe <= MIN) "5+" else it.toString() }
    }

    /** What a logged set carries at its right: "RPE 8", "RPE ≤5", "2 RIR", "5+ RIR". */
    fun value(rpe: Int, scale: EffortScale): String = when (scale) {
        EffortScale.RPE -> "RPE ${chip(rpe, scale)}"
        EffortScale.RIR -> "${chip(rpe, scale)} RIR"
    }

    private val reps = listOf("nothing", "one rep", "two reps", "three reps", "four reps")

    /** The line under the chips for the chosen one: "RPE 8: about two reps left", "2 RIR: two reps left". */
    fun caption(rpe: Int, scale: EffortScale): String {
        val left = rir(rpe)
        val words = when {
            left == 0 -> "nothing left"
            rpe <= MIN -> "five or more reps left"
            scale == EffortScale.RPE -> "about ${reps[left]} left"
            else -> "${reps[left]} left"
        }
        return "${value(rpe, scale)}: $words"
    }

    /** The line before anything is chosen: what the scale means. */
    fun hint(scale: EffortScale): String = when (scale) {
        EffortScale.RPE -> "RPE 10 is your limit; 8 leaves two reps in hand."
        EffortScale.RIR -> "RIR is the reps you had left; 0 is your limit."
    }
}
