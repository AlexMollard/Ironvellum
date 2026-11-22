package com.ironvellum.app.data

import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.BuildConfig
import com.ironvellum.app.data.cloud.*
import com.ironvellum.app.data.localconnector.*
import com.ironvellum.app.domain.LocalConnectorFiles.sha256
import com.ironvellum.app.ui.onboarding.OnboardingViewModel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Normal gate runs both halves locally. The opt-in host exchanges the two exact files between halves. */
class LocalFileRoundtripTest {
    private val instrument = InstrumentationRegistry.getInstrumentation()
    private val target = instrument.targetContext
    private val args = InstrumentationRegistry.getArguments()

    @Test fun trainingFilesSurviveAReopenAndOnlyExplicitSaveWrites() = runBlocking {
        val phase = args.getString("localFilePhase")
        val standalone = target.packageName == "com.ironvellum.app.receiptfixture"
        if (phase != null || standalone) {
            assertStandaloneTarget(target)
            assertEquals("", BuildConfig.SUPABASE_URL)
            assertEquals("", BuildConfig.SUPABASE_KEY)
            assertTrue(phase in setOf("export", "import"))
            Words.init(target, databaseExists = false, welcomeSeen = false)
        }
        val id = if (phase == null) UUID.randomUUID() else UUID.fromString(args.getString("localFileId"))
        val prefix = "local-roundtrip-$id"
        val name = "$prefix.db"
        val contextFile = File(target.filesDir, "$prefix.json")
        val prefNames = mutableSetOf<String>()
        val isolated = object : ContextWrapper(target) {
            override fun getSharedPreferences(name: String, mode: Int) =
                target.getSharedPreferences("$prefix-$name".also(prefNames::add), mode)
        }
        var keep = false
        try {
            if (phase != "import") {
                assertFalse(target.getDatabasePath(name).exists())
                withDatabase(name) { db ->
                    val repo = Repository(db)
                    repo.ensureSeeded()
                    val onboarding = OnboardingViewModel(repo, isolated)
                    assertFalse(onboarding.welcomeSeen.value)
                    onboarding.leaveWelcome()
                    onboarding.leavePreferences()
                    onboarding.skip()
                    assertTrue(OnboardingViewModel(repo, isolated).dismissed.value)
                    if (standalone) {
                        // The manifest denies network before the process starts. Exercise a failing optional probe.
                        val result = runCatching { Cloud.probe(CloudConfig("https://127.0.0.1:1", "fixture", false)) }
                        assertTrue(result.getOrNull() == ProbeResult.Unreachable || result.exceptionOrNull() is SecurityException)
                    }
                    val bench = db.exerciseDao().byName("Bench Press")!!.id
                    val preset = repo.savePreset(null, "Offline practice", "", null,
                        listOf(Repository.PresetDraftEntry(bench, 1, 8, 12.5)))
                    val session = repo.startSessionFromPreset(preset)
                    val set = db.sessionDao().setsFor(session).single { !it.isWarmup }
                    repo.updateSet(set.id, 8, 12.5, true)
                    repo.completeSession(session)
                    assertNotNull(db.sessionDao().byId(session)!!.completedAtMs)
                }
                withDatabase(name) { reopened ->
                    val export = LocalConnectorRepository(reopened).exportContext()
                    assertEquals(1, export.summary.coverage.workoutsIncluded)
                    assertEquals(1, export.summary.coverage.setsIncluded)
                    contextFile.writeBytes(export.bytes)
                    if (phase == "export") {
                        send("contextSha256", sha256(export.bytes))
                        keep = true
                    }
                }
            }
            if (!keep) {
                val context = contextFile.readBytes()
                val bytes = if (phase == "import") Base64.decode(args.getString("localProposal"), Base64.URL_SAFE)
                    else recordedProposal(context)
                withDatabase(name) { db -> verifySave(db, context, bytes) }
                if (standalone) send("proposalSha256", sha256(bytes))
            }
        } finally {
            prefNames.forEach(target::deleteSharedPreferences)
            if (!keep) {
                target.deleteDatabase(name)
                contextFile.delete()
            }
        }
    }

    private suspend fun withDatabase(name: String, block: suspend (IronvellumDatabase) -> Unit) {
        val db = IronvellumDatabase.create(target, name)
        try { block(db) } finally { db.close() }
    }

    private fun send(key: String, value: String) = instrument.sendStatus(0, Bundle().apply { putString(key, value) })

    private fun recordedProposal(context: ByteArray): ByteArray {
        val source = Json.parseToJsonElement(context.decodeToString()).jsonObject
        val now = System.currentTimeMillis()
        return buildJsonObject {
            put("type", "ironvellum.workout-draft"); put("schemaVersion", 1)
            put("datasetId", source.getValue("datasetId")); put("snapshotId", source.getValue("snapshotId"))
            put("contextSha256", sha256(context)); put("draftId", UUID.randomUUID().toString())
            put("sender", "Personal connector"); put("createdAtMs", now); put("expiresAtMs", now + 60_000L)
            put("draft", Json.parseToJsonElement("""{"schemaVersion":1,"name":"Next practice","warmup":"AUTO","cooldown":"NONE","entries":[{"exerciseKey":"bench press","sets":3,"reps":8,"loadKg":12.5}]}"""))
        }.toString().encodeToByteArray()
    }

    private suspend fun verifySave(db: IronvellumDatabase, context: ByteArray, bytes: ByteArray) {
        val files = LocalConnectorRepository(db)
        fun count(table: String) = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use {
            check(it.moveToFirst()); it.getInt(0)
        }
        val before = count("presets")
        assertEquals("Only the workout created during offline training exists", 1, before)
        val source = Json.parseToJsonElement(context.decodeToString()).jsonObject
        val review = (files.inspectDraft(bytes) as LocalInspection.Ready).review
        val p = review.proposal
        assertEquals(source.getValue("datasetId").jsonPrimitive.content, p.datasetId)
        assertEquals(source.getValue("snapshotId").jsonPrimitive.content, p.snapshotId)
        assertEquals(sha256(context), p.contextSha256)
        val otherName = "local-other-" + UUID.randomUUID() + ".db"
        try {
            withDatabase(otherName) { other ->
                val otherFiles = LocalConnectorRepository(other)
                assertNotEquals(p.datasetId, otherFiles.exportContext().summary.datasetId)
                assertEquals(LocalInspection.Rejected(LocalFailure.WRONG_DATASET), otherFiles.inspectDraft(bytes))
            }
        } finally {
            target.deleteDatabase(otherName)
        }
        assertEquals(before, count("presets"))
        assertEquals(0, count("local_connector_receipts"))
        val saved = (files.saveDraft(review) as LocalSave.Saved).localPresetId
        val receipt = db.localConnectorDao().receipt(p.datasetId, p.draftId)!!
        assertEquals(sha256(bytes), receipt.proposalSha256)
        assertEquals(p.snapshotId, receipt.snapshotId)
        assertEquals(saved, receipt.localPresetId)
        val preset = db.presetDao().presetWithEntries(saved)!!
        assertNull(preset.preset.scheduledDay)
        assertEquals("Next practice", preset.preset.name)
        val row = preset.entries.single()
        assertEquals(3, row.targetSets)
        assertEquals(8, row.targetReps)
        assertEquals(12.5, row.targetWeightKg!!, 0.0)
        assertEquals(LocalInspection.AlreadySaved(saved), files.inspectDraft(bytes))
        assertEquals(LocalSave.AlreadySaved(saved), files.saveDraft(review))
        val changed = bytes.decodeToString().replace("Next practice", "Changed practice").encodeToByteArray()
        assertEquals(LocalInspection.Rejected(LocalFailure.CONFLICT), files.inspectDraft(changed))
        assertEquals(LocalInspection.Rejected(LocalFailure.CONFLICT), files.inspectDraft(bytes + 32.toByte()))
        assertEquals(before + 1, count("presets"))
        db.openHelper.writableDatabase.execSQL("DELETE FROM presets WHERE id=?", arrayOf(saved))
        assertEquals(LocalInspection.AlreadySaved(saved), files.inspectDraft(bytes))
        assertEquals(before, count("presets"))
        assertEquals(1, count("local_connector_receipts"))
        assertEquals(1, count("sessions"))
    }
}
