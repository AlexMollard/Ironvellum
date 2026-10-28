package com.ironvellum.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironvellum.app.data.IdleSnapshot
import com.ironvellum.app.domain.Crests
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.IdleRate
import com.ironvellum.app.domain.IdleState
import com.ironvellum.app.domain.MuscleMap
import com.ironvellum.app.domain.RepMax
import com.ironvellum.app.domain.MuscleGroup
import com.ironvellum.app.domain.PlayerProfile
import com.ironvellum.app.domain.PresetEntry
import com.ironvellum.app.domain.RelicHouses
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.VaultSlot
import com.ironvellum.app.domain.WorkoutPreset
import com.ironvellum.app.domain.WorkoutSession
import com.ironvellum.app.ui.recap.RecapSample
import com.ironvellum.app.ui.recap.ReviewSample
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.program.exerciseMuscleSummary
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
    private val peak = RepMax.Recent(
        name = "Bench Press", atMs = now - 24 * hour, hit = RepMax.Hit(bench, RepMax.Kind.HEAVIER, RepMax.Mark(5, 90.0)),
        isHold = false, addsToBody = false,
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
        onOpenRecap: (Long) -> Unit = {},
        onOpenReview: (String, Long) -> Unit = { _, _ -> },
        onDismissReview: (String) -> Unit = {},
        onOpenRite: (Long) -> Unit = {},
        report: com.ironvellum.app.domain.FatigueReport? = null,
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
                            actions = TodayActions(onOpenRecap = onOpenRecap, onOpenReview = onOpenReview, onDismissReview = onDismissReview, onOpenRite = onOpenRite), nowMs = now, motion = motion,
                            report = report,
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

    /** The words no longer drawn under a progress bar are still the bar's spoken state. */
    private fun assertStateInside(tag: String, words: String) {
        val state = compose.onNodeWithTag(tag).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription]
        assertTrue("\"$state\" should say \"$words\"", state.contains(words))
        compose.onAllNodesWithText(words, substring = true, useUnmergedTree = true).assertCountEquals(0)
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
        assertInside("Inscribe (3)")
        assertInside("more", substring = true)
        assertInside("Movement 1")
        saveShot("today-worst-case")
    }

    @Test
    fun the48dpExerciseLinesStillFitAShortPhone() {
        // 640dp of content: 120dp less than the owner's phone. Lines are 48dp tall, so a nine-movement rite folds.
        show(ui = DashboardUi(presets = listOf(rite(9))), selectedDay = 3, height = 640)
        assertDoesNotScroll()
        assertInside("Movement 1")
        assertInside("more", substring = true)
        com.ironvellum.app.ui.ShotRecorder.take(compose, "today-short-phone")
    }

    @Test
    fun everyExerciseLineSaysWhatTappingItDoes() {
        show(ui = DashboardUi(presets = listOf(rite(2))), selectedDay = 3)
        val label = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            .getString(com.ironvellum.app.R.string.today_exercise_open_label, "Movement 1")
        compose.onAllNodes(
            SemanticsMatcher("click label \"$label\"") {
                it.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.OnClick)?.label == label
            },
        ).assertCountEquals(1)
    }

    @Test
    fun theLastWeekLinkFitsAmongEveryOtherRowAndOpensTheRecap() {
        var opened = -1L
        show(
            ui = DashboardUi(presets = listOf(rite(9)), newPeaks = listOf(peak), lastWeek = RecapSample.full()),
            selectedDay = 3, live = trial, liveSets = trialSets, bodyGap = BodyGap.BOTH,
            onOpenRecap = { opened = it },
        )
        assertDoesNotScroll()
        assertInside("Last week")
        assertInside("4 trials · 3 records · +15%")
        assertInside("Add your height and weight")
        assertInside("3 inscriptions waiting")
        compose.onNodeWithText("Last week").performClick()
        assertEquals(RecapSample.monday.toEpochDay(), opened)
        com.ironvellum.app.ui.ShotRecorder.take(compose, "today_last_week")
    }

    @Test
    fun theReviewCardFitsAmongTheOtherRowsOpensTheReviewAndCloses() {
        // With the Last week link as well (Monday to Wednesday of the first week) the worst case below scrolls: rows are never dropped.
        var opened = ""
        var closed = ""
        show(
            ui = DashboardUi(presets = listOf(rite(9)), newPeaks = listOf(peak), review = ReviewSample.yearSummary()),
            selectedDay = 3, live = trial, liveSets = trialSets, bodyGap = BodyGap.BOTH,
            onOpenReview = { kind, start -> opened = "$kind $start" },
            onDismissReview = { closed = it },
        )
        assertDoesNotScroll()
        assertInside("2026: 94 trials · 423.0 t")
        assertInside("Add your height and weight")
        assertInside("3 inscriptions waiting")
        compose.onNodeWithText("2026: 94 trials · 423.0 t").performClick()
        assertEquals("year ${ReviewSample.year.from.toEpochDay()}", opened)
        compose.onNode(hasContentDescription("Dismiss the review")).performClick()
        assertEquals("2026", closed)
        com.ironvellum.app.ui.ShotRecorder.take(compose, "today_review_card")
    }

    @Test
    fun withoutAReviewThereIsNoCard() {
        show(ui = DashboardUi(presets = listOf(rite(3, day = 1))), selectedDay = 1)
        compose.onAllNodes(hasContentDescription("Dismiss the review")).assertCountEquals(0)
    }

    @Test
    fun withoutALastWeekThereIsNoLink() {
        show(ui = DashboardUi(presets = listOf(rite(3, day = 1))), selectedDay = 1)
        compose.onAllNodesWithText("Last week").assertCountEquals(0)
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

    private fun named(vararg names: String) = WorkoutPreset(
        id = 1, name = "Back + Chest Volume (dip bars)", scheduledDay = 1,
        entries = names.mapIndexed { i, n -> PresetEntry(exerciseId = i + 1L, exerciseName = n, targetSets = 3, targetReps = 8, position = i + 1) },
    )

    /** Where [text] sits on the page: the probes the layout measures with are in the unmerged tree too, but never placed. */
    private fun top(text: String) = compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().first { it.layoutInfo.isPlaced }.boundsInRoot.top

    @Test
    fun shortNeighboursShareALineLongNamesDoNotAndEveryExerciseStaysItsOwnNode() {
        show(
            ui = DashboardUi(presets = listOf(named("Pull-up", "Inverted Row", "Dip", "Push-up", "Deficit Push-up", "Bulgarian Split Squat", "Ab Wheel Rollout"))),
            selectedDay = 1, height = 1000,
        )
        assertDoesNotScroll()
        // Routine order, left to right then down: two pairs, then three lines of their own.
        assertEquals(top("Pull-up"), top("Inverted Row"), 0.5f)
        assertEquals(top("Dip"), top("Push-up"), 0.5f)
        assertTrue(top("Dip") > top("Pull-up"))
        assertTrue(top("Deficit Push-up") > top("Dip"))
        assertTrue(top("Bulgarian Split Squat") > top("Deficit Push-up"))
        assertTrue(top("Ab Wheel Rollout") > top("Bulgarian Split Squat"))
        // Each exercise is its own button, with its own sets in the merged reading.
        val reading = compose.onNode(hasText("Pull-up")).fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text }
        assertEquals(listOf("Pull-up", "3 × 8"), reading)
        compose.onNode(hasText("Inverted Row")).assertHasClickAction()
        // All seven fit: no "+N more".
        compose.onAllNodesWithText("more", substring = true).assertCountEquals(0)
        // The title is one line: it never wraps into a second.
        assertTrue("the title wrapped", compose.onAllNodesWithText("BACK + CHEST VOLUME (DIP BARS)", useUnmergedTree = true).fetchSemanticsNodes().first { it.layoutInfo.isPlaced }.boundsInRoot.height < 40 * androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density)
        saveShot("today-paired-rows")
    }

    @Test
    fun exerciseTargetsOpenTheirOwnDetailsAndLeaveTheCardActionIntact() {
        val pull = Exercise(id = 1, name = "Pull-up", muscleGroup = MuscleGroup.PULL, isWeighted = false)
        val dip = Exercise(id = 2, name = "Dip", muscleGroup = MuscleGroup.PUSH, isWeighted = false)
        val rite = named("Pull-up", "Dip").let {
            it.copy(entries = it.entries.mapIndexed { index, entry -> if (index == 0) entry.copy(modifiers = "weighted") else entry })
        }
        var openedRite = -1L
        show(
            ui = DashboardUi(presets = listOf(rite), exercises = mapOf(pull.id to pull, dip.id to dip)),
            selectedDay = 1,
            onOpenRite = { openedRite = it },
        )
        val first = compose.onNodeWithText("Pull-up").assertHasClickAction()
        val second = compose.onNodeWithText("Dip").assertHasClickAction()
        val firstBounds = first.getUnclippedBoundsInRoot()
        val secondBounds = second.getUnclippedBoundsInRoot()
        assertTrue(firstBounds.right - firstBounds.left >= 48.dp && firstBounds.bottom - firstBounds.top >= 48.dp)
        assertTrue(secondBounds.right - secondBounds.left >= 48.dp && secondBounds.bottom - secondBounds.top >= 48.dp)
        assertTrue(firstBounds.right <= secondBounds.left)
        com.ironvellum.app.ui.ShotRecorder.take(compose, "today_exercise_targets")
        first.performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Close", ignoreCase = true).assertIsDisplayed()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Pull-up")).assertIsDisplayed()
        assertEquals(-1L, openedRite)
        compose.onNodeWithText("Close", ignoreCase = true).performClick()
        compose.mainClock.advanceTimeBy(1_000)
        second.performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Close", ignoreCase = true).assertIsDisplayed()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Dip")).assertIsDisplayed()
        assertEquals(-1L, openedRite)
        compose.onNodeWithText("Close", ignoreCase = true).performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("BACK + CHEST VOLUME (DIP BARS)").performClick()
        assertEquals(rite.id, openedRite)
    }

    @Test
    fun plannedVariationShapesTheExerciseSheet() {
        val exercise = Exercise(id = 1, name = "Push-up", muscleGroup = MuscleGroup.PUSH, isWeighted = false)
        val rite = named("Push-up").let {
            it.copy(entries = listOf(it.entries.single().copy(modifiers = "elevated")))
        }
        val expected = exerciseMuscleSummary(MuscleMap.profile("Push-up", "elevated")!!.muscles)
        val plain = exerciseMuscleSummary(MuscleMap.profile("Push-up", "")!!.muscles)
        assertTrue("fixture must distinguish the variation", expected != plain)
        show(ui = DashboardUi(presets = listOf(rite), exercises = mapOf(exercise.id to exercise)), selectedDay = 1)
        compose.onNodeWithText("Push-up").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNode(hasContentDescription(expected), useUnmergedTree = true).assertExists()
    }

    @Test
    fun liveTrialExerciseOpensItsDetails() {
        val exercise = Exercise(id = 10, name = "Pull-up", muscleGroup = MuscleGroup.PULL, isWeighted = false)
        val set = SessionSet(exerciseId = exercise.id, exerciseName = exercise.name, exercisePosition = 0, setIndex = 0, reps = 8, modifiers = "weighted", done = true)
        show(
            ui = DashboardUi(exercises = mapOf(exercise.id to exercise)),
            selectedDay = 1,
            live = trial,
            liveSets = listOf(set),
        )
        compose.onNodeWithText("Pull-up").assertHasClickAction().performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Pull-up")).assertIsDisplayed()
    }

    @Test
    fun theGlyphIsNoTallerThanTheWordsBesideIt() {
        // The long name runs above, so the glyph sits beside the narrator and meta lines only: it ends where they end, give or take the 4dp above the narrator and the meta line's leading (a 78dp glyph ended 30dp+ below).
        show(
            ui = DashboardUi(presets = listOf(named("Pull-up", "Inverted Row", "Dip", "Push-up", "Deficit Push-up", "Bulgarian Split Squat", "Ab Wheel Rollout")), newPeaks = listOf(peak)),
            selectedDay = 1, fontScale = 0.9f,
        )
        assertDoesNotScroll()
        val glyph = placed("today-glyph").single().boundsInRoot
        val meta = compose.onAllNodesWithText("exercises", substring = true, useUnmergedTree = true).fetchSemanticsNodes().first { it.layoutInfo.isPlaced }.boundsInRoot
        val dp = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        assertTrue("the glyph leaves a band under the meta line: ${glyph.bottom} vs ${meta.bottom}", glyph.bottom <= meta.bottom + 8 * dp)
        assertTrue("the glyph is back to its full 78dp", glyph.height < 78 * dp)
    }

    @Test
    fun atTwoHundredPercentNothingPairs() {
        show(
            ui = DashboardUi(presets = listOf(named("Pull-up", "Inverted Row", "Dip", "Push-up", "Chin-up", "Row", "Plank"))),
            selectedDay = 1, fontScale = 2f, height = PHONE_CONTENT_HEIGHT_DP * 2, // tall: every line shows, so the test reads the packing, not the fit
        )
        assertTrue("two exercises share a line at 200%", top("Inverted Row") > top("Pull-up"))
    }

    /** The essence figure Today's Veil shows, read off its text. */
    private fun shownEssence(): Long = placed("veil-essence").single()
        .config[SemanticsProperties.Text].joinToString("").replace(",", "").toLong()

    /** Animations off (battery saver, Remove animations) freeze the effects, not the count: the clock still runs. */
    @Test
    fun theVeilKeepsCountingWithAnimationsOff() {
        // 100 essence a second, 600 ms short of nine hours since the last collection: a second later the
        // strength caption has dropped an hour (16 h left to 15 h) and the essence has climbed by about a hundred.
        val fast = VeilGlance(
            IdleSnapshot(IdleState(essence = 1_000, figures = 0, relicMultiplier = 1.0, lastCollectedAtMs = now - 9 * hour + 600), IdleRate(360_000.0, 1.0, 1.0)),
            inscriptions = 0,
        )
        show(ui = DashboardUi(presets = listOf(rite(9, day = 3))), selectedDay = 2, motion = false, glance = fast)
        val before = shownEssence()
        assertInside("16 h left at full strength")
        Thread.sleep(1_200)
        compose.mainClock.advanceTimeBy(3_100)
        val after = shownEssence()
        assertTrue("the essence stood still at $before with animators off, now $after", after >= before + 50)
        assertInside("15 h left at full strength")
    }

    @Test
    fun aRespiteDayShowsTheFullVeil() {
        show(ui = DashboardUi(presets = listOf(rite(9, day = 3))), selectedDay = 2, bodyGap = BodyGap.WEIGHT)
        assertDoesNotScroll()
        assertPlaced("veil-hero")
        assertInside("essence")
        assertInside("Add your weight")
        assertInside("3 inscriptions waiting")
        saveShot("today-respite-full")
    }

    /** The lifter's real respite: a relic in the centre and a crest worn under it, both of which make the hero taller. */
    @Test
    fun aRespiteDayWithARelicAndACrestStillShowsTheHero() {
        val glance = VeilGlance(veil.snapshot, 3, active = VaultSlot(RelicHouses.CATALOGUE.first(), owned = null, active = true), worn = Crests.ALL.first(), wornWorth = 4.0)
        show(ui = DashboardUi(presets = listOf(rite(9, day = 3))), selectedDay = 2, bodyGap = BodyGap.BOTH, glance = glance)
        assertDoesNotScroll()
        assertPlaced("veil-hero")
        assertInside("essence")
        // The relic's name is the hero's last line; a hero too short for it once hid it under the bar.
        val hero = placed("veil-hero").single().boundsInRoot
        val name = placed("veil-relic-name").single().boundsInRoot
        assertTrue("the relic's name spills out of the hero: $name vs $hero", name.bottom <= hero.bottom)
        assertInside("3 inscriptions waiting")
        saveShot("today-respite-relic-crest")
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
     * tags and never places them; adjacent days are placed offscreen. Neither is visible.
     */
    private fun placed(tag: String) =
        compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().filter { it.layoutInfo.isPlaced && it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f }

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
        compose.onAllNodesWithText("Inscribe (3)").assertCountEquals(0)
        compose.runOnUiThread { glance = veilWith(3) }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("3 inscriptions waiting").assertIsDisplayed()
        compose.onNodeWithText("Inscribe (3)").assertIsDisplayed()
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
        assertInside("Sealed · 8 exercises", substring = true)
        assertInside("Sealed move 0")
        assertInside("Bench Press")
        assertInside("The Veil")
        saveShot("today-sealed")
    }

    @Test
    fun pairedProgressKeepsTheBarsAlignedAndOpensTheOathExplanation() {
        show(ui = DashboardUi(profile = PlayerProfile(name = "Alex", currentTitleId = "marching_orders"), streak = 10, presets = listOf(rite(3, day = 1))), selectedDay = 1)
        val panel = compose.onNodeWithTag("today-oath")
        panel.assertIsDisplayed()
        val header = compose.onNodeWithTag("today").getUnclippedBoundsInRoot()
        val bounds = panel.getUnclippedBoundsInRoot()
        val level = compose.onNodeWithTag("today-level").getUnclippedBoundsInRoot()
        assertEquals(header.left + 16.dp, level.left)
        assertEquals(header.right - 16.dp, bounds.right)
        assertEquals((level.right - level.left).value, (bounds.right - bounds.left).value, 0.5f)
        assertEquals(level.top, bounds.top)
        assertTrue("progress controls must not overlap", level.right < bounds.left)
        val xpBar = compose.onNodeWithTag("today-xp-bar", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val oathBar = compose.onNodeWithTag("today-oath-bar", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(xpBar.top, oathBar.top)
        assertEquals((xpBar.right - xpBar.left).value, (oathBar.right - oathBar.left).value, 0.5f)
        assertInside("10 / 14 days")
        assertStateInside("today-oath", "Fortnight Vigil")
        assertStateInside("today-level", "Level progress")
        saveShot("today-oath")
        compose.mainClock.autoAdvance = false
        panel.performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.onNodeWithText("The days you have kept training without a break.", substring = true).assertExists()
        // The sheet's anchors are measured by the Android window before its entrance can animate.
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("The days you have kept training without a break.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Close").assertIsDisplayed()
    }

    @Test
    fun anOathThatHasNotBegunShowsHowToStart() {
        show(ui = DashboardUi(presets = listOf(rite(3, day = 1))), selectedDay = 1)
        assertInside("0 / 3 days")
        assertStateInside("today-oath", "seal a trial to begin")
        compose.onNodeWithTag("today-oath").assertIsDisplayed()
        saveShot("today-oath-empty")
    }

    @Test
    fun theLevelProgressOpensAnExplanationWithoutLeavingToday() {
        show(ui = DashboardUi(presets = listOf(rite(3, day = 1))), selectedDay = 1)
        compose.onNodeWithTag("today-level").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Each sealed trial earns XP.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Close").assertIsDisplayed()
    }

    @Test
    fun reachingAnOathMilestoneTargetsTheNextExistingDeed() {
        show(ui = DashboardUi(streak = 14, presets = listOf(rite(3, day = 1))), selectedDay = 1)
        assertInside("14 / 30 days")
        assertStateInside("today-oath", "Unrelenting Watch")
    }

    @Test
    fun theFinalOathMilestoneKeepsShowingTheStreak() {
        show(ui = DashboardUi(streak = 105, presets = listOf(rite(3, day = 1))), selectedDay = 1)
        assertInside("105 days kept")
        assertStateInside("today-oath", "milestones complete")
    }

    @Test
    fun aLongNameGivesWayToTheRank() {
        val name = "Bartholomew Featherstonehaugh of the Northern Reach"
        show(ui = DashboardUi(profile = PlayerProfile(name = name), presets = listOf(rite(3, day = 1))), selectedDay = 1)
        // The rank link stays whole and on screen; the name is the one that ellipsizes.
        assertInside("Iron", substring = true)
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
        compose.onNodeWithText("Inscribe (3)").assertExists()
        compose.onNodeWithText("Add your height and weight").assertExists()
    }

    // ------------------------------------------------------------------------------------- fatigue advice

    /** A Monday rite of real lifts, so the muscles they lean on are known. */
    private fun fatigueRite(vararg lifts: String) = WorkoutPreset(
        id = 1, name = "Push Strength", scheduledDay = 1,
        entries = lifts.mapIndexed { i, l -> PresetEntry(id = i + 1L, exerciseId = i + 1L, exerciseName = l, targetSets = 3, targetReps = 8, position = i) },
    )

    private fun tiredReport(muscles: Collection<com.ironvellum.app.domain.Muscle>, dose: Double = 4.0) = com.ironvellum.app.domain.FatigueReport(
        nowMs = now, newLifter = false,
        contributions = muscles.associateWith {
            listOf(com.ironvellum.app.domain.FatigueContribution(it, 1, now, "Weighted Dip", "", "weighted dip", dose, 0.0, false, false, 24.0))
        },
    )

    @Test
    fun theAdjustLineTakesRoomFromTheExerciseRowsAndNothingScrolls() {
        val lifts = arrayOf("Bench Press", "Overhead Press", "Back Squat", "Barbell Row", "Pull-up", "Bicep Curl", "Lateral Raise", "Romanian Deadlift", "Plank")
        show(
            ui = DashboardUi(presets = listOf(fatigueRite(*lifts)), newPeaks = listOf(peak)),
            selectedDay = 1, report = tiredReport(listOf(com.ironvellum.app.domain.Muscle.MID_CHEST)),
        )
        assertDoesNotScroll()
        assertInside("Mid chest is still High")
        assertInside("Adjust")
        assertInside("Begin Push Strength")
        assertInside("The Veil")
        assertInside("more", substring = true)
        // The exercise that leans on the muscle carries its name.
        compose.onAllNodesWithText("Mid chest").assertCountEquals(1)
        saveShot("today-adjust-fits")
    }

    @Test
    fun aWornOutDayShowsTheRestPanelInsteadOfTheBeginButton() {
        val rite = fatigueRite("Bench Press", "Overhead Press", "Lateral Raise")
        val worn = (rite.entries.flatMap { com.ironvellum.app.domain.FatigueAdvice.mainMuscles(it.exerciseName) }).toSet()
        show(ui = DashboardUi(presets = listOf(rite)), selectedDay = 1, report = tiredReport(worn, dose = 5.0))
        assertDoesNotScroll()
        assertInside("Most of today's muscles are still High")
        assertInside("Rest today")
        assertInside("Begin Push Strength anyway")
        compose.onAllNodesWithText("Begin Push Strength").assertCountEquals(0)
        compose.onAllNodesWithText("Mid chest is still High").assertCountEquals(0)
        saveShot("today-rest-fits")
    }

    @Test
    fun withFatigueAdviceOffTodayLooksAsItDid() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val before = com.ironvellum.app.data.FatigueAdviceSetting.enabled
        com.ironvellum.app.data.FatigueAdviceSetting.setEnabled(context, false)
        try {
            show(
                ui = DashboardUi(presets = listOf(fatigueRite("Bench Press", "Overhead Press"))),
                selectedDay = 1, report = tiredReport(listOf(com.ironvellum.app.domain.Muscle.MID_CHEST), dose = 9.0),
            )
            compose.onAllNodesWithText("is still", substring = true).assertCountEquals(0)
            compose.onAllNodesWithText("Adjust").assertCountEquals(0)
            assertInside("Begin Push Strength")
        } finally {
            com.ironvellum.app.data.FatigueAdviceSetting.setEnabled(context, before)
        }
    }

    /** Exercise the real companion content without opening the app's database. */
    private fun showDashboardPanes(
        width: Int,
        onRite: (Long) -> Unit = {},
        onPresets: () -> Unit = {},
        onMuscles: (com.ironvellum.app.ui.program.MusclesView) -> Unit = {},
        onBegin: (Long) -> Unit = {},
    ) {
        val todayRite = rite(1, day = 1)
        val otherRite = WorkoutPreset(id = 42, name = "Companion plan", scheduledDay = 3)
        val paneLight = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("paneLight") == "true"
        compose.mainClock.autoAdvance = false
        compose.setContent {
            IronvellumTheme(light = paneLight) {
                // Fit the whole simulated window into the actual device viewport, including on
                // resized portrait displays; otherwise its plan can lie outside the test activity.
                BoxWithConstraints(Modifier.fillMaxSize().background(IronvellumColors.Abyss)) {
                val testDensity = minOf(constraints.maxWidth / width.toFloat(), constraints.maxHeight / 760f)
                CompositionLocalProvider(LocalDensity provides Density(testDensity)) {
                    Box(Modifier.size(width.dp, 760.dp)) {
                        DashboardPanes(
                            windowWidth = width.dp,
                            today = {
                                Box(Modifier.testTag("dashboard-today")) {
                                TodayContent(
                                    ui = DashboardUi(presets = listOf(todayRite)),
                                    selectedDay = 1, today = today, live = null, liveSets = emptyList(),
                                    bodyGap = null, strengthRank = "Iron", rankBreakdown = null,
                                    veil = null, actions = TodayActions(onBeginPreset = onBegin),
                                    nowMs = now, motion = false,
                                )
                                }
                            },
                            plan = { modifier ->
                                DashboardPlanContent(
                                    plan = com.ironvellum.app.ui.train.TrainUi(presets = listOf(otherRite)),
                                    live = false,
                                    fatigue = com.ironvellum.app.ui.program.FatigueUi(
                                        report = tiredReport(emptyList()), loaded = true,
                                    ),
                                    coverageLine = "This week: fixture coverage",
                                    today = today, onOpenRite = onRite, onOpenPresets = onPresets,
                                    onOpenMuscles = onMuscles, modifier = modifier.testTag("dashboard-plan"),
                                )
                            },
                        )
                    }
                }
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
    }

    private val planHeader: String
        get() = com.ironvellum.app.data.Words.text(
            com.ironvellum.app.domain.UiText.themed(
                com.ironvellum.app.R.string.presets_cycle_header,
                com.ironvellum.app.R.string.presets_cycle_header__t,
            ),
        )

    private val musclesHeader: String
        get() = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            .getString(com.ironvellum.app.R.string.muscles_now_title).uppercase()

    @Test
    fun expandedTodayShowsPlanAndMusclesAndRoutesTheirActions() {
        var begun: Long? = null
        var opened: Long? = null
        var presetsOpened = 0
        val musclesOpened = mutableListOf<com.ironvellum.app.ui.program.MusclesView>()
        showDashboardPanes(840, onRite = { opened = it }, onPresets = { presetsOpened++ },
            onMuscles = { musclesOpened.add(it) }, onBegin = { begun = it })
        val todayBounds = compose.onNodeWithTag("dashboard-today").getUnclippedBoundsInRoot()
        val planBounds = compose.onNodeWithTag("dashboard-plan").getUnclippedBoundsInRoot()
        assertTrue("Today and the plan must be side by side", todayBounds.right <= planBounds.left)
        if (androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("shots") == "true") {
            val autoAdvance = compose.mainClock.autoAdvance
            com.ironvellum.app.ui.ShotRecorder.take(compose, "expanded-today-plan-muscles")
            compose.mainClock.autoAdvance = autoAdvance
        }

        compose.onNodeWithText("Begin Full Body A").assertIsDisplayed().performClick()
        assertEquals(1L, begun)
        compose.onNodeWithText("Companion plan").assertIsDisplayed().performClick()
        assertEquals(42L, opened)
        compose.onNodeWithText(planHeader).assertIsDisplayed().performClick()
        assertEquals(1, presetsOpened)
        compose.onNodeWithText(musclesHeader).assertIsDisplayed().performClick()
        compose.onNodeWithText("This week: fixture coverage").assertIsDisplayed().performClick()
        assertEquals(
            listOf(com.ironvellum.app.ui.program.MusclesView.FATIGUE, com.ironvellum.app.ui.program.MusclesView.COVERAGE),
            musclesOpened,
        )
    }

    @Test
    fun belowExpandedBreakpointTodayRemainsAndCompanionIsAbsent() {
        var begun: Long? = null
        showDashboardPanes(839, onBegin = { begun = it })
        compose.onNodeWithText("Begin Full Body A").assertIsDisplayed().performClick()
        assertEquals(1L, begun)
        compose.onAllNodesWithText("Companion plan").assertCountEquals(0)
        compose.onAllNodesWithText(musclesHeader).assertCountEquals(0)
        compose.onAllNodesWithText(planHeader).assertCountEquals(0)
    }

    @Test
    fun phoneWidthKeepsTodayWithoutCompanion() {
        showDashboardPanes(PHONE_WIDTH_DP)
        compose.onNodeWithText("Begin Full Body A").assertIsDisplayed()
        compose.onAllNodesWithTag("dashboard-plan").assertCountEquals(0)
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
