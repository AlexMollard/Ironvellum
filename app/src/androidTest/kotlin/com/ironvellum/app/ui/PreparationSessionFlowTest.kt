package com.ironvellum.app.ui

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.MainActivity
import com.ironvellum.app.R
import com.ironvellum.app.data.*
import com.ironvellum.app.domain.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
    private fun check() = compose.onNode(hasContentDescription(text(R.string.preparation_complete_unit), substring = true))
    private fun awaitCheck() {
        compose.waitUntil(10000) { driver.settle(); compose.onAllNodes(hasContentDescription(text(R.string.preparation_complete_unit), substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun offerMenu() { compose.onNodeWithContentDescription(text(R.string.preparation_actions)).performClick(); driver.settle() }
    private fun typedLoad() = SemanticsMatcher("type load action") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == app.getString(R.string.session_stepper_load_type)
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
    @Test fun beginPersistsBeforeDirectMovementNavigation() = runBlocking {
        val id = seed(); open(id)
        driver.awaitText(text(R.string.preparation_begin, text(R.string.preparation_warmup)))
        val state = repo.preparations.observeSession(id, PreparationKind.WARMUP).first()!!
        preparationCapture(compose, "refinement-workout-offer")
        driver.click(text(R.string.preparation_begin, text(R.string.preparation_warmup)))
        driver.awaitText(state.snapshot.steps.first().exerciseName)
        assertEquals(PreparationStatus.RUNNING, repo.preparations.observeSession(id, PreparationKind.WARMUP).first()!!.progress.status)
        assertFalse(driver.allText().contains(text(R.string.preparation_begin, text(R.string.preparation_warmup))))
        compose.onNode(hasContentDescription(text(R.string.preparation_complete_unit), substring = true)).assertIsDisplayed()
        preparationCapture(compose, "refinement-task2-nav-warmup")
        repeat(state.snapshot.steps.sumOf { it.rounds * it.repetitions }) {
            awaitCheck()
            check().performClick()
            driver.settle()
        }
        driver.awaitText(text(R.string.preparation_return_workout))
        assertEquals(PreparationStatus.DONE, repo.preparations.observeSession(id, PreparationKind.WARMUP).first()!!.progress.status)
        preparationCapture(compose, "refinement-task2-nav-warmup-complete")
        driver.click(text(R.string.preparation_return_workout))
        driver.awaitText(text(R.string.preparation_view))
        preparationCapture(compose, "refinement-task2-nav-workout-terminal")
        Unit
    }
    @Test fun failedStartShowsErrorAndRetryEntersRunner() = runBlocking {
        val id = seed(); open(id)
        val begin = text(R.string.preparation_begin, text(R.string.preparation_warmup))
        driver.awaitText(begin)
        val db = app.database.openHelper.writableDatabase
        db.execSQL("CREATE TRIGGER fail_preparation_start BEFORE INSERT ON session_preparations BEGIN SELECT RAISE(ABORT, 'test start failure'); END")
        try {
            driver.click(begin)
            driver.awaitText(text(R.string.preparation_error))
            assertEquals(PreparationStatus.OPEN, repo.preparations.observeSession(id, PreparationKind.WARMUP).first()!!.progress.status)
            assertTrue(driver.allText().contains(begin))
        } finally {
            db.execSQL("DROP TRIGGER IF EXISTS fail_preparation_start")
        }
        driver.click(begin)
        awaitCheck()
        assertEquals(PreparationStatus.RUNNING, repo.preparations.observeSession(id, PreparationKind.WARMUP).first()!!.progress.status)
    }
    @Test fun assistedTypedAndSteppedZeroPersistAndCanBeRecorded() = runBlocking {
        val originalUnit = Units.current
        compose.runOnIdle { Units.set(app, LoadUnit.KG) }
        try {
            val assisted = repo.observeExercises().first().first { it.name == "Assisted Pull-up" }
            val preset = repo.savePreset(null, "Assistance entry", "", null,
                listOf(Repository.PresetDraftEntry(assisted.id, 1, 5, 2.5)))
            val id = repo.startSessionFromPreset(preset)
            val loadLabel = app.getString(R.string.session_load_field_label, "kg")
            fun openLoadDialog() {
                driver.settle()
                compose.onNode(typedLoad()).performClick()
                driver.awaitText(loadLabel)
            }
            open(id)
            driver.awaitText("Assisted Pull-up")
            compose.waitUntil(10000) {
                driver.settle()
                compose.onAllNodes(typedLoad()).fetchSemanticsNodes().isNotEmpty()
            }
            openLoadDialog()
            compose.onNodeWithText(loadLabel).performTextReplacement("0")
            driver.settle()
            driver.click(app.getString(R.string.session_load_this_set))
            withTimeout(10000) { repo.observeSessionSets(id).first { it.single().weightKg == 0.0 } }
            openLoadDialog()
            compose.onNodeWithText(loadLabel).performTextClearance()
            driver.settle()
            driver.click(app.getString(R.string.session_load_this_set))
            withTimeout(10000) { repo.observeSessionSets(id).first { it.single().weightKg == null } }
            openLoadDialog()
            compose.onNodeWithText(loadLabel).performTextReplacement("0")
            driver.settle()
            driver.click(app.getString(R.string.session_load_this_set))
            withTimeout(10000) { repo.observeSessionSets(id).first { it.single().weightKg == 0.0 } }
            driver.settle()
            compose.onNodeWithContentDescription(app.getString(R.string.session_stepper_load_increase)).performClick()
            withTimeout(10000) { repo.observeSessionSets(id).first { it.single().weightKg == 2.5 } }
            driver.settle()
            compose.onNodeWithContentDescription(app.getString(R.string.session_stepper_load_decrease)).performClick()
            withTimeout(10000) { repo.observeSessionSets(id).first { it.single().weightKg == 0.0 } }
            driver.settle()
            compose.onNodeWithContentDescription(app.getString(R.string.session_log_set_description, 1)).performClick()
            val zero = withTimeout(10000) { repo.observeSessionSets(id).first { it.single().done } }.single()
            val session = repo.observeSession(id).first()!!
            val record = SetRecords.records(listOf(session to listOf(zero)), { 80.0 }).values.single()
            assertEquals("Assisted Pull-up", record.exerciseName)
            assertEquals(0.0, record.weightKg!!, 0.0)
            assertEquals(5, record.reps)
            assertEquals(session.id, record.sessionId)
        } finally {
            compose.runOnIdle { Units.set(app, originalUnit) }
        }
    }
    @Test fun keepAwakeFollowsTimedRunnerAndWorkoutOwnership() = runBlocking {
        val prefs = TrialPrefs(app)
        val original = prefs.keepScreenOn
        prefs.keepScreenOn = true
        try {
            val exercises = repo.observeExercises().first()
            val arm = exercises.first { it.name == "Arm Circles" }
            val plank = exercises.first { it.name == "Plank" }
            val routine = repo.preparations.saveRoutine(PreparationRoutine(kind = PreparationKind.WARMUP, name = "Awake warmup",
                steps = listOf(PreparationStep(arm.id, arm.name, PreparationMeasure.SECONDS, 300))))
            val preset = repo.savePreset(null, "Awake trial", "", null, listOf(Repository.PresetDraftEntry(plank.id, 1, 300, null)),
                mapOf(PreparationKind.WARMUP to PreparationChoice(PreparationMode.SAVED, routine)))
            val id = repo.startSessionFromPreset(preset)
            fun awake(): Boolean {
                var result = false
                compose.runOnIdle {
                    fun owns(view: View): Boolean = view.keepScreenOn || (view is ViewGroup && (0 until view.childCount).any { owns(view.getChildAt(it)) })
                    result = owns(compose.activity.window.decorView) ||
                        (compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
                }
                return result
            }
            fun expectAwake(expected: Boolean) {
                compose.waitUntil(10000) { driver.settle(); awake() == expected }
                assertEquals(expected, awake())
            }
            fun startHold() {
                driver.awaitText(app.getString(R.string.timedset_start_button))
                driver.click(app.getString(R.string.timedset_start_button))
                driver.awaitText(app.getString(R.string.timedset_stop_button))
                compose.waitUntil(10000) {
                    driver.settle()
                    compose.onAllNodes(hasContentDescription("elapsed, target", substring = true)).fetchSemanticsNodes().isNotEmpty()
                }
            }
            open(id)
            val begin = text(R.string.preparation_begin, text(R.string.preparation_warmup))
            driver.awaitText(begin)
            expectAwake(true)
            driver.click("Awake warmup")
            driver.awaitText(text(R.string.preparation_no_scoring))
            expectAwake(false)
            back()
            driver.click(begin)
            awaitCheck()
            preparationCapture(compose, "awake-running-preparation")
            expectAwake(true)
            driver.click(text(R.string.preparation_pause))
            driver.awaitText(text(R.string.preparation_start_timer))
            expectAwake(false)
            driver.click(text(R.string.preparation_start_timer))
            expectAwake(true)
            compose.onNodeWithContentDescription(text(R.string.preparation_actions)).performClick()
            driver.settle()
            driver.awaitText(text(R.string.preparation_preview))
            driver.click(text(R.string.preparation_preview))
            driver.awaitText(text(R.string.preparation_no_scoring))
            expectAwake(false)
            back()
            awaitCheck()
            expectAwake(true)
            check().performClick()
            driver.awaitText(text(R.string.preparation_return_workout))
            expectAwake(false)
            driver.click(text(R.string.preparation_return_workout))
            driver.awaitText(text(R.string.preparation_view))
            expectAwake(true)
            startHold()
            expectAwake(true)
            compose.onNodeWithContentDescription(app.getString(R.string.session_lock_description)).performClick()
            driver.settle()
            driver.awaitText(app.getString(R.string.pocketlock_hold_label))
            expectAwake(true)
            preparationCapture(compose, "awake-running-hold-pocket-lock")
            compose.onNode(hasContentDescription(text(R.string.pocketlock_description, ""), substring = true))
                .performSemanticsAction(SemanticsActions.OnLongClick)
            driver.settle()
            driver.awaitText(app.getString(R.string.timedset_stop_button))
            expectAwake(true)
            prefs.keepScreenOn = false
            back()
            driver.settle()
            repo.observeSessionSets(id).first().forEach { repo.updateSet(it.id, it.reps, it.weightKg, done = true) }
            repo.completeSession(id)
            val second = repo.startSessionFromPreset(preset)
            open(second)
            driver.awaitText(begin)
            expectAwake(false)
            driver.click(begin)
            awaitCheck()
            expectAwake(false)
            check().performClick()
            driver.awaitText(text(R.string.preparation_return_workout))
            driver.click(text(R.string.preparation_return_workout))
            startHold()
            expectAwake(false)
            compose.onNodeWithContentDescription(app.getString(R.string.session_lock_description)).performClick()
            driver.settle()
            driver.awaitText(app.getString(R.string.pocketlock_hold_label))
            expectAwake(false)
        } finally {
            prefs.keepScreenOn = original
        }
    }
    @Test fun failedCooldownStartRemainsOnSummaryAndCanRetry() = runBlocking {
        val id = seed()
        repo.completeSession(id)
        open(id)
        val begin = text(R.string.preparation_begin, text(R.string.preparation_cooldown))
        driver.awaitText(begin)
        val db = app.database.openHelper.writableDatabase
        db.execSQL("CREATE TRIGGER fail_cooldown_start BEFORE INSERT ON session_preparations BEGIN SELECT RAISE(ABORT, 'test start failure'); END")
        try {
            driver.click(begin)
            driver.awaitText(text(R.string.preparation_error))
            assertEquals(PreparationStatus.OPEN, repo.preparations.observeSession(id, PreparationKind.COOLDOWN).first()!!.progress.status)
            assertTrue(driver.allText().contains(begin))
        } finally {
            db.execSQL("DROP TRIGGER IF EXISTS fail_cooldown_start")
        }
        driver.click(begin)
        awaitCheck()
        assertEquals(PreparationStatus.RUNNING, repo.preparations.observeSession(id, PreparationKind.COOLDOWN).first()!!.progress.status)
    }
    @Test fun previewNeverStartsOrResumesAcrossRecreation() = runBlocking {
        val id = seed(); open(id)
        driver.awaitText(text(R.string.preparation_begin, text(R.string.preparation_warmup)))
        val open = repo.preparations.observeSession(id, PreparationKind.WARMUP).first()!!
        driver.click(open.snapshot.name)
        driver.awaitText(text(R.string.preparation_no_scoring))
        preparationCapture(compose, "refinement-overview")
        compose.activityRule.scenario.recreate()
        driver.awaitText(text(R.string.preparation_no_scoring))
        back()
        assertEquals(open, repo.preparations.observeSession(id, PreparationKind.WARMUP).first())
        repo.preparations.act(id, PreparationKind.WARMUP, PreparationAction.START, System.currentTimeMillis())
        repo.preparations.act(id, PreparationKind.WARMUP, PreparationAction.PAUSE, System.currentTimeMillis())
        val paused = repo.preparations.observeSession(id, PreparationKind.WARMUP).first()!!
        driver.awaitText(text(R.string.preparation_resume, text(R.string.preparation_warmup)))
        driver.click(paused.snapshot.name)
        driver.awaitText(text(R.string.preparation_no_scoring))
        compose.activityRule.scenario.recreate()
        driver.awaitText(text(R.string.preparation_no_scoring))
        back()
        assertEquals(paused, repo.preparations.observeSession(id, PreparationKind.WARMUP).first())
    }
    @Test fun cooldownPreviewKeepsStoredSummaryAndAwards() = runBlocking {
        val id = seed()
        repo.completeSession(id)
        val session = repo.observeSession(id).first()
        val profile = repo.observeProfile().first()
        val sets = repo.observeSessionSets(id).first()
        val rewards = rewardRows()
        open(id)
        driver.awaitText(text(R.string.preparation_begin, text(R.string.preparation_cooldown)))
        val state = repo.preparations.observeSession(id, PreparationKind.COOLDOWN).first()!!
        repeat(2) {
            driver.click(state.snapshot.name)
            driver.awaitText(text(R.string.preparation_no_scoring))
            compose.activityRule.scenario.recreate()
            driver.awaitText(text(R.string.preparation_no_scoring))
            back()
            driver.awaitText(text(R.string.preparation_begin, text(R.string.preparation_cooldown)))
        }
        assertEquals(state, repo.preparations.observeSession(id, PreparationKind.COOLDOWN).first())
        assertEquals(session, repo.observeSession(id).first())
        assertEquals(profile, repo.observeProfile().first())
        assertEquals(sets, repo.observeSessionSets(id).first())
        assertEquals(rewards, rewardRows())
    }
    @Test fun selectorCreateManageBackRetainsOwnerAndOnlyUseChangesTheSession() {
        val id = seed(); open(id)
        driver.awaitText(text(R.string.preparation_begin, text(R.string.preparation_warmup)))
        val before = runBlocking { repo.preparations.observeSession(id, PreparationKind.WARMUP).first() }
        offerMenu(); driver.click(text(R.string.preparation_change))
        driver.awaitText(text(R.string.preparation_use_kind, text(R.string.preparation_warmup)))
        driver.click(text(R.string.preparation_none))
        driver.click(text(R.string.preparation_create))
        driver.awaitText(text(R.string.preparation_save))
        // Creation with no edits goes back without mutating the selector or its session owner.
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        driver.awaitText(text(R.string.preparation_use_kind, text(R.string.preparation_warmup)))
        driver.click(text(R.string.preparation_manage))
        driver.awaitText(text(R.string.preparation_library))
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        driver.awaitText(text(R.string.preparation_use_kind, text(R.string.preparation_warmup)))
        assertEquals(before, runBlocking { repo.preparations.observeSession(id, PreparationKind.WARMUP).first() })
        // Back cancels the draft choice.
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        driver.awaitText(text(R.string.preparation_begin, text(R.string.preparation_warmup)))
        assertEquals(before, runBlocking { repo.preparations.observeSession(id, PreparationKind.WARMUP).first() })
        offerMenu(); driver.click(text(R.string.preparation_change))
        driver.awaitText(text(R.string.preparation_use_kind, text(R.string.preparation_warmup)))
        driver.click(text(R.string.preparation_none))
        driver.click(text(R.string.preparation_manage))
        driver.awaitText(text(R.string.preparation_library))
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        driver.awaitText(text(R.string.preparation_use_kind, text(R.string.preparation_warmup)))
        driver.click(text(R.string.preparation_use_kind, text(R.string.preparation_warmup)))
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
        preparationCapture(compose, "refinement-cooldown-offer")
        driver.click(text(R.string.preparation_begin, text(R.string.preparation_cooldown)))
        awaitCheck()
        preparationCapture(compose, "refinement-cooldown-runner")
        val deadline = repo.preparations.observeSession(id, PreparationKind.COOLDOWN).first()!!.progress.deadlineMs
        compose.activityRule.scenario.recreate()
        awaitCheck()
        assertEquals(deadline, repo.preparations.observeSession(id, PreparationKind.COOLDOWN).first()!!.progress.deadlineMs)
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        driver.awaitText(text(R.string.preparation_leave_title))
        preparationCapture(compose, "refinement-leave-confirmation")
        driver.click(text(R.string.preparation_leave))
        driver.awaitText(text(R.string.preparation_resume, text(R.string.preparation_cooldown)))
        assertEquals(PreparationStatus.PAUSED, repo.preparations.observeSession(id, PreparationKind.COOLDOWN).first()!!.progress.status)
        driver.click(text(R.string.preparation_resume, text(R.string.preparation_cooldown)))
        awaitCheck()
        check().performClick(); driver.settle()
        driver.awaitText(text(R.string.preparation_direction_second))
        check().performClick(); driver.settle()
        driver.awaitText(text(R.string.preparation_return_summary))
        preparationCapture(compose, "refinement-cooldown-complete")
        driver.click(text(R.string.preparation_return_summary))
        driver.awaitText(text(R.string.preparation_finished, text(R.string.preparation_cooldown)))
        preparationCapture(compose, "refinement-summary-terminal")
        driver.click(text(R.string.preparation_view))
        driver.awaitText(text(R.string.preparation_return_summary))
        driver.click(text(R.string.preparation_return_summary))
        driver.awaitText(text(R.string.preparation_finished, text(R.string.preparation_cooldown)))
        assertEquals(session, repo.observeSession(id).first())
        assertEquals(profile, repo.observeProfile().first())
        assertEquals(sets, repo.observeSessionSets(id).first())
        assertEquals(beforeRows, rewardRows())
    }
    @Test fun workoutDraftSurvivesNestedSavedRoutineManagementCancellationAndRecreation() = runBlocking {
        val catalogue = repo.observeExercises().first()
        val bench = catalogue.first { it.name == "Bench Press" }
        val cool = repo.preparations.saveRoutine(PreparationRoutine(kind = PreparationKind.COOLDOWN,
            name = "Opposite cooldown retained", steps = PreparationStarters.templates(PreparationKind.COOLDOWN, catalogue)[0].steps))
        val presetId = repo.savePreset(null, "Navigation draft original", "Original note", null,
            listOf(Repository.PresetDraftEntry(bench.id, 1, 5, 20.0)),
            mapOf(PreparationKind.COOLDOWN to PreparationChoice(PreparationMode.SAVED, cool)))
        val original = repo.observePresets().first().single { it.id == presetId }
        driver.tab("Train")
        driver.awaitText(original.name)
        driver.click(original.name)
        driver.awaitText(app.getString(R.string.ritedetail_edit_button))
        driver.click(app.getString(R.string.ritedetail_edit_button))
        driver.awaitText(text(R.string.preseteditor_save_button))
        driver.awaitText(bench.name)
        setField(text(R.string.preseteditor_name_label), "Unsaved navigation workout")
        setField(app.getString(R.string.preseteditor_note_label), "Unsaved note retained")
        driver.click(bench.name)
        setField(app.getString(R.string.preseteditor_sets_label), "3")
        setField(app.getString(R.string.preseteditor_reps_label), "7")
        driver.click(app.getString(R.string.preparation_warmup))
        driver.awaitText(text(R.string.preparation_edit_copy))
        compose.activityRule.scenario.recreate()
        driver.awaitText(text(R.string.preparation_use_kind, text(R.string.preparation_warmup)))
        compose.onNodeWithText(app.getString(R.string.pushedheader_back_chip)).assertIsDisplayed()
        preparationCapture(compose, "refinement-task2-nav-selector")
        driver.click(text(R.string.preparation_edit_copy))
        driver.awaitText(text(R.string.preparation_seconds))
        compose.activityRule.scenario.recreate()
        driver.awaitText(text(R.string.preparation_seconds))
        compose.onNodeWithText(app.getString(R.string.pushedheader_back_chip)).assertIsDisplayed()
        // Force the recreated window's next draw before reading its real framebuffer.
        compose.activityRule.scenario.onActivity { it.window.decorView.requestLayout(); it.window.decorView.invalidate() }
        driver.settle(8)
        Thread.sleep(300)
        preparationCapture(compose, "refinement-task2-nav-editor")
        setField(text(R.string.preparation_name), "Created inside workout draft")
        // Actual root NavHost recreation while the workout owner sits below selector and creator.
        compose.activityRule.scenario.recreate()
        driver.awaitText(text(R.string.preparation_seconds))
        compose.onNodeWithContentDescription(text(R.string.preparation_name)).assertTextEquals("Created inside workout draft")
        driver.click(text(R.string.preparation_save))
        driver.awaitText(text(R.string.preparation_use_kind, text(R.string.preparation_warmup)))
        val created = withTimeout(10000) { repo.preparations.observeRoutines(PreparationKind.WARMUP).first { rows -> rows.any { it.name == "Created inside workout draft" } } }
            .single { it.name == "Created inside workout draft" }
        driver.awaitText(created.name)
        driver.click(created.name)
        driver.click(text(R.string.preparation_manage))
        driver.awaitText(text(R.string.preparation_library))
        compose.activityRule.scenario.recreate()
        driver.awaitText(text(R.string.preparation_library))
        preparationCapture(compose, "refinement-task2-nav-library")
        back()
        driver.awaitText(text(R.string.preparation_use_kind, text(R.string.preparation_warmup)))
        driver.click(text(R.string.preparation_use_kind, text(R.string.preparation_warmup)))
        driver.awaitText(text(R.string.preseteditor_save_button))
        compose.onNodeWithContentDescription(text(R.string.preseteditor_name_label)).assertTextEquals("Unsaved navigation workout")
        compose.onNodeWithContentDescription(app.getString(R.string.preseteditor_note_label)).assertTextEquals("Unsaved note retained")
        compose.onNodeWithText(created.name).assertExists()
        compose.onNodeWithText("Opposite cooldown retained").assertExists()
        // Cancel changes to the opposite kind; neither attachment nor the working draft is saved yet.
        driver.click(app.getString(R.string.preparation_cooldown))
        driver.awaitText(text(R.string.preparation_use_kind, text(R.string.preparation_cooldown)))
        driver.click(text(R.string.preparation_none))
        back()
        driver.awaitText(text(R.string.preseteditor_save_button))
        compose.onNodeWithText("Opposite cooldown retained").assertExists()
        compose.onNodeWithText(created.name).assertExists()
        assertEquals(original, repo.observePresets().first().single { it.id == presetId })
        driver.click(app.getString(R.string.preparation_cooldown))
        driver.awaitText(text(R.string.preparation_use_kind, text(R.string.preparation_cooldown)))
        driver.click(text(R.string.preparation_none))
        driver.click(text(R.string.preparation_use_kind, text(R.string.preparation_cooldown)))
        driver.awaitText(text(R.string.preseteditor_save_button))
        compose.onNodeWithText(created.name).assertExists()
        driver.click(text(R.string.preseteditor_save_button))
        // Await save completion/navigation, not an intermediate combine emission of name and old attachments.
        driver.awaitText(app.getString(R.string.ritedetail_edit_button))
        val saved = repo.observePresets().first().single { it.id == presetId }
        assertEquals("Unsaved navigation workout", saved.name)
        assertEquals("Unsaved note retained", saved.note)
        assertEquals(3, saved.entries.single().targetSets)
        assertEquals(7, saved.entries.single().targetReps)
        assertEquals(20.0, saved.entries.single().targetWeightKg!!, 0.0)
        assertEquals(PreparationChoice(PreparationMode.SAVED, created.id), saved.preparation[PreparationKind.WARMUP])
        assertEquals(PreparationChoice(PreparationMode.NONE), saved.preparation[PreparationKind.COOLDOWN])
    }
    private fun back() { compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }; driver.settle() }
    private fun setField(description: String, value: String) {
        // Like FlowDriver.click, use the control's accessibility action with the root's paused animation clock.
        compose.onNodeWithContentDescription(description).performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString(value)) }
        driver.settle()
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
        awaitCheck()
        assertEquals(prefs.getLong("deadline", 0), repo.preparations.observeSession(id, PreparationKind.COOLDOWN).first()!!.progress.deadlineMs)
        assertEquals(prefs.getString("signature", null), resultSignature(id))
        if (phase != "seed") {
            check().performClick(); driver.settle()
            driver.awaitText(text(R.string.preparation_direction_second))
            check().performClick(); driver.settle()
            driver.awaitText(text(R.string.preparation_return_summary))
            driver.click(text(R.string.preparation_return_summary))
            driver.awaitText(text(R.string.preparation_finished, text(R.string.preparation_cooldown)))
            assertEquals(prefs.getString("signature", null), resultSignature(id))
        }
    }
    private suspend fun resultSignature(id: Long) = listOf(repo.observeSession(id).first().toString(),
        repo.observeProfile().first().toString(), repo.observeSessionSets(id).first().toString(), rewardRows().toString()).joinToString("\n")
}
