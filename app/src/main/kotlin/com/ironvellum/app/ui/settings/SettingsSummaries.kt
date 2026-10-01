package com.ironvellum.app.ui.settings

import com.ironvellum.app.domain.Equipment
import com.ironvellum.app.domain.Gear
import com.ironvellum.app.domain.Sex
import com.ironvellum.app.ui.components.formatBodyValue

/**
 * The hub's one-line values. Pure, so the wording is unit-tested rather than
 * eyeballed on a phone.
 */
object SettingsSummaries {

    /** "Not set", "Full gym", "Nothing", or the first two items in catalogue order plus a count. */
    fun armoury(equipment: Equipment?): String = when {
        equipment == null -> "Not set"
        equipment.fullGym -> "Full gym"
        equipment.gear.isEmpty() -> "Nothing"
        else -> {
            val owned = Gear.entries.filter { it in equipment.gear }
            val shown = owned.take(2).mapIndexed { i, gear -> if (i == 0) gear.label else gear.label.lowercase() }
            shown.joinToString(", ") + if (owned.size > 2) " +${owned.size - 2}" else ""
        }
    }

    /** "Sam · 180 cm · Male"; a missing height says so, since Today asks for it. */
    fun profile(name: String?, heightCm: Double?, sex: Sex): String = listOfNotNull(
        name?.trim()?.takeIf { it.isNotEmpty() },
        heightCm?.let { "${formatHeightCm(it)} cm" } ?: "height not set",
        sex.name.lowercase().replaceFirstChar { it.uppercase() },
    ).joinToString(" · ")

    /** Whether Health Connect is linked, and how many days it has filled. */
    fun health(link: HealthLink, daysSynced: Int): String = when (link) {
        HealthLink.UNAVAILABLE -> "Not on this device"
        HealthLink.UPDATE_REQUIRED -> "Update needed"
        HealthLink.NOT_CONNECTED -> "Not connected"
        HealthLink.CONNECTED ->
            if (daysSynced > 0) "Connected · $daysSynced ${if (daysSynced == 1) "day" else "days"}" else "Connected"
    }

    /** "1 trial", "2 trials": a count with its noun agreed. */
    private fun count(n: Int, one: String, many: String = one + "s") = "$n ${if (n == 1) one else many}"

    fun csvImported(sessions: Int, sets: Int, xp: Int, skipped: Int): String =
        "Imported ${count(sessions, "trial")} · ${count(sets, "set")} · +$xp XP · $skipped already in your Chronicle"

    fun archiveRestored(
        presets: Int, sessions: Int, sets: Int, stats: Int, titles: Int, skills: Int, healthDays: Int,
    ): String = "restored ${count(presets, "rite")} · ${count(sessions, "sealed trial")} · " +
        "${count(sets, "set")} · ${count(stats, "reading")} · ${count(titles, "deed")} · " +
        "${count(skills, "Journal attempt")} · ${count(healthDays, "health day")}"

    /**
     * Heights are almost always whole centimetres: show "180", not "180.0".
     * Falls back to the shared one-decimal format for a fractional reading.
     */
    fun formatHeightCm(cm: Double): String =
        if (cm == kotlin.math.floor(cm)) cm.toLong().toString() else formatBodyValue(cm)
}
