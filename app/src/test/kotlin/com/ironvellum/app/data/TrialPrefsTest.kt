package com.ironvellum.app.data

import android.content.SharedPreferences
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.RestAlert
import com.ironvellum.app.domain.SetClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The device settings a live trial reads: rest off (all, or one movement), the alert mode, the awake switch. */
class TrialPrefsTest {

    private val prefs = TrialPrefs(FakePrefs())

    @Test
    fun `the defaults are rest on, vibrate and the screen kept awake`() {
        assertFalse(prefs.restOff)
        assertEquals(RestAlert.VIBRATE, prefs.restAlert)
        assertTrue(prefs.keepScreenOn)
        assertFalse(prefs.restIsOff("Back Squat"))
    }

    @Test
    fun `rest off for everything switches every movement off`() {
        prefs.restOff = true
        assertTrue(prefs.restIsOff("Back Squat"))
        assertTrue(prefs.restIsOff("Plank"))
        prefs.restOff = false
        assertFalse(prefs.restIsOff("Back Squat"))
    }

    @Test
    fun `one movement can opt out by name, whatever its case or padding`() {
        prefs.setRestOffFor("  Bicep Curl ", true)
        assertTrue(prefs.restIsOff("bicep curl"))
        assertTrue(prefs.restOffFor("BICEP CURL"))
        assertFalse("another movement still rests", prefs.restIsOff("Back Squat"))
        assertEquals(setOf("bicep curl"), prefs.restOffNames())
        prefs.setRestOffFor("Bicep Curl", false)
        assertFalse(prefs.restIsOff("bicep curl"))
        assertTrue(prefs.restOffNames().isEmpty())
    }

    @Test
    fun `the alert mode is stored and an unknown word reads as vibrate`() {
        RestAlert.entries.forEach {
            prefs.restAlert = it
            assertEquals(it, prefs.restAlert)
        }
        assertEquals(RestAlert.VIBRATE, RestAlert.fromWire("shout"))
        assertEquals(RestAlert.VIBRATE, RestAlert.fromWire(null))
    }

    @Test
    fun `a cue plays what the alert mode allows and silent plays nothing`() {
        val tick = SetClock.Cue.TICK
        val done = SetClock.Cue.DONE
        RestAlert.entries.forEach { assertEquals(RestAlert.CuePlan(0, 0), it.cuePlan(SetClock.Cue.NONE)) }
        assertEquals(RestAlert.CuePlan(0, 0), RestAlert.SILENT.cuePlan(tick))
        assertEquals(RestAlert.CuePlan(0, 0), RestAlert.SILENT.cuePlan(done))
        assertEquals("vibrate buzzes and makes no sound", 0, RestAlert.VIBRATE.cuePlan(tick).toneMs)
        assertTrue(RestAlert.VIBRATE.cuePlan(tick).buzzMs > 0)
        assertTrue(RestAlert.SOUND_VIBRATE.cuePlan(tick).toneMs > 0 && RestAlert.SOUND_VIBRATE.cuePlan(tick).buzzMs > 0)
        assertTrue("the end is longer than a tick", RestAlert.VIBRATE.cuePlan(done).buzzMs > RestAlert.VIBRATE.cuePlan(tick).buzzMs)
        assertTrue(RestAlert.SOUND_VIBRATE.cuePlan(done).toneMs > RestAlert.SOUND_VIBRATE.cuePlan(tick).toneMs)
    }

    @Test
    fun `the alert modes say what they do`() {
        assertTrue(RestAlert.VIBRATE.vibrates && !RestAlert.VIBRATE.sounds)
        assertTrue(RestAlert.SOUND_VIBRATE.vibrates && RestAlert.SOUND_VIBRATE.sounds)
        assertTrue(!RestAlert.SILENT.vibrates && !RestAlert.SILENT.sounds)
    }
}

/** Muscle Coverage's dismissed Heavy flags outlive the screen: they are stored per muscle on the device. */
class DismissedHeavyStoreTest {

    @Test
    fun `a dismissal is kept per muscle and read back by a fresh store`() {
        val prefs = FakePrefs()
        assertEquals(emptySet<Muscle>(), DismissedHeavyStore(prefs).muscles)
        DismissedHeavyStore(prefs).muscles = setOf(Muscle.QUADS, Muscle.CALVES)
        assertEquals(setOf(Muscle.QUADS, Muscle.CALVES), DismissedHeavyStore(prefs).muscles)
    }

    @Test
    fun `a muscle name that no longer exists is skipped, not fatal`() {
        val prefs = FakePrefs()
        prefs.edit().putStringSet("coverage_heavy_dismissed", mutableSetOf("QUADS", "GONE")).apply()
        assertEquals(setOf(Muscle.QUADS), DismissedHeavyStore(prefs).muscles)
    }
}

/** A SharedPreferences in memory. */
private class FakePrefs : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = data.toMutableMap()
    override fun getString(key: String?, defValue: String?) = data[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        @Suppress("UNCHECKED_CAST")
        return data[key] as? MutableSet<String> ?: defValues
    }
    override fun getInt(key: String?, defValue: Int) = data[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long) = data[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float) = data[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean) = data[key] as? Boolean ?: defValue
    override fun contains(key: String?) = data.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val staged = mutableMapOf<String, Any?>()

        private fun put(key: String?, value: Any?) = apply { staged[key!!] = value }
        override fun putString(key: String?, value: String?) = put(key, value)
        override fun putStringSet(key: String?, values: MutableSet<String>?) = put(key, values)
        override fun putInt(key: String?, value: Int) = put(key, value)
        override fun putLong(key: String?, value: Long) = put(key, value)
        override fun putFloat(key: String?, value: Float) = put(key, value)
        override fun putBoolean(key: String?, value: Boolean) = put(key, value)
        override fun remove(key: String?) = apply { staged.remove(key) }
        override fun clear() = apply { data.clear() }
        override fun commit(): Boolean {
            data.putAll(staged)
            return true
        }
        override fun apply() {
            commit()
        }
    }
}
