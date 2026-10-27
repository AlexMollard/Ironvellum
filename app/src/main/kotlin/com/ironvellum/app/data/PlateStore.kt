package com.ironvellum.app.data

import android.content.Context
import androidx.core.content.edit
import com.ironvellum.app.domain.LoadUnit
import com.ironvellum.app.domain.PlateRack
import com.ironvellum.app.domain.PlateSizes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The bar and plates the lifter owns, per unit: kg and lb each keep their own bar and pair counts
 * (45 lb plates are not 20 kg ones), and an exercise can override the bar (a women's bar for press).
 * Kept in the "program_answers" preferences beside the rest of the armoury, under "plates." keys,
 * so no new file and no database migration; portable settings travel in the archive.
 *
 * Nothing stored reads as the unit's defaults. The [racks] flow mirrors the saved values so the
 * Armoury page and the live trial's sheet see each other's edits.
 */
object PlateStore {
    private const val PREFS = "program_answers"

    /** What is saved for each unit, or the unit's defaults when nothing is. */
    class Setup(val bar: Double, val pairs: Map<Double, Int>, val barFor: Map<String, Double>)

    private val _setups = MutableStateFlow(defaults())
    val setups: StateFlow<Map<LoadUnit, Setup>> = _setups.asStateFlow()

    fun rack(setup: Setup, unit: LoadUnit, exercise: String? = null): PlateRack =
        PlateRack(unit, exercise?.let { setup.barFor[key(it)] } ?: setup.bar, setup.pairs)

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _setups.value = LoadUnit.entries.associateWith { unit ->
            val tag = unit.name
            val sizes = PlateSizes.sizes(unit)
            Setup(
                bar = prefs.getString("plates.$tag.bar", null)?.toDoubleOrNull()?.takeIf { it >= 0.0 }
                    ?: PlateSizes.defaultBar(unit),
                pairs = sizes.associateWith { size ->
                    prefs.getInt("plates.$tag.pairs.$size", PlateSizes.defaultPairs(unit)[size] ?: 0).coerceIn(0, MAX_PAIRS)
                },
                barFor = prefs.all.mapNotNull { (k, v) ->
                    val prefix = "plates.$tag.barFor."
                    if (k.startsWith(prefix) && v is String) v.toDoubleOrNull()?.let { k.removePrefix(prefix) to it } else null
                }.toMap(),
            )
        }
    }

    fun setBar(context: Context, unit: LoadUnit, bar: Double) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString("plates.${unit.name}.bar", bar.toString()) }
        update(unit) { Setup(bar, it.pairs, it.barFor) }
    }

    fun setPairs(context: Context, unit: LoadUnit, size: Double, count: Int) {
        val n = count.coerceIn(0, MAX_PAIRS)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putInt("plates.${unit.name}.pairs.$size", n) }
        update(unit) { Setup(it.bar, it.pairs + (size to n), it.barFor) }
    }

    /** One exercise's bar; a [bar] equal to the unit's bar clears the override. */
    fun setBarFor(context: Context, unit: LoadUnit, exercise: String, bar: Double) {
        val key = "plates.${unit.name}.barFor.${key(exercise)}"
        val current = _setups.value.getValue(unit)
        val clear = bar == current.bar
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            if (clear) remove(key) else putString(key, bar.toString())
        }
        update(unit) { Setup(it.bar, it.pairs, if (clear) it.barFor - key(exercise) else it.barFor + (key(exercise) to bar)) }
    }

    /** Most pairs of one size the stepper offers. */
    const val MAX_PAIRS = 10

    private fun key(exercise: String) = exercise.trim().lowercase()

    private fun update(unit: LoadUnit, change: (Setup) -> Setup) {
        _setups.value = _setups.value + (unit to change(_setups.value.getValue(unit)))
    }

    private fun defaults(): Map<LoadUnit, Setup> = LoadUnit.entries.associateWith {
        Setup(PlateSizes.defaultBar(it), PlateSizes.sizes(it).associateWith { s -> PlateSizes.defaultPairs(it)[s] ?: 0 }, emptyMap())
    }
}
