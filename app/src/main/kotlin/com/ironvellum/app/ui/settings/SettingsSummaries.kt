package com.ironvellum.app.ui.settings

import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.Equipment
import com.ironvellum.app.domain.Gear
import com.ironvellum.app.domain.RestAlert
import com.ironvellum.app.domain.Sex
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.components.formatBodyValue

/**
 * The hub's one-line values. Pure, so the wording is unit-tested rather than
 * eyeballed on a phone.
 */
object SettingsSummaries {

    /** "Not set", "Full gym", "Nothing", or the first two items in catalogue order plus a count. */
    fun armoury(equipment: Equipment?): String = when {
        equipment == null -> plain(R.string.settings_summary_not_set)
        equipment.fullGym -> plain(R.string.settings_summary_full_gym)
        equipment.gear.isEmpty() && equipment.kit.vestMaxKg == null && !equipment.kit.dipBelt -> plain(R.string.settings_summary_nothing)
        else -> {
            // The vest and belt follow the gear, so a lifter with only them still reads what they own.
            val owned = Gear.entries.filter { it in equipment.gear }.map { it.label } +
                listOfNotNull(
                    plain(R.string.settings_summary_weighted_vest).takeIf { equipment.kit.vestMaxKg != null },
                    plain(R.string.settings_summary_dip_belt).takeIf { equipment.kit.dipBelt },
                )
            val shown = owned.take(2).mapIndexed { i, label -> if (i == 0) label else label.lowercase() }
            shown.joinToString(", ") + if (owned.size > 2) " +${owned.size - 2}" else ""
        }
    }

    /** The rest between sets at a glance: "Rest off", or the way its end is announced. */
    fun trial(restOff: Boolean, alert: RestAlert): String = if (restOff) {
        plain(R.string.settings_summary_rest_off)
    } else {
        plain(R.string.settings_summary_rest_alert, alert.label.lowercase())
    }

    /** "Sam · 180 cm · Male"; a missing height says so, since Today asks for it. */
    fun profile(name: String?, heightCm: Double?, sex: Sex?): String = listOfNotNull(
        name?.trim()?.takeIf { it.isNotEmpty() }?.let(Words::displayName),
        heightCm?.let { plain(R.string.settings_summary_height_cm, formatHeightCm(it)) }
            ?: plain(R.string.settings_summary_height_not_set),
        sex?.label,
    ).joinToString(" · ")

    /** Whether Health Connect is linked, and how many days it has filled. */
    fun health(link: HealthLink, daysSynced: Int): String = when (link) {
        HealthLink.UNAVAILABLE -> plain(R.string.settings_summary_health_unavailable)
        HealthLink.UPDATE_REQUIRED -> plain(R.string.settings_summary_health_update)
        HealthLink.NOT_CONNECTED -> plain(R.string.settings_summary_health_not_connected)
        HealthLink.CONNECTED ->
            if (daysSynced > 0) {
                Words.text(UiText.plural(R.plurals.settings_summary_health_connected_days, daysSynced))
            } else {
                plain(R.string.settings_summary_health_connected)
            }
    }

    private fun plain(id: Int, vararg args: Any): String = Words.text(UiText.res(id, *args))

    private fun themedCount(plain: Int, themed: Int, n: Int): String = Words.text(UiText.themedPlural(plain, themed, n))

    private fun setsCount(n: Int): String = Words.text(UiText.plural(R.plurals.common_sets_count, n))

    fun csvImported(sessions: Int, sets: Int, xp: Int, skipped: Int): String =
        listOf(
            themedCount(R.plurals.settings_import_csv_sessions, R.plurals.settings_import_csv_sessions__t, sessions),
            setsCount(sets),
            plain(R.string.settings_import_csv_xp, xp),
            Words.text(UiText.themed(R.string.settings_import_csv_skipped, R.string.settings_import_csv_skipped__t, skipped)),
        ).joinToString(" · ")

    fun archiveRestored(
        presets: Int, sessions: Int, sets: Int, stats: Int, titles: Int, skills: Int, healthDays: Int,
    ): String = listOf(
        themedCount(R.plurals.settings_archive_restored_routines, R.plurals.settings_archive_restored_routines__t, presets),
        themedCount(R.plurals.settings_archive_restored_sessions, R.plurals.settings_archive_restored_sessions__t, sessions),
        setsCount(sets),
        themedCount(R.plurals.settings_archive_restored_stats, R.plurals.settings_archive_restored_stats__t, stats),
        Words.text(UiText.plural(R.plurals.settings_archive_restored_deeds, titles)),
        themedCount(R.plurals.settings_archive_restored_skills, R.plurals.settings_archive_restored_skills__t, skills),
        Words.text(UiText.plural(R.plurals.settings_archive_restored_health_days, healthDays)),
    ).joinToString(" · ")

    /**
     * Heights are almost always whole centimetres: show "180", not "180.0".
     * Falls back to the shared one-decimal format for a fractional reading.
     */
    fun formatHeightCm(cm: Double): String =
        if (cm == kotlin.math.floor(cm)) cm.toLong().toString() else formatBodyValue(cm)
}
