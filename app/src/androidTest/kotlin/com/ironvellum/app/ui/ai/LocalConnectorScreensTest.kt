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
import androidx.compose.ui.Modifier
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
import androidx.room.Room
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.IronvellumDatabase
import com.ironvellum.app.data.Units
import com.ironvellum.app.data.Words
import com.ironvellum.app.data.db.ExerciseEntity
import com.ironvellum.app.data.localconnector.LocalConnectorRepository
import com.ironvellum.app.domain.LoadUnit
import com.ironvellum.app.domain.Loads
import com.ironvellum.app.domain.LocalConnectorFiles
import com.ironvellum.app.domain.MAX_PROPOSAL_BYTES
import com.ironvellum.app.ui.ShotRecorder
import com.ironvellum.app.ui.settings.shareFile
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTheme
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID
import java.util.concurrent.CountDownLatch
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
        db = Room.inMemoryDatabaseBuilder(context, IronvellumDatabase::class.java).build()
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

    private fun open(light: Boolean = false, testContext: Context? = null) {
        compose.activityRule.scenario.onActivity { activity ->
            Words.setThemed(context, light)
            ShotRecorder.darkWindow(activity)
            if (light) WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
        compose.setContent {
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides picker,
                LocalContext provides (testContext ?: LocalContext.current),
            ) {
                IronvellumTheme(light) {
                    Box(Modifier.fillMaxSize().background(IronvellumColors.Abyss).windowInsetsPadding(WindowInsets.safeDrawing)) {
                        LocalConnectorScreen(repo, onBack = {}, onConnections = {}, onCloudDrafts = {})
                    }
                }
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

    private fun click(text: String) = find(text).performScrollTo().performClick()

    private fun checkLocalizedCounts(tag: String, themed: Boolean, counts: String) {
        val configuration = android.content.res.Configuration(context.resources.configuration).apply {
            setLocale(java.util.Locale.forLanguageTag(tag))
        }
        val localized = context.createConfigurationContext(configuration)
        open(light = themed, testContext = localized)
        click(localized.getString(com.ironvellum.app.R.string.local_ai_share))
        compose.waitUntil(8_000) { compose.onAllNodesWithText(counts).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun spanishPlainPreviewShowsBothCoverageLabels() =
        checkLocalizedCounts("es", false, "Incluidos: 0\nOmitidos: 0")

    @Test fun portugueseThemedPreviewShowsBothCoverageLabels() =
        checkLocalizedCounts("pt-BR", true, "Incluídos: 0\nOmitidos: 0")

    private fun shot(name: String) {
        ShotRecorder.take(compose, "local_connector_$name")
        compose.mainClock.autoAdvance = true
    }

    private fun count(table: String) = db.openHelper.readableDatabase.query("SELECT count(*) FROM $table").use {
        it.moveToFirst()
        it.getInt(0)
    }

    private fun select(uri: Uri?, label: String = "Import workout draft") {
        nextSelection = uri
        click(label)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, lastPickerIntent?.action)
    }

    @Test fun pickerCancelAndDiscardLeavePresetsAndReceiptsEmpty() {
        open()
        select(null)
        find("Import workout draft").assertIsDisplayed()
        select(draft())
        find("Review workout").assertIsDisplayed()
        assertEquals(0, count("presets"))
        assertEquals(0, count("local_connector_receipts"))
        shot("review_dark_plain")
        click("Discard")
        find("Import workout draft").assertIsDisplayed()
        assertEquals(0, count("presets"))
        assertEquals(0, count("local_connector_receipts"))
    }

    @Test fun saveUsesReviewedBytesAndRepeatImportReturnsOneReceiptEvenAfterDeletion() {
        val selected = draft()
        val original = context.contentResolver.openInputStream(selected)!!.use { it.readBytes() }
        Units.setExercise(context, "bench press", LoadUnit.LB)
        open(light = true)
        select(selected, "Import rite draft")
        find("Suggested by Training assistant · Unverified sender").assertIsDisplayed()
        find("40.01234 kg").assertIsDisplayed()
        find(Loads.withUnit(40.01234, LoadUnit.LB)).assertIsDisplayed()
        find("Warmup · Automatic").performScrollTo().assertIsDisplayed()
        assertEquals(0, count("presets"))
        shot("review_vellum_themed")
        // The provider can change the source after review. Save must use the bytes already reviewed.
        context.contentResolver.openOutputStream(selected, "wt")!!.use { it.write(byteArrayOf(0)) }
        click("Save rite")
        find("Rite saved").assertIsDisplayed()
        assertEquals(1, count("local_connector_receipts"))
        assertEquals(1, count("presets"))
        assertEquals(0, count("sessions"))
        val storedId = db.openHelper.readableDatabase.query("SELECT id, scheduledDay FROM presets").use {
            assertTrue(it.moveToFirst())
            assertTrue(it.isNull(1))
            it.getLong(0)
        }
        shot("saved_vellum_themed")
        click("Done")
        // Reopen the same receipt with the original file bytes restored, with and without its preset.
        context.contentResolver.openOutputStream(selected, "wt")!!.use { it.write(original) }
        select(selected, "Import rite draft")
        find("Already saved").assertIsDisplayed()
        find("Upper A").assertIsDisplayed()
        assertEquals(1, count("presets"))
        click("Done")
        runBlocking { db.presetDao().deletePreset(storedId) }
        select(selected, "Import rite draft")
        find("Already saved").assertIsDisplayed()
        find("This draft was saved on this device before. The saved rite was later deleted.").assertIsDisplayed()
        assertEquals(0, count("presets"))
        assertEquals(1, count("local_connector_receipts"))
        shot("deleted_vellum_themed")
    }

    @Test fun rejectedFilesAndProviderErrorsNeverOfferSave() {
        open()
        select(uri(file(ByteArray(MAX_PROPOSAL_BYTES + 1))))
        find("Draft file too large").assertIsDisplayed()
        compose.onNodeWithText("Save workout").assertDoesNotExist()
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
        assertEquals(0, count("presets"))
        assertEquals(0, count("local_connector_receipts"))
    }

    @Test fun savingPreviewUsesSameBytesAndCancellationDoesNotGenerateAnotherSnapshot() {
        open()
        click("Share training file")
        find("Save file on this device")
        shot("preview_dark_plain")
        val destination = file()
        nextSelection = null
        click("Save file on this device")
        val intent = checkNotNull(lastPickerIntent)
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertTrue("The on-device save picker must request only local providers", intent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false))
        assertEquals(0L, destination.length())
        compose.onNodeWithText("Training file saved on this device.").assertDoesNotExist()
        nextSelection = uri(destination)
        click("Save file on this device")
        find("Training file saved on this device.").assertIsDisplayed()
        val first = destination.readBytes()
        val hash = db.openHelper.readableDatabase.query("SELECT contextSha256 FROM local_connector_exports").use {
            assertTrue(it.moveToFirst())
            it.getString(0)
        }
        assertEquals(hash, LocalConnectorFiles.sha256(first))
        val second = file()
        nextSelection = uri(second)
        click("Save file on this device")
        compose.waitUntil(8_000) { second.length() == first.size.toLong() }
        assertArrayEquals(first, second.readBytes())
        assertEquals(1, count("local_connector_exports"))
    }

    @Test fun unwritableDestinationKeepsPreviewAndDoesNotClaimSuccess() {
        open()
        click("Share training file")
        nextSelection = uri(File(file(), "unwritable.json"))
        click("Save file on this device")
        find("Couldn’t save the file. Choose an available location and try again.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Training file saved on this device.").assertDoesNotExist()
        assertEquals(1, count("local_connector_exports"))
    }

    @Test fun sharingTheSamePreviewTwiceGivesBothReadersTheRecordedSnapshot() {
        val invitations = LinkedBlockingQueue<Intent>()
        open(testContext = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) { invitations.add(intent) }
        })
        click("Share training file")
        find("Save file on this device")
        fun share(): Uri {
            compose.onNode(hasText("Share training file") and hasClickAction()).performScrollTo().performClick()
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
        val exactLoad = "40." + "123456789".repeat(8)
        open()
        select(draft(loadKg = exactLoad))
        find("$exactLoad kg").assertIsDisplayed()
        find("Bench Press").assertIsDisplayed()
        find("3 sets × 8 reps").assertWidthIsAtLeast(65.dp)
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
        open(testContext = wrapped)
        val cancelledRead = try {
            select(slowUri)
            assertTrue("First selection reached the provider", opened.await(5, SECONDS))
            find("Reading and checking the draft file…").assertIsDisplayed()
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
        find("Save workout").assertIsDisplayed()
        assertEquals(0, count("presets"))
        assertEquals(0, count("local_connector_receipts"))
    }
}
