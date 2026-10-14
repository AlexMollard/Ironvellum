package com.ironvellum.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironvellum.app.data.Units
import com.ironvellum.app.domain.LiftLoading
import com.ironvellum.app.domain.SchemeType
import com.ironvellum.app.domain.SetScheme
import com.ironvellum.app.ui.theme.IronvellumTheme
import com.ironvellum.app.ui.train.EditorEntry
import com.ironvellum.app.ui.train.SetsSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The rite editor's "Sets for <lift>" sheet: choosing a scheme previews the sets the trial will make and Done hands back
 * the entry with the scheme and the set count and reps it implies; a drop scheme is refused inside a superset.
 */
@RunWith(AndroidJUnit4::class)
class SetsSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private var done: EditorEntry? = null

    private val entry = EditorEntry(1, "Weighted Pull-Up", sets = "3", reps = "8", weight = "22", modifiers = "")
    private val lift = LiftLoading(stepKg = 2.5, bodyweightKg = 80.0, bodyweightLift = true)

    private fun show(entry: EditorEntry, inSuperset: Boolean = false) {
        done = null
        compose.setContent {
            IronvellumTheme {
                SetsSheet(entry = entry, lift = lift, worn = null, inSuperset = inSuperset, onDone = { done = it }, onDismiss = {})
            }
        }
    }

    @Test
    fun choosingTopAndBackoffsPreviewsThemAndDoneSavesTheScheme() {
        show(entry)
        compose.onNodeWithText("Sets for Weighted Pull-Up").assertExists()
        compose.onNodeWithText("Top set + back-offs").performScrollTo().performClick()
        compose.onNodeWithText("You'll do").performScrollTo().assertExists()
        compose.onNodeWithText("Done").performClick()

        val saved = done
        assertNotNull("Done must hand the entry back", saved)
        assertEquals(SchemeType.TOP_BACKOFF, saved!!.scheme.type)
        // One top set and two back-offs: three working sets, the entry's reps the top set's floor.
        assertEquals("3", saved.sets)
        assertEquals(saved.scheme.topReps.first.toString(), saved.reps)
    }

    @Test
    fun theOwnersPullUpPlanReadsBackExactly() {
        val owner = entry.copy(
            sets = "4",
            reps = "2",
            scheme = SetScheme(
                type = SchemeType.TOP_BACKOFF, topReps = 2..4, backoffSets = 3, backoffFixedKg = 15.0, backoffReps = 4..5,
            ),
        )
        show(owner)
        // The back-offs' own load is shown as given, not worked out from the top set.
        compose.onNodeWithText("4–5 × +" + Units.withUnit(15.0)).performScrollTo().assertExists()
        compose.onNodeWithText("Done").performClick()
        assertEquals(15.0, done!!.scheme.backoffFixedKg!!, 0.0)
        assertEquals("4", done!!.sets)
    }

    @Test
    fun dropSetsAreRefusedInsideASuperset() {
        show(entry.copy(supersetGroup = 1), inSuperset = true)
        compose.onNodeWithText("Not inside a superset").performScrollTo().assertExists()
        compose.onNodeWithText("Drop sets").assertIsNotEnabled()
    }
}
