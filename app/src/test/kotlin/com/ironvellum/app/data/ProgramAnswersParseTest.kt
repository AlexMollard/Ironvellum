package com.ironvellum.app.data

import android.content.SharedPreferences
import com.ironvellum.app.domain.Equipment
import com.ironvellum.app.domain.Gear
import com.ironvellum.app.domain.MuscleArea
import com.ironvellum.app.domain.TrainingFocus
import com.ironvellum.app.domain.TrainingSplit
import com.ironvellum.app.domain.VolumeLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** One stale or hand-edited token must not void the saved answers or the gear. */
class ProgramAnswersParseTest {

    private val good = mapOf<String, Any?>(
        "volume" to "STANDARD",
        "split" to "UPPER_LOWER",
        "focus" to "MUSCLE",
        "fullGym" to false,
        "gear" to "BARBELL,DUMBBELLS",
        "dumbbellMaxKg" to "24.0",
        "dumbbellPair" to true,
        "daysPerWeek" to 4,
        "priorities" to "CHEST,ARMS",
        "compoundOnly" to true,
        "maxExercises" to 6,
    )

    private fun parse(vararg overrides: Pair<String, Any?>) =
        ProgramAnswersStore.parse(MapPrefs(good + overrides))

    @Test
    fun `a clean save reads back whole`() {
        val (equipment, answers) = parse()
        assertEquals(
            Equipment(fullGym = false, gear = setOf(Gear.BARBELL, Gear.DUMBBELLS), dumbbellMaxKg = 24.0, dumbbellPair = true),
            equipment,
        )
        assertEquals(VolumeLevel.STANDARD, answers!!.volume)
        assertEquals(TrainingFocus.MUSCLE, answers.focus)
        assertEquals(TrainingSplit.UPPER_LOWER, answers.split)
        assertEquals(setOf(MuscleArea.CHEST, MuscleArea.ARMS), answers.priorities)
        assertEquals(4, answers.daysPerWeek)
        assertEquals(6, answers.maxExercises)
    }

    @Test
    fun `an unknown gear or priority token is dropped and the rest survives`() {
        val (equipment, answers) = parse("gear" to "BARBELL,KETTLEBELL,DUMBBELLS", "priorities" to "CHEST,TRAPS_OF_STEEL")
        assertEquals(setOf(Gear.BARBELL, Gear.DUMBBELLS), equipment!!.gear)
        assertNotNull(answers)
        assertEquals(setOf(MuscleArea.CHEST), answers!!.priorities)
    }

    @Test
    fun `an unknown single enum voids only that field, and the gear still reads`() {
        for (key in listOf("volume", "split", "focus")) {
            val (equipment, answers) = parse(key to "FROM_THE_FUTURE")
            assertEquals("$key: gear lost", setOf(Gear.BARBELL, Gear.DUMBBELLS), equipment?.gear)
            assertEquals("$key: dumbbell max lost", 24.0, equipment?.dumbbellMaxKg)
            assertNull("$key: answers should read as not yet complete", answers)
        }
    }

    @Test
    fun `a value of the wrong stored type reads as missing instead of throwing`() {
        val (equipment, answers) = parse("daysPerWeek" to "four", "maxExercises" to "lots", "compoundOnly" to "yes")
        assertNotNull(equipment)
        assertNull(answers)
        val (_, noCompound) = parse("maxExercises" to "lots", "compoundOnly" to "yes")
        assertEquals(com.ironvellum.app.domain.ProgramRules.DEFAULT_MAX_EXERCISES, noCompound!!.maxExercises)
        assertEquals(false, noCompound.compoundOnly)
    }

    @Test
    fun `nothing saved reads as no gear and no answers`() {
        val (equipment, answers) = ProgramAnswersStore.parse(MapPrefs(emptyMap()))
        assertNull(equipment)
        assertNull(answers)
    }
}

/** A read-only [SharedPreferences] over a map: a wrong-typed read throws, as the real one does. */
private class MapPrefs(private val data: Map<String, Any?>) : SharedPreferences {
    override fun getAll(): MutableMap<String, *> = data.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? = data[key]?.let { it as String } ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
    override fun getInt(key: String?, defValue: Int): Int = data[key]?.let { it as Int } ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = data[key]?.let { it as Long } ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = data[key]?.let { it as Float } ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key]?.let { it as Boolean } ?: defValue
    override fun contains(key: String?): Boolean = data.containsKey(key)
    override fun edit(): SharedPreferences.Editor = throw UnsupportedOperationException()
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
}
