package com.ironvellum.app.domain

import androidx.annotation.StringRes
import com.ironvellum.app.R
import com.ironvellum.app.data.Words

/** How effort is read and shown: RPE (10 is your limit) or RIR (the reps you had left). The same stored figure either way. */
enum class EffortScale(@StringRes private val labelRes: Int) {
    RPE(R.string.effort_scale_rpe),
    RIR(R.string.effort_scale_rir),
    ;

    /** The scale's abbreviation as shown. */
    val label: String get() = Words.text(UiText.res(labelRes))

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
        EffortScale.RPE -> if (rpe <= MIN) Words.text(UiText.res(R.string.effort_chip_rpe_low)) else rpe.toString()
        EffortScale.RIR -> rir(rpe).let { if (rpe <= MIN) Words.text(UiText.res(R.string.effort_chip_rir_high)) else it.toString() }
    }

    /** What a logged set carries at its right: "RPE 8", "RPE ≤5", "2 RIR", "5+ RIR". */
    fun value(rpe: Int, scale: EffortScale): String = Words.text(
        UiText.res(if (scale == EffortScale.RPE) R.string.effort_value_rpe else R.string.effort_value_rir, chip(rpe, scale)),
    )

    /** The line under the chips for the chosen one: "RPE 8: about two reps left", "2 RIR: two reps left". */
    fun caption(rpe: Int, scale: EffortScale): String {
        val left = rir(rpe)
        val rpeScale = scale == EffortScale.RPE
        val sentence = when {
            left == 0 -> R.string.effort_caption_none_left
            rpe <= MIN -> R.string.effort_caption_five_plus
            left == 1 -> if (rpeScale) R.string.effort_caption_rpe_1 else R.string.effort_caption_rir_1
            left == 2 -> if (rpeScale) R.string.effort_caption_rpe_2 else R.string.effort_caption_rir_2
            left == 3 -> if (rpeScale) R.string.effort_caption_rpe_3 else R.string.effort_caption_rir_3
            else -> if (rpeScale) R.string.effort_caption_rpe_4 else R.string.effort_caption_rir_4
        }
        return Words.text(UiText.res(sentence, value(rpe, scale)))
    }

    /** The line before anything is chosen: what the scale means. */
    fun hint(scale: EffortScale): String = Words.text(
        UiText.res(if (scale == EffortScale.RPE) R.string.effort_hint_rpe else R.string.effort_hint_rir),
    )
}
