package com.ironvellum.app.ui

import android.accessibilityservice.AccessibilityService
import android.os.ParcelFileDescriptor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.MainActivity
import com.ironvellum.app.data.Words
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Text follows the system font scale, so the calls to action have to survive the largest setting. This runs the
 * real app at the device's 200% text size (Android 14 scales it non-linearly past 1.3) and asserts, screen by
 * screen, that the controls a lifter needs are shown whole: on screen, not clipped by the box around them, and
 * with a label the text layout did not have to cut or overflow.
 *
 * The scale is set through the system setting, not a density override in the test host, so dialogs, sheets and
 * menus, which compose in windows of their own, are swept at it too. It is put back afterwards, even when a
 * check fails. Every screen is checked and the failures reported together, so one clipped label does not hide
 * the next. `shots=true` also saves a picture of each screen (see [ShotRecorder]).
 */
@RunWith(AndroidJUnit4::class)
class FontScaleSweepTest {

    private val systemScale = SystemFontScale(
        InstrumentationRegistry.getArguments().getString("fontscale")?.toFloatOrNull() ?: 2.0f,
    )
    private val activity = createAndroidComposeRule<MainActivity>()

    // The setting has to be in place before the activity is created, or the activity starts at 1.0 and is recreated.
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(systemScale).around(activity)

    private val compose get() = activity
    private val driver by lazy { FlowDriver(compose) }
    private val failures = ArrayList<String>()

    @Before
    fun seedAndTakeTheClock() {
        // Plain words: the labels below are the ones a lifter who has not turned the themed names on sees.
        Words.setThemed(app(), false)
        TestProfile.ensureSetUp()
        DemoHistory.seed()
        compose.mainClock.autoAdvance = false
        driver.settle()
    }

    @After
    fun leaveNoTrialBehind() = runBlocking {
        val sessions = app().database.sessionDao()
        while (true) {
            val live = sessions.liveSession() ?: break
            sessions.deleteAbandoned(live.id)
        }
    }

    @Test
    fun keyScreensHoldTheirCallsToActionAtTwoHundredPercent() {
        screen("today") {
            for (label in navLabels) whole(label, inTab = true)
        }
        // The other kinds of day the card can show, and the sheets the header opens.
        screen("today-respite") { clickDescribed("Wednesday") }
        screen("today-sealed") { clickDescribed("Tuesday") }
        clickDescribed("Friday")
        screen("today-rank-sheet") { clickLabelled("Show Strength Rank") }
        back()
        screen("today-level-sheet") { clickLabelled("Explain level and XP") }
        back()
        screen("today-streak-sheet") { clickLabelled("Explain ${Words.oath.t}") }
        back()
        screen("stats-body") {
            driver.tab(Words.ledger.c)
            for (label in listOf("BODY", "TRAINING", "DAILY")) whole(label, inTab = true)
        }
        screen("stats-training") { driver.click("TRAINING") }
        screen("stats-lift") { driver.click("Bench Press") }
        // A lift page keeps the bottom bar, so this back is wanted even where the bar shows.
        back(pushed = true)
        screen("stats-daily") { driver.click("DAILY") }
        screen("train") {
            driver.tab("Train")
            // The week's routines load after the page opens; the New and Share chips come with them.
            driver.awaitText("Share")
            whole("Empty workout")
            whole("New")
            whole("Share")
        }
        // Pushed pages and dialogs: each is opened, looked at, and closed again.
        screen("train-new") { driver.click("New") }
        back()
        screen("train-log") { driver.click("All") }
        back()
        // Coverage is a view inside Train now (Muscles), so the way back is the Plan tab, not a back press.
        screen("train-coverage") {
            driver.click("Weekly coverage")
            driver.awaitText("FRONT")
        }
        driver.click("Plan")
        screen("train-exercises") {
            driver.click("Exercises")
            driver.awaitText("Search exercises")
        }
        screen("train-exercise-info") {
            compose.onAllNodes(hasContentDescription("About ", substring = true)).onFirst()
                .performSemanticsAction(SemanticsActions.OnClick)
            driver.settle()
        }
        backUntilShowing("Empty workout", pushed = true)
        screen("rite") {
            openFirstRite()
            // The routine loads after the page opens; its buttons are not there until it has.
            driver.awaitText("Begin ")
            whole("Edit")
            whole("BACK")
            whole("Begin ", substring = true)
        }
        screen("editor") {
            driver.click("Edit")
            driver.awaitText("Save routine")
            whole("Save routine")
        }
        screen("editor-sets") {
            // A movement's row opens the sets sheet.
            compose.onAllNodes(hasText("\u00D7", substring = true)).onFirst().performSemanticsAction(SemanticsActions.OnClick)
            driver.settle()
            driver.awaitText("Sets for ")
            whole("Done")
            whole("Cancel")
        }
        screen("editor-add") { driver.click("Add exercise") }
        back()
        backUntilShowing("Begin ")
        screen("live") {
            clickStarting("Begin ")
            driver.awaitText("sets")
            whole("BACK")
            displayed(hasContentDescription("Log set 1"))
        }
        screen("rest") {
            compose.onAllNodes(hasContentDescription("Log set 1")).onFirst()
                .performSemanticsAction(SemanticsActions.OnClick)
            driver.settle()
            // The warm-up card has a Skip of its own; the +15 is the rest bar's alone.
            driver.awaitText("+15")
            whole("Skip")
            whole("+15")
        }
        finishTheTrial()
        toTopLevel()
        screen("progress-deeds") {
            driver.tab(Words.codex.c)
            for (label in listOf("DEEDS", "SKILLS")) whole(label, inTab = true)
        }
        screen("progress-skills") { driver.click("SKILLS") }
        screen("friends") { driver.tab(Words.ally.cpl) }
        screen("settings") {
            driver.tab("Today")
            compose.onAllNodes(hasOnClickLabel("Open settings")).onFirst()
                .performSemanticsAction(SemanticsActions.OnClick)
            driver.settle()
            whole("BACK")
            whole("Appearance")
        }
        for (page in listOf("Profile", "Training mode", "Appearance", "Equipment", "Reminder", "Data", "Health Connect")) {
            screen("settings-" + page.lowercase().replace(' ', '-')) {
                driver.click(page)
                whole("BACK")
            }
            back()
        }
        // Equipment with dumbbells only: the One / Pair and max stepper sub-row appears, then the default goes back.
        screen("settings-equipment-dumbbells") {
            driver.click("Equipment")
            driver.click("Nothing")
            driver.click("Dumbbells")
            whole("Pair")
            whole("One")
        }
        runCatching { driver.click("Full gym") }
        back()

        check(failures.isEmpty()) { "At 200% text:\n" + failures.joinToString("\n") }
    }

    /**
     * Seals the trial from the slider's own action, through the "unlogged sets" confirm, then walks the
     * celebration pages (each its own window) to the end: every page is looked at and its button checked.
     */
    private fun finishTheTrial() {
        runCatching {
            compose.onAllNodes(hasContentDescription("${Words.seal.c} the ${Words.trial.s}")).onFirst().performSemanticsAction(SemanticsActions.OnClick)
            driver.settle()
            driver.awaitText("${Words.seal.c} anyway")
            ShotRecorder.take(compose, "fontscale-seal-confirm")
            whole("${Words.seal.c} anyway")
            driver.click("${Words.seal.c} anyway")
            for (page in 1..12) {
                val advance = runCatching {
                    driver.awaitAnyText(attempts = 20) { label -> advanceLabels.any { label.equals(it, ignoreCase = true) } }
                }.getOrNull() ?: break
                runCatching { whole(advance) }.onFailure { failures += "[finish-$page] ${it.message ?: it}" }
                ShotRecorder.take(compose, "fontscale-finish-$page")
                driver.click(advance)
            }
        }.onFailure { failures += "[finish] ${it.message ?: it}" }
    }

    /** Runs one screen's checks, saves its picture, and records instead of throwing, so the sweep goes on. */
    private fun screen(name: String, checks: () -> Unit) {
        runCatching { checks() }.onFailure {
            failures += "[$name] ${it.message ?: it}\n    on screen: ${runCatching { driver.allText().take(12) }.getOrNull()}"
        }
        ShotRecorder.take(compose, "fontscale-$name")
    }

    /**
     * The system back, which also closes dialogs and sheets. On a top-level screen with nothing open it does nothing:
     * back there would end the activity, and a sweep that lost one page must not take the rest down with it.
     */
    private fun back(pushed: Boolean = false) {
        val atTopLevel = barShows() && !pushed
        val dialogOpen = compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty()
        if (atTopLevel && !dialogOpen) return
        if (dialogOpen) {
            // A dialog or sheet answers its own window's back key, not the activity's.
            InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        } else {
            compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        }
        driver.settle()
    }

    private fun clickStarting(prefix: String) {
        compose.onAllNodes(hasText(prefix, substring = true)).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        driver.settle()
    }

    private fun clickDescribed(description: String) {
        compose.onAllNodes(hasContentDescription(description, substring = true) and hasClickAction()).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        driver.settle()
    }

    private fun clickLabelled(label: String) {
        compose.onAllNodes(hasOnClickLabel(label)).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        driver.settle()
    }

    /** Backs out, a page at a time, until some text starting with [prefix] is on screen. */
    private fun backUntilShowing(prefix: String, pushed: Boolean = false) {
        repeat(4) {
            if (compose.onAllNodes(hasText(prefix, substring = true)).fetchSemanticsNodes().isNotEmpty()) return
            back(pushed)
        }
    }

    /** The bottom bar is on screen: a top-level page, not a pushed one. */
    private fun barShows() = runCatching {
        compose.onAllNodes(hasContentDescription("Today")).onFirst().assertIsDisplayed()
    }.isSuccess

    /** Back out of pushed pages until the bottom bar shows again. */
    private fun toTopLevel() {
        repeat(5) {
            if (barShows()) return
            back()
        }
    }

    private fun openFirstRite() {
        compose.onAllNodes(hasOnClickLabel("Open ", prefix = true)).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        driver.settle()
    }

    private fun app() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IronvellumApp

    private fun hasOnClickLabel(label: String, prefix: Boolean = false) = SemanticsMatcher("clickable \"$label\"") {
        val actual = it.config.getOrNull(SemanticsActions.OnClick)?.label
        actual != null && (if (prefix) actual.startsWith(label) else actual == label)
    }

    private fun displayed(matcher: SemanticsMatcher) {
        compose.onAllNodes(matcher).onFirst().assertIsDisplayed()
    }

    /**
     * The first node showing [text] is on screen, is not cut by a box around it (its visible bounds are its own
     * size) and was laid out without overflow, so no ellipsis hides part of the label.
     */
    private fun whole(text: String, substring: Boolean = false, inTab: Boolean = false) {
        var matcher = hasText(text, substring = substring)
        if (inTab) matcher = matcher and hasAnyAncestor(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        val matches = compose.onAllNodes(matcher, useUnmergedTree = true)
        val node = matches.onFirst()
        runCatching { node.assertIsDisplayed() }.onFailure {
            error("${it.message} Matches: " + matches.fetchSemanticsNodes().joinToString { n -> "${n.boundsInRoot} size=${n.size}" })
        }
        val info = node.fetchSemanticsNode()
        val shown = info.boundsInRoot
        check(abs(shown.width - info.size.width) < 1f && abs(shown.height - info.size.height) < 1f) {
            "\"$text\" is clipped: ${shown.width}x${shown.height} of ${info.size.width}x${info.size.height}px"
        }
        val layouts = ArrayList<TextLayoutResult>()
        info.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        check(layouts.none { layout -> (0 until layout.lineCount).any(layout::isLineEllipsized) }) { "\"$text\" is cut short with an ellipsis" }
        // A single word on more than one line has been broken mid-word by a box too narrow for it.
        check(' ' in text.trim() || layouts.none { layout -> layout.lineCount > 1 }) { "\"$text\" is broken across lines" }
    }

    private val advanceLabels get() = listOf("Continue", "Done", "Keep ${Words.rite.s}")
    private val navLabels get() = listOf("Today", Words.ledger.c, "Train", Words.codex.c, Words.ally.cpl)
}

/**
 * Sets the system font scale for the test and puts the old value back. It waits until the app's own configuration
 * has taken the change, so the activity is created at that size.
 */
private class SystemFontScale(private val scale: Float) : ExternalResource() {
    private var before: String? = null

    override fun before() {
        before = shell("settings get system font_scale").trim().takeIf { it != "null" && it.isNotEmpty() }
        apply(scale.toString())
    }

    override fun after() {
        apply(before ?: "1.0")
    }

    private fun apply(value: String) {
        shell("settings put system font_scale $value")
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val wanted = value.toFloat()
        repeat(100) {
            if (abs(resources.configuration.fontScale - wanted) < 0.01f) return
            Thread.sleep(100)
        }
        error("the app never saw font scale $value; it is ${resources.configuration.fontScale}")
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command))
            .use { it.readBytes().decodeToString() }
}
