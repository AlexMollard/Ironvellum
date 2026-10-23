package com.ironvellum.app.domain

import com.ironvellum.app.R
import com.ironvellum.app.plainText
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The movement done with no variation modifier is "Standard" on every screen (the explorer chips and the history
 * chips counted one screen each; "Plain" is also the glossary word for the non-themed names, so it lost).
 */
class VariationNameTest {

    @Test
    fun `the unmodified variation is one string called Standard`() {
        assertEquals("Standard", UiText.res(R.string.common_variation_standard).plainText())
    }

    @Test
    fun `no screen carries its own word for it`() {
        val root = File("src/main").takeIf { it.isDirectory } ?: File("app/src/main")
        val strings = File(root, "res/values/strings.xml").readText()
        assertFalse(strings.contains("explorer_variation_plain"))
        assertFalse(strings.contains("infosheet_history_variation_plain"))
        val sources = root.walkTopDown().filter { it.extension == "kt" }.toList()
        val users = sources.filter { it.readText().contains("R.string.common_variation_standard") }.map { it.name }.toSet()
        assertTrue(users.toString(), users.containsAll(setOf("ExerciseExplorerScreen.kt", "ExerciseHistoryPage.kt")))
    }
}
