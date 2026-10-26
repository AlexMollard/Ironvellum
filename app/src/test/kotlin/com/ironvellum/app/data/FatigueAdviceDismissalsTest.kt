package com.ironvellum.app.data

import android.content.SharedPreferences
import com.ironvellum.app.domain.EntryAdjust
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.SessionOverride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** What the lifter waved away from the fatigue suggestions, and the override each workout began with. */
class FatigueAdviceDismissalsTest {

    private val monday = LocalDate.of(2026, 10, 26)

    @Test
    fun `not today hides one routine for one day only`() {
        val store = FatigueAdviceDismissals(AdvicePrefs())
        assertFalse(store.isNotToday(monday, 7))
        store.dismissToday(monday, 7)
        assertTrue(store.isNotToday(monday, 7))
        assertFalse("another routine is still advised", store.isNotToday(monday, 8))
        assertFalse("tomorrow it comes back", store.isNotToday(monday.plusDays(1), 7))
    }

    @Test
    fun `not today is kept on the device and forgets earlier days`() {
        val prefs = AdvicePrefs()
        FatigueAdviceDismissals(prefs).dismissToday(monday, 7)
        assertTrue("a new instance reads it back", FatigueAdviceDismissals(prefs).isNotToday(monday, 7))

        val later = FatigueAdviceDismissals(prefs)
        later.dismissToday(monday.plusDays(1), 9)
        assertEquals("yesterday's is dropped", setOf("${monday.plusDays(1).toEpochDay()}:9"), later.notToday)
    }

    @Test
    fun `keep as is stands per routine and muscle, so another muscle brings the finding back`() {
        val store = FatigueAdviceDismissals(AdvicePrefs())
        store.keepAsIs(3, Muscle.LATS)
        assertTrue(store.isKept(3, Muscle.LATS))
        assertFalse("the same routine on another muscle is a new pattern", store.isKept(3, Muscle.QUADS))
        assertFalse("another routine on the same muscle too", store.isKept(4, Muscle.LATS))
    }

    @Test
    fun `keep as is survives a restart and does not touch not today`() {
        val prefs = AdvicePrefs()
        FatigueAdviceDismissals(prefs).keepAsIs(3, Muscle.LATS)
        val again = FatigueAdviceDismissals(prefs)
        assertTrue(again.isKept(3, Muscle.LATS))
        assertFalse(again.isNotToday(monday, 3))
    }

    @Test
    fun `the override a workout began with is remembered under its session`() {
        val adjustments = PrefsSessionAdjustments(AdvicePrefs())
        val override = SessionOverride(mapOf(4L to EntryAdjust(lighten = true)))
        assertTrue(adjustments.get(10).isEmpty)
        adjustments.put(10, override)
        assertEquals(override.entries, adjustments.get(10).entries)
        assertTrue("another session has none", adjustments.get(11).isEmpty)
    }

    @Test
    fun `an empty override stores nothing and only the newest workouts are kept`() {
        val prefs = AdvicePrefs()
        val adjustments = PrefsSessionAdjustments(prefs)
        adjustments.put(1, SessionOverride.NONE)
        assertTrue(prefs.all.isEmpty())

        val override = SessionOverride(mapOf(4L to EntryAdjust(lighten = true)))
        (1L..25L).forEach { adjustments.put(it, override) }
        assertTrue("the oldest are pruned", adjustments.get(1).isEmpty)
        assertFalse("the newest stay", adjustments.get(25).isEmpty)
        assertEquals(20, prefs.all.size)
    }

    @Test
    fun `the none store remembers nothing`() {
        SessionAdjustments.None.put(1, SessionOverride(mapOf(1L to EntryAdjust(lighten = true))))
        assertTrue(SessionAdjustments.None.get(1).isEmpty)
    }
}

/** An in-memory preferences file, enough for the stores that only read and write strings and string sets. */
internal class AdvicePrefs : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = data.toMutableMap()
    override fun getString(key: String?, defValue: String?) = data[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        @Suppress("UNCHECKED_CAST")
        return (data[key] as? Set<String>)?.toMutableSet() ?: defValues
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
        private val removed = mutableSetOf<String>()

        private fun put(key: String?, value: Any?) = apply { staged[key!!] = value }
        override fun putString(key: String?, value: String?) = put(key, value)
        override fun putStringSet(key: String?, values: MutableSet<String>?) = put(key, values?.toSet())
        override fun putInt(key: String?, value: Int) = put(key, value)
        override fun putLong(key: String?, value: Long) = put(key, value)
        override fun putFloat(key: String?, value: Float) = put(key, value)
        override fun putBoolean(key: String?, value: Boolean) = put(key, value)
        override fun remove(key: String?) = apply { removed += key!! }
        override fun clear() = apply { data.clear() }
        override fun commit(): Boolean {
            removed.forEach { data.remove(it) }
            data.putAll(staged)
            return true
        }
        override fun apply() {
            commit()
        }
    }
}
