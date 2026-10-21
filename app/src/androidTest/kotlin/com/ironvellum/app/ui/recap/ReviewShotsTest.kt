package com.ironvellum.app.ui.recap

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.PeriodReview
import com.ironvellum.app.ui.ShotRecorder
import com.ironvellum.app.ui.stats.RecapsPanel
import com.ironvellum.app.ui.theme.IronvellumTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Photographs of the review screen, its share screen and its share card in each look, for a human to lay beside the mockups.
 * Nothing is asserted: run with `-Pandroid.testInstrumentationRunnerArguments.shots=true` (see [ShotRecorder]); the gate
 * composes each screen once and saves nothing.
 */
@RunWith(AndroidJUnit4::class)
class ReviewShotsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as IronvellumApp

    @After
    fun themedAgain() {
        Words.setThemed(app, true)
    }

    private fun screen(review: PeriodReview, themed: Boolean, light: Boolean, name: String, scrollTo: List<String> = emptyList()) {
        compose.runOnUiThread { Words.setThemed(app, themed) }
        compose.activityRule.scenario.onActivity { activity ->
            ShotRecorder.darkWindow(activity)
            activity.setContent {
                IronvellumTheme(light = light) {
                    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                        ReviewContent(review, onBack = {}, onShare = {}, onOpenWeek = {})
                    }
                }
            }
        }
        compose.waitForIdle()
        ShotRecorder.take(compose, "review_${name}_a")
        scrollTo.forEachIndexed { i, text ->
            compose.mainClock.autoAdvance = true
            compose.onNodeWithText(text, ignoreCase = true).performScrollTo()
            ShotRecorder.take(compose, "review_${name}_${'b' + i}")
        }
    }

    @Test
    fun monthDark() = screen(
        ReviewSample.month(), themed = false, light = false, name = "month_dark",
        scrollTo = listOf("Most trained muscles", "Workouts and streak", "The weeks behind it"),
    )

    @Test
    fun monthLight() = screen(ReviewSample.month(), themed = false, light = true, name = "month_light", scrollTo = listOf("Most trained muscles"))

    @Test
    fun monthThemed() = screen(ReviewSample.month(), themed = true, light = false, name = "month_themed", scrollTo = listOf("Trials and oath"))

    @Test
    fun yearDark() = screen(ReviewSample.fullYear(), themed = false, light = false, name = "year_dark", scrollTo = listOf("Workouts and streak"))

    @Test
    fun partialYearDark() = screen(ReviewSample.partialYear(), themed = false, light = false, name = "partial_year_dark", scrollTo = listOf("Workouts and streak"))

    private fun share(review: PeriodReview, themed: Boolean, light: Boolean, name: String) {
        compose.runOnUiThread { Words.setThemed(app, themed) }
        compose.activityRule.scenario.onActivity { activity ->
            ShotRecorder.darkWindow(activity)
            activity.setContent {
                IronvellumTheme(light = light) {
                    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) { ReviewShareContent(review, onBack = {}) }
                }
            }
        }
        compose.waitForIdle()
        settle()
        compose.waitForIdle()
        ShotRecorder.take(compose, "review_share_$name")
    }

    @Test
    fun shareDark() = share(ReviewSample.month(), themed = false, light = false, name = "dark")

    @Test
    fun shareYearLightThemed() = share(ReviewSample.fullYear(), themed = true, light = true, name = "year_light_themed")

    private fun card(review: PeriodReview, themed: Boolean, light: Boolean, name: String) {
        compose.runOnUiThread { Words.setThemed(app, themed) }
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                IronvellumTheme(light = light) {
                    val palette = RecapCardPalette.current()
                    LaunchedEffect(Unit) { dump(ReviewCard.render(context, review, palette = palette), name) }
                }
            }
        }
        compose.waitForIdle()
        settle()
    }

    /** The cards themselves, full size, written beside the captures. */
    @Test
    fun cardMonthDark() = card(ReviewSample.month(), themed = false, light = false, name = "review_card_month_dark")

    @Test
    fun cardMonthThemed() = card(ReviewSample.month(), themed = true, light = false, name = "review_card_month_themed")

    @Test
    fun cardMonthLight() = card(ReviewSample.month(), themed = false, light = true, name = "review_card_month_light")

    @Test
    fun cardYearDark() = card(ReviewSample.fullYear(), themed = false, light = false, name = "review_card_year_dark")

    @Test
    fun cardPartialYearDark() = card(ReviewSample.partialYear(), themed = false, light = false, name = "review_card_partial_year_dark")

    /** The Today card and the Stats rows, as they sit on a page. */
    @Test
    fun todayCardsAndStatsRows() {
        compose.runOnUiThread { Words.setThemed(app, false) }
        compose.activityRule.scenario.onActivity { activity ->
            ShotRecorder.darkWindow(activity)
            activity.setContent {
                IronvellumTheme {
                    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp)) {
                        ReviewTodayCard(ReviewSample.yearSummary(), onOpen = {}, onDismiss = {})
                        ReviewTodayCard(ReviewSample.monthSummary(), onOpen = {}, onDismiss = {})
                        Spacer(Modifier.height(12.dp))
                        RecapsPanel(listOf(ReviewSample.yearSummary(), ReviewSample.monthSummary()), listOf(RecapSample.full()), onOpenRecap = {}, onOpenReview = { _, _ -> })
                    }
                }
            }
        }
        compose.waitForIdle()
        ShotRecorder.take(compose, "review_today_stats")
    }

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
