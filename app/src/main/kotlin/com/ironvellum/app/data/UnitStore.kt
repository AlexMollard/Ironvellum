package com.ironvellum.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.core.content.edit
import com.ironvellum.app.domain.LoadUnit
import com.ironvellum.app.domain.Loads
import com.ironvellum.app.domain.fmt
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Display preferences only: all stored loads and calculations remain in kilograms. */
object Units {
    private const val PREFS = "appearance"
    private const val KEY = "loadUnit"
    internal const val EXERCISE_PREFIX = "exerciseLoadUnit."
    private var preferences: SharedPreferences? = null

    var current: LoadUnit by mutableStateOf(LoadUnit.KG)
        private set
    var exerciseOverrides: Map<String, LoadUnit> by mutableStateOf(emptyMap())
        private set

    val flow: Flow<LoadUnit> = snapshotFlow { current }.distinctUntilChanged()
    val changes: Flow<Unit> = snapshotFlow { current to exerciseOverrides }.map { }

    internal fun exerciseKey(name: String): String = name.trim().lowercase(Locale.ROOT)
    internal fun validExerciseKey(key: String): Boolean =
        key.isNotBlank() && key.length <= 200 && key.none { it.isISOControl() } && key == exerciseKey(key)

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        preferences = prefs
        current = runCatching { LoadUnit.valueOf(prefs.getString(KEY, null).orEmpty()) }.getOrDefault(LoadUnit.KG)
        exerciseOverrides = prefs.all.mapNotNull { (key, value) ->
            if (!key.startsWith(EXERCISE_PREFIX)) return@mapNotNull null
            val name = key.removePrefix(EXERCISE_PREFIX)
            if (!validExerciseKey(name) || value !is String) return@mapNotNull null
            LoadUnit.entries.firstOrNull { it.name == value }?.let { name to it }
        }.toMap()
    }

    fun overrideFor(exerciseName: String): LoadUnit? = exerciseOverrides[exerciseKey(exerciseName)]
    fun forExercise(exerciseName: String): LoadUnit = overrideFor(exerciseName) ?: current
    private fun unitFor(exerciseName: String?): LoadUnit = exerciseName?.let(::forExercise) ?: current
    fun nameFor(exerciseName: String): String = forExercise(exerciseName).label

    fun withUnit(kg: Double, exerciseName: String? = null): String = Loads.withUnit(kg, unitFor(exerciseName), current)
    fun format(kg: Double, exerciseName: String? = null): String = Loads.format(kg, unitFor(exerciseName))
    fun label(kg: Double?, weighted: Boolean = false, exerciseName: String? = null): String =
        Loads.label(kg, unitFor(exerciseName), weighted, current)
    fun amount(kg: Double, exerciseName: String? = null): Double = Loads.toUnit(kg, unitFor(exerciseName))
    fun fixed(kg: Double, decimals: Int, exerciseName: String? = null): String {
        val unit = unitFor(exerciseName)
        val primary = "${Loads.fixed(kg, unit, decimals)} ${unit.label}"
        return equivalent(kg, unit)?.let { "$primary $it" } ?: primary
    }
    fun volume(kg: Double, exerciseName: String? = null): String {
        val unit = unitFor(exerciseName)
        val primary = "${"%,.0f".fmt(Loads.toUnit(kg, unit))} ${unit.label}"
        return equivalent(kg, unit)?.let { "$primary $it" } ?: primary
    }
    fun whole(kg: Double, exerciseName: String? = null): Long = Math.round(amount(kg, exerciseName))
    val name: String get() = current.label
    fun equivalent(kg: Double, unit: LoadUnit): String? = Loads.defaultEquivalent(kg, unit, current)

    fun set(context: Context, unit: LoadUnit) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, unit.name) }
        current = unit
    }

    fun setExercise(context: Context, exerciseName: String, unit: LoadUnit?) {
        val key = exerciseKey(exerciseName)
        require(validExerciseKey(key))
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        preferences = prefs
        prefs.edit { if (unit == null) remove(EXERCISE_PREFIX + key) else putString(EXERCISE_PREFIX + key, unit.name) }
        exerciseOverrides = if (unit == null) exerciseOverrides - key else exerciseOverrides + (key to unit)
    }

    /** Called only after the corresponding custom-exercise transaction has committed. */
    internal fun renameExercise(oldName: String, newName: String) {
        val old = exerciseKey(oldName)
        val new = exerciseKey(newName)
        if (old == new) return
        val unit = exerciseOverrides[old]
        val prefs = preferences ?: return
        prefs.edit {
            remove(EXERCISE_PREFIX + old)
            if (unit == null) remove(EXERCISE_PREFIX + new) else putString(EXERCISE_PREFIX + new, unit.name)
        }
        exerciseOverrides = if (unit == null) exerciseOverrides - old - new else exerciseOverrides - old + (new to unit)
    }

    internal fun removeExercise(name: String) {
        val key = exerciseKey(name)
        val prefs = preferences ?: return
        prefs.edit { remove(EXERCISE_PREFIX + key) }
        exerciseOverrides = exerciseOverrides - key
    }
}
