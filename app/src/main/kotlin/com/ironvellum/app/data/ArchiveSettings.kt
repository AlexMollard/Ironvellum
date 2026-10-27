package com.ironvellum.app.data

import android.content.Context
import android.content.SharedPreferences
import com.ironvellum.app.domain.EffortScale
import com.ironvellum.app.domain.LiftingExperience
import com.ironvellum.app.domain.LoadUnit
import com.ironvellum.app.domain.RestAlert
import com.ironvellum.app.ui.theme.ThemeMode
import com.ironvellum.app.domain.EarnedOrder
import com.ironvellum.app.domain.Gear
import com.ironvellum.app.domain.MuscleArea
import com.ironvellum.app.domain.PlateSizes
import com.ironvellum.app.domain.TrainingFocus
import com.ironvellum.app.domain.TrainingSplit
import com.ironvellum.app.domain.VolumeLevel
import com.ironvellum.app.domain.ProgramRules
import com.ironvellum.app.ui.theme.parseAccentHex
import org.json.JSONArray
import org.json.JSONTokener

/** Only explicitly portable preferences cross the archive boundary; an absent file is left unchanged. */
class ArchiveSettings(
    private val preferences: (String) -> SharedPreferences,
    private val onRestore: suspend (Map<String, Map<String, String>>, Boolean) -> Unit = { _, _ -> },
) {
    constructor(context: Context, onRestore: suspend (Map<String, Map<String, String>>, Boolean) -> Unit = { _, _ -> }) :
        this({ context.getSharedPreferences(it, Context.MODE_PRIVATE) }, onRestore)

    fun snapshot(includeDeviceOnly: Boolean = true): Map<String, Map<String, String>> = files.associateWith { file ->
        val values = preferences(file).all.mapNotNull { (key, value) ->
            if (kind(file, key) == null || (!includeDeviceOnly && file == "onboarding" && key == "sex_unset")) return@mapNotNull null
            val encoded = when (value) {
                is String -> "s:$value"
                is Boolean -> "b:$value"
                is Int -> "i:$value"
                is Long -> "l:$value"
                is Float -> "f:$value"
                is Set<*> -> "ss:" + JSONArray(value.sortedBy { it.toString() }).toString()
                else -> error("Unsupported preference $file/$key")
            }
            decode(file, key, encoded)
            key to encoded
        }.toMap()
        values
    }

    suspend fun restore(settings: Map<String, Map<String, String>>, includeDeviceOnly: Boolean = true) {
        validate(settings)
        settings.forEach { (file, values) ->
            val prefs = preferences(file)
            val editor = prefs.edit()
            prefs.all.keys.filter { kind(file, it) != null && (includeDeviceOnly || file != "onboarding" || it != "sex_unset") }.forEach { editor.remove(it) }
            values.filterKeys { includeDeviceOnly || file != "onboarding" || it != "sex_unset" }.forEach { (key, encoded) ->
                when (val value = decode(file, key, encoded)) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Set<*> -> { @Suppress("UNCHECKED_CAST") editor.putStringSet(key, value as Set<String>) }
                }
            }
            check(editor.commit()) { "Could not restore preferences: $file" }
        }
        onRestore(settings, includeDeviceOnly)
    }

    companion object {
        private val files = listOf("appearance", "trial_prefs", "trial", "onboarding", "reminders", "inbox_notifier", "weekly_recap", "program_answers", "deeds", "health_write", "warmup", "info_sheet_hints", "share_card")
        private fun kind(file: String, key: String): String? = when (file) {
            "appearance" -> when (key) {
                "theme", "primary", "secondary", "light_primary", "light_secondary", "liftingExperience", "loadUnit", "effortScale" -> "s"
                "themedNames", "fatigueAdvice", "logEffort" -> "b"
                "coverage_heavy_dismissed" -> "ss"
                else -> null
            }
            "trial_prefs" -> when (key) {
                "rest_off", "keep_screen_on", "live_update", "lifts_on_lock", "convert_card_done" -> "b"
                "rest_alert" -> "s"
                "rest_off_movements", "twin_kept" -> "ss"
                else -> null
            }
            "trial" -> when (key) { "asked_post_notifications" -> "b"; "post_notifications_not_now" -> "i"; else -> null }
            "onboarding" -> if (key in setOf("welcome_seen", "preferences_seen", "veil_intro_seen", "veil_terms_seen", "sex_unset", "dismissed", "term_seen_rank_titles", "term_seen_bmi_ffmi", "term_seen_deeds", "term_seen_skill_tiers")) "b" else null
            "reminders" -> if (key == "enabled") "b" else null
            "inbox_notifier" -> if (key in setOf("enabled", "asked_permission")) "b" else null
            "weekly_recap" -> if (key == "dismissed_reviews") "ss" else null
            "deeds" -> if (key == "earned_order") "s" else null
            "health_write", "warmup" -> if (key == "enabled") "b" else null
            "info_sheet_hints" -> if (key == "page_peek") "b" else null
            "share_card" -> if (key in setOf("include_exercise_notes", "include_effort", "sign_with_crest", "recap_records", "recap_streak")) "b" else null
            "program_answers" -> when (key) {
                "volume", "split", "focus", "gear", "dumbbellMaxKg", "vestMaxKg", "vestBlockKg", "priorities" -> "s"
                "fullGym", "dumbbellPair", "dipBelt", "compoundOnly" -> "b"
                "daysPerWeek", "maxExercises", "sessionMinutes" -> "i"
                else -> plateKind(key)
            }
            else -> null
        }


        private fun plateKind(key: String): String? {
            for (unit in LoadUnit.entries) {
                val prefix = "plates." + unit.name + "."
                if (key == prefix + "bar") return "s"
                if (PlateSizes.sizes(unit).any { key == prefix + "pairs.$it" }) return "i"
                val exercise = key.removePrefix(prefix + "barFor.")
                if (key.startsWith(prefix + "barFor.") && exercise.isNotBlank() &&
                    exercise.length <= 200 && exercise.none { it.isISOControl() }) return "s"
            }
            return null
        }
        fun validate(settings: Map<String, Map<String, String>>) {
            settings.forEach { (file, values) ->
                require(file in files) { "Unknown settings file: $file" }
                values.forEach { (key, value) -> decode(file, key, value) }
            }
        }

        private fun decode(file: String, key: String, encoded: String): Any {
            val type = kind(file, key) ?: throw IllegalArgumentException("Unknown setting: $file/$key")
            require(encoded.startsWith("$type:")) { "Invalid setting type: $file/$key" }
            val raw = encoded.substring(type.length + 1)
            return when (type) {
                "b" -> when (raw) { "true" -> true; "false" -> false; else -> error("Invalid boolean: $file/$key") }
                "i" -> raw.toInt().also {
                    require(when {
                        key == "daysPerWeek" -> it in 1..6
                        key == "maxExercises" -> it in ProgramRules.MAX_EXERCISES_RANGE
                        key == "sessionMinutes" -> it in ProgramRules.SESSION_MINUTES_OPTIONS
                        key.contains(".pairs.") -> it in 0..PlateStore.MAX_PAIRS
                        else -> it >= 0
                    })
                }
                "ss" -> {
                    val tokener = JSONTokener(raw)
                    val array = tokener.nextValue() as? JSONArray ?: throw IllegalArgumentException("Invalid string set: $file/$key")
                    require(tokener.nextClean() == '\u0000') { "Trailing string set data: $file/$key" }
                    (0 until array.length()).map { index ->
                        val value = array.get(index)
                        require(value is String && value.length <= 4096)
                        value
                    }.toSet()
                }
                else -> {
                    require(raw.length <= 4096)
                    val valid = when (key) {
                        "theme" -> ThemeMode.entries.any { it.name == raw }
                        "liftingExperience" -> LiftingExperience.entries.any { it.name == raw }
                        "loadUnit" -> LoadUnit.entries.any { it.name == raw }
                        "effortScale" -> EffortScale.entries.any { it.name == raw }
                        "rest_alert" -> RestAlert.entries.any { it.wire == raw }
                        "volume" -> VolumeLevel.entries.any { it.name == raw }
                        "split" -> TrainingSplit.entries.any { it.name == raw }
                        "focus" -> TrainingFocus.entries.any { it.name == raw }
                        "earned_order" -> EarnedOrder.entries.any { it.name == raw }
                        "gear" -> raw.isEmpty() || raw.split(",").all { token -> Gear.entries.any { it.name == token } }
                        "priorities" -> raw.isEmpty() || raw.split(",").all { token -> MuscleArea.entries.any { it.name == token } }
                        "dumbbellMaxKg", "vestMaxKg", "vestBlockKg" -> raw.toDoubleOrNull()?.let { it.isFinite() && it > 0 } == true
                        "primary", "secondary", "light_primary", "light_secondary" -> parseAccentHex(raw) != null
                        else -> if (file == "program_answers" && key.startsWith("plates.")) {
                            raw.toDoubleOrNull()?.let { it.isFinite() && it >= 0 } == true
                        } else true
                    }
                    require(valid) { "Invalid setting value: $file/$key" }
                    raw
                }
            }
        }
    }
}
