package com.ironvellum.app.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.domain.Equipment
import com.ironvellum.app.domain.Gear
import com.ironvellum.app.domain.TrainingFocus
import com.ironvellum.app.domain.TrainingSplit
import com.ironvellum.app.domain.VolumeLevel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settings saves gear on its own. That must work before the generator was
 * ever run, and must never erase or change the other generator answers.
 */
@RunWith(AndroidJUnit4::class)
class GearSettingTest {
    private lateinit var context: Context
    private val barAndDumbbell = Equipment(false, setOf(Gear.PULL_UP_BAR, Gear.DUMBBELLS), 24.0, false)

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        clear()
    }

    @After
    fun tearDown() = clear()

    private fun clear() {
        context.getSharedPreferences("program_answers", Context.MODE_PRIVATE).edit().clear().commit()
        ProgramAnswersStore.get(context)
    }

    @Test
    fun gearSavedAloneReadsBackWithoutGeneratorAnswers() {
        ProgramAnswersStore.saveEquipment(context, barAndDumbbell)
        // A fresh read from disk, not the in-memory flow the save just set.
        assertNull(ProgramAnswersStore.get(context))
        assertEquals(barAndDumbbell, ProgramAnswersStore.equipment.value)
    }

    @Test
    fun gearSavedAloneKeepsEveryOtherAnswer() {
        val answers = ProgramAnswers(
            VolumeLevel.STANDARD, TrainingFocus.STRENGTH, Equipment.FULL_GYM, 3, emptySet(), TrainingSplit.PUSH_PULL_LEGS,
            compoundOnly = true, maxExercises = 5,
        )
        ProgramAnswersStore.save(context, answers)
        ProgramAnswersStore.saveEquipment(context, barAndDumbbell)
        assertEquals(answers.copy(equipment = barAndDumbbell), ProgramAnswersStore.get(context))
    }
}
