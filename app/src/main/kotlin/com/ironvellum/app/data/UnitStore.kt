package com.ironvellum.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.core.content.edit
import com.ironvellum.app.domain.LoadUnit
import com.ironvellum.app.domain.Loads
import com.ironvellum.app.domain.fmt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The lifter's load unit, kg or lb, kept on the device with the other display choices ("appearance"
 * preferences). Storage stays kg everywhere; this decides only what is shown and typed.
 *
 * [current] is Compose state, so any composable that formats a load through
 * [com.ironvellum.app.domain.Loads] with it recomposes when the unit changes, the same way
 * IronvellumColors.accents carries the accent pair. It is read once at app start by [init].
 */
object Units {
    private const val PREFS = "appearance"
    private const val KEY = "loadUnit"

    var current: LoadUnit by mutableStateOf(LoadUnit.KG)
        private set

    /** The unit as a flow, for text built off the main thread (the Strength Rank's breakdown). */
    val flow: Flow<LoadUnit> = snapshotFlow { current }.distinctUntilChanged()

    fun init(context: Context) {
        current = runCatching {
            LoadUnit.valueOf(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null).orEmpty())
        }.getOrDefault(LoadUnit.KG)
    }

    /** "225 lb" in the lifter's unit. */
    fun withUnit(kg: Double): String = Loads.withUnit(kg, current)

    /** "225", the figure alone. */
    fun format(kg: Double): String = Loads.format(kg, current)

    /** "225 lb", or "BW" for no load. */
    fun label(kg: Double?): String = Loads.label(kg, current)

    /** [kg] as a count in the lifter's unit, for text that appends its own unit. */
    fun amount(kg: Double): Double = Loads.toUnit(kg, current)

    /** "225 lb" to [decimals] places (a whole pound at 0, else the nearest 0.5 lb). */
    fun fixed(kg: Double, decimals: Int): String = "${Loads.fixed(kg, current, decimals)} ${current.label}"

    /** A total moved, whole and grouped: "3,420 lb". */
    fun volume(kg: Double): String = "${"%,.0f".fmt(amount(kg))} ${current.label}"

    /** [kg] as whole units, for a count that groups its own digits. */
    fun whole(kg: Double): Long = Math.round(amount(kg))

    /** "lb". */
    val name: String get() = current.label

    fun set(context: Context, unit: LoadUnit) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, unit.name) }
        current = unit
    }
}
