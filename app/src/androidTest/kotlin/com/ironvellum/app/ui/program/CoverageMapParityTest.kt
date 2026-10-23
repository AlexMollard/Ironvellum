package com.ironvellum.app.ui.program

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.ui.FlowDriver
import com.ironvellum.app.ui.TestProfile
import com.ironvellum.app.ui.theme.IronvellumTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The preview page and a bare [CoverageMap] fed the same plan describe the figure in the same words. */
@RunWith(AndroidJUnit4::class)
class CoverageMapParityTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val driver by lazy { FlowDriver(compose) }

    private val plan = listOf(
        PlannedPreset("Push", "", 1, listOf(PlannedEntry("Bench Press", 30, 8, null), PlannedEntry("Squat", 4, 8, null))),
    )

    @Before
    fun setUp() {
        TestProfile.ensureSetUp()
        driver.takeTheClock()
    }

    private fun summaries(): List<String> =
        compose.onAllNodes(
            SemanticsMatcher("coverage summary") {
                it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { d -> d.startsWith("Muscle coverage:") } == true
            },
            useUnmergedTree = true,
        ).fetchSemanticsNodes().mapNotNull { n -> n.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() }

    @Test
    fun theFigureSaysTheSameThingOnEveryEntry() {
        compose.activityRule.scenario.onActivity { it.setContent { IronvellumTheme { CoveragePreviewScreen(plan) {} } } }
        driver.settle()
        val preview = summaries().single()

        val model = coverageMapModel(plan, pooled = false, dismissed = emptySet())
        compose.activityRule.scenario.onActivity {
            it.setContent {
                IronvellumTheme {
                    CoverageMap(model.loads, emptySet(), mutableStateOf<Muscle?>(null), unattributed = 0)
                }
            }
        }
        driver.settle()
        assertEquals(preview, summaries().single())
        assertTrue(preview.contains("heavy", ignoreCase = true))
    }
}
