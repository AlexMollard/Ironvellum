package com.ironvellum.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.domain.LiftingExperience
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The answer survives a restart, and nothing stored reads as unanswered (experienced). */
@RunWith(AndroidJUnit4::class)
class LiftingExperienceSettingTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val before = LiftingExperienceSetting.answer

    @Before
    fun clear() {
        context.getSharedPreferences("appearance", android.content.Context.MODE_PRIVATE).edit().remove("liftingExperience").commit()
        LiftingExperienceSetting.init(context)
    }

    @After
    fun restore() {
        LiftingExperienceSetting.set(context, before)
    }

    @Test
    fun nothingStoredIsUnanswered() {
        assertEquals(LiftingExperience.UNANSWERED, LiftingExperienceSetting.answer)
    }

    @Test
    fun anAnswerIsKeptAcrossARestart() {
        LiftingExperienceSetting.set(context, LiftingExperience.UNDER_TWO_MONTHS)
        LiftingExperienceSetting.init(context)
        assertEquals(LiftingExperience.UNDER_TWO_MONTHS, LiftingExperienceSetting.answer)
    }

    @Test
    fun anUnknownStoredNameReadsAsUnanswered() {
        context.getSharedPreferences("appearance", android.content.Context.MODE_PRIVATE).edit().putString("liftingExperience", "LONGER_AND_WISER").commit()
        LiftingExperienceSetting.init(context)
        assertEquals(LiftingExperience.UNANSWERED, LiftingExperienceSetting.answer)
    }
}
