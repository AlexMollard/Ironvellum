package com.monarch.app.ui

import androidx.test.platform.app.InstrumentationRegistry
import com.monarch.app.MonarchApp
import kotlinx.coroutines.runBlocking

/**
 * First-run onboarding stands in front of the whole app until a height exists,
 * which is correct for a new hunter and fatal for a UI test: an instrumented
 * run installs a clean app, so every test that reaches for the nav bar was
 * landing on the welcome screen instead and failing with "no compose
 * hierarchies" or a missing Train destination.
 *
 * Any test that drives the app past its front door calls this first. It writes
 * through the app's OWN repository - a second handle on the database while the
 * app holds it open is the divergence trap DbSnapshot documents - and it is
 * idempotent, so a test that runs after a real profile exists changes nothing.
 *
 * Setup also no longer imposes a training week, so a hunter who skips it owns
 * an empty board. Tests that begin a session need one to begin, so this writes
 * the starter week through the same call the setup flow offers - only when the
 * board is empty, leaving a real hunter's own presets untouched.
 */
object TestProfile {

    /** A height the profile bounds accept; the value itself is irrelevant. */
    const val HEIGHT_CM = 175.0

    fun ensureSetUp() {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as MonarchApp
        runBlocking {
            val repo = app.repository
            repo.ensureSeeded()
            if (app.database.profileDao().get()?.heightCm == null) {
                repo.setHeight(HEIGHT_CM)
            }
            if (app.database.presetDao().count() == 0) {
                repo.applyStarterTemplate()
            }
        }
    }
}
