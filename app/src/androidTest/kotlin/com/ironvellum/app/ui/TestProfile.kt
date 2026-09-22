package com.ironvellum.app.ui

import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.IronvellumApp
import kotlinx.coroutines.runBlocking

/**
 * First-run onboarding stands in front of the whole app until a height exists,
 * which is correct for a new lifter and fatal for a UI test: an instrumented
 * run installs a clean app, so every test that reaches for the nav bar was
 * landing on the welcome screen instead and failing with "no compose
 * hierarchies" or a missing Train destination.
 *
 * Any test that drives the app past its front door calls this first. It writes
 * through the app's OWN repository - a second handle on the database while the
 * app holds it open is the divergence trap DbSnapshot documents - and it is
 * idempotent, so a test that runs after a real profile exists changes nothing.
 *
 * Setup also no longer imposes a training week, so a lifter who skips it owns
 * an empty board. Tests that begin a session need one, and tests that name a
 * starter preset need THAT one, so the starter week is written every time.
 *
 * Writing it only when the board was empty is what this replaced: driving the
 * app by hand left a generated routine behind, the helper honoured it, and a
 * test asserting on a starter preset failed on a machine where it had passed
 * an hour earlier. A UI test may not inherit whatever the last run left.
 */
object TestProfile {

    /** A height the profile bounds accept; the value itself is irrelevant. */
    const val HEIGHT_CM = 175.0

    fun ensureSetUp() {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as IronvellumApp
        runBlocking {
            val repo = app.repository
            repo.ensureSeeded()
            if (app.database.profileDao().get()?.heightCm == null) {
                repo.setHeight(HEIGHT_CM)
            }
            // A full replace inside one transaction - the same call the setup
            // flow offers a lifter who takes the starter week.
            repo.applyStarterTemplate()
        }
    }
}
