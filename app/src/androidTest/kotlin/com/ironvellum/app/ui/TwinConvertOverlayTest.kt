package com.ironvellum.app.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.AutoBackup
import com.ironvellum.app.data.IronvellumDatabase
import com.ironvellum.app.data.Repository
import com.ironvellum.app.data.SeededDb
import com.ironvellum.app.data.db.PresetEntity
import com.ironvellum.app.data.db.PresetEntryEntity
import com.ironvellum.app.ui.settings.TwinConvertOverlay
import com.ironvellum.app.ui.settings.TwinConvertViewModel
import com.ironvellum.app.ui.theme.IronvellumTheme
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TwinConvertOverlayTest {
    @get:Rule val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = File(context.cacheDir, "twin-convert-overlay-test")
    private val isolatedContext = object : ContextWrapper(context) {
        override fun getFilesDir(): File = files
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            context.getSharedPreferences("twin_convert_overlay_test_$name", mode)
    }
    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository
    private var presetId = 0L
    private var baseId = 0L
    private var targetId = 0L

    @Before
    fun setUp() = runBlocking {
        files.deleteRecursively()
        files.mkdirs()
        isolatedContext.getSharedPreferences("auto_backup", Context.MODE_PRIVATE).edit().clear().commit()
        SeededDb.fresh(context, DB_NAME)
        db = IronvellumDatabase.create(context, DB_NAME)
        repo = Repository(db)
        repo.ensureSeeded()
        baseId = requireNotNull(repo.exerciseIdByName("Pull-up"))
        targetId = requireNotNull(repo.exerciseIdByName("Weighted Pull-up"))
        presetId = db.presetDao().insertPreset(PresetEntity(name = "Pull day", note = "", scheduledDay = 2))
        db.presetDao().insertEntries(listOf(
            PresetEntryEntity(presetId = presetId, exerciseId = baseId, targetSets = 4, targetReps = 6,
                targetWeightKg = 12.5, modifiers = "", position = 0),
        ))
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(DB_NAME)
        files.deleteRecursively()
        context.deleteSharedPreferences("twin_convert_overlay_test_auto_backup")
        AutoBackup.state(context)
    }

    @Test
    fun routineOnlyPreviewRequiresConfirmationAndCreatesBackup() {
        val vm = TwinConvertViewModel(repo, isolatedContext)
        compose.setContent { IronvellumTheme { TwinConvertOverlay(vm, onDismiss = {}) } }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Pull day")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Saved rites · 1 entry").assertIsDisplayed()
        compose.onNodeWithText("Pull-up → Weighted Pull-up").assertIsDisplayed()
        compose.onNodeWithText("Workout history").assertDoesNotExist()
        ShotRecorder.take(compose, "twin-convert-routine-preview")
        compose.mainClock.autoAdvance = true
        compose.onNode(hasText("Move exercises") and hasClickAction()).performClick()
        compose.onNodeWithText("Move these exercises?").assertIsDisplayed()
        assertTrue(files.resolve("backups").listFiles().isNullOrEmpty())
        assertEquals(baseId, runBlocking { db.presetDao().presetWithEntries(presetId)!!.entries.single().exerciseId })
        compose.onNode(hasText("Move exercises") and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("Moved 1 saved rite entry.")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Moved 1 saved rite entry.").performScrollTo().assertIsDisplayed()
        assertEquals(targetId, runBlocking { db.presetDao().presetWithEntries(presetId)!!.entries.single().exerciseId })
        val backups = files.resolve("backups").listFiles().orEmpty()
        assertEquals(1, backups.size)
        assertTrue(backups.single().readText().contains("Pull-up"))
    }

    private companion object { const val DB_NAME = "twin_convert_overlay_test.db" }
}
