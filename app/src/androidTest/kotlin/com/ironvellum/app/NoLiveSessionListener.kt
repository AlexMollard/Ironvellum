package com.ironvellum.app

import androidx.test.platform.app.InstrumentationRegistry
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
 */
class NoLiveSessionListener : RunListener() {
    override fun testStarted(description: Description) {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as IronvellumApp
        val sessions = app.database.sessionDao()
        runBlocking {
            while (true) {
                val live = sessions.liveSession() ?: break
                sessions.deleteAbandoned(live.id)
            }
        }
    }
}
