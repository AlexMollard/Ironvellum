package com.ironvellum.app.data

import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A concept form is a string resource, and a form no screen used when the strings were written has none. Reading it
 * would fail on a device, so this finds every `Words.<concept>.<form>` the sources read and resolves it in both modes.
 */
class WordsFormsTest {

    @After fun backToDefault() = Words.useForTest(true)

    private val use = Regex("""\bWords\.(\w+)\.(s|c|t|u|pl|cpl|tpl|upl|a|ca|at|cat|themedName)\b""")

    @Test
    fun `every concept form the sources read has its strings, in both modes`() {
        val concepts = Words::class.java.declaredFields.filter { it.type.name.endsWith("Words\$Concept") }
            .associate { it.name to it.apply { isAccessible = true }.get(Words) }
        val seen = sortedSetOf<String>()
        val src = File("src")
        for (file in src.walkTopDown().filter { it.isFile && it.extension == "kt" }) {
            for (m in use.findAll(file.readText())) {
                if (m.groupValues[1] in concepts) seen += m.groupValues[1] + "." + m.groupValues[2]
            }
        }
        assertTrue("the scan found no concept forms; it would pass vacuously", seen.size > 100)
        val broken = mutableListOf<String>()
        for (themed in listOf(false, true)) {
            Words.useForTest(themed)
            for (word in seen) {
                val (name, form) = word.split(".")
                val concept = concepts.getValue(name)
                val getter = "get" + form.replaceFirstChar { it.uppercase() }
                val text = runCatching { concept.javaClass.getMethod(getter).invoke(concept) as String }
                    .getOrElse { (it.cause ?: it).message ?: "failed" }
                if (text.isBlank() || text.startsWith("Words.")) broken += "$word (themed=$themed): $text"
            }
        }
        assertEquals("forms with no strings: add concept_<name>_<form> and its __t twin, and the form to the Concept:\n" + broken.joinToString("\n"), 0, broken.size)
    }

    @Test
    fun `a form nobody gave strings fails loudly instead of printing nothing`() {
        val message = runCatching { Words.Concept("nothing").s }.exceptionOrNull()?.message.orEmpty()
        assertTrue(message, "has no `s` form" in message)
    }
}
