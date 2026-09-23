package com.ironvellum.app.data.cloud

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironvellum.app.IronvellumApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Switching backends without forgetting the push watermark leaves every
 * session marked "already on the server" against a project that has never
 * seen it (skill: client-watermark-server-wipe) — the training would sit
 * local forever while Settings showed cloud on. The watermark reset is wired
 * through Cloud.reconfigure's beforeSwap hook, so this pins the ORDER too:
 * the client must be swapped AND sync_state empty afterwards.
 */
@RunWith(AndroidJUnit4::class)
class ReconfigureResetsWatermarkTest {

    @Test
    fun reconfigureSwapsTheClientAndClearsTheWatermark() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<IronvellumApp>()
        Cloud.init(app)
        val oldClient = Cloud.client()
        val cloudSync = app.cloudSync

        try {
            Cloud.reconfigure(
                CloudConfig("https://byo-test.example.supabase.co", "test-anon-key", isDefault = false),
            ) {
                cloudSync.forgetPushedState()
            }

            assertNotEquals(
                "Cloud.client() still returned the old backend's instance after reconfigure",
                oldClient,
                Cloud.client(),
            )
            assertTrue(
                "sync_state survived the switch — sessions would never re-upload to the new backend",
                app.repository.pushWatermark().isEmpty(),
            )
        } finally {
            // Put the app back on its build default so the rest of the suite
            // is not pointed at a fake project.
            Cloud.reconfigure(null) {
                cloudSync.forgetPushedState()
            }
        }
    }
}
