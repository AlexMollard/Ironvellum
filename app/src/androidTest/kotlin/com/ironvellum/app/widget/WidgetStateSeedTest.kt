package com.ironvellum.app.widget

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.data.Repository
import com.ironvellum.app.ui.DemoHistory
import com.ironvellum.app.ui.theme.AccentPresets
import com.ironvellum.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Puts the installed app's own database into one widget face (begin, live, done, rest or noplan) for a picture of
 * the real widgets on the launcher: `am instrument -e widgetState live -e class ...WidgetStateSeedTest`. Without the
 * argument it does nothing and passes, so the gate runs it for free. It rewrites the app's plan and sessions: for
 * the emulator, never the phone.
 */
@RunWith(AndroidJUnit4::class)
class WidgetStateSeedTest {
    private val state get() = InstrumentationRegistry.getArguments().getString("widgetState")

    @Test
    fun seed() = runBlocking {
        val state = state ?: return@runBlocking
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IronvellumApp
        val repo = app.repository
        InstrumentationRegistry.getArguments().getString("widgetTheme")?.let { theme ->
            app.appearanceStore.setMode(if (theme == "light") ThemeMode.LIGHT else ThemeMode.DARK)
        }
        // `-e widgetAccent <index>`: the saved pair of the theme in force becomes that preset (dark or Vellum list).
        InstrumentationRegistry.getArguments().getString("widgetAccent")?.toIntOrNull()?.let { index ->
            val presets = AccentPresets.forTheme(app.appearanceStore.mode.value.isLight(false))
            app.appearanceStore.set(presets[index].palette)
        }
        // Ten weeks of finished workouts (the newest yesterday), then the plan and today's trial on top.
        DemoHistory.seed()
        repo.observePresets().first().forEach { repo.deletePreset(it.id) }
        repo.observeLiveSession().first()?.let { repo.abandonSession(it.id) }
        val today = LocalDate.now().dayOfWeek.value
        fun day(offset: Int) = (today - 1 + offset) % 7 + 1
        if (state != "noplan") {
            val pushDay = if (state == "rest") day(1) else day(0)
            val push = plan(repo, "Push", pushDay, listOf(
                Triple("Bench Press", 4 to 6, 72.5), Triple("Overhead Press", 3 to 8, 40.0), Triple("Incline Dumbbell Press", 3 to 10, 24.0),
                Triple("Dip", 3 to 10, 10.0), Triple("Lateral Raise", 3 to 15, 8.0),
            ))
            plan(repo, "Pull", day(2), listOf(Triple("Barbell Row", 4 to 8, 60.0), Triple("Pull-up", 3 to 8, 0.0)))
            plan(repo, "Legs", day(if (state == "rest") 3 else 4), listOf(
                Triple("Back Squat", 4 to 5, 100.0), Triple("Romanian Deadlift", 3 to 8, 80.0), Triple("Walking Lunge", 3 to 12, 16.0), Triple("Calf Raise", 4 to 12, 60.0),
            ))
            if (state == "live" || state == "done") {
                val id = repo.startSessionFromPreset(push)
                val sets = repo.observeSessionSets(id).first().filter { !it.warmup }
                val ticked = if (state == "done") sets else sets.take(7)
                ticked.forEach { repo.updateSet(it.id, it.reps, it.weightKg, true) }
                if (state == "done") repo.completeSession(id)
            }
        }
        TodayWidget.update(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    private suspend fun plan(repo: Repository, name: String, day: Int, lifts: List<Triple<String, Pair<Int, Int>, Double>>): Long {
        val catalogue = repo.observeExercises().first()
        val entries = lifts.map { (lift, setsReps, kg) ->
            val exercise = catalogue.firstOrNull { it.name.equals(lift, ignoreCase = true) }
                ?: catalogue.first { it.name.contains(lift.substringBefore(' '), ignoreCase = true) }
            Repository.PresetDraftEntry(exercise.id, setsReps.first, setsReps.second, kg.takeIf { it > 0.0 })
        }
        return repo.savePreset(null, name, "", day, entries)
    }
}
