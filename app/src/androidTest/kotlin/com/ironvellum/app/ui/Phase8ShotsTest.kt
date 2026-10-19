package com.ironvellum.app.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.R
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.data.CircleBonusPaid
import com.ironvellum.app.data.CircleGateway
import com.ironvellum.app.data.CircleRead
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.Circle
import com.ironvellum.app.domain.CircleMember
import com.ironvellum.app.domain.CollectionTab
import com.ironvellum.app.domain.CrestEffects
import com.ironvellum.app.domain.Gacha
import com.ironvellum.app.ui.idle.CollectionScreen
import com.ironvellum.app.ui.idle.HousesSheet
import com.ironvellum.app.ui.idle.IdleScreen
import com.ironvellum.app.ui.idle.RateScreen
import com.ironvellum.app.ui.idle.VeilBuySheet
import com.ironvellum.app.ui.social.AccountScreen
import com.ironvellum.app.ui.social.CircleSection
import com.ironvellum.app.ui.social.CircleViewModel
import com.ironvellum.app.ui.theme.IronvellumTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * String-extraction phase 8: the Veil screens, the circle block and the signed-out account screen, in plain and themed
 * mode, photographed with `-Pandroid.testInstrumentationRunnerArguments.shots=true` (see [ShotRecorder]) for a human to
 * read for raw keys, "null", wrong plurals for 1 and 2, and truncation. Without the flag the tests do nothing (the gate's time budget).
 *
 * The circle is seeded through its own gateway interface (the unit tests' pattern). The other social screens (feed,
 * inbox, lifter, comments, leaderboard, account settings) read the cloud through objects with no seam and need a
 * signed-in account, so they are not captured here.
 */
@RunWith(AndroidJUnit4::class)
class Phase8ShotsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IronvellumApp

    @Before
    fun setUp() {
        TestProfile.ensureSetUp()
    }

    @After
    fun themedAgain() {
        compose.runOnUiThread { Words.setThemed(app, true) }
    }

    private fun show(themed: Boolean, content: @Composable () -> Unit) {
        compose.runOnUiThread { Words.setThemed(app, themed) }
        compose.activityRule.scenario.onActivity { activity ->
            ShotRecorder.darkWindow(activity)
            activity.setContent {
                IronvellumTheme {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.then(androidx.compose.ui.Modifier.padding(top = 48.dp))) { content() }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun shoot(name: String, scroll: Boolean = false) {
        ShotRecorder.take(compose, name)
        if (scroll && InstrumentationRegistry.getArguments().getString("shots") == "true") {
            compose.mainClock.autoAdvance = true
            swipeUp()
            Thread.sleep(600)
            ShotRecorder.take(compose, name + "_b")
        }
        compose.mainClock.autoAdvance = true
    }

    /** A real swipe on the display: works through sheet and dialog windows, which have no single semantics root. */
    private fun swipeUp() {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input swipe 540 1900 540 700 250")
        java.io.FileInputStream(pfd.fileDescriptor).use { it.readBytes() }
    }

    private fun member(id: String, name: String, days: Int, counts: Boolean = true) =
        CircleMember(id, name, level = 12, titleId = null, daysThisWeek = days, counts = counts, lastWorkoutAtMs = null)

    private class FakeGateway(private val circle: Circle?, private val paid: CircleBonusPaid? = null) : CircleGateway {
        override val userId = MutableStateFlow<String?>("me")
        override fun currentUserId(): String? = "me"
        override suspend fun read(force: Boolean) = Result.success(CircleRead(circle, paid))
        override suspend fun create(name: String) = Result.success(Unit)
        override suspend fun join(code: String) = Result.success(Unit)
        override suspend fun leave() = Result.success(Unit)
        override suspend fun setGoal(perMember: Int) = Result.success(Unit)
        override suspend fun rename(name: String) = Result.success(Unit)
        override suspend fun rotateCode() = Result.success(Unit)
        override suspend fun removeMember(userId: String) = Result.success(Unit)
    }

    private fun circleScreens(themed: Boolean, tag: String) {
        val one = Circle(
            id = "c1", name = "Iron Pals", code = "AB23CD", ownerId = "me", week = "2026-10-12", perMember = 1,
            goal = 2, total = 1, roster = 2, weeksMet = 1,
            members = listOf(member("me", "You", 1), member("u2", "Sam", 0)),
        )
        val many = one.copy(
            perMember = 3, pendingPerMember = 2, goal = 9, total = 5, roster = 3, weeksMet = 2,
            members = listOf(member("me", "You", 3), member("u2", "Sam", 2), member("u3", "Rae", 0, counts = false)),
        )
        show(themed) { CircleSection(onOpenLifter = { _, _ -> }, viewModel = CircleViewModel(FakeGateway(null))) }
        shoot("p8_${tag}_circle_none")
        show(themed) { CircleSection(onOpenLifter = { _, _ -> }, viewModel = CircleViewModel(FakeGateway(one))) }
        shoot("p8_${tag}_circle_one_day", scroll = true)
        show(themed) { CircleSection(onOpenLifter = { _, _ -> }, viewModel = CircleViewModel(FakeGateway(one, CircleBonusPaid(40, "Iron Pals")))) }
        shoot("p8_${tag}_circle_paid")
        show(themed) { CircleSection(onOpenLifter = { _, _ -> }, viewModel = CircleViewModel(FakeGateway(many))) }
        shoot("p8_${tag}_circle_many", scroll = true)
    }

    private fun veilScreens(themed: Boolean, tag: String) {
        show(themed) { IdleScreen(onBack = {}) }
        shoot("p8_${tag}_veil_intro")
        // The first-run sheet stands in front of the Veil; dismiss it to see the page under it.
        compose.mainClock.autoAdvance = true
        runCatching { compose.onNodeWithText("Got it").performClick() }
        compose.waitForIdle()
        shoot("p8_${tag}_veil", scroll = true)
        show(themed) { RateScreen(onBack = {}, onOpenCollection = {}) }
        shoot("p8_${tag}_rate", scroll = true)
        show(themed) { CollectionScreen(initialTab = CollectionTab.Relics, onBack = {}) }
        shoot("p8_${tag}_collection_relics", scroll = true)
        show(themed) { CollectionScreen(initialTab = CollectionTab.Crests, onBack = {}) }
        shoot("p8_${tag}_collection_crests", scroll = true)
        show(themed) { HousesSheet(onDismiss = {}) }
        shoot("p8_${tag}_houses")
        show(themed) {
            VeilBuySheet(
                cost = 120, essence = 300, nextCost = 150, crest = CrestEffects(), ownedFrames = emptySet(),
                pity = Gacha.Pity(figureStreak = 3, relicStreak = 2, draws = 5, hasRelic = true),
                onConfirm = {}, onDismiss = {},
            )
        }
        shoot("p8_${tag}_buy_sheet", scroll = true)
    }

    private fun accountSignedOut(themed: Boolean, tag: String) {
        show(themed) { AccountScreen(onBack = {}, pushed = true) }
        shoot("p8_${tag}_account_signed_out", scroll = true)
    }

    /** The converted plurals at 1 and 2, both modes: one page each, read for a wrong form or a stray raw key. */
    private fun pluralsPage(themed: Boolean, tag: String) {
        show(themed) {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp)) {
                for (n in 1..2) {
                    val lines = listOf(
                        UiText.plural(R.plurals.today_sets_logged, n, 1, n),
                        UiText.themedPlural(R.plurals.titles_paths_tally, R.plurals.titles_paths_tally__t, n, 1, n),
                        UiText.themedPlural(R.plurals.settings_advanced_dev_changed, R.plurals.settings_advanced_dev_changed__t, n, 1, n),
                        UiText.themedPlural(R.plurals.cloud_remark_too_long, R.plurals.cloud_remark_too_long__t, n),
                        UiText.plural(R.plurals.customex_error_name_long, n),
                        UiText.themedPlural(R.plurals.cloud_name_letters, R.plurals.cloud_name_letters__t, n),
                        UiText.plural(R.plurals.skilljournal_heatmap_summary, n, n, "quiet", "attempts", "Mon"),
                        UiText.plural(R.plurals.always_deeds_ladder_held, n, 1, n),
                        UiText.plural(R.plurals.veilreveal_run_description, n, 1, n),
                        UiText.plural(R.plurals.acct_sync_skipped, n),
                    )
                    for (line in lines) androidx.compose.material3.Text("$n: " + line.asString(), color = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
        shoot("p8_${tag}_plurals")
    }

    /** Captures only: about 2.5 minutes of screens, over the gate's budget, and nothing here asserts (a skip reads as red there). */
    private fun whenCapturing(block: () -> Unit) {
        if (InstrumentationRegistry.getArguments().getString("shots") == "true") block()
    }

    @Test fun pluralsPlain() = whenCapturing { pluralsPage(false, "plain") }

    @Test fun pluralsThemed() = whenCapturing { pluralsPage(true, "themed") }

    @Test fun veilPlain() = whenCapturing { veilScreens(false, "plain") }

    @Test fun veilThemed() = whenCapturing { veilScreens(true, "themed") }

    @Test fun circlePlain() = whenCapturing { circleScreens(false, "plain") }

    @Test fun circleThemed() = whenCapturing { circleScreens(true, "themed") }

    @Test fun accountPlain() = whenCapturing { accountSignedOut(false, "plain") }

    @Test fun accountThemed() = whenCapturing { accountSignedOut(true, "themed") }
}
