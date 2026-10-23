package com.ironvellum.app.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Account is one destination: Settings and Friends both open [Routes.ACCOUNT], which hosts AccountSettingsScreen.
 * Signed out, both land on the same AccountScreen composable (Settings pushes it as SIGN_IN, Friends embeds it).
 */
class AccountRoutesTest {

    private val nav = File("src/main/kotlin/com/ironvellum/app/ui/IronvellumNav.kt").also {
        assertTrue("sources not found from ${File(".").absolutePath}", it.isFile)
    }.readText().replace("\r\n", "\n")

    @Test
    fun `every open-account lambda navigates the one Account route`() {
        val lambdas = Regex("""onOpenAccount = \{([^}]*)\}""").findAll(nav).map { it.groupValues[1].trim() }.toList()
        assertTrue("expected the Settings and Friends entries", lambdas.size >= 2)
        lambdas.forEach { assertEquals("navController.navigate(Routes.ACCOUNT)", it) }
    }

    @Test
    fun `Account has a single destination and it hosts the settings screen`() {
        val destinations = Regex("""composable\(Routes\.ACCOUNT\)\s*\{([^}]*)\}""").findAll(nav).toList()
        assertEquals(1, destinations.size)
        assertTrue(destinations.single().groupValues[1].contains("AccountSettingsScreen("))
    }

    @Test
    fun `signed out, Settings and Friends show the same AccountScreen`() {
        val social = File("src/main/kotlin/com/ironvellum/app/ui/social/SocialScreen.kt").readText()
        assertTrue(social.contains("AccountScreen("))
        assertTrue(Regex("""composable\(Routes\.SIGN_IN\)[\s\S]*?AccountScreen\(""").containsMatchIn(nav))
    }
}
