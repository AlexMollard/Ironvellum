package com.ironvellum.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.data.PhotoPrefs
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.PhotoKind
import com.ironvellum.app.domain.PhotoRules
import com.ironvellum.app.domain.Pose
import com.ironvellum.app.ui.components.PhotoSource
import com.ironvellum.app.ui.components.PhotoSourceTiles
import com.ironvellum.app.ui.dashboard.PoseTodayCard
import com.ironvellum.app.ui.stats.PhotoCompare
import com.ironvellum.app.ui.stats.ProgressPhotosScreen
import com.ironvellum.app.ui.theme.IronvellumTheme
import com.ironvellum.app.ui.train.PhotoOfferPage
import com.ironvellum.app.ui.train.TrainScreen
import com.ironvellum.app.ui.train.WorkoutDetailScreen
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The progress photo screens: the finish step, the pose screen with its comparison, Today's prompt and the workout
 * detail's photo card. The checks run in the gate; the captures run only with
 * `-Pandroid.testInstrumentationRunnerArguments.shots=true` (see [ShotRecorder]). Every picture is a generated colour
 * field with a label, never a real photo, and every row the test adds is removed again.
 */
@RunWith(AndroidJUnit4::class)
class ProgressPhotosScreensTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IronvellumApp
    private val shots get() = InstrumentationRegistry.getArguments().getString("shots") == "true"
    private val thisWeek = PhotoRules.weekStart(LocalDate.now())
    private var sessionId = 0L

    @Before
    fun setUp() {
        TestProfile.ensureSetUp()
        runBlocking { clear() }
    }

    @After
    fun tearDown() {
        runBlocking {
            clear()
            if (sessionId != 0L) app.repository.deleteWorkout(sessionId)
        }
        compose.runOnUiThread { Words.setThemed(app, true) }
        PhotoPrefs.setPoseDay(app, DayOfWeek.SUNDAY)
    }

    private suspend fun clear() {
        val library = app.repository.photoLibrary
        library.photos.first().forEach { library.delete(it.id) }
    }

    /** A portrait colour field with a plain label and a rough, faceless figure so a comparison has something to compare. */
    private fun picture(label: String, hue: Float, figure: Float): Uri {
        val bitmap = Bitmap.createBitmap(900, 1200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.HSVToColor(floatArrayOf(hue, 0.35f, 0.55f)))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.HSVToColor(floatArrayOf(hue, 0.2f, 0.9f)) }
        canvas.drawCircle(450f, 330f, 110f, paint)
        canvas.drawRoundRect(450f - 150f * figure, 470f, 450f + 150f * figure, 1000f, 60f, 60f, paint)
        paint.color = Color.WHITE
        paint.textSize = 72f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(label, 450f, 1120f, paint)
        val file = File(app.cacheDir, "shot-${label.replace(' ', '-')}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return Uri.fromFile(file)
    }

    private fun seedPoses(withLastWeek: Boolean = true) = runBlocking {
        val library = app.repository.photoLibrary
        Pose.entries.forEachIndexed { i, pose ->
            library.savePose(app, picture("${pose.key} now", 200f + i * 40f, 1.15f), thisWeek.toEpochDay(), pose)
            if (withLastWeek) library.savePose(app, picture("${pose.key} before", 20f + i * 40f, 1.0f), thisWeek.minusWeeks(2).toEpochDay(), pose)
        }
    }

    private fun seedWorkoutWithPhoto() = runBlocking {
        val repo = app.repository
        val preset = repo.observePresets().first().first()
        sessionId = repo.startSessionFromPreset(preset.id)
        val set = repo.observeSessionSets(sessionId).first().first()
        repo.updateSet(set.id, reps = 8, weightKg = 20.0, done = true)
        repo.completeSession(sessionId)
        val session = repo.observeWorkout(sessionId).first()!!.first
        val day = java.time.Instant.ofEpochMilli(session.completedAtMs!!).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
        repo.photoLibrary.saveWorkout(app, picture("thumbs up", 120f, 1.1f), sessionId, day)
    }

    private fun show(light: Boolean, themed: Boolean = true, density: Float? = null, content: @Composable () -> Unit) {
        compose.runOnUiThread { Words.setThemed(app, themed) }
        compose.activityRule.scenario.onActivity { activity ->
            val bars = if (light) SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) else SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            activity.enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            activity.window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(if (light) 0xFFF6F3EC.toInt() else 0xFF171715.toInt()))
            activity.setContent {
                val pixels = LocalWindowInfo.current.containerSize.width
                val fontScale = LocalDensity.current.fontScale
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides if (density == null) base else Density(pixels / density, fontScale)) {
                    IronvellumTheme(light = light) {
                        Box(Modifier.fillMaxSize().padding(top = 36.dp)) { content() }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** A pose slot: the clickable that holds the pose's name. */
    private fun slot(pose: String) = compose.onNode(hasText(pose) and hasClickAction())

    private fun waitForSlots() = compose.waitUntil(10_000) { runCatching { slot("Front").assertExists() }.isSuccess }

    private fun shoot(name: String) {
        ShotRecorder.take(compose, "photos-$name")
        compose.mainClock.autoAdvance = true
    }

    // ---- checks (run in the gate) ----

    @Test
    fun skippingTheOfferIsOneTapAndItNeverAsksAgainByItself() {
        var skipped = 0
        var picked = 0
        show(light = false) { PhotoOfferPage(saving = false, failed = false, onPicked = { picked++ }, onSkip = { skipped++ }) }
        compose.onNodeWithText("Add a photo?").assertIsDisplayed()
        compose.onNodeWithText("Skip").assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle {
            assertEquals(1, skipped)
            assertEquals(0, picked)
        }
    }

    @Test
    fun theOfferHoldsItsButtonsWhileASaveIsRunning() {
        var skipped = 0
        show(light = false) { PhotoOfferPage(saving = true, failed = true, onPicked = {}, onSkip = { skipped++ }) }
        compose.onNodeWithText("That photo could not be saved.").assertIsDisplayed()
        compose.onNodeWithText("Saving").assertIsDisplayed()
        compose.onNodeWithText("Skip").performClick()
        compose.runOnIdle { assertEquals("Skip is held while the photo is written", 0, skipped) }
    }

    @Test
    fun thePoseScreenShowsThisWeeksSlotsAndComparesTwoWeeksPoseByPose() {
        seedPoses()
        show(light = false) { ProgressPhotosScreen(onBack = {}) }
        waitForSlots()
        slot("Side").assertExists()
        slot("Back").assertExists()
        compose.onNodeWithText("Compare weeks").assertExists()
        compose.onNodeWithText("Left week").assertExists()
        compose.onNodeWithText("Right week").assertExists()
        // The left side opens on the older week, the right on this one: a front photo for each is described by its week.
        val rows = runBlocking { app.repository.photoLibrary.photos.first() }
        assertEquals(6, rows.count { it.kind == PhotoKind.POSE })
    }

    @Test
    fun withNoPosesTheSlotsInviteOneAndTheComparisonSaysWhatItNeeds() {
        show(light = false) { ProgressPhotosScreen(onBack = {}) }
        slot("Front").assertExists().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("Take your poses in two different weeks to compare them side by side.").assertExists()
    }

    @Test
    fun aSlotOpensTheTakeChooseAndRemoveSheet() {
        seedPoses(withLastWeek = false)
        show(light = false) { ProgressPhotosScreen(onBack = {}) }
        waitForSlots()
        slot("Front").performClick()
        compose.onNodeWithText("Take photo").assertExists()
        compose.onNodeWithText("From gallery").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("Stand where your last front photo was taken. Same light, same distance.").assertIsDisplayed()
        compose.onNodeWithText("Remove").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun withNoCameraTheSheetOffersOnlyTheGalleryAtFullWidth() {
        var picked = 0
        fun tileWidth(canTake: Boolean): Float {
            show(light = false) {
                PhotoSourceTiles(PhotoSource(canTake, take = {}, pick = { picked++ }), enabled = true, helper = "Helper")
            }
            compose.onNodeWithText("From gallery").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            return compose.onNodeWithText("From gallery").getUnclippedBoundsInRoot().width.value
        }
        val withCamera = tileWidth(true)
        compose.onNodeWithText("Take photo").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        val galleryOnly = tileWidth(false)
        compose.onNodeWithText("Take photo").assertDoesNotExist()
        compose.onNodeWithText("Remove").assertDoesNotExist()
        assertTrue("the lone tile fills the row: $galleryOnly vs $withCamera", galleryOnly > withCamera * 1.8f)
        compose.onNodeWithText("From gallery").performClick()
        compose.runOnIdle { assertEquals(1, picked) }
    }

    @Test
    fun anEmptySlotKeepsItsAddCueAndOnlyAFilledOneDescribesAPhoto() {
        // No silhouette art exists for a pose, so the empty field is the camera-with-plus mark: it still names what a tap does.
        show(light = false) { ProgressPhotosScreen(onBack = {}) }
        slot("Front").assertExists().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("Front photo, this week").assertDoesNotExist()
        seedPoses(withLastWeek = false)
        show(light = false) { ProgressPhotosScreen(onBack = {}) }
        waitForSlots()
        compose.onNodeWithContentDescription("Front photo, this week").assertExists()
    }

    private fun sliders() = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))

    @Test
    fun theCompareHandleIsASliderThatStartsAtHalfAndMovesByDragAndByAction() {
        seedPoses()
        show(light = false) { ProgressPhotosScreen(onBack = {}) }
        waitForSlots()
        sliders().assertCountEquals(3)
        val handle = sliders()[0]
        handle.performScrollTo().assertRangeInfoEquals(ProgressBarRangeInfo(0.5f, 0f..1f))
        handle.assertHeightIsAtLeast(48.dp)
        assertTrue("the handle is at least 48dp wide", handle.getUnclippedBoundsInRoot().width.value >= 48f)
        handle.performSemanticsAction(SemanticsActions.SetProgress) { it(0.8f) }
        handle.assertRangeInfoEquals(ProgressBarRangeInfo(0.8f, 0f..1f))
        handle.performSemanticsAction(SemanticsActions.SetProgress) { it(7f) }
        handle.assertRangeInfoEquals(ProgressBarRangeInfo(1f, 0f..1f))
        // The two custom actions step a tenth each way and stop at the edge.
        val actions = handle.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(2, actions.size)
        compose.runOnIdle { actions[0].action() }
        handle.assertRangeInfoEquals(ProgressBarRangeInfo(0.9f, 0f..1f))
        // A real drag moves it too.
        handle.performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        handle.performTouchInput { down(center); moveBy(Offset(-width * 4f, 0f)); up() }
        val value = handle.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        assertTrue("dragging left moved the divider left: $value", value < 0.5f)
    }

    @Test
    fun theWeekLabelsStayAboveTheDividerLineAtEitherEdge() {
        val left = File(requireNotNull(picture("left photo", 20f, 1f).path))
        val right = File(requireNotNull(picture("right photo", 200f, 1.1f).path))
        show(light = false) { PhotoCompare("Front", "Week of 12 Oct", "Week of 26 Oct", left, right, 400.dp, Modifier.fillMaxWidth()) }
        val handle = sliders()[0]
        for ((progress, week) in listOf(0f to "Week of 12 Oct", 1f to "Week of 26 Oct")) {
            handle.performSemanticsAction(SemanticsActions.SetProgress) { it(progress) }
            compose.waitForIdle()
            val label = compose.onNodeWithText(week).assertIsDisplayed()
            val bounds = label.getUnclippedBoundsInRoot()
            val lineX = handle.getUnclippedBoundsInRoot().let { (it.left + it.right) / 2 }
            assertTrue("the divider line runs behind the label at $progress", lineX > bounds.left && lineX < bounds.right)
            val image = label.captureToImage().asAndroidBitmap()
            val scale = image.width / bounds.width.value
            // The label's top padding is clear of its text, so only the line could put white there.
            val pixel = image.getPixel(((lineX - bounds.left).value * scale).toInt(), (2f * scale).toInt())
            assertTrue("the line is drawn over the label at $progress: ${Integer.toHexString(pixel)}", Color.red(pixel) < 200)
        }
    }

    @Test
    fun aWeekWithoutAPhotoForAPoseShowsTheOtherWeeksPhotoAloneWithANote() {
        runBlocking {
            val library = app.repository.photoLibrary
            Pose.entries.forEachIndexed { i, pose -> library.savePose(app, picture("${pose.key} before", 20f + i * 40f, 1.0f), thisWeek.minusWeeks(2).toEpochDay(), pose) }
            library.savePose(app, picture("front now", 200f, 1.15f), thisWeek.toEpochDay(), Pose.FRONT)
        }
        show(light = false) { ProgressPhotosScreen(onBack = {}) }
        waitForSlots()
        sliders().assertCountEquals(1)
        compose.onNode(hasText("has a side photo", substring = true)).assertExists()
        compose.onNode(hasText("has a back photo", substring = true)).assertExists()
    }

    @Test
    fun todaysPromptOpensTheScreenAndItsCloseIsA48dpTarget() {
        var opened = 0
        var closed = 0
        show(light = false) { PoseTodayCard(onOpen = { opened++ }, onDismiss = { closed++ }) }
        compose.onNodeWithContentDescription("Dismiss for today").assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("Time for this week's poses").performClick()
        compose.runOnIdle {
            assertEquals(1, closed)
            assertEquals(1, opened)
        }
    }

    @Test
    fun aWorkoutsDetailShowsTheDaysPhotoAndOffersToChangeIt() {
        seedWorkoutWithPhoto()
        show(light = false) { WorkoutDetailScreen(sessionId = sessionId, onBack = {}) }
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithText("Remove").assertExists() }.isSuccess }
        compose.onNodeWithText("Take a new photo").assertExists()
        compose.onNodeWithText("Choose another").assertExists()
        assertTrue(runBlocking { app.repository.photoLibrary.photos.first() }.any { it.kind == PhotoKind.WORKOUT && it.sessionId == sessionId })
    }

    // ---- captures (opt in with shots=true) ----

    @Test
    fun captureTheFinishStep() {
        if (!shots) return
        for (light in listOf(false, true)) {
            val tag = if (light) "light" else "dark"
            show(light = light) { PhotoOfferPage(saving = false, failed = false, onPicked = {}, onSkip = {}) }
            shoot("offer-$tag")
        }
    }

    @Test
    fun captureThePoseScreen() {
        if (!shots) return
        for (light in listOf(false, true)) {
            val tag = if (light) "light" else "dark"
            show(light = light) { ProgressPhotosScreen(onBack = {}) }
            shoot("poses-empty-$tag")
        }
        seedPoses()
        for (light in listOf(false, true)) {
            val tag = if (light) "light" else "dark"
            show(light = light) { ProgressPhotosScreen(onBack = {}) }
            waitForSlots()
            compose.mainClock.autoAdvance = true
            Thread.sleep(800)
            shoot("poses-filled-$tag")
            slot("Front").performClick()
            compose.mainClock.autoAdvance = true
            compose.waitUntil(10_000) { runCatching { compose.onNodeWithText("From gallery").assertExists() }.isSuccess }
            Thread.sleep(1500)
            shoot("poses-sheet-$tag")
        }
        // Swipe compare: the divider at half, then dragged left.
        for (light in listOf(false, true)) {
            val tag = if (light) "light" else "dark"
            show(light = light) { ProgressPhotosScreen(onBack = {}) }
            waitForSlots()
            compose.mainClock.autoAdvance = true
            val handle = sliders()[0]
            handle.performScrollTo()
            Thread.sleep(800)
            shoot("compare-50-$tag")
            handle.performTouchInput { down(center); moveBy(Offset(-width * 4f, 0f)); up() }
            Thread.sleep(500)
            shoot("compare-dragged-$tag")
            // Near either edge the line and handle sit under the week labels in the top corners.
            handle.performSemanticsAction(SemanticsActions.SetProgress) { it(0.03f) }
            Thread.sleep(500)
            shoot("compare-left-edge-$tag")
            handle.performSemanticsAction(SemanticsActions.SetProgress) { it(0.97f) }
            Thread.sleep(500)
            shoot("compare-right-edge-$tag")
        }
        // 720dp wide: the comparison grows and keeps its two sides.
        show(light = false, density = 720f) { ProgressPhotosScreen(onBack = {}) }
        waitForSlots()
        Thread.sleep(800)
        shoot("poses-wide-dark")
    }

    @Test
    fun captureTheTrainChips() {
        if (!shots) return
        for ((light, themed) in listOf(false to true, true to false)) {
            val tag = if (light) "light" else "dark"
            show(light = light, themed = themed) {
                TrainScreen(
                    onOpenRite = {}, onNew = {}, onGenerate = { _, _ -> }, onStartSession = {},
                    onQuickSession = {}, onOpenExercises = {}, onOpenLog = {}, onOpenMuscles = {},
                )
            }
            Thread.sleep(1500)
            shoot("train-chips-$tag")
        }
    }

    @Test
    fun captureTodaysPromptAndTheWorkoutPhoto() {
        if (!shots) return
        for (light in listOf(false, true)) {
            val tag = if (light) "light" else "dark"
            show(light = light) { PoseTodayCard(onOpen = {}, onDismiss = {}) }
            shoot("today-prompt-$tag")
        }
        seedWorkoutWithPhoto()
        for ((light, themed) in listOf(false to true, true to false)) {
            val tag = if (light) "light" else "dark"
            show(light = light, themed = themed) { WorkoutDetailScreen(sessionId = sessionId, onBack = {}) }
            compose.waitUntil(10_000) { runCatching { compose.onNodeWithText("Remove").assertExists() }.isSuccess }
            Thread.sleep(800)
            shoot("detail-photo-$tag")
        }
    }
}
