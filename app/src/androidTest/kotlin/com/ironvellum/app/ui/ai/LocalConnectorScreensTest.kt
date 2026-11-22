package com.ironvellum.app.ui.ai

import android.app.Activity
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ResultReceiver
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.IntentCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.IronvellumDatabase
import com.ironvellum.app.data.IncomingAiWorkout
import com.ironvellum.app.data.Repository
import com.ironvellum.app.data.Units
import com.ironvellum.app.data.Words
import com.ironvellum.app.data.db.ExerciseEntity
import com.ironvellum.app.data.localconnector.LocalConnectorRepository
import com.ironvellum.app.domain.LoadUnit
import com.ironvellum.app.domain.Loads
import com.ironvellum.app.domain.LocalConnectorFiles
import com.ironvellum.app.domain.MAX_PROPOSAL_BYTES
import com.ironvellum.app.ui.ShotRecorder
import com.ironvellum.app.ui.Routes
import com.ironvellum.app.ui.train.PresetsViewModel
import com.ironvellum.app.ui.train.RiteDetailScreen
import com.ironvellum.app.ui.settings.shareFile
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTheme
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.SECONDS
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.job
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Picker results are controlled. Streams, parser, review, save, receipt and Room use production code. */
class LocalConnectorScreensTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val oldWords = Words.themed

    @Test fun pcGuideCopiesStaticSetupRequestAndKeepsTrainingActionsAvailable() {
        verifyPcGuide(false)
    }

    @Test fun themedPcGuideCopiesTheLocalizedSetupRequest() {
        verifyPcGuide(true)
    }

    private fun verifyPcGuide(themed: Boolean) {
        open(light = themed)
        click("Using AI on your PC")
        click("Copy setup instructions")
        find("Setup instructions copied").assertIsDisplayed()
        val clipboard = compose.activity.getSystemService(android.content.ClipboardManager::class.java)
        val guide = "https://github.com/AlexMollard/Ironvellum/blob/main/tools/local_connector/AGENT_SETUP.md"
        val key = if (themed) com.ironvellum.app.R.string.local_ai_pc_request__t else com.ironvellum.app.R.string.local_ai_pc_request
        val expected = context.getString(key, guide)
        compose.runOnIdle { assertEquals(expected, clipboard.primaryClip?.getItemAt(0)?.text?.toString()) }
        if (!themed) {
            click("Paste workout code")
            find("Paste workout code here").assertIsDisplayed()
        }
    }

    @Test fun abandoningInvalidPasteClearsItAndColdRestoringPasteOffersRetry() {
        checkColdImportRestore(incoming = false) {
            click("Paste workout code")
            val abandoned = "{\"abandoned\":"
            find("Paste workout code here").performTextInput(abandoned)
            click("Review workout")
            find("Invalid draft file").assertIsDisplayed()
            find(abandoned).assertIsDisplayed()
            compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            click("Paste workout code")
            compose.onNodeWithText(abandoned).assertDoesNotExist()
            compose.onNodeWithText("Invalid draft file").assertDoesNotExist()
            find("Review workout").assertIsNotEnabled()
            find("Paste workout code here").performTextInput("{\"unsaved\":")
            "{\"unsaved\":"
        }
    }

    @Test fun coldRestoringAnInboundRouteLosesItsReviewAndOffersRetry() {
        checkColdImportRestore(incoming = true) {
            find("Cold restore fixture").assertIsDisplayed()
            find("Workout name").performTextReplacement("Unsaved edits")
            "Unsaved edits"
        }
    }

    private fun checkColdImportRestore(incoming: Boolean, arrange: () -> String) {
        val restoration = StateRestorationTester(compose)
        val token = "fixture-token"
        var nextRoute: String? = if (incoming) Routes.aiWorkoutImport(token) else Routes.LOCAL_AI_TOOLS
        var pending = if (incoming) IncomingAiWorkout.source(
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, portableCode("Cold restore fixture")),
        ) else null
        var activeModel: LocalConnectorViewModel? = null
        open(restoration = restoration) {
            // Simulate a dead process by discarding both the nav back stack's stores and the pending source.
            val owner = remember { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
            DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                val nav = rememberNavController()
                NavHost(nav, startDestination = "base") {
                    composable("base") {
                        LaunchedEffect(Unit) { nextRoute?.let { nextRoute = null; nav.navigate(it) } }
                    }
                    val destinations = listOf(Routes.LOCAL_AI_TOOLS, Routes.AI_WORKOUT_IMPORT)
                    destinations.forEach { route ->
                        composable(route) { entry ->
                            val receive: (() -> IncomingAiWorkout.Source?)? =
                                if (route == Routes.AI_WORKOUT_IMPORT) {
                                    {
                                        check(entry.arguments?.getString("token") == token)
                                        pending.also { pending = null }
                                    }
                                } else null
                            LocalConnectorScreen(repo, Repository(db).portableWorkout, Repository(db).observeExercises(),
                                onBack = {}, onConnections = {}, onCloudDrafts = {}, onOpenWorkout = {}, receive = receive)
                            SideEffect { activeModel = ViewModelProvider(entry)[LocalConnectorViewModel::class.java] }
                        }
                    }
                }
            }
        }
        val transientText = arrange()
        val original = compose.runOnIdle { checkNotNull(activeModel) }
        restoration.emulateSavedInstanceStateRestore()
        find("The import needs to be opened again").assertIsDisplayed()
        compose.runOnIdle {
            assertNotSame(original, activeModel)
            assertEquals("", checkNotNull(activeModel).pasteText)
        }
        compose.onNodeWithText(transientText).assertDoesNotExist()
        compose.onNodeWithText("Cold restore fixture").assertDoesNotExist()
        compose.onNodeWithText("Workout saved").assertDoesNotExist()
        compose.onNodeWithText("Save workout").assertDoesNotExist()
        assertEquals(0, count("presets"))
        assertEquals(0, count("portable_workout_receipts"))
        click("Paste workout code")
        find("Review workout").assertIsNotEnabled()
    }

    private fun openEditableFixture() {
        open(withDetail = true)
        click("Paste workout code")
        find("Paste workout code here").performTextInput(portableCode("Fixture"))
        click("Review workout")
    }

    @Test fun underflowDisablesSaveAndRemainsCorrectable() {
        openEditableFixture()
        find("Reps").performTextReplacement("19")
        find("Load (kg)").performTextReplacement("1e-400")
        find("Save workout").assertIsNotEnabled()
        find("Load (kg)").performTextReplacement("22.125")
        click("Save workout")
        find("Workout saved").assertIsDisplayed()
        db.openHelper.readableDatabase.query("SELECT targetReps, targetWeightKg FROM preset_entries").use {
            assertTrue(it.moveToFirst())
            assertEquals(19, it.getInt(0))
            assertEquals(22.125, it.getDouble(1), 0.0)
        }
    }

    @Test fun pastedNumericFieldsNeverSaveAValidPrefix() {
        openEditableFixture()
        find("Sets").performTextReplacement("00012")
        find("Load (kg)").performTextReplacement("1." + "0".repeat(138) + "invalid")
        find("Save workout").assertIsNotEnabled()
        find("Load (kg)").performTextReplacement("40")
        click("Save workout")
        find("Workout saved").assertIsDisplayed()
        db.openHelper.readableDatabase.query("SELECT targetSets FROM preset_entries").use {
            assertTrue(it.moveToFirst())
            assertEquals(12, it.getInt(0))
        }
    }

    @Test fun pastedWorkoutStaysEditableUntilExplicitSave() {
        open(withDetail = true)
        click("Paste workout code")
        find("Review workout").assertIsNotEnabled()
        shot("inbound_paste_dark_plain")
        val code = find("Paste workout code here")
        code.performTextInput("{")
        click("Review workout")
        find("Invalid draft file").assertIsDisplayed()
        val source = portableCode("Upper A", "cable press")
        code.performTextReplacement(source)
        click("Review workout")
        find("Choose movement").assertIsDisplayed()
        find("Save workout").assertIsNotEnabled()
        shot("inbound_unmatched_dark_plain")
        assertEquals(0, count("presets"))
        click("Choose movement")
        click("Bench Press")
        find("40.01234").assertIsDisplayed()
        shot("inbound_matched_dark_plain")
        find("Sets").performTextReplacement("4")
        find("Reps").performTextReplacement("9")
        find("Load (kg)").performTextReplacement("41.125")
        assertEquals(0, count("presets"))
        click("Save workout")
        find("Workout saved").assertIsDisplayed()
        assertEquals(1, count("presets"))
        assertEquals(1, count("portable_workout_receipts"))
        val storedId = db.openHelper.readableDatabase.query("SELECT id, scheduledDay FROM presets").use {
            assertTrue(it.moveToFirst())
            assertTrue(it.isNull(1))
            it.getLong(0)
        }
        val targets = runBlocking { checkNotNull(db.presetDao().presetWithEntries(storedId)).entries.single() }
        assertEquals(4, targets.targetSets)
        assertEquals(9, targets.targetReps)
        assertEquals(41.125, checkNotNull(targets.targetWeightKg), 0.0)
        click("View workout")
        find("Bench Press").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        val sourceUri = uri(file(source.encodeToByteArray()))
        select(sourceUri)
        find("Already saved").assertIsDisplayed()
        click("Done")
        runBlocking { db.presetDao().deletePreset(storedId) }
        select(sourceUri)
        find("Imported before").assertIsDisplayed()
        compose.onNodeWithText("View workout").assertDoesNotExist()
        compose.onNodeWithText("Save workout").assertDoesNotExist()
        assertEquals(0, count("presets"))
        assertEquals(1, count("portable_workout_receipts"))
    }
    private val oldBenchUnit = Units.overrideFor("bench press")
    private val files = mutableListOf<File>()
    private lateinit var db: IronvellumDatabase
    private lateinit var repo: LocalConnectorRepository
    private val now = 1_795_308_000_000L
    @Volatile private var failPresetNameLookup = false
    private val alternateShots get() = InstrumentationRegistry.getArguments().getString("aiAlternateShots") == "true"
    private var nextSelection: Uri? = null
    private var lastPickerIntent: Intent? = null
    private val picker = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                lastPickerIntent = contract.createIntent(context, input)
                val selected = nextSelection
                dispatchResult(requestCode, if (selected == null) Activity.RESULT_CANCELED else Activity.RESULT_OK,
                    Intent().setData(selected))
            }
        }
    }

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, IronvellumDatabase::class.java)
            .setQueryCallback(object : RoomDatabase.QueryCallback {
                override fun onQuery(sqlQuery: String, bindArgs: List<Any?>) {
                    if (failPresetNameLookup && sqlQuery == "SELECT name FROM presets WHERE id = ?") {
                        throw android.database.sqlite.SQLiteException("Preset availability read unavailable")
                    }
                }
            }, Executor { it.run() }).build()
        repo = LocalConnectorRepository(db) { now }
        db.exerciseDao().insertAll(listOf("Bench Press", "Barbell Row").map {
            ExerciseEntity(name = it, muscleGroup = "Chest", isWeighted = true)
        })
        Unit
    }

    @After fun close() {
        compose.activityRule.scenario.close()
        Words.setThemed(context, oldWords)
        Units.setExercise(context, "bench press", oldBenchUnit)
        files.forEach(File::delete)
        db.close()
    }

    private fun open(
        light: Boolean = false,
        testContext: Context? = null,
        fontScale: Float? = null,
        withDetail: Boolean = false,
        restoration: StateRestorationTester? = null,
        customContent: (@Composable () -> Unit)? = null,
    ) {
        compose.activityRule.scenario.onActivity { activity ->
            Words.setThemed(context, light)
            ShotRecorder.darkWindow(activity)
            if (light) WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
        val screen: @Composable () -> Unit = {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides if (fontScale == null) density else Density(density.density, fontScale),
                LocalActivityResultRegistryOwner provides picker,
                LocalContext provides (testContext ?: LocalContext.current),
            ) {
                IronvellumTheme(light) {
                    Box(Modifier.fillMaxSize().background(IronvellumColors.Abyss).windowInsetsPadding(WindowInsets.safeDrawing)) {
                        if (customContent != null) customContent()
                        else if (withDetail) DetailNavigation() else {
                            LocalConnectorScreen(repo, Repository(db).portableWorkout, Repository(db).observeExercises(),
                                onBack = {}, onConnections = {}, onCloudDrafts = {}, onOpenWorkout = {})
                        }
                    }
                }
            }
        }
        if (restoration == null) compose.setContent(screen) else restoration.setContent(screen)
    }

    @Composable
    private fun DetailNavigation() {
        val nav = rememberNavController()
        NavHost(nav, startDestination = Routes.LOCAL_AI_TOOLS) {
            composable(Routes.LOCAL_AI_TOOLS) {
                LocalConnectorScreen(repo, Repository(db).portableWorkout, Repository(db).observeExercises(),
                    onBack = {}, onConnections = {}, onCloudDrafts = {},
                    onOpenWorkout = { nav.navigate(Routes.riteDetail(it)) })
            }
            composable(Routes.RITE_DETAIL, listOf(navArgument("presetId") { type = NavType.LongType })) { entry ->
                val model: PresetsViewModel = viewModel(factory = viewModelFactory {
                    initializer { PresetsViewModel(Repository(db), context) }
                })
                RiteDetailScreen(checkNotNull(entry.arguments).getLong("presetId"),
                    onBack = { nav.popBackStack() }, onEdit = {}, onStartSession = {}, viewModel = model)
            }
        }
    }

    private fun file(bytes: ByteArray = byteArrayOf()): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        return File.createTempFile("local-ui-", ".json", dir).apply {
            writeBytes(bytes)
            files.add(this)
        }
    }

    private fun uri(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.exports", file)

    private fun draft(foreign: Boolean = false, expired: Boolean = false, loadKg: String = "40.01234"): Uri = runBlocking {
        val contextFile = repo.exportContext()
        uri(file(buildJsonObject {
            put("type", "ironvellum.workout-draft")
            put("schemaVersion", 1)
            put("datasetId", if (foreign) UUID.randomUUID().toString() else contextFile.summary.datasetId)
            put("snapshotId", contextFile.summary.snapshotId)
            put("contextSha256", LocalConnectorFiles.sha256(contextFile.bytes))
            put("draftId", UUID.randomUUID().toString())
            put("createdAtMs", now - 1000)
            put("expiresAtMs", if (expired) now else now + 1000)
            put("sender", "Training assistant")
            put("draft", Json.parseToJsonElement("""{"schemaVersion":1,"name":"Upper A","warmup":"AUTO","cooldown":"NONE",
                "entries":[{"exerciseKey":"bench press","sets":3,"reps":8,"loadKg":$loadKg},
                {"exerciseKey":"barbell row","sets":3,"reps":10,"loadKg":35}]}"""))
        }.toString().encodeToByteArray()))
    }

    private fun find(text: String) = compose.onNodeWithText(text).also {
        compose.waitUntil(8_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun click(text: String) = find(text).let { node ->
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
    }

    private fun checkLocalizedCounts(tag: String, themed: Boolean) {
        val configuration = android.content.res.Configuration(context.resources.configuration).apply {
            setLocale(java.util.Locale.forLanguageTag(tag))
        }
        val localized = context.createConfigurationContext(configuration)
        open(light = themed, testContext = localized, fontScale = 2f)
        shot("tools_${tag}_200")
        click(localized.getString(com.ironvellum.app.R.string.local_ai_share_training))
        find(localized.getString(com.ironvellum.app.R.string.local_ai_sets)).performScrollTo().assertIsDisplayed()
        assertEquals(3, compose.onAllNodesWithText("0").fetchSemanticsNodes().size)
        shot("v2_${tag}_sets_200")
        val names = if (themed) com.ironvellum.app.R.string.local_ai_names_included__t else com.ironvellum.app.R.string.local_ai_names_included
        find(localized.getString(names)).performScrollTo().assertIsDisplayed()
        shot("v2_${tag}_names_200")
        click(localized.getString(com.ironvellum.app.R.string.local_ai_raw_actions))
        val privateText = if (themed) com.ironvellum.app.R.string.local_ai_private_hint__t
            else com.ironvellum.app.R.string.local_ai_private_hint
        find(localized.getString(privateText)).performScrollTo().assertIsDisplayed()
        shot("v2_${tag}_privacy_200")
        find(localized.getString(com.ironvellum.app.R.string.local_ai_raw_save)).performScrollTo().assertIsDisplayed()
        shot("v2_${tag}_actions_200")
    }

    @Test fun spanishPlainPreviewShowsCountsAndPrivacyDetails() =
        checkLocalizedCounts("es", false)

    @Test fun portugueseThemedPreviewShowsCountsAndPrivacyDetails() =
        checkLocalizedCounts("pt-BR", true)

    private fun shot(name: String) {
        val layout = InstrumentationRegistry.getArguments().getString("aiLayout")?.let { "_$it" }.orEmpty()
        ShotRecorder.take(compose, "local_connector_${name}$layout")
        compose.mainClock.autoAdvance = true
    }

    private fun count(table: String) = db.openHelper.readableDatabase.query("SELECT count(*) FROM $table").use {
        it.moveToFirst()
        it.getInt(0)
    }

    private fun select(uri: Uri?, label: String = if (Words.themed) "Open rite file" else "Open workout file") {
        nextSelection = uri
        click(label)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, lastPickerIntent?.action)
    }

    @Test fun pickerCancelAndNotNowLeavePresetsAndReceiptsEmpty() {
        open()
        select(null)
        find("Add an AI workout").assertIsDisplayed()
        select(draft())
        find("Review workout").assertIsDisplayed()
        assertEquals(0, count("presets"))
        assertEquals(0, count("local_connector_receipts"))
        shot("review_dark_plain")
        if (InstrumentationRegistry.getArguments().containsKey("aiLayout")) {
            for (label in listOf("Save workout", "Not now")) {
                val action = find(label)
                if (!action.isDisplayed()) action.performScrollTo()
                action.assertIsDisplayed()
            }
            shot("review_actions_dark_plain")
        }
        click("Not now")
        find("Add an AI workout").assertIsDisplayed()
        assertEquals(0, count("presets"))
        assertEquals(0, count("local_connector_receipts"))
    }

    @Test fun saveUsesReviewedBytesAndRepeatImportReturnsOneReceiptEvenAfterDeletion() {
        val selected = draft()
        val original = context.contentResolver.openInputStream(selected)!!.use { it.readBytes() }
        Units.setExercise(context, "bench press", LoadUnit.LB)
        val light = !alternateShots
        val palette = if (light) "vellum_themed" else "dark_plain"
        val saveLabel = if (light) "Save rite" else "Save workout"
        val viewLabel = if (light) "View rite" else "View workout"
        open(light = light)
        select(selected)
        find("Suggested by Training assistant · Unverified sender").assertIsDisplayed()
        find("40.01234").performScrollTo().assertIsDisplayed()
        find(Loads.withUnit(40.01234, LoadUnit.LB)).performScrollTo().assertIsDisplayed()
        find("Warmup · Automatic").performScrollTo().assertIsDisplayed()
        assertEquals(0, count("presets"))
        if (!alternateShots) shot("review_vellum_themed")
        // The provider can change the source after review. Save must use the bytes already reviewed.
        context.contentResolver.openOutputStream(selected, "wt")!!.use { it.write(byteArrayOf(0)) }
        click(saveLabel)
        find(if (light) "Rite saved" else "Workout saved").assertIsDisplayed()
        assertEquals(1, count("local_connector_receipts"))
        assertEquals(1, count("presets"))
        assertEquals(0, count("sessions"))
        val storedId = db.openHelper.readableDatabase.query("SELECT id, scheduledDay FROM presets").use {
            assertTrue(it.moveToFirst())
            assertTrue(it.isNull(1))
            it.getLong(0)
        }
        if (!alternateShots) shot("saved_vellum_themed")
        click("Done")
        // Reopen the same receipt with the original file bytes restored, with and without its preset.
        context.contentResolver.openOutputStream(selected, "wt")!!.use { it.write(original) }
        runBlocking {
            val dao = db.presetDao()
            dao.updatePreset(checkNotNull(dao.presetWithEntries(storedId)).preset.copy(name = "Upper A edited"))
        }
        select(selected)
        find("Already saved").assertIsDisplayed()
        find("Upper A edited").assertIsDisplayed()
        compose.onNodeWithText("2 movements · 6 sets").assertDoesNotExist()
        find(viewLabel).assertIsDisplayed()
        compose.onNodeWithText(saveLabel).assertDoesNotExist()
        shot("repeated_$palette")
        assertEquals(1, count("presets"))
        click("Done")
        runBlocking { db.presetDao().deletePreset(storedId) }
        select(selected)
        find("Imported before").assertIsDisplayed()
        compose.onNodeWithText(viewLabel).assertDoesNotExist()
        compose.onNodeWithText(saveLabel).assertDoesNotExist()
        val deleted = if (light) com.ironvellum.app.R.string.local_ai_deleted__t else com.ironvellum.app.R.string.local_ai_deleted
        find(context.getString(deleted)).assertIsDisplayed()
        assertEquals(0, count("presets"))
        assertEquals(1, count("local_connector_receipts"))
        shot("deleted_$palette")
    }

    @Test fun viewWorkoutOpensTheSavedPresetDetailAndBackReturnsToAiTools() {
        open(withDetail = true)
        select(draft())
        // A failing optional receipt lookup must have no effect on a newly committed save.
        failPresetNameLookup = true
        click("Save workout")
        find("Workout saved").assertIsDisplayed()
        compose.onNodeWithText("Couldn’t save the draft").assertDoesNotExist()
        assertEquals(1, count("local_connector_receipts"))
        shot("saved_dark_plain")
        click("View workout")
        find("Bench Press").performScrollTo().assertIsDisplayed()
        find("Barbell Row").performScrollTo().assertIsDisplayed()
        find("Upper A").assertIsDisplayed()
        shot("view_workout_dark_plain")
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        find("AI tools").assertIsDisplayed()
        assertEquals(1, count("presets"))
    }

    @Test fun rejectedFilesAndProviderErrorsNeverOfferSave() {
        val palette = if (alternateShots) "vellum_themed" else "dark_plain"
        open(light = alternateShots)
        select(uri(file(byteArrayOf(0))))
        find("Invalid draft file").assertIsDisplayed()
        shot("invalid_$palette")
        click("Back to AI tools")
        select(uri(file(ByteArray(MAX_PROPOSAL_BYTES + 1))))
        find("Draft file too large").assertIsDisplayed()
        compose.onNodeWithText(if (alternateShots) "Save rite" else "Save workout").assertDoesNotExist()
        click("Back to AI tools")
        select(draft(foreign = true))
        find("Different training data").assertIsDisplayed()
        click("Back to AI tools")
        select(draft(expired = true))
        find("Draft expired").assertIsDisplayed()
        click("Back to AI tools")
        val missing = file()
        val missingUri = uri(missing)
        assertTrue(missing.delete())
        select(missingUri)
        find("Couldn’t read the file").assertIsDisplayed()
        select(null, "Choose file")
        find("Couldn’t read the file").assertIsDisplayed()
        shot("error_$palette")
        assertEquals(0, count("presets"))
        assertEquals(0, count("local_connector_receipts"))
    }

    @Test fun savingPreviewUsesSameBytesAndCancellationDoesNotGenerateAnotherSnapshot() {
        open()
        click("Share training")
        find("Other apps").performScrollTo().assertIsDisplayed()
        shot("preview_share_dark_plain")
        click("Sharing details · Save a copy")
        val destination = file()
        nextSelection = null
        click("Save a copy")
        val intent = checkNotNull(lastPickerIntent)
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertTrue("The on-device save picker must request only local providers", intent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false))
        assertEquals(0L, destination.length())
        compose.onNodeWithText("Training file saved on this device.").assertDoesNotExist()
        nextSelection = uri(destination)
        click("Save a copy")
        find("Training file saved on this device.").assertIsDisplayed()
        val first = destination.readBytes()
        val hash = db.openHelper.readableDatabase.query("SELECT contextSha256 FROM local_connector_exports").use {
            assertTrue(it.moveToFirst())
            it.getString(0)
        }
        assertEquals(hash, LocalConnectorFiles.sha256(first))
        val second = file()
        nextSelection = uri(second)
        click("Save a copy")
        compose.waitUntil(8_000) { second.length() == first.size.toLong() }
        assertArrayEquals(first, second.readBytes())
        assertEquals(1, count("local_connector_exports"))
    }

    @Test fun unwritableDestinationKeepsPreviewAndDoesNotClaimSuccess() {
        open()
        click("Share training")
        click("Sharing details · Save a copy")
        nextSelection = uri(File(file(), "unwritable.json"))
        click("Save a copy")
        find("Couldn’t save the file. Choose an available location and try again.").assertIsDisplayed()
        compose.onNodeWithText("Training file saved on this device.").assertDoesNotExist()
        assertEquals(1, count("local_connector_exports"))
    }

    @Test fun sharingTheSamePreviewTwiceGivesBothReadersTheRecordedSnapshot() {
        val invitations = LinkedBlockingQueue<Intent>()
        open(testContext = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) { invitations.add(intent) }
        })
        click("Share training")
        click("Sharing details · Save a copy")
        fun share(): Uri {
            click("Share JSON file")
            val chooser = checkNotNull(invitations.poll(5, SECONDS))
            val send = checkNotNull(IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java))
            assertEquals(Intent.ACTION_SEND, send.action)
            assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            return checkNotNull(IntentCompat.getParcelableExtra(send, Intent.EXTRA_STREAM, Uri::class.java))
        }
        val first = share()
        files.add(File(context.cacheDir, "exports/${first.lastPathSegment}"))
        checkNotNull(context.contentResolver.openInputStream(first)).use { reader ->
            val leadingByte = reader.read()
            assertTrue(leadingByte >= 0)
            val second = share()
            assertEquals(first, second)
            val bytes = byteArrayOf(leadingByte.toByte()) + reader.readBytes()
            assertArrayEquals(bytes, checkNotNull(context.contentResolver.openInputStream(second)).use { it.readBytes() })
            db.openHelper.readableDatabase.query("SELECT contextSha256 FROM local_connector_exports").use {
                assertEquals(1, it.count)
                assertTrue(it.moveToFirst())
                assertEquals(it.getString(0), LocalConnectorFiles.sha256(bytes))
            }
        }
    }

    @Test fun aiChooserSharesToAnotherUidAndChangingTheQuestionKeepsEarlierBytes() {
        val invitations = LinkedBlockingQueue<Intent>()
        open(light = alternateShots, testContext = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) { invitations.add(intent) }
        })
        click("Share training")
        val question = find("What would you like help with? (optional)").performScrollTo()
        val palette = if (alternateShots) "vellum_themed" else "dark_plain"
        shot("outbound_$palette")
        find("Copy AI instructions").performScrollTo().assertIsDisplayed()
        shot("outbound_actions_$palette")
        question.performScrollTo()
        question.performTextInput("How has my strength changed?")
        fun share(): Pair<Intent, Uri> {
            click("Other apps")
            val chooser = checkNotNull(invitations.poll(5, SECONDS))
            assertEquals(Intent.ACTION_CHOOSER, chooser.action)
            val send = checkNotNull(IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java))
            val uri = checkNotNull(IntentCompat.getParcelableExtra(send, Intent.EXTRA_STREAM, Uri::class.java))
            assertEquals(Intent.ACTION_SEND, send.action)
            assertEquals(AI_SHARE_MIME, send.type)
            assertEquals(uri, send.clipData?.getItemAt(0)?.uri)
            assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(0, send.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            assertTrue(uri.lastPathSegment!!.endsWith(".md"))
            files.add(File(context.cacheDir, "exports/${uri.lastPathSegment}"))
            return send to uri
        }
        val (send, first) = share()
        val results = LinkedBlockingQueue<Bundle>()
        val recipientPackage = InstrumentationRegistry.getInstrumentation().context.packageName
        fun deliver(grant: Boolean): Bundle {
            val reply = object : ResultReceiver(null) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle) { results.add(resultData) }
            }
            context.startActivity(Intent(send).apply {
                setClassName(recipientPackage, "com.ironvellum.app.data.AiShareTestRecipient")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (!grant) flags = flags and Intent.FLAG_GRANT_READ_URI_PERMISSION.inv()
                putExtra("test_reply", reply)
            })
            return checkNotNull(results.poll(10, SECONDS))
        }
        val denied = deliver(grant = false)
        assertNotEquals(android.os.Process.myUid(), denied.getInt("uid"))
        assertTrue(denied.getString("failure")?.contains("SecurityException") == true)
        val received = deliver(grant = true)
        assertEquals(denied.getInt("uid"), received.getInt("uid"))
        assertNull(received.getString("failure"))
        val bytes = checkNotNull(received.getByteArray("contents"))
        val embedded = bytes.decodeToString().substringAfter("~~~json\n").substringBeforeLast("\n~~~\n")
        assertTrue(bytes.decodeToString().contains("How has my strength changed?"))
        assertEquals(first, share().second)
        question.performScrollTo().performTextReplacement("Suggest one workout")
        val second = share().second
        assertNotEquals(first, second)
        assertArrayEquals(bytes, checkNotNull(context.contentResolver.openInputStream(first)).use { it.readBytes() })
        assertTrue(checkNotNull(context.contentResolver.openInputStream(second)).bufferedReader().use {
            it.readText().contains("Suggest one workout")
        })
        db.openHelper.readableDatabase.query("SELECT contextSha256 FROM local_connector_exports").use {
            assertEquals(1, it.count)
            assertTrue(it.moveToFirst())
            assertEquals(it.getString(0), LocalConnectorFiles.sha256(embedded.encodeToByteArray()))
        }
    }

    @Test fun dismissingAndroidChooserKeepsTheQuestionAndAllowsRetry() {
        open()
        click("Share training")
        val question = "Synthetic chooser retry"
        find("What would you like help with? (optional)").performTextReplacement(question)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val chooserPackage = checkNotNull(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(AI_SHARE_MIME), null)
            .resolveActivity(context.packageManager)).packageName
        repeat(2) {
            click("Other apps")
            compose.waitUntil(8000) { automation.rootInActiveWindow?.packageName == chooserPackage }
            assertTrue(automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
            compose.waitUntil(8000) { automation.rootInActiveWindow?.packageName == context.packageName }
            find(question).assertExists()
        }
        assertEquals(1, count("local_connector_exports"))
    }

    @Test fun chooserCallbackPrioritisesOnlyAnEligibleSelection() {
        val syntheticPrefs = context.getSharedPreferences("synthetic_ai_order", Context.MODE_PRIVATE)
        val receiver = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String?, mode: Int) = syntheticPrefs
        }
        val installed = AiShareTarget.entries.toList()
        fun chosen(target: AiShareTarget) = Intent().putExtra(Intent.EXTRA_CHOSEN_COMPONENT,
            android.content.ComponentName(target.packageName, "SyntheticActivity"))
        syntheticPrefs.edit().clear().commit()
        try {
            rememberAiSelection(receiver, chosen(AiShareTarget.CHATGPT), installed)
            val expected = listOf(AiShareTarget.CHATGPT, AiShareTarget.CLAUDE)
            assertEquals(expected, eligibleAiTargets(receiver, installed))
            rememberAiSelection(receiver, chosen(AiShareTarget.CLAUDE), listOf(AiShareTarget.CHATGPT))
            rememberAiSelection(receiver, Intent(), installed)
            assertEquals(expected, eligibleAiTargets(receiver, installed))
        } finally {
            syntheticPrefs.edit().clear().commit()
        }
    }

    @Test fun unavailableAiAppFallsBackToChooserWithoutRecordingDelivery() = runBlocking {
        val invitations = LinkedBlockingQueue<Intent>()
        val receiver = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) { invitations.add(intent) }
        }
        // The test emulator deliberately has no actual AI providers installed.
        val missing = AiShareTarget.entries.first { it !in eligibleAiTargets(context) }
        val bytes = "Synthetic attachment".encodeToByteArray()
        files.add(File(context.cacheDir, "exports/${AiTrainingPackage.fileName(bytes)}"))
        shareAiPackage(receiver, "Synthetic chooser", missing, bytes)
        val chooser = checkNotNull(invitations.poll(5, SECONDS))
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertNull(IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java)?.`package`)
        assertTrue(invitations.isEmpty())
    }

    @Test fun fixedNameImageSharesStillReplaceContentsAndGrantThePreviewClip() = runBlocking {
        val target = file()
        var send: Intent? = null
        val receiver = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) {
                send = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
            }
        }
        val expected = uri(target)
        for (bytes in listOf(byteArrayOf(1, 2, 3), byteArrayOf(4))) {
            shareFile(receiver, "Card", target.name, bytes, "image/png")
            val intent = checkNotNull(send)
            assertEquals(Intent.ACTION_SEND, intent.action)
            assertEquals("image/png", intent.type)
            assertEquals(target.name, intent.getStringExtra(Intent.EXTRA_TITLE))
            assertEquals(expected, IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            assertEquals(expected, intent.clipData?.getItemAt(0)?.uri)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertArrayEquals(bytes, target.readBytes())
            send = null
        }
    }

    @Test fun longExactLoadLeavesRoomForTheMovementAndTargets() {
        // File validation allows a maximum of 128 bytes for this whole numeric token.
        val exactLoad = "4." + "123456789".repeat(14)
        open(fontScale = 2f)
        select(draft(loadKg = exactLoad))
        find("$exactLoad kg").performScrollTo().assertIsDisplayed()
        shot("review_exact_dark_plain_200")
        find("Bench Press").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Sets")[0].performScrollTo().assertWidthIsAtLeast(65.dp)
        find("Save workout").performScrollTo().assertIsDisplayed()
        find("Not now").performScrollTo().assertIsDisplayed()
        shot("review_actions_dark_plain_200")
    }

    @Test
    @SdkSuppress(minSdkVersion = 29) // ContentResolver.wrap attaches an in-process provider for this test.
    fun cancelledProviderFailureDoesNotReplaceTheNextReview() {
        val slowUri = Uri.parse("content://local-connector-test/delayed")
        val validUri = draft()
        val opened = CountDownLatch(1)
        val releaseFailure = CountDownLatch(1)
        val resolver = ContentResolver.wrap(object : ContentProvider() {
            override fun onCreate() = true
            override fun getType(uri: Uri) = "application/json"
            override fun insert(uri: Uri, values: ContentValues?): Uri? = error("Unexpected write")
            override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = error("Unexpected write")
            override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = error("Unexpected delete")
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sortOrder: String?): Cursor? =
                error("Unexpected query")

            override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor {
                if (uri != slowUri) return checkNotNull(this@LocalConnectorScreensTest.context.contentResolver.openAssetFileDescriptor(uri, mode))
                opened.countDown()
                check(releaseFailure.await(10, SECONDS)) { "Test did not release the first provider" }
                throw FileNotFoundException("The cancelled provider failed after the second selection")
            }
        })
        val wrapped = object : ContextWrapper(context) {
            override fun getContentResolver(): ContentResolver = resolver
        }
        val palette = if (alternateShots) "vellum_themed" else "dark_plain"
        open(light = alternateShots, testContext = wrapped)
        val cancelledRead = try {
            select(slowUri)
            assertTrue("First selection reached the provider", opened.await(5, SECONDS))
            find("Reading and checking the draft file…").assertIsDisplayed()
            shot("checking_draft_$palette")
            val reading = compose.runOnIdle {
                val model = ViewModelProvider(compose.activity)[LocalConnectorViewModel::class.java]
                model.viewModelScope.coroutineContext.job.children.single()
            }
            click("Cancel")
            select(validUri)
            find("Upper A").assertIsDisplayed()
            reading
        } finally {
            releaseFailure.countDown()
        }
        runBlocking { withTimeout(5_000) { cancelledRead.join() } }
        assertTrue(cancelledRead.isCancelled)
        compose.onNodeWithText("Couldn’t read the file").assertDoesNotExist()
        find("Upper A").assertIsDisplayed()
        find(if (alternateShots) "Save rite" else "Save workout").assertIsDisplayed()
        assertEquals(0, count("presets"))
        assertEquals(0, count("local_connector_receipts"))
    }
}
