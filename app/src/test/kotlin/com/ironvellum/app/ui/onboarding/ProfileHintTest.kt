package com.ironvellum.app.ui.onboarding

import com.ironvellum.app.domain.BodyLimits
import com.ironvellum.app.domain.BodyLimits.Reading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileHintTest {

    @Test
    fun `a typed figure is read against its range, not as missing`() {
        assertEquals(Reading.EMPTY, BodyLimits.readHeight(""))
        assertEquals(Reading.EMPTY, BodyLimits.readHeight("."))
        assertEquals(Reading.OUT_OF_RANGE, BodyLimits.readHeight("17"))
        assertEquals(Reading.OUT_OF_RANGE, BodyLimits.readHeight("300"))
        assertEquals(Reading.VALID, BodyLimits.readHeight("175"))
        assertEquals(Reading.VALID, BodyLimits.readHeight("272"))
        assertEquals(Reading.OUT_OF_RANGE, BodyLimits.readWeight("19.9"))
        assertEquals(Reading.VALID, BodyLimits.readWeight("20"))
        assertEquals(Reading.OUT_OF_RANGE, BodyLimits.readWeight("400.1"))
    }

    @Test
    fun `ranges are said as whole numbers`() {
        assertEquals("50 to 272", BodyLimits.say(BodyLimits.HEIGHT_CM))
        assertEquals("20 to 400", BodyLimits.say(BodyLimits.WEIGHT_KG))
    }

    @Test
    fun `nothing is said when the profile is complete`() {
        assertNull(profileHint(false, Reading.VALID, Reading.VALID))
    }

    @Test
    fun `missing figures are asked for`() {
        assertEquals("Add weight to continue.", profileHint(false, Reading.VALID, Reading.EMPTY))
        assertEquals("Add a name, height and weight to continue.", profileHint(true, Reading.EMPTY, Reading.EMPTY))
    }

    @Test
    fun `an out of range figure is named out of range, with its range`() {
        assertEquals("Height must be 50 to 272 cm.", profileHint(false, Reading.OUT_OF_RANGE, Reading.VALID))
        assertEquals(
            "Height must be 50 to 272 cm. Weight must be 20 to 400 kg.",
            profileHint(false, Reading.OUT_OF_RANGE, Reading.OUT_OF_RANGE),
        )
    }

    @Test
    fun `a missing figure and an out of range one are both said`() {
        assertEquals(
            "Add weight. Height must be 50 to 272 cm.",
            profileHint(false, Reading.OUT_OF_RANGE, Reading.EMPTY),
        )
    }
}
