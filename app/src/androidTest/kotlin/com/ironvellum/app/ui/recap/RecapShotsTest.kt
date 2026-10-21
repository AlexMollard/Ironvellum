package com.ironvellum.app.ui.recap

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.WeeklyRecap
import com.ironvellum.app.ui.ShotRecorder
import com.ironvellum.app.ui.theme.IronvellumTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Photographs of the recap screen, the share screen and the share card in each look, for a human to lay beside the mockups.
 * Nothing is asserted: run with `-Pandroid.testInstrumentationRunnerArguments.shots=true` (see [ShotRecorder]).
 */
@RunWith(AndroidJUnit4::class)
class RecapShotsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as IronvellumApp

    @After
    fun themedAgain() {
        Words.setThemed(app, true)
    }

    private fun screen(recap: WeeklyRecap, themed: Boolean, light: Boolean, name: String, scrollTo: List<String> = emptyList()) {
        compose.runOnUiThread { Words.setThemed(app, themed) }
        compose.activityRule.scenario.onActivity { activity ->
            ShotRecorder.darkWindow(activity)
            activity.setContent {
                IronvellumTheme(light = light) {
                    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                        RecapContent(recap, null, today = RecapSample.monday.plusDays(6), onBack = {}, onShare = {}, onBegin = {})
                    }
                }
            }
        }
        compose.waitForIdle()
        ShotRecorder.take(compose, "recap_${name}_a")
        scrollTo.forEachIndexed { i, text ->
            compose.mainClock.autoAdvance = true
            compose.onNodeWithText(text, ignoreCase = true).performScrollTo()
            ShotRecorder.take(compose, "recap_${name}_${'b' + i}")
        }
    }

    @Test
    fun fullWeekDark() = screen(RecapSample.full(), themed = false, light = false, name = "full_dark", scrollTo = listOf("Muscles hit", "Next week"))

    @Test
    fun fullWeekLight() = screen(RecapSample.full(), themed = false, light = true, name = "full_light", scrollTo = listOf("Muscles hit", "Next week"))

    @Test
    fun fullWeekThemed() = screen(RecapSample.full(), themed = true, light = false, name = "full_themed", scrollTo = listOf("Next week"))

    @Test
    fun lightWeekDark() = screen(RecapSample.light(), themed = false, light = false, name = "light_dark")

    @Test
    fun emptyWeekDark() = screen(RecapSample.empty(), themed = false, light = false, name = "empty_dark")

    private fun share(themed: Boolean, light: Boolean, name: String) {
        compose.runOnUiThread { Words.setThemed(app, themed) }
        compose.activityRule.scenario.onActivity { activity ->
            ShotRecorder.darkWindow(activity)
            activity.setContent {
                IronvellumTheme(light = light) {
                    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                        RecapShareContent(RecapSample.full(), onBack = {})
                    }
                }
            }
        }
        compose.waitForIdle()
        settle()
        compose.waitForIdle()
        ShotRecorder.take(compose, "recap_share_$name")
    }

    @Test
    fun shareDark() = share(themed = false, light = false, name = "dark")

    @Test
    fun shareLightThemed() = share(themed = true, light = true, name = "light_themed")

    private fun card(light: Boolean, name: String) {
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                IronvellumTheme(light = light) {
                    val palette = RecapCardPalette.current()
                    LaunchedEffect(Unit) { dump(RecapCard.render(context, RecapSample.full(), palette = palette), name) }
                }
            }
        }
        compose.waitForIdle()
        settle()
    }

    /** The card itself, full size, written beside the captures. */
    @Test
    fun cardDark() = card(light = false, name = "card_full_dark")

    @Test
    fun cardLight() = card(light = true, name = "card_full_light")

    /** The card is drawn off the main thread; only a capture run waits for it to land. */
    private fun settle() {
        if (InstrumentationRegistry.getArguments().getString("shots") == "true") Thread.sleep(1500)
    }

    private fun dump(bitmap: android.graphics.Bitmap, name: String) {
        if (InstrumentationRegistry.getArguments().getString("shots") != "true") return
        val file = java.io.File(checkNotNull(context.getExternalFilesDir(null)), "$name.png")
        file.outputStream().use { it.write(RecapCard.png(bitmap)) }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        for (command in listOf("mkdir -p /data/local/tmp/optimise-shots", "cp ${file.absolutePath} /data/local/tmp/optimise-shots/$name.png")) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
        }
        file.delete()
    }
}
