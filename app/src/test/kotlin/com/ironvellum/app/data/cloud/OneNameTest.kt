package com.ironvellum.app.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The profile name and the cloud true name are one name: signed in, the cloud
 * one wins. These are the decisions behind that, kept pure so they run here.
 */
class OneNameTest {

    @Test
    fun `a claimed cloud name replaces the local name`() {
        assertEquals("Marcus", localNameToAdopt(local = "Ironbound", cloud = "Marcus", profileLoaded = true))
        assertEquals("Marcus", localNameToAdopt(local = "Alex", cloud = " Marcus ", profileLoaded = true))
    }

    @Test
    fun `a seeded handle never overwrites a name the lifter chose`() {
        assertNull(localNameToAdopt("Alex", "Ironbound1234", true))
        assertNull(localNameToAdopt("Alex", "Lifter0042", true))
        assertNull(localNameToAdopt("Alex", "Hunter9999", true))
        // Five digits is a name someone typed, not a seed.
        assertEquals("Ironbound12345", localNameToAdopt("Alex", "Ironbound12345", true))
    }

    @Test
    fun `nothing is adopted from a profile that was never read or is already the local name`() {
        assertNull(localNameToAdopt("Alex", "", true))
        assertNull(localNameToAdopt("Alex", "Marcus", profileLoaded = false)) // offline restore placeholder
        assertNull(localNameToAdopt("Marcus", "Marcus", true))
        assertEquals("Marcus", localNameToAdopt(null, "Marcus", true)) // no local row yet
    }

    @Test
    fun `the sign-up field starts from the local name unless it is still the default`() {
        assertEquals("Alex", signUpNamePrefill("Alex"))
        assertEquals("Alex", signUpNamePrefill("  Alex "))
        assertEquals("", signUpNamePrefill("Ironbound"))
        assertEquals("", signUpNamePrefill("ironbound"))
        assertEquals("", signUpNamePrefill(null))
        assertEquals("", signUpNamePrefill("A".repeat(25))) // older builds never capped it at 24
    }

    @Test
    fun `a true name is 2 to 24 characters with at least two letters or digits`() {
        assertNull(trueNameProblem("Al"))
        assertNull(trueNameProblem("  Marcus  "))
        assertNull(trueNameProblem("A".repeat(24)))
        assertNotNull(trueNameProblem("A"))
        assertNotNull(trueNameProblem(" A "))
        assertNotNull(trueNameProblem(""))
        assertNotNull(trueNameProblem("A".repeat(25)))
        assertNotNull(trueNameProblem("!!")) // would clean to nothing and be replaced by a seeded-looking handle
    }
}
