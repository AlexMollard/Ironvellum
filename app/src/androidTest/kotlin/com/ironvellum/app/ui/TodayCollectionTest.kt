package com.ironvellum.app.ui

import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodayCollectionTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun returningToTodayBanksEssenceOnceWithoutEnteringTheVeil() {
        TestProfile.ensureSetUp()
        val driver = FlowDriver(compose)
        driver.takeTheClock()
        driver.tab("Codex")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IronvellumApp
        val dao = app.database.idleDao()
        val original = runBlocking { checkNotNull(dao.get()) }
        val baseline = System.currentTimeMillis() - 6 * 3_600_000L
        try {
            runBlocking { dao.upsert(original.copy(essence = 0, lifetimeEssence = 0, lastCollectedAtMs = baseline)) }
            driver.tab("Today")
            compose.waitUntil(10_000) {
                compose.mainClock.advanceTimeByFrame()
                runBlocking { dao.get()!!.lastCollectedAtMs > baseline }
            }
            val collected = runBlocking { dao.get()!! }
            assertTrue("Today must bank the six-hour interval", collected.essence > 0)
            assertEquals(collected.essence, collected.lifetimeEssence)
            ShotRecorder.take(compose, "today-collected-veil")
            // The three-second preview tick must never trigger another collection.
            SystemClock.sleep(3_500)
            driver.settle()
            assertEquals(collected.lastCollectedAtMs, runBlocking { dao.get()!!.lastCollectedAtMs })
        } finally {
            runBlocking { dao.upsert(original) }
        }
    }
}
