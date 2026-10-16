package com.ironvellum.app

import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.StringSource
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.domain.resolve
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** UiText resolution (the plain/themed pick, arguments, plurals) and the disk resolver that stands in for a device in tests. */
class UiTextTest {

    @get:Rule val tmp = TemporaryFolder()

    @After fun restoreMode() = Words.useForTest(true)

    /** Echoes ids and arguments, so the test sees exactly what UiText asked for. */
    private class Echo(override val themedComplete: Boolean = true) : StringSource {
        override fun string(id: Int, args: List<Any>) = "s$id$args"
        override fun plural(id: Int, count: Int, args: List<Any>) = "p$id#$count$args"
    }

    @Test
    fun `a themed pair shows the plain side unless the themed names are on`() {
        val pair = UiText.themed(1, 2)
        assertEquals("s1[]", pair.resolve(Echo(), themed = false))
        assertEquals("s2[]", pair.resolve(Echo(), themed = true))
    }

    @Test
    fun `a language without complete themed strings keeps plain whatever the switch says`() {
        val pair = UiText.themed(1, 2)
        assertEquals("s1[]", pair.resolve(Echo(themedComplete = false), themed = true))
        assertEquals("p3#2[2]", UiText.themedPlural(3, 4, 2).resolve(Echo(themedComplete = false), themed = true))
        assertEquals("p4#2[2]", UiText.themedPlural(3, 4, 2).resolve(Echo(), themed = true))
    }

    @Test
    fun `arguments pass through and a UiText argument is resolved first`() {
        val inner = UiText.themed(5, 6)
        assertEquals("s1[Ada, s6[]]", UiText.res(1, "Ada", inner).resolve(Echo(), themed = true))
        assertEquals("p7#3[3, x]", UiText.plural(7, 3, 3, "x").resolve(Echo(), themed = false))
        assertEquals("not translated", UiText.Raw("not translated").resolve(Echo(), themed = true))
    }

    @Test
    fun `the disk resolver gives the English plain and themed text for the proof screen`() {
        val caption = UiText.themed(
            R.string.settings_summons_evening_caption,
            R.string.settings_summons_evening_caption__t,
        )
        // The same sentence the screen built from Words before the move, in both modes.
        Words.useForTest(false)
        assertEquals(
            "One evening reminder on days your ${Words.cycle.s} holds ${Words.rite.a} you have not begun.",
            caption.plainText(),
        )
        Words.useForTest(true)
        assertEquals(
            "One evening reminder on days your ${Words.cycle.s} holds ${Words.rite.a} you have not begun.",
            caption.themedText(),
        )
        assertEquals("The reminder won't arrive until you allow notifications.",
            UiText.themed(R.string.settings_summons_blocked, R.string.settings_summons_blocked__t).plainText())
        assertEquals("The Summons won't arrive until you allow notifications.",
            UiText.themed(R.string.settings_summons_blocked, R.string.settings_summons_blocked__t).themedText())
        // No themed strings in this language: the switch is on but plain shows.
        assertEquals(
            "The reminder won't arrive until you allow notifications.",
            UiText.themed(R.string.settings_summons_blocked, R.string.settings_summons_blocked__t)
                .themedText(DiskStrings(themedComplete = false)),
        )
    }

    @Test
    fun `strings xml values are read the way aapt2 reads them`() {
        assertEquals("Today's plan", StringsXml.unescape("Today\\'s   plan "))
        assertEquals("Streak & week", StringsXml.unescape("  Streak & week\n    "))
        assertEquals("two  spaces", StringsXml.unescape("\"two  spaces\""))
        assertEquals("a\nb", StringsXml.unescape("a\\nb"))
        assertEquals("@home ?", StringsXml.unescape("\\@home \\?"))
        assertEquals("é", StringsXml.unescape("\\u00e9"))
        assertEquals("100%", StringsXml.unescape("100%"))
    }

    @Test
    fun `plurals, formats and entities load from a file`() {
        val xml = tmp.newFile("strings.xml").apply {
            writeText(
                """
                <resources xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">
                    <string name="greet">Hi <xliff:g id="n">%1${'$'}s</xliff:g>, you did 100%% &amp; more</string>
                    <plurals name="sets">
                        <item quantity="one">%d set</item>
                        <item quantity="other">%d sets</item>
                    </plurals>
                    <string name="brand" translatable="false">Ironvellum</string>
                </resources>
                """.trimIndent(),
            )
        }
        val entries = StringsXml.read(xml).associateBy { it.name }
        assertEquals("Hi %1${'$'}s, you did 100%% & more", entries.getValue("greet").text)
        assertEquals("Hi Ada, you did 100% & more", StringsXml.format(entries.getValue("greet").text!!, listOf("Ada")))
        val sets = entries.getValue("sets").items!!
        assertEquals("1 set", StringsXml.format(StringsXml.quantity("sets", sets, 1), listOf(1)))
        assertEquals("0 sets", StringsXml.format(StringsXml.quantity("sets", sets, 0), listOf(0)))
        assertEquals("3 sets", StringsXml.format(StringsXml.quantity("sets", sets, 3), listOf(3)))
        assertFalse(entries.getValue("brand").translatable)
        assertTrue(entries.getValue("sets").isPlural)
    }

    @Test
    fun `the Themed names switch is offered only when the language has its themed strings`() {
        assertTrue(DefaultStrings.themedComplete)
        assertFalse(DiskStrings(themedComplete = false).themedComplete)
        // The shipped English file declares it, and a missing declaration would default to offering the switch.
        val bools = File("src/main/res/values/bools.xml").readText()
        assertTrue(bools.contains("""name="themed_strings_complete">true<"""))
    }
}
