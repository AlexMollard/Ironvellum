package com.ironvellum.app.data

import android.content.Context
import androidx.core.content.edit
import com.ironvellum.app.domain.EquipmentAccess
import com.ironvellum.app.domain.MuscleArea
import com.ironvellum.app.domain.TrainingFocus
import com.ironvellum.app.domain.TrainingSplit
import com.ironvellum.app.domain.VolumeLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the lifter last told the program generator, kept so the Weekly
 *  Coverage screen and the next builder visit can read the SAME volume and
 *  goal she answered with. The Weekly Coverage bug this replaces: it guessed
 *  the level from history (none -> lean) and focus from the profile's
 *  progression mode (default STRENGTH), contradicting the STANDARD / MUSCLE
 *  week she had just accepted moments earlier. */
data class ProgramAnswers(
    val volume: VolumeLevel,
    val focus: TrainingFocus,
    val equipment: EquipmentAccess,
    val daysPerWeek: Int,
    val priorities: Set<MuscleArea>,
    val split: TrainingSplit,
)

/**
 * Process-wide store over SharedPreferences ("program_answers"). Enum names
 * travel as strings; anything missing, unknown or corrupt makes the whole
 * answer read back null - a stale hand-edited value must never crash a read.
 * The StateFlow mirrors the latest get/save so view models observe changes
 * without re-reading prefs, the same way OnboardingViewModel keeps its
 * dismissed flag.
 */
object ProgramAnswersStore {

    private const val PREFS = "program_answers"
    private const val KEY_VOLUME = "volume"
    private const val KEY_SPLIT = "split"
    private const val KEY_FOCUS = "focus"
    private const val KEY_EQUIPMENT = "equipment"
    private const val KEY_DAYS = "daysPerWeek"
    private const val KEY_PRIORITIES = "priorities"

    private val _answers = MutableStateFlow<ProgramAnswers?>(null)

    /** Latest known answers, or null before the first read and when unset. */
    val answers: StateFlow<ProgramAnswers?> = _answers.asStateFlow()

    fun get(context: Context): ProgramAnswers? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val parsed = runCatching {
            val volume = prefs.getString(KEY_VOLUME, null)?.let { VolumeLevel.valueOf(it) }
            val split = prefs.getString(KEY_SPLIT, null)?.let { TrainingSplit.valueOf(it) }
            val focus = prefs.getString(KEY_FOCUS, null)?.let { TrainingFocus.valueOf(it) }
            val equipment = prefs.getString(KEY_EQUIPMENT, null)?.let { EquipmentAccess.valueOf(it) }
            val days = prefs.getInt(KEY_DAYS, -1)
            val priorities = prefs.getString(KEY_PRIORITIES, null)
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.map { MuscleArea.valueOf(it) }
                ?.toSet()
                ?: emptySet()
            if (volume == null || split == null || focus == null || equipment == null || days !in split.dayOptions) {
                null
            } else {
                ProgramAnswers(volume, focus, equipment, days, priorities, split)
            }
        }.getOrNull()
        _answers.value = parsed
        return parsed
    }

    fun save(context: Context, answers: ProgramAnswers) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_VOLUME, answers.volume.name)
            putString(KEY_SPLIT, answers.split.name)
            putString(KEY_FOCUS, answers.focus.name)
            putString(KEY_EQUIPMENT, answers.equipment.name)
            putInt(KEY_DAYS, answers.daysPerWeek)
            putString(KEY_PRIORITIES, answers.priorities.joinToString(",") { it.name })
        }
        _answers.value = answers
    }
}
