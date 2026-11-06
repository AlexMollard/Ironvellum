package com.ironvellum.app.ui

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.MainActivity
import com.ironvellum.app.R
import com.ironvellum.app.data.*
import com.ironvellum.app.domain.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Real app NavHost: selector nesting, retained session owner, and sealed cooldown round trips. */
@RunWith(AndroidJUnit4::class)
class PreparationSessionFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val driver by lazy { FlowDriver(compose) }
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IronvellumApp
    private val repo get() = app.repository
    private fun text(id: Int, vararg args: Any): String {
        val themed = app.resources.getIdentifier(app.resources.getResourceEntryName(id) + "__t", "string", app.packageName)
        return Words.text(UiText.themed(id, themed.takeIf { it != 0 } ?: id, *args))
    }
    @Before fun setup() {
        if (InstrumentationRegistry.getArguments().getString("processProbe") != "verify") {
            TestProfile.ensureSetUp()
            app.database.openHelper.writableDatabase.execSQL("DELETE FROM sessions")
            WarmupStore(app).enabled = true
        }
        driver.takeTheClock()
    }
    private fun open(id: Long) {
        compose.activityRule.scenario.onActivity { activity ->
            activity.startActivity(Intent(activity.intent).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(Notifications.EXTRA_OPEN_TAB, Notifications.TAB_TRIAL).putExtra(Notifications.EXTRA_SESSION_ID, id))
        }
        driver.settle()
    }
    private fun seed(): Long = runBlocking {
        val exercises = repo.observeExercises().first()
        val bench = exercises.first { it.name == "Bench Press" }
        val arm = exercises.first { it.name == "Arm Circles" }
        val cool = repo.preparations.saveRoutine(PreparationRoutine(kind = PreparationKind.COOLDOWN, name = "Flow cooldown", steps = listOf(
            PreparationStep(arm.id, arm.name, PreparationMeasure.SECONDS, 30, repeat = PreparationRepeat.DIRECTIONS))))
        val preset = repo.savePreset(null, "Preparation flow", "", null, listOf(Repository.PresetDraftEntry(bench.id, 1, 5, 20.0)),
            mapOf(PreparationKind.COOLDOWN to PreparationChoice(PreparationMode.SAVED, cool)))
        repo.startSessionFromPreset(preset)
    }
    @Test fun missingSessionReturnsSafelyWithoutLiveLogControls() {
        open(Long.MAX_VALUE)
        driver.awaitText("Today")
        assertFalse(driver.allText().any { it == app.getString(R.string.session_set_log_button) })
    }
    @Test fun selectorCreateManageBackRetainsOwnerAndOnlyUseChangesTheSession() {
        val id = seed(); open(id)
        driver.awaitText(text(R.string.preparation_begin, text(R.string.preparation_warmup)))
        val before = runBlocking { repo.preparations.observeSession(id, PreparationKind.WARMUP).first() }
        driver.click(text(R.string.preparation_change))
        driver.awaitText(text(R.string.preparation_use))
        driver.click(text(R.string.preparation_none))
        driver.click(text(R.string.preparation_create))
        driver.awaitText(text(R.string.preparation_save))
        // Creation with no edits goes back without mutating the selector or its session owner.
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        driver.awaitText(text(R.string.preparation_use))
        driver.click(text(R.string.preparation_manage))
        driver.awaitText(text(R.string.preparation_library))
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        driver.awaitText(text(R.string.preparation_use))
        assertEquals(before, runBlocking { repo.preparations.observeSession(id, PreparationKind.WARMUP).first() })
        // Back cancels the draft choice.
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        driver.awaitText(text(R.string.preparation_change))
        assertEquals(before, runBlocking { repo.preparations.observeSession(id, PreparationKind.WARMUP).first() })
        driver.click(text(R.string.preparation_change))
        driver.awaitText(text(R.string.preparation_use))
        driver.click(text(R.string.preparation_none))
        driver.click(text(R.string.preparation_manage))
        driver.awaitText(text(R.string.preparation_library))
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        driver.awaitText(text(R.string.preparation_use))
        driver.click(text(R.string.preparation_use))
        compose.waitUntil(10000) { runBlocking { repo.preparations.observeSession(id, PreparationKind.WARMUP).first()!!.snapshot.steps.isEmpty() } }
        assertEquals(1, runBlocking { repo.observeSessionSets(id).first() }.size)
    }
    @Test fun sealedCooldownRotateLeaveResumeCompleteAndRevisitNeverChangesResults() = runBlocking {
        val id = seed()
        val set = repo.observeSessionSets(id).first().single()
        repo.updateSet(set.id, reps = 5, weightKg = 20.0, done = true)
        repo.completeSession(id)
        val session = repo.observeSession(id).first()
        val profile = repo.observeProfile().first()
        val sets = repo.observeSessionSets(id).first()
        val beforeRows = rewardRows()
        // Opens without any transient CompletionResult, just as after a killed process.
        open(id)
        driver.awaitText(text(R.string.preparation_begin, text(R.string.preparation_cooldown)))
        driver.click(text(R.string.preparation_begin, text(R.string.preparation_cooldown)))
        driver.awaitText(text(R.string.preparation_no_scoring))
        driver.click(text(R.string.preparation_begin, text(R.string.preparation_cooldown)))
        driver.awaitText(text(R.string.preparation_complete_unit))
        val deadline = repo.preparations.observeSession(id, PreparationKind.COOLDOWN).first()!!.progress.deadlineMs
        compose.activityRule.scenario.recreate()
        driver.awaitText(text(R.string.preparation_complete_unit))
        assertEquals(deadline, repo.preparations.observeSession(id, PreparationKind.COOLDOWN).first()!!.progress.deadlineMs)
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        driver.awaitText(text(R.string.preparation_leave_title))
        driver.click(text(R.string.preparation_leave))
        driver.awaitText(text(R.string.preparation_resume, text(R.string.preparation_cooldown)))
        assertEquals(PreparationStatus.PAUSED, repo.preparations.observeSession(id, PreparationKind.COOLDOWN).first()!!.progress.status)
        driver.click(text(R.string.preparation_resume, text(R.string.preparation_cooldown)))
        driver.awaitText(text(R.string.preparation_complete_unit))
        driver.click(text(R.string.preparation_complete_unit))
        driver.awaitText(text(R.string.preparation_direction_second))
        driver.click(text(R.string.preparation_complete_unit))
        driver.awaitText(text(R.string.preparation_return_summary))
        driver.click(text(R.string.preparation_return_summary))
        driver.awaitText(text(R.string.preparation_finished, text(R.string.preparation_cooldown)))
        driver.click(text(R.string.preparation_preview))
        driver.awaitText(text(R.string.preparation_return_summary))
        driver.click(text(R.string.preparation_return_summary))
        driver.awaitText(text(R.string.preparation_finished, text(R.string.preparation_cooldown)))
        assertEquals(session, repo.observeSession(id).first())
        assertEquals(profile, repo.observeProfile().first())
        assertEquals(sets, repo.observeSessionSets(id).first())
        assertEquals(beforeRows, rewardRows())
    }
    private fun rewardRows(): List<String> = app.database.openHelper.writableDatabase.query("SELECT name FROM sqlite_master WHERE type='table' AND (name LIKE '%reward%' OR name LIKE '%title%' OR name LIKE '%achievement%') ORDER BY name").use { cursor ->
        buildList { while (cursor.moveToNext()) {
            val table = cursor.getString(0)
            app.database.openHelper.writableDatabase.query("SELECT * FROM `$table`").use { rows ->
                while (rows.moveToNext()) add(table + (0 until rows.columnCount).joinToString { if (rows.isNull(it)) "NULL" else rows.getString(it) })
            }
        } }
    }
    /** Also run as two adb instrumentation invocations with an intervening force-stop (seed / verify). */
    @Test fun processRestartProbe() = runBlocking {
        val phase = InstrumentationRegistry.getArguments().getString("processProbe")
        val prefs = app.getSharedPreferences("preparation-process-probe", 0)
        val id = if (phase == "verify") prefs.getLong("session", -1) else {
            val seeded = seed()
            val set = repo.observeSessionSets(seeded).first().single()
            repo.updateSet(set.id, 5, 20.0, true)
            repo.completeSession(seeded)
            repo.preparations.act(seeded, PreparationKind.COOLDOWN, PreparationAction.START, System.currentTimeMillis())
            prefs.edit().putLong("session", seeded).putString("signature", resultSignature(seeded))
                .putInt("pid", android.os.Process.myPid())
                .putLong("deadline", repo.preparations.observeSession(seeded, PreparationKind.COOLDOWN).first()!!.progress.deadlineMs!!).commit()
            seeded
        }
        assertTrue(id > 0)
        if (phase == "verify") assertNotEquals("probe must cross an actual app process boundary", prefs.getInt("pid", -1), android.os.Process.myPid())
        open(id)
        driver.awaitText(text(R.string.preparation_resume, text(R.string.preparation_cooldown)))
        driver.click(text(R.string.preparation_resume, text(R.string.preparation_cooldown)))
        driver.awaitText(text(R.string.preparation_complete_unit))
        assertEquals(prefs.getLong("deadline", 0), repo.preparations.observeSession(id, PreparationKind.COOLDOWN).first()!!.progress.deadlineMs)
        assertEquals(prefs.getString("signature", null), resultSignature(id))
        if (phase != "seed") {
            driver.click(text(R.string.preparation_complete_unit))
            driver.awaitText(text(R.string.preparation_direction_second))
            driver.click(text(R.string.preparation_complete_unit))
            driver.awaitText(text(R.string.preparation_return_summary))
            driver.click(text(R.string.preparation_return_summary))
            driver.awaitText(text(R.string.preparation_finished, text(R.string.preparation_cooldown)))
            assertEquals(prefs.getString("signature", null), resultSignature(id))
        }
    }
    private suspend fun resultSignature(id: Long) = listOf(repo.observeSession(id).first().toString(),
        repo.observeProfile().first().toString(), repo.observeSessionSets(id).first().toString(), rewardRows().toString()).joinToString("\n")
}
