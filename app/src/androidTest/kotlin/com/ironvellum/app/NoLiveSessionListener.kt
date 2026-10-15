package com.ironvellum.app

import android.Manifest
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.Words
import kotlinx.coroutines.runBlocking
import org.junit.runner.Description
import org.junit.runner.notification.RunListener

/**
 * The app reopens a recent unfinished session on launch, and many data tests
 * leave one behind in the app's own database. A UI test that followed them
 * launched straight into that trial instead of Today. A compose rule starts
 * the activity before any @Before, so the clean-up has to run earlier than a
 * test can: here, as each test starts. A test that wants a live session
 * creates it itself.
 *
 * Notifications are granted up front for the same reason: the first trial
 * asks for them, and the system dialog then sat over whichever UI test ran
 * next, hiding the controls it looked for.
 *
 * The suite asserts on the themed names, and a fresh install now starts plain, so each test starts themed.
 * A test that wants the plain words flips the switch itself (NamesToggleTest).
 */
class NoLiveSessionListener : RunListener() {
    override fun testStarted(description: Description) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as IronvellumApp
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        Words.setThemed(app, true)
        val sessions = app.database.sessionDao()
        runBlocking {
            while (true) {
                val live = sessions.liveSession() ?: break
                sessions.deleteAbandoned(live.id)
            }
        }
    }
}
