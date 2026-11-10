package com.ironvellum.app.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.R
import com.ironvellum.app.data.IronvellumDatabase
import com.ironvellum.app.data.Repository
import com.ironvellum.app.data.SeededDb
import com.ironvellum.app.data.Units
import com.ironvellum.app.data.db.SessionEntity
import com.ironvellum.app.data.db.SetLogEntity
import com.ironvellum.app.data.db.StatEntity
import com.ironvellum.app.domain.LoadUnit
import com.ironvellum.app.ui.theme.IronvellumTheme
import com.ironvellum.app.ui.train.ExerciseExplorerScreen
import com.ironvellum.app.ui.train.ExerciseExplorerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExerciseExplorerAssistanceTest {
    @get:Rule val compose = createComposeRule()
    private val driver by lazy { FlowDriver(compose) }
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository
    private lateinit var vm: ExerciseExplorerViewModel
    private lateinit var originalUnit: LoadUnit
    private var storedUnit: String? = null

    @Before fun setUp() = runBlocking {
        driver.takeTheClock()
        originalUnit = Units.current
        storedUnit = context.getSharedPreferences("appearance", Context.MODE_PRIVATE).getString("loadUnit", null)
        compose.runOnIdle { Units.set(context, LoadUnit.KG) }
        SeededDb.fresh(context, TEST_DB)
        db = IronvellumDatabase.create(context, TEST_DB)
        repo = Repository(db)
        repo.ensureSeeded()
        db.statDao().insert(StatEntity(takenAtMs = 1_000L, weightKg = 80.0, heightCm = 178.0, bodyFatPct = null))
        vm = ExerciseExplorerViewModel(repo)
    }

    @After fun tearDown() {
        runBlocking(Dispatchers.Main) { if (::vm.isInitialized) vm.viewModelScope.cancel() }
        if (::db.isInitialized) db.close()
        context.deleteDatabase(TEST_DB)
        if (::originalUnit.isInitialized) {
            compose.runOnIdle { Units.set(context, originalUnit) }
            val editor = context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit()
            if (storedUnit == null) editor.remove("loadUnit") else editor.putString("loadUnit", storedUnit)
            editor.commit()
        }
    }

    private suspend fun trial(name: String, at: Long, vararg sets: Pair<Double?, Int>, modifiers: String = "") {
        val exercise = db.exerciseDao().observeAll().first().first { it.name == name }
        val session = db.sessionDao().insertSession(SessionEntity(
            presetId = null, label = "T$at", startedAtMs = at, completedAtMs = at + 600_000L,
            xpAwarded = 0, strengthScore = 0, title = "", note = "", privateNote = "",
        ))
        db.sessionDao().insertSets(sets.mapIndexed { index, (weight, reps) ->
            SetLogEntity(sessionId = session, exerciseId = exercise.id, exercisePosition = 0,
                setIndex = index, reps = reps, weightKg = weight, modifiers = modifiers, done = true)
        })
    }

    private fun show(name: String, variation: String? = null) {
        compose.setContent { IronvellumTheme { ExerciseExplorerScreen(onBack = {}, initialName = name, initialVariation = variation, viewModel = vm) } }
        driver.settle()
        driver.awaitText(name)
    }

    @Test fun distinctPeakAndHighestEffectiveLoadShowMachineSupport() = runBlocking {
        trial("Assisted Pull-up", 2_000_000L, 0.0 to 1, 2.5 to 10)
        show("Assisted Pull-up")
        driver.awaitText("2.5 kg × 10")
        driver.awaitText("0 kg × 1")
        compose.onNodeWithText("0 kg × 1").performScrollTo()
        driver.settle()
        ShotRecorder.take(compose, "explorer-assistance-highest-effective")
        compose.runOnIdle { Units.set(context, LoadUnit.LB) }
        driver.settle()
        driver.awaitText("0 lb × 1")
        Unit
    }

    @Test fun zeroPeakAndExpandedRecordRemainNumericAndUnknownRecentIsNotBodyweight() = runBlocking {
        trial("Assisted Dip", 2_000_000L, 0.0 to 1)
        trial("Assisted Dip", 3_000_000L, null to 3)
        show("Assisted Dip")
        driver.awaitText("0 kg × 1")
        val title = context.getString(R.string.explorer_setrecords_title)
        val themedTitle = context.getString(R.string.explorer_setrecords_title__t)
        val panel = driver.awaitAnyText { it == title || it == themedTitle }
        driver.click(panel)
        driver.awaitAnyText { it == "0 kg × 1" && driver.allText().count { text -> text == it } >= 2 }
        val unknown = context.getString(R.string.assistance_unknown) + " × 3"
        driver.awaitText(unknown)
        compose.onNodeWithText(unknown, substring = true).performScrollTo()
        driver.settle()
        ShotRecorder.take(compose, "explorer-assistance-unknown")
    }

    @Test fun ordinaryBodyweightNullAndZeroStayBodyweightIncludingBandVariation() = runBlocking {
        trial("Push-up", 2_000_000L, null to 4, 0.0 to 5, modifiers = "assisted")
        show("Push-up", "assisted")
        driver.awaitAnyText { it.contains("BW × 4") && it.contains("BW × 5") }
        compose.onNodeWithText("BW × 4", substring = true).performScrollTo()
        driver.settle()
        compose.onNodeWithText("BW × 4", substring = true).assertIsDisplayed()
        Unit
    }

    private companion object { const val TEST_DB = "explorer-assistance-test.db" }
}
