package com.ironvellum.app.data

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The database is excluded from Android backup, so any SharedPreferences file
 * that describes DB state must be excluded too: restored without the DB,
 * "onboarding" (dismissed) skipped first run into an empty app.
 *
 * Every prefs file the app opens must be classified here, so a new one cannot
 * slip into backups without someone deciding whether it survives a reinstall.
 */
class BackupRulesTest {

    private val res = File("src/main/res/xml")
    private val sources = File("src/main/kotlin")

    /** Meaningless, or actively wrong, without the database beside it. */
    private val dbDependent = setOf("onboarding", "program_answers", "reminders", "inbox_notifier", "warband_payout", "circle_payout", "rank_state")

    /** Safe to restore alone: a custom backend URL/key or a seen-this-hint flag, not training state. */
    private val restorable = setOf("cloud_config", "info_sheet_hints")

    @Test
    fun `every prefs file the app opens is classified`() {
        val literal = Regex("""getSharedPreferences\(\s*"([^"]+)"""")
        val constant = Regex("""const val PREFS(?:_FILE)?\s*=\s*"([^"]+)"""")
        val found = sources.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                val text = file.readText()
                (literal.findAll(text) + constant.findAll(text)).map { it.groupValues[1] }
            }
            .toSortedSet()
        assertTrue("the scan found no prefs files at all", found.isNotEmpty())
        assertEquals((dbDependent + restorable).toSortedSet(), found)
    }

    @Test
    fun `db-dependent prefs are excluded from every backup channel`() {
        val sections = listOf(
            parse("backup_rules.xml").documentElement,
            section("data_extraction_rules.xml", "cloud-backup"),
            section("data_extraction_rules.xml", "device-transfer"),
        )
        for (section in sections) {
            val excluded = excludes(section, "sharedpref")
            dbDependent.forEach { name ->
                assertTrue("${section.tagName} must exclude $name.xml", "$name.xml" in excluded)
            }
            assertTrue("${section.tagName} must still exclude the database", "ironvellum.db" in excludes(section, "database"))
        }
    }

    private fun parse(name: String) =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, name))

    private fun section(file: String, tag: String): Element =
        parse(file).getElementsByTagName(tag).item(0) as Element

    private fun excludes(section: Element, domain: String): Set<String> {
        val nodes = section.getElementsByTagName("exclude")
        return (0 until nodes.length)
            .map { nodes.item(it) as Element }
            .filter { it.getAttribute("domain") == domain }
            .map { it.getAttribute("path") }
            .toSet()
    }
}
