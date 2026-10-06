package com.ironvellum.app.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironvellum.app.data.IdleSnapshot
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.IdleRate
import com.ironvellum.app.domain.IdleState
import com.ironvellum.app.domain.LiftRecord
import com.ironvellum.app.domain.MuscleGroup
import com.ironvellum.app.domain.PlayerProfile
import com.ironvellum.app.domain.PresetEntry
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.WorkoutPreset
import com.ironvellum.app.domain.WorkoutSession
import com.ironvellum.app.ui.theme.IronvellumTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Today never needs scrolling on the owner's phone (411x891dp, less the status bar and the bottom
 * nav: about 411x760dp of content). It renders Today's content in the worst day it can be given and
 * asserts every part sits inside that box, that exercise rows folded into "+N more", and that the
 * probes the layout measures with leave no second copy behind. The Veil's reserved inscriptions line
 * must never move anything, whether none or three wait.
 */
@RunWith(AndroidJUnit4::class)
class TodayFitsTest {

    @get:Rule
    val compose = createComposeRule()

    /** A Monday, so Wednesday (3) is another day's rite and Monday (1) is today. */
    private val today = LocalDate.of(2026, 10, 5)
    private val now = System.currentTimeMillis()
    private val hour = 3_600_000L

    private fun rite(entries: Int, day: Int = 3) = WorkoutPreset(
        id = 1,
        name = "Full Body A",
        scheduledDay = day,
        entries = (1..entries).map {
            PresetEntry(exerciseId = it.toLong(), exerciseName = "Movement $it", targetSets = 3, targetReps = 8, position = it)
        },
    )

    private val bench = SessionSet(exerciseId = 99, exerciseName = "Bench Press", setIndex = 0, reps = 5, weightKg = 100.0, done = true)
    private val peak = LiftRecord(
        name = "Bench Press", bestE1rmKg = 116.7, bestAtMs = now - 24 * hour, series = listOf(104.0, 116.7),
        deltaKg = null, lastAtMs = now, bestSet = bench,
    )
    private val trial = WorkoutSession(id = 7, presetId = null, label = "Open Trial", startedAtMs = now - hour)
    /** Nine movements, one set each, the first logged. */
    private val trialSets = (0..8).map {
        SessionSet(exerciseId = 10L + it, exerciseName = "Trial move $it", exercisePosition = it, setIndex = 0, reps = 8, weightKg = 40.0, done = it == 0)
    }
    private val veil = VeilGlance(
        IdleSnapshot(IdleState(essence = 12_345, figures = 3, relicMultiplier = 1.25, lastCollectedAtMs = now - 9 * hour), IdleRate(40.0, 1.0, 1.0)),
        inscriptions = 3,
    )

    private fun show(
        ui: DashboardUi,
        selectedDay: Int,
        live: WorkoutSession? = null,
        liveSets: List<SessionSet> = emptyList(),
        bodyGap: BodyGap? = null,
        height: Int = PHONE_CONTENT_HEIGHT_DP,
        fontScale: Float = 1f,
        motion: Boolean = false,
        glance: VeilGlance = veil,
    ) {
        // The ink treatment animates forever, so Compose never idles on its own. The Veil's own motion is
        // off unless a test asks for it: the emulator runs with system animations on, so it would not be.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IronvellumTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    Box(Modifier.size(PHONE_WIDTH_DP.dp, height.dp).testTag("today")) {
                        TodayContent(
                            ui = ui, selectedDay = selectedDay, today = today, live = live, liveSets = liveSets,
                            bodyGap = bodyGap, strengthRank = "Iron", rankBreakdown = null, veil = glance,
                            actions = TodayActions(), nowMs = now, motion = motion,
                        )
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
    }

    /** The node is shown once, whole, inside the Today box. */
    private fun assertInside(text: String, substring: Boolean = false) {
        compose.onAllNodesWithText(text, substring = substring).assertCountEquals(1)
        val node = compose.onNodeWithText(text, substring = substring)
        node.assertIsDisplayed()
        val box = compose.onNodeWithTag("today").getUnclippedBoundsInRoot()
        val bounds = node.getUnclippedBoundsInRoot()
        assertTrue("\"$text\" runs past the box: ${bounds.bottom} > ${box.bottom}", bounds.bottom <= box.bottom)
        assertTrue("\"$text\" starts above the box", bounds.top >= box.top)
    }

    /** Saves the Today box to the app's external files dir as `<name>.png` for a human to look at; never fails a test. */
    private fun saveShot(name: String) {
        runCatching {
            compose.mainClock.autoAdvance = true
            val image = compose.onNodeWithTag("today").captureToImage()
            val dir = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
            java.io.File(dir, "$name.png").outputStream().use { image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun assertDoesNotScroll() = compose.onAllNodes(hasScrollAction()).assertCountEquals(0)

    @Test
    fun theWorstDayFitsWithRowsFoldedAndNothingElseLost() {
        // Another day's nine-movement rite, a trial under way, both body gaps, a fresh peak and
        // inscriptions waiting: every plain row at once.
        show(
            ui = DashboardUi(presets = listOf(rite(9)), newPeaks = listOf(peak)),
            selectedDay = 3, live = trial, liveSets = trialSets, bodyGap = BodyGap.BOTH,
        )
        assertDoesNotScroll()
        assertInside("Continue Open Trial")
        assertInside("Add your height and weight")
        assertInside("Bench Press")
        assertInside("New peak", substring = true)
        assertInside("100 kg × 5")
        assertInside("The Veil")
        // The compact Veil: one line of essence and rate over the bar; its caption rides the bar for a screen reader.
        assertInside("essence", substring = true)
        compose.onAllNodesWithText("h left at full strength", substring = true).assertCountEquals(0)
        assertNotPlaced("veil-hero")
        assertInside("3 inscriptions waiting")
        assertInside("Inscribe")
        assertInside("more", substring = true)
        assertInside("Movement 1")
        saveShot("today-worst-case")
    }

    @Test
    fun aLiveTrialKeepsItsContinueButtonInsideTheBox() {
        show(
            ui = DashboardUi(presets = listOf(rite(9, day = 1)), newPeaks = listOf(peak)),
            selectedDay = 1, live = WorkoutSession(id = 7, presetId = 1, label = "Full Body A", startedAtMs = now - hour),
            liveSets = trialSets, bodyGap = BodyGap.HEIGHT,
        )
        assertDoesNotScroll()
        assertInside("Continue Full Body A")
        assertInside("The Veil")
        assertInside("Add your height")
        assertInside("more", substring = true)
    }

    @Test
    fun aSmallRiteShowsEveryRowAndNoMoreLine() {
        show(ui = DashboardUi(presets = listOf(rite(3, day = 1))), selectedDay = 1)
        assertDoesNotScroll()
        assertInside("Movement 3")
        assertInside("Begin Full Body A")
        assertInside("The Veil")
        // Room to spare: the full Veil, with its figure, caption and the reserved line.
        assertInside("essence")
        assertInside("h left at full strength", substring = true)
        assertInside("3 inscriptions waiting")
        compose.onAllNodesWithText("more", substring = true).assertCountEquals(0)
        saveShot("today-training-full")
    }

    @Test
    fun aRespiteDayShowsTheFullVeil() {
        show(ui = DashboardUi(presets = listOf(rite(9, day = 3))), selectedDay = 2, bodyGap = BodyGap.WEIGHT)
        assertDoesNotScroll()
        assertPlaced("veil-hero")
        assertInside("essence · 3 echoes · relic ×1.25")
        assertInside("Add a weight reading")
        assertInside("3 inscriptions waiting")
        saveShot("today-respite-full")
    }

    @Test
    fun aRespiteDayInATightBoxFallsBackToTheCompactVeil() {
        show(ui = DashboardUi(presets = listOf(rite(9, day = 3))), selectedDay = 2, bodyGap = BodyGap.BOTH, height = 610)
        assertDoesNotScroll()
        assertNotPlaced("veil-hero")
        assertInside("essence", substring = true)
        assertInside("3 inscriptions waiting")
        saveShot("today-respite-compact")
    }

    /**
     * The tagged nodes that are really on the page. The layout composes its measuring probes with the same
     * tags and never places them, and the unmerged tree still lists them.
     */
    private fun placed(tag: String) =
        compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().filter { it.layoutInfo.isPlaced }

    private fun assertPlaced(tag: String) = assertEquals("\"$tag\" should be on the page once", 1, placed(tag).size)

    private fun assertNotPlaced(tag: String) = assertEquals("\"$tag\" should not be on the page", 0, placed(tag).size)

    /** The Veil's own box, its inscriptions line, and the exercise rows when there are any. */
    private fun veilAndRowsBounds(): List<androidx.compose.ui.geometry.Rect> =
        listOf("veil-section", "veil-inscriptions", "today-rows").mapNotNull { placed(it).singleOrNull()?.boundsInRoot }

    private fun veilWith(waiting: Int) = veil.let { VeilGlance(it.snapshot, waiting) }

    /** One composition, the glance flipped from no inscriptions to three while it is on screen. */
    private fun assertInscriptionsNeverMoveTheLayout(
        ui: DashboardUi,
        selectedDay: Int,
        live: WorkoutSession? = null,
        liveSets: List<SessionSet> = emptyList(),
        height: Int = PHONE_CONTENT_HEIGHT_DP,
    ): List<androidx.compose.ui.geometry.Rect> {
        var glance by mutableStateOf(veilWith(0))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IronvellumTheme {
                Box(Modifier.size(PHONE_WIDTH_DP.dp, height.dp).testTag("today")) {
                    TodayContent(
                        ui = ui, selectedDay = selectedDay, today = today, live = live, liveSets = liveSets,
                        bodyGap = null, strengthRank = "Iron", rankBreakdown = null, veil = glance,
                        actions = TodayActions(), nowMs = now, motion = false,
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        val none = veilAndRowsBounds()
        compose.onNodeWithText("No inscriptions waiting").assertIsDisplayed()
        compose.onAllNodesWithText("Inscribe").assertCountEquals(0)
        compose.runOnUiThread { glance = veilWith(3) }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("3 inscriptions waiting").assertIsDisplayed()
        compose.onNodeWithText("Inscribe").assertIsDisplayed()
        assertEquals("an inscription arriving moved the Veil or the rows", none, veilAndRowsBounds())
        assertDoesNotScroll()
        return none
    }

    @Test
    fun anInscriptionArrivingNeverMovesTheFullVeilOrTheRows() {
        val bounds = assertInscriptionsNeverMoveTheLayout(DashboardUi(presets = listOf(rite(3, day = 1))), selectedDay = 1)
        assertTrue("expected the Veil, its line and the rows", bounds.size == 3)
    }

    @Test
    fun anInscriptionArrivingNeverMovesTheCompactVeilOrTheRows() {
        val bounds = assertInscriptionsNeverMoveTheLayout(
            DashboardUi(presets = listOf(rite(9)), newPeaks = listOf(peak)),
            selectedDay = 3, live = trial, liveSets = trialSets,
        )
        assertTrue("expected the Veil, its line and the rows", bounds.size == 3)
        assertNotPlaced("veil-hero")
    }

    @Test
    fun anInscriptionArrivingNeverMovesTheRespiteHero() {
        val bounds = assertInscriptionsNeverMoveTheLayout(DashboardUi(presets = listOf(rite(9, day = 3))), selectedDay = 2)
        assertEquals("a respite day has no rows", 2, bounds.size)
        assertPlaced("veil-hero")
    }

    @Test
    fun theVeilMovesNothingWhileItDrifts() {
        show(ui = DashboardUi(presets = listOf(rite(3, day = 1))), selectedDay = 1, motion = true)
        val before = veilAndRowsBounds()
        // Long enough for the essence to be recomputed more than once and for every loop to wrap.
        compose.mainClock.advanceTimeBy(7_000)
        assertEquals(before, veilAndRowsBounds())
        assertInside("Begin Full Body A")
        assertDoesNotScroll()
    }

    @Test
    fun aRespiteHeroKeepsItsPlaceWhileTheRingTurns() {
        show(ui = DashboardUi(presets = listOf(rite(9, day = 3))), selectedDay = 2, bodyGap = BodyGap.WEIGHT, motion = true)
        val before = veilAndRowsBounds()
        compose.mainClock.advanceTimeBy(7_000)
        assertEquals(before, veilAndRowsBounds())
        assertPlaced("veil-hero")
        assertDoesNotScroll()
    }

    @Test
    fun aSealedDayShowsItsNarratorAndFitsAnEightMovementRite() {
        val sealed = WorkoutSession(id = 5, presetId = 1, label = "Full Body A", startedAtMs = now - 2 * hour, completedAtMs = now - hour, xpAwarded = 268)
        val sets = (0..7).map {
            SessionSet(exerciseId = 20L + it, exerciseName = "Sealed move $it", exercisePosition = it, setIndex = 0, reps = 8, weightKg = 40.0, done = true)
        }
        show(
            ui = DashboardUi(
                presets = listOf(rite(8, day = 1), rite(8, day = 3).copy(id = 2, name = "Full Body B")),
                weekDone = mapOf(1 to sealed), sealedSets = mapOf(5L to sets), newPeaks = listOf(peak),
            ),
            selectedDay = 1, motion = true,
        )
        assertDoesNotScroll()
        assertInside("The Ledger gilds its page.")
        assertInside("Sealed", substring = true)
        assertInside("Sealed move 0")
        assertInside("Bench Press")
        assertInside("The Veil")
        saveShot("today-sealed")
    }

    @Test
    fun aLongNameGivesWayToTheRank() {
        val name = "Bartholomew Featherstonehaugh of the Northern Reach"
        show(ui = DashboardUi(profile = PlayerProfile(name = name), presets = listOf(rite(3, day = 1))), selectedDay = 1)
        // The rank link stays whole and on screen; the name is the one that ellipsizes.
        assertInside("Unranked", substring = true)
    }

    @Test
    fun whenNothingCanFitItScrollsRatherThanClip() {
        show(
            ui = DashboardUi(presets = listOf(rite(9)), newPeaks = listOf(peak)),
            selectedDay = 3, live = trial, liveSets = trialSets, bodyGap = BodyGap.BOTH, fontScale = 2f,
        )
        compose.onAllNodes(hasScrollAction()).assertCountEquals(1)
        // Scrolling really has somewhere to go, and everything is still there to scroll to.
        val range = compose.onNode(hasScrollAction()).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("nothing to scroll", range.maxValue() > 0f)
        compose.onNodeWithText("Inscribe").assertExists()
        compose.onNodeWithText("Add your height and weight").assertExists()
    }

    private companion object {
        const val PHONE_WIDTH_DP = 411

        /**
         * 891dp minus the status bar and the bottom nav. On the emulator (1080x2340 at 420dpi, the phone)
         * the nav's top edge sits at about 792dp and the status bar ends at about 24dp: 768dp, taken
         * as 760 to stay on the safe side.
         */
        const val PHONE_CONTENT_HEIGHT_DP = 760
    }
}
