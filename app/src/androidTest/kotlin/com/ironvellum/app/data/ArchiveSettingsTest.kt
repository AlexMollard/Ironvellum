package com.ironvellum.app.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.ironvellum.app.R
import com.ironvellum.app.ui.onboarding.OnboardingViewModel
import kotlinx.coroutines.withTimeout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArchiveSettingsTest {
    private lateinit var context: Context
    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository
    private lateinit var settings: ArchiveSettings
    private var refreshed = 0
    private fun prefs(file: String) = context.getSharedPreferences("archive_settings_test_$file", Context.MODE_PRIVATE)

    @Before fun setup() = runTest {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("archive_settings_test.db")
        listOf("appearance", "onboarding", "trial_prefs", "trial", "reminders", "inbox_notifier", "auto_backup", "cloud_config", "program_answers", "health_write", "info_sheet_hints", "share_card", "deeds", "warmup").forEach { prefs(it).edit().clear().commit() }
        db = IronvellumDatabase.create(context, "archive_settings_test.db")
        settings = ArchiveSettings(::prefs) { _, _ -> refreshed++ }
        repo = Repository(db, archiveSettings = settings)
        repo.ensureSeeded()
        repo.applyStarterTemplate()
    }

    @After fun cleanup() {
        db.close()
        context.deleteDatabase("archive_settings_test.db")
        listOf("appearance", "onboarding", "trial_prefs", "trial", "reminders", "inbox_notifier", "auto_backup", "cloud_config", "program_answers", "health_write", "info_sheet_hints", "share_card", "deeds", "warmup").forEach { prefs(it).edit().clear().commit() }
    }

    @Test fun portableSettingsRoundTripAndExcludeSecretsInBothCopies() = runTest {
        prefs("appearance").edit().putString("theme", "DARK").putBoolean("themedNames", true).putString("liftingExperience", "LONGER").putBoolean("fatigueAdvice", false).commit()
        prefs("onboarding").edit().putBoolean("welcome_seen", true).putBoolean("term_seen_rank_titles", true).commit()
        prefs("trial_prefs").edit().putStringSet("rest_off_movements", setOf("pull-up", "dip")).commit()
        prefs("program_answers").edit().putString("gear", "PULL_UP_BAR").putInt("daysPerWeek", 3).putString("plates.KG.bar", "20.0").commit()
        prefs("health_write").edit().putBoolean("enabled", true).putString("last_error", "private status").commit()
        prefs("info_sheet_hints").edit().putBoolean("page_peek", true).commit()
        prefs("warmup").edit().putBoolean("enabled", false).commit()
        prefs("auto_backup").edit().putString("folder", "content://private").commit()
        prefs("cloud_config").edit().putString("key", "secret").commit()
        val local = repo.exportArchive().json
        val cloud = repo.exportArchive(includeDeviceOnly = false).json
        assertFalse(local.contains("content://private"))
        assertFalse(cloud.contains("secret"))
        assertFalse(cloud.contains("private status"))
        assertTrue(JSONObject(cloud).getJSONObject("settings").has("appearance"))
        prefs("appearance").edit().putString("theme", "LIGHT").putBoolean("fatigueAdvice", true).commit()
        prefs("onboarding").edit().putBoolean("welcome_seen", false).commit()
        prefs("trial_prefs").edit().clear().commit()
        prefs("program_answers").edit().clear().commit()
        prefs("info_sheet_hints").edit().clear().commit()
        assertTrue(repo.importArchive(local).isSuccess)
        assertEquals("DARK", prefs("appearance").getString("theme", null))
        assertFalse(prefs("appearance").getBoolean("fatigueAdvice", true))
        assertTrue(prefs("onboarding").getBoolean("welcome_seen", false))
        assertEquals(setOf("pull-up", "dip"), prefs("trial_prefs").getStringSet("rest_off_movements", null))
        assertEquals("PULL_UP_BAR", prefs("program_answers").getString("gear", null))
        assertEquals("20.0", prefs("program_answers").getString("plates.KG.bar", null))
        assertTrue(prefs("info_sheet_hints").getBoolean("page_peek", false))
        assertTrue(prefs("health_write").getBoolean("enabled", false))
        assertFalse(prefs("warmup").getBoolean("enabled", true))
        assertEquals(1, refreshed)
    }

    @Test fun oldArchivesPreserveSettingsAndInvalidSettingsNeverFailTheTrainingRestore() = runTest {
        val json = JSONObject(repo.exportJson())
        json.remove("settings")
        prefs("appearance").edit().putString("theme", "LIGHT").commit()
        assertTrue(repo.importArchive(json.toString()).isSuccess)
        assertEquals("LIGHT", prefs("appearance").getString("theme", null))
        assertEquals(0, refreshed)
        val before = db.presetDao().observePresets().first().size
        assertTrue(before > 0)
        // A stale enum, an unknown key and an unknown file are dropped and reported; training still restores.
        json.put(
            "settings",
            JSONObject()
                .put("appearance", JSONObject().put("theme", "s:INVALID").put("brandNewKey", "b:true").put("themedNames", "b:true"))
                .put("auto_backup", JSONObject().put("folder", "s:content://private")),
        )
        val result = repo.importArchive(json.toString())
        assertTrue(result.isSuccess)
        assertEquals(3, result.getOrThrow().problems.size)
        assertEquals(before, db.presetDao().observePresets().first().size)
        assertTrue(prefs("appearance").getBoolean("themedNames", false))
        assertNull(prefs("appearance").getString("theme", null))
        assertFalse(prefs("auto_backup").contains("folder"))
    }

    @Test fun exportSurvivesStoredValuesTheReadersWouldSnap() = runTest {
        prefs("program_answers").edit().putInt("sessionMinutes", 37).putString("dumbbellMaxKg", "-4").putString("gear", "NOT_A_GEAR")
            .putInt("daysPerWeek", 3).commit()
        prefs("appearance").edit().putString("theme", "RETIRED_THEME").putBoolean("themedNames", true).commit()
        val exported = JSONObject(repo.exportArchive().json).getJSONObject("settings")
        val answers = exported.getJSONObject("program_answers")
        assertFalse(answers.has("sessionMinutes"))
        assertFalse(answers.has("dumbbellMaxKg"))
        assertFalse(answers.has("gear"))
        assertEquals("i:3", answers.getString("daysPerWeek"))
        assertFalse(exported.getJSONObject("appearance").has("theme"))
        assertEquals("b:true", exported.getJSONObject("appearance").getString("themedNames"))
        // Back up now and the cloud copy take the same path.
        assertTrue(JSONObject(repo.exportArchive(includeDeviceOnly = false).json).has("settings"))
    }

    @Test fun permissionStateStaysOnTheDeviceAndWantedSwitchesNeedThisDevicesPermission() = runTest {
        prefs("trial").edit().putBoolean("asked_post_notifications", true).putInt("post_notifications_not_now", 2).commit()
        prefs("inbox_notifier").edit().putBoolean("enabled", true).putBoolean("asked_permission", true).commit()
        prefs("reminders").edit().putBoolean("enabled", true).commit()
        prefs("health_write").edit().putBoolean("enabled", true).commit()
        val archive = repo.exportJson()
        val exported = JSONObject(archive).getJSONObject("settings")
        assertFalse(exported.getJSONObject("trial").has("asked_post_notifications"))
        assertFalse(exported.getJSONObject("trial").has("post_notifications_not_now"))
        assertFalse(exported.getJSONObject("inbox_notifier").has("asked_permission"))
        assertEquals("b:true", exported.getJSONObject("inbox_notifier").getString("enabled"))

        // An old archive that still carries the flags: this device's own state wins and nothing is reported.
        val old = JSONObject(archive)
        old.getJSONObject("settings").getJSONObject("trial").put("asked_post_notifications", "b:true").put("post_notifications_not_now", "i:9")
        old.getJSONObject("settings").getJSONObject("inbox_notifier").put("asked_permission", "b:true")
        prefs("trial").edit().clear().putInt("post_notifications_not_now", 1).commit()
        prefs("inbox_notifier").edit().clear().putBoolean("asked_permission", false).commit()
        val denied = Repository(db, archiveSettings = ArchiveSettings(::prefs, permitted = { false }) { _, _ -> })
        val result = denied.importArchive(old.toString())
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().problems.isEmpty())
        assertFalse(prefs("trial").contains("asked_post_notifications"))
        assertEquals(1, prefs("trial").getInt("post_notifications_not_now", -1))
        assertFalse(prefs("inbox_notifier").getBoolean("asked_permission", true))
        assertTrue(prefs("inbox_notifier").getBoolean("enabled", false))
        assertFalse(prefs("reminders").getBoolean("enabled", true))
        assertFalse(prefs("health_write").getBoolean("enabled", true))

        assertTrue(repo.importArchive(archive).isSuccess)
        assertTrue(prefs("reminders").getBoolean("enabled", false))
        assertTrue(prefs("health_write").getBoolean("enabled", false))
    }

    @Test fun settingsRefreshFailureReportsThatTrainingWasRestored() = runTest {
        prefs("appearance").edit().putString("theme", "DARK").commit()
        val archive = repo.exportJson()
        db.presetDao().clearAll()
        val failing = Repository(db, archiveSettings = ArchiveSettings(::prefs) { _, _ -> error("refresh failed") })
        val error = failing.importArchive(archive).exceptionOrNull()
        assertTrue(error is Repository.SettingsRestoreException)
        assertEquals(context.getString(R.string.settings_restore_settings_failed), error!!.message)
        assertTrue(db.presetDao().observePresets().first().isNotEmpty())
    }

    @Test fun absentSourceValuesRestoreDefaultsWithoutClearingExcludedValues() = runTest {
        val archive = repo.exportJson()
        assertTrue(JSONObject(archive).getJSONObject("settings").has("appearance"))
        prefs("appearance").edit().putBoolean("fatigueAdvice", false).putString("unrelated", "preserve").commit()
        prefs("health_write").edit().putBoolean("enabled", true).putString("last_error", "preserve").commit()
        assertTrue(repo.importArchive(archive).isSuccess)
        assertFalse(prefs("appearance").contains("fatigueAdvice"))
        assertTrue(prefs("appearance").getBoolean("fatigueAdvice", true))
        assertEquals("preserve", prefs("appearance").getString("unrelated", null))
        assertFalse(prefs("health_write").contains("enabled"))
        assertEquals("preserve", prefs("health_write").getString("last_error", null))
    }

    @Test fun cloudArchiveNeverCarriesOrRestoresSexChoiceFlag() = runTest {
        prefs("onboarding").edit().putBoolean("sex_unset", false).commit()
        val local = JSONObject(repo.exportArchive().json)
        val cloud = JSONObject(repo.exportArchive(includeDeviceOnly = false).json)
        assertTrue(local.getJSONObject("settings").getJSONObject("onboarding").has("sex_unset"))
        assertFalse(cloud.getJSONObject("settings").getJSONObject("onboarding").has("sex_unset"))
        prefs("onboarding").edit().putBoolean("sex_unset", true).commit()
        assertTrue(repo.importArchive(cloud.toString()).isSuccess)
        assertTrue(prefs("onboarding").getBoolean("sex_unset", false))
        cloud.getJSONObject("settings").getJSONObject("onboarding").put("sex_unset", "b:false")
        assertTrue(repo.importArchive(cloud.toString()).isSuccess)
        assertTrue(prefs("onboarding").getBoolean("sex_unset", false))
        assertTrue(repo.importArchive(local.toString()).isSuccess)
        assertFalse(prefs("onboarding").getBoolean("sex_unset", true))
    }

    @Test fun completeRestoreClosesOnboardingWithoutChangingRestoredChoices() = runTest {
        repo.setHeight(170.0)
        prefs("onboarding").edit().putBoolean("preferences_seen", true).putBoolean("sex_unset", false).commit()
        val isolated = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = prefs(name)
        }
        val viewModel = OnboardingViewModel(repo, isolated)
        viewModel.restoreStarted()
        viewModel.restoreFinished()
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { withTimeout(10_000) { viewModel.dismissed.first { it } } }
        assertTrue(prefs("onboarding").getBoolean("preferences_seen", false))
        assertFalse(prefs("onboarding").getBoolean("sex_unset", true))
    }
}
