package com.ironvellum.app.data

import android.content.SharedPreferences
import com.ironvellum.app.domain.Equipment
import com.ironvellum.app.domain.Gear
import com.ironvellum.app.domain.LoadKit
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
        "sessionMinutes" to 90,
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
    fun `session minutes read back, default to 75 when missing, and snap when odd`() {
        assertEquals(90, parse().second!!.sessionMinutes)
        assertEquals(75, ProgramAnswersStore.parse(MapPrefs(good - "sessionMinutes")).second!!.sessionMinutes)
        assertEquals(45, parse("sessionMinutes" to 20).second!!.sessionMinutes)
        assertEquals(60, parse("sessionMinutes" to 55).second!!.sessionMinutes)
        assertEquals(90, parse("sessionMinutes" to 500).second!!.sessionMinutes)
        assertEquals(75, parse("sessionMinutes" to 74).second!!.sessionMinutes)
    }

    @Test
    fun `a garbage session minutes value defaults to 75 and voids nothing else`() {
        val (equipment, answers) = parse("sessionMinutes" to "an hour")
        assertNotNull(equipment)
        assertEquals(75, answers!!.sessionMinutes)
        assertEquals(6, answers.maxExercises)
        assertEquals(4, answers.daysPerWeek)
        assertEquals(setOf(MuscleArea.CHEST, MuscleArea.ARMS), answers.priorities)
    }

    @Test
    fun `a saved answer set round-trips including session minutes`() {
        val saved = mutableMapOf<String, Any?>()
        val answers = ProgramAnswers(
            volume = VolumeLevel.HIGH,
            focus = TrainingFocus.STRENGTH,
            equipment = Equipment(fullGym = true),
            daysPerWeek = 6,
            priorities = setOf(MuscleArea.BACK),
            split = TrainingSplit.PUSH_PULL_LEGS,
            compoundOnly = false,
            maxExercises = 7,
            sessionMinutes = 60,
        )
        with(ProgramAnswersStore) { MapEditor(saved).putAnswers(answers) }
        assertEquals(answers, ProgramAnswersStore.parse(MapPrefs(saved)).second)
    }

    @Test
    fun `saving an off-menu session length stores the nearest option`() {
        val saved = mutableMapOf<String, Any?>()
        val answers = ProgramAnswers(
            VolumeLevel.LOW, TrainingFocus.MUSCLE, Equipment(fullGym = true), 3, emptySet(), TrainingSplit.FULL_BODY,
            sessionMinutes = 100,
        )
        with(ProgramAnswersStore) { MapEditor(saved).putAnswers(answers) }
        assertEquals(90, saved["sessionMinutes"])
    }

    @Test
    fun `gear saved before the vest and belt existed reads as owning neither`() {
        val (equipment, _) = parse()
        assertEquals(LoadKit.NONE, equipment!!.kit)
        assertNull(equipment.kit.vestMaxKg)
        assertEquals(LoadKit.DEFAULT_VEST_BLOCK_KG, equipment.kit.vestBlockKg, 0.0)
        assertEquals(false, equipment.kit.dipBelt)
    }

    @Test
    fun `a saved vest and belt round-trip, and a removed vest is forgotten`() {
        val saved = mutableMapOf<String, Any?>()
        val kit = LoadKit(vestMaxKg = 30.0, vestBlockKg = 0.5, dipBelt = true)
        val answers = ProgramAnswers(
            VolumeLevel.LOW, TrainingFocus.MUSCLE, Equipment(fullGym = false, gear = setOf(Gear.PULL_UP_BAR), kit = kit),
            3, emptySet(), TrainingSplit.FULL_BODY,
        )
        with(ProgramAnswersStore) { MapEditor(saved).putAnswers(answers) }
        assertEquals(kit, ProgramAnswersStore.parse(MapPrefs(saved)).first!!.kit)
        with(ProgramAnswersStore) { MapEditor(saved).putAnswers(answers.copy(equipment = answers.equipment.copy(kit = LoadKit.NONE))) }
        assertEquals(LoadKit.NONE, ProgramAnswersStore.parse(MapPrefs(saved)).first!!.kit)
    }

    @Test
    fun `a corrupt vest value reads as no vest and voids nothing else`() {
        val (equipment, answers) = parse("vestMaxKg" to "heavy", "vestBlockKg" to "-3", "dipBelt" to "yes")
        assertNull(equipment!!.kit.vestMaxKg)
        assertEquals(LoadKit.DEFAULT_VEST_BLOCK_KG, equipment.kit.vestBlockKg, 0.0)
        assertEquals(false, equipment.kit.dipBelt)
        assertEquals(24.0, equipment.dumbbellMaxKg)
        assertNotNull(answers)
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

/** An [SharedPreferences.Editor] that writes straight into a map, for round-trip tests. */
private class MapEditor(private val out: MutableMap<String, Any?>) : SharedPreferences.Editor {
    override fun putString(key: String?, value: String?) = apply { out[key!!] = value }
    override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { out[key!!] = values }
    override fun putInt(key: String?, value: Int) = apply { out[key!!] = value }
    override fun putLong(key: String?, value: Long) = apply { out[key!!] = value }
    override fun putFloat(key: String?, value: Float) = apply { out[key!!] = value }
    override fun putBoolean(key: String?, value: Boolean) = apply { out[key!!] = value }
    override fun remove(key: String?) = apply { out.remove(key) }
    override fun clear() = apply { out.clear() }
    override fun commit(): Boolean = true
    override fun apply() = Unit
}
