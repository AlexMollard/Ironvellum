package com.ironvellum.app.data

import android.content.SharedPreferences
import com.ironvellum.app.domain.Circle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CircleBonus is the one door every circle read passes through, so the rules
 * around the pay call are what keep a bonus from being paid twice or never:
 * the 30s rate limit, the gate that skips a lifter who never joined a circle
 * while still paying one who has left, and a failed lookup that must not hide
 * the circle that was read. The cloud, the account and the repository are
 * replaced by the four calls the class makes; the once-per-week flags
 * themselves are Repository.payCircleBonus's and are not exercised here.
 */
class CircleBonusTest {

    private val me = "lifter-1"
    private val circle = Circle(id = "c1", name = "North Gate", code = "ABCD2345", ownerId = me, members = emptyList())
    private val week = CircleBonusWeek("2026-09-21", 3, "North Gate")
    private val paid = CircleBonusPaid(40, "North Gate")

    private class Fixture(
        var userId: String? = "lifter-1",
        var circle: Circle? = null,
        var circleFailure: Throwable? = null,
        var owed: Result<List<CircleBonusWeek>> = Result.success(emptyList()),
        var payResult: CircleBonusPaid? = null,
        var payFailure: Throwable? = null,
        var now: Long = 1_000_000L,
    ) {
        val prefs = MemoryPrefs()
        val store = CirclePayoutStore(prefs)
        var circleReads = 0
        var lookups = 0
        var pays = 0

        val bonus = CircleBonus(
            readCircle = {
                circleReads++
                circleFailure?.let { Result.failure(it) } ?: Result.success(circle)
            },
            myUserId = { userId },
            owedWeeks = { lookups++; owed },
            pay = { _, _ ->
                pays++
                payFailure?.let { throw it }
                payResult
            },
            store = store,
            clock = { now },
        )
    }

    @Test
    fun `a read in a circle looks up the bonus, pays it and hands the payout back`() = runTest {
        val f = Fixture(circle = circle, owed = Result.success(listOf(week)), payResult = paid)
        val read = f.bonus.read().getOrThrow()
        assertEquals(circle, read.circle)
        assertEquals(paid, read.paid)
        assertEquals(1, f.pays)
    }

    @Test
    fun `reads inside 30 seconds pay once, a forced read does not wait`() = runTest {
        val f = Fixture(circle = circle, payResult = paid)
        f.bonus.read()
        f.now += CircleBonus.CHECK_EVERY_MS - 1
        assertNull(f.bonus.read().getOrThrow().paid)
        assertEquals(1, f.lookups)
        f.bonus.read(force = true)
        assertEquals(2, f.lookups)
        f.now += CircleBonus.CHECK_EVERY_MS
        f.bonus.read()
        assertEquals(3, f.lookups)
    }

    @Test
    fun `a lifter who never joined a circle costs no lookup`() = runTest {
        val f = Fixture(circle = null)
        assertNull(f.bonus.read(force = true).getOrThrow().paid)
        assertEquals(0, f.lookups)
        assertEquals(0, f.pays)
    }

    @Test
    fun `a lifter who has left still gets the week they left in`() = runTest {
        val f = Fixture(circle = circle, payResult = null)
        f.bonus.read() // seen in a circle: remembered on the phone
        f.circle = null
        f.now += CircleBonus.CHECK_EVERY_MS
        f.payResult = paid
        val read = f.bonus.read().getOrThrow()
        assertNull(read.circle)
        assertEquals(paid, read.paid)
        assertEquals(2, f.pays)
    }

    @Test
    fun `a lookup that fails leaves the circle on screen and the rate limit spent`() = runTest {
        val f = Fixture(circle = circle, owed = Result.failure(IllegalStateException("offline")))
        val read = f.bonus.read().getOrThrow()
        assertEquals(circle, read.circle)
        assertNull(read.paid)
        assertEquals(0, f.pays)
        f.bonus.read()
        assertEquals(1, f.lookups)
    }

    @Test
    fun `a payout that throws leaves the circle on screen`() = runTest {
        val f = Fixture(circle = circle, payFailure = IllegalStateException("disk full"))
        val read = f.bonus.read().getOrThrow()
        assertEquals(circle, read.circle)
        assertNull(read.paid)
    }

    @Test
    fun `a failed circle read is the read's failure and settles nothing`() = runTest {
        val f = Fixture(circle = circle, circleFailure = IllegalStateException("offline"))
        assertTrue(f.bonus.read().isFailure)
        assertEquals(0, f.lookups)
    }

    @Test
    fun `signed out, the circle is read and nothing is paid`() = runTest {
        val f = Fixture(userId = null, circle = circle)
        val read = f.bonus.read().getOrThrow()
        assertNotNull(read.circle)
        assertNull(read.paid)
        assertEquals(0, f.lookups)
    }

    @Test
    fun `being seen in a circle is remembered per lifter`() = runTest {
        val f = Fixture(circle = circle)
        f.bonus.read()
        assertTrue(f.store.wasInCircle(me))
        assertTrue(!f.store.wasInCircle("someone-else"))
    }
}

/** A SharedPreferences in memory: CirclePayoutStore only reads and writes booleans. */
private class MemoryPrefs : SharedPreferences {
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
        private val removed = mutableSetOf<String>()
        private var cleared = false

        private fun put(key: String?, value: Any?) = apply { staged[key!!] = value }
        override fun putString(key: String?, value: String?) = put(key, value)
        override fun putStringSet(key: String?, values: MutableSet<String>?) = put(key, values)
        override fun putInt(key: String?, value: Int) = put(key, value)
        override fun putLong(key: String?, value: Long) = put(key, value)
        override fun putFloat(key: String?, value: Float) = put(key, value)
        override fun putBoolean(key: String?, value: Boolean) = put(key, value)
        override fun remove(key: String?) = apply { removed += key!! }
        override fun clear() = apply { cleared = true }
        override fun commit(): Boolean {
            if (cleared) data.clear()
            removed.forEach { data.remove(it) }
            data.putAll(staged)
            return true
        }
        override fun apply() {
            commit()
        }
    }
}
