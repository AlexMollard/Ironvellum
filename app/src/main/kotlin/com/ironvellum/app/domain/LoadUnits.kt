package com.ironvellum.app.domain

import androidx.annotation.StringRes
import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

/** The unit a lifter reads loads in. Storage is kg everywhere; this only changes what is shown and typed. */
enum class LoadUnit(@StringRes private val labelRes: Int) {
    KG(R.string.unit_kg),
    LB(R.string.unit_lb),
    ;

    /** The unit's symbol, "kg" or "lb", in the app's language. */
    val label: String get() = Words.text(UiText.res(labelRes))
}

/**
 * The one conversion and formatting helper for loads. Every screen, share text and progression
 * line that prints or reads a load goes through here, so kg and lb never drift between surfaces.
 *
 * lb is shown to the nearest 0.5 and typed lb converts to kg at the exact pound (0.45359237 kg),
 * kept to the gram like every stored load. Anything that floors, ceils or solves first snaps to the
 * display precision: 225 lb is stored as 102.058 kg, which reads back as 224.9993 lb, and a floor
 * of that would call a clean 225 "between plates" and make "+" a no-op.
 */
object Loads {

    /** Kilograms in one pound, by definition. */
    const val KG_PER_LB = 0.45359237

    /** lb steppers move 5 lb on compounds, 2.5 lb on small and isolation work. */
    private const val LB_STEP_COMPOUND = 5.0
    private const val LB_STEP_SMALL = 2.5
    private const val LB_STEP_SLED = 10.0

    /** [kg] as a number in [unit], unrounded. */
    fun toUnit(kg: Double, unit: LoadUnit): Double = if (unit == LoadUnit.LB) kg / KG_PER_LB else kg

    /** A [value] typed or built in [unit] as stored kg, to the gram. */
    fun toKg(value: Double, unit: LoadUnit): Double =
        if (unit == LoadUnit.LB) round3(value * KG_PER_LB) else value

    /** The load as it is displayed: nearest 0.5 lb, or 0.01 kg. */
    fun snap(kg: Double, unit: LoadUnit): Double = when (unit) {
        LoadUnit.LB -> (toUnit(kg, unit) * 2.0).roundToLong() / 2.0
        LoadUnit.KG -> (kg * 100.0).roundToLong() / 100.0
    }

    /** "220.5", "225", "8.75": the snapped number, no unit, trailing zeros dropped. */
    fun format(kg: Double, unit: LoadUnit): String =
        String.format(java.util.Locale.US, "%.2f", snap(kg, unit)).trimEnd('0').trimEnd('.')

    /** [kg] to [decimals] places in kg; in lb the nearest 0.5, or a whole pound when [decimals] is 0. */
    fun fixed(kg: Double, unit: LoadUnit, decimals: Int): String = when {
        unit == LoadUnit.KG -> String.format(java.util.Locale.US, "%.${decimals}f", kg)
        decimals == 0 -> Math.round(toUnit(kg, unit)).toString()
        else -> format(kg, unit)
    }

    /** "220.5 lb". */
    fun withUnit(kg: Double, unit: LoadUnit): String = "${format(kg, unit)} ${unit.label}"

    /**
     * "225 lb", or "BW" for no load. A [weighted] lift (a barbell, a dumbbell, a machine) has no bodyweight to fall
     * back on: with no load it reads "0 kg", never "BW", which would say the bar was not there.
     */
    fun label(kg: Double?, unit: LoadUnit, weighted: Boolean = false): String =
        kg?.takeIf { it > 0.0 }?.let { withUnit(it, unit) } ?: if (weighted) withUnit(0.0, unit) else "BW"

    /** The ceiling as the "Enter 0 to N" hint reads it. */
    fun ceilingText(maxKg: Double, unit: LoadUnit): String = floor(snap(maxKg, unit)).toLong().toString()

    /** The text field's prefill: the snapped figure, no unit. */
    fun fieldText(kg: Double, unit: LoadUnit): String = format(kg, unit)

    /**
     * A typed load in [unit] as kg: blank is bodyweight (0.0), a comma is a decimal point, and the
     * result is kept to the gram. Anything else, or above [maxKg], fails.
     */
    fun parse(text: String, unit: LoadUnit, maxKg: Double): Result<Double> {
        if (text.isBlank()) return Result.success(0.0)
        val typed = DecimalInput.parse(text)
            ?: return Result.failure(IllegalArgumentException("not a number: $text"))
        val kg = if (unit == LoadUnit.LB) toKg(typed, unit) else round3(typed)
        if (typed < 0.0 || kg > maxKg) return Result.failure(IllegalArgumentException("out of range: $text"))
        return Result.success(kg)
    }

    /** The lb stepper's grid for a movement whose kg progression step is [stepKg]: 5 / 2.5 kg to 5 lb, 1.25 kg to 2.5 lb, a sled's 10 kg to 10 lb. */
    fun lbGrid(stepKg: Double): Double = when {
        stepKg >= 10.0 -> LB_STEP_SLED
        stepKg >= 2.5 -> LB_STEP_COMPOUND
        else -> LB_STEP_SMALL
    }

    /** The next load up the grid. kg walks the 2.5 kg plate grid; lb walks [gridLb] on the displayed figure. */
    fun stepUp(kg: Double?, unit: LoadUnit, gridLb: Double, gridKg: Double): Double {
        if (unit == LoadUnit.KG) return (floor((kg ?: 0.0) / gridKg + 1e-9) + 1) * gridKg
        val lb = snap(kg ?: 0.0, unit)
        return toKg((floor(lb / gridLb + 1e-9) + 1) * gridLb, unit)
    }

    /** The next load down the grid, or null at or below zero (bodyweight). */
    fun stepDown(kg: Double?, unit: LoadUnit, gridLb: Double, gridKg: Double): Double? {
        if (kg == null) return null
        if (unit == LoadUnit.KG) return ((ceil(kg / gridKg - 1e-9) - 1) * gridKg).takeIf { it > 0.0 }
        val lb = snap(kg, unit)
        return ((ceil(lb / gridLb - 1e-9) - 1) * gridLb).takeIf { it > 0.0 }?.let { toKg(it, unit) }
    }

    private fun round3(v: Double): Double = Math.round(v * 1000.0) / 1000.0
}
