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
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.data.PhotoPrefs
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.PhotoKind
import com.ironvellum.app.domain.PhotoRules
import com.ironvellum.app.domain.Pose
import com.ironvellum.app.ui.dashboard.PoseTodayCard
import com.ironvellum.app.ui.stats.ProgressPhotosScreen
import com.ironvellum.app.ui.theme.IronvellumTheme
import com.ironvellum.app.ui.train.PhotoOfferPage
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
        compose.onNodeWithText("Choose photo").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("Remove").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
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
            compose.waitUntil(10_000) { runCatching { compose.onNodeWithText("Choose photo").assertExists() }.isSuccess }
            Thread.sleep(1500)
            shoot("poses-sheet-$tag")
        }
        // 720dp wide: the comparison grows and keeps its two sides.
        show(light = false, density = 720f) { ProgressPhotosScreen(onBack = {}) }
        waitForSlots()
        Thread.sleep(800)
        shoot("poses-wide-dark")
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
