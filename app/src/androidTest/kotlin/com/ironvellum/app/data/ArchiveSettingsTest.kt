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
        listOf("appearance", "onboarding", "trial_prefs", "auto_backup", "cloud_config", "program_answers", "health_write", "info_sheet_hints", "share_card", "deeds", "warmup").forEach { prefs(it).edit().clear().commit() }
        db = IronvellumDatabase.create(context, "archive_settings_test.db")
        settings = ArchiveSettings(::prefs) { _, _ -> refreshed++ }
        repo = Repository(db, archiveSettings = settings)
        repo.ensureSeeded()
        repo.applyStarterTemplate()
    }

    @After fun cleanup() {
        db.close()
        context.deleteDatabase("archive_settings_test.db")
        listOf("appearance", "onboarding", "trial_prefs", "auto_backup", "cloud_config", "program_answers", "health_write", "info_sheet_hints", "share_card", "deeds", "warmup").forEach { prefs(it).edit().clear().commit() }
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

    @Test fun oldArchivesPreserveSettingsAndInvalidSettingsCannotClearTraining() = runTest {
        val json = JSONObject(repo.exportJson())
        json.remove("settings")
        prefs("appearance").edit().putString("theme", "LIGHT").commit()
        assertTrue(repo.importArchive(json.toString()).isSuccess)
        assertEquals("LIGHT", prefs("appearance").getString("theme", null))
        assertEquals(0, refreshed)
        val before = db.presetDao().observePresets().first().size
        assertTrue(before > 0)
        json.put("settings", JSONObject().put("appearance", JSONObject().put("theme", "s:INVALID")))
        assertTrue(repo.importArchive(json.toString()).isFailure)
        assertEquals(before, db.presetDao().observePresets().first().size)
        json.put("settings", JSONObject().put("auto_backup", JSONObject().put("folder", "s:content://private")))
        assertTrue(repo.importArchive(json.toString()).isFailure)
        assertEquals(before, db.presetDao().observePresets().first().size)
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
