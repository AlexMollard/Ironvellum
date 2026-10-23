package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A deed id from the cloud that this build does not know shows a neutral name, never nothing. */
class DeedDisplayNameTest {

    @Test
    fun `a known id shows its name`() {
        assertEquals("First Mark", Titles.displayName("awakened"))
        assertEquals("Cast Off the Crutch", Titles.displayName("cast_off_the_crutch"))
    }

    @Test
    fun `an id from a newer version shows the fallback not a blank`() {
        assertEquals("A deed from a newer version", Titles.displayName("some_deed_added_next_year"))
    }

    @Test
    fun `no worn deed stays no name`() {
        assertNull(Titles.displayName(null))
        assertNull(Titles.displayName(""))
        assertNull(Titles.displayName("  "))
    }
}
