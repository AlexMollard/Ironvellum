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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.IntentCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
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

    private fun open(light: Boolean = false, testContext: Context? = null, fontScale: Float? = null, withDetail: Boolean = false) {
        compose.activityRule.scenario.onActivity { activity ->
            Words.setThemed(context, light)
            ShotRecorder.darkWindow(activity)
            if (light) WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides if (fontScale == null) density else Density(density.density, fontScale),
                LocalActivityResultRegistryOwner provides picker,
                LocalContext provides (testContext ?: LocalContext.current),
            ) {
                IronvellumTheme(light) {
                    Box(Modifier.fillMaxSize().background(IronvellumColors.Abyss).windowInsetsPadding(WindowInsets.safeDrawing)) {
                        if (withDetail) DetailNavigation() else {
                            LocalConnectorScreen(repo, onBack = {}, onConnections = {}, onCloudDrafts = {}, onOpenWorkout = {})
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun DetailNavigation() {
        val nav = rememberNavController()
        NavHost(nav, startDestination = Routes.LOCAL_AI_TOOLS) {
            composable(Routes.LOCAL_AI_TOOLS) {
                LocalConnectorScreen(repo, onBack = {}, onConnections = {}, onCloudDrafts = {},
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
        click(localized.getString(com.ironvellum.app.R.string.local_ai_preview))
        find(localized.getString(com.ironvellum.app.R.string.local_ai_sets)).performScrollTo().assertIsDisplayed()
        assertEquals(3, compose.onAllNodesWithText("0").fetchSemanticsNodes().size)
        shot("v2_${tag}_sets_200")
        val names = if (themed) com.ironvellum.app.R.string.local_ai_names_included__t else com.ironvellum.app.R.string.local_ai_names_included
        find(localized.getString(names)).performScrollTo().assertIsDisplayed()
        shot("v2_${tag}_names_200")
        click(localized.getString(com.ironvellum.app.R.string.local_ai_sharing_details))
        val privateText = if (themed) com.ironvellum.app.R.string.local_ai_private_hint__t
            else com.ironvellum.app.R.string.local_ai_private_hint
        find(localized.getString(privateText)).performScrollTo().assertIsDisplayed()
        shot("v2_${tag}_privacy_200")
        find(localized.getString(com.ironvellum.app.R.string.local_ai_save_file)).performScrollTo().assertIsDisplayed()
        shot("v2_${tag}_actions_200")
    }

    @Test fun spanishPlainPreviewShowsCountsAndPrivacyDetails() =
        checkLocalizedCounts("es", false)

    @Test fun portugueseThemedPreviewShowsCountsAndPrivacyDetails() =
        checkLocalizedCounts("pt-BR", true)

    private fun shot(name: String) {
        ShotRecorder.take(compose, "local_connector_$name")
        compose.mainClock.autoAdvance = true
    }

    private fun count(table: String) = db.openHelper.readableDatabase.query("SELECT count(*) FROM $table").use {
        it.moveToFirst()
        it.getInt(0)
    }

    private fun select(uri: Uri?, label: String = "Choose draft file") {
        nextSelection = uri
        click(label)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, lastPickerIntent?.action)
    }

    @Test fun pickerCancelAndNotNowLeavePresetsAndReceiptsEmpty() {
        open()
        select(null)
        find("Import a workout draft").assertIsDisplayed()
        select(draft())
        find("Review workout").assertIsDisplayed()
        assertEquals(0, count("presets"))
        assertEquals(0, count("local_connector_receipts"))
        shot("review_dark_plain")
        click("Not now")
        find("Import a workout draft").assertIsDisplayed()
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
        find("40.01234 kg").performScrollTo().assertIsDisplayed()
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
        click("Preview training file")
        find("Save on this phone")
        shot("preview_dark_plain")
        val destination = file()
        nextSelection = null
        click("Save on this phone")
        val intent = checkNotNull(lastPickerIntent)
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertTrue("The on-device save picker must request only local providers", intent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false))
        assertEquals(0L, destination.length())
        compose.onNodeWithText("Training file saved on this device.").assertDoesNotExist()
        nextSelection = uri(destination)
        click("Save on this phone")
        find("Training file saved on this device.").assertIsDisplayed()
        val first = destination.readBytes()
        val hash = db.openHelper.readableDatabase.query("SELECT contextSha256 FROM local_connector_exports").use {
            assertTrue(it.moveToFirst())
            it.getString(0)
        }
        assertEquals(hash, LocalConnectorFiles.sha256(first))
        val second = file()
        nextSelection = uri(second)
        click("Save on this phone")
        compose.waitUntil(8_000) { second.length() == first.size.toLong() }
        assertArrayEquals(first, second.readBytes())
        assertEquals(1, count("local_connector_exports"))
    }

    @Test fun unwritableDestinationKeepsPreviewAndDoesNotClaimSuccess() {
        open()
        click("Preview training file")
        nextSelection = uri(File(file(), "unwritable.json"))
        click("Save on this phone")
        find("Couldn’t save the file. Choose an available location and try again.").assertIsDisplayed()
        compose.onNodeWithText("Training file saved on this device.").assertDoesNotExist()
        assertEquals(1, count("local_connector_exports"))
    }

    @Test fun sharingTheSamePreviewTwiceGivesBothReadersTheRecordedSnapshot() {
        val invitations = LinkedBlockingQueue<Intent>()
        open(testContext = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) { invitations.add(intent) }
        })
        click("Preview training file")
        find("Save on this phone")
        fun share(): Uri {
            click("Share file")
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
        find("3 sets × 8 reps").performScrollTo().assertWidthIsAtLeast(65.dp)
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
            override fun getApplicationContext(): Context = this
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
