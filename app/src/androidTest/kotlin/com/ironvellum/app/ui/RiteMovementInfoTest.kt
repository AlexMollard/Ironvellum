package com.ironvellum.app.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.MainActivity
import com.ironvellum.app.data.Words
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A movement of a routine is a way to its exercise sheet: on the routine page, and by an info button in the editor. */
@RunWith(AndroidJUnit4::class)
class RiteMovementInfoTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val driver by lazy { FlowDriver(compose) }

    @Before
    fun setUp() {
        Words.setThemed(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IronvellumApp, false)
        TestProfile.ensureSetUp()
        driver.takeTheClock()
        driver.settle()
    }

    private fun clickLabelled(prefix: String) {
        compose.onAllNodes(SemanticsMatcher("clickable \"$prefix...\"") {
            it.config.getOrNull(SemanticsActions.OnClick)?.label?.startsWith(prefix) == true
        }).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        driver.settle()
    }

    @Test
    fun aMovementOnTheRoutinePageAndInTheEditorOpensItsExerciseSheet() {
        driver.tab("Train")
        driver.awaitText("Empty workout")
        clickLabelled("Open ")
        driver.awaitText("Begin ")
        clickLabelled("About ")
        driver.awaitText("Close")
        driver.click("Close")
        driver.click("Edit")
        driver.awaitText("Save routine")
        // The entries load after the page opens; their summary lines read "2 × 5 · 40 kg".
        driver.awaitText("×")
        clickLabelled("About ")
        driver.awaitText("Close")
    }
}
