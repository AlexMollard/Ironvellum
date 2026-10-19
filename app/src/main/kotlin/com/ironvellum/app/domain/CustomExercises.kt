package com.ironvellum.app.domain

import androidx.annotation.StringRes
import com.ironvellum.app.R
import com.ironvellum.app.data.Lines
import com.ironvellum.app.data.Words

/** The Type chips of the exercise form: what the movement trains, which fixes its group and category. */
enum class CustomType(@StringRes private val labelRes: Int, val group: MuscleGroup, val category: String) {
    PULL(R.string.customex_type_pull, MuscleGroup.PULL, ""),
    PUSH(R.string.customex_type_push, MuscleGroup.PUSH, ""),
    LEGS(R.string.customex_type_legs, MuscleGroup.LEGS, ""),
    CORE(R.string.customex_type_core, MuscleGroup.CORE, ""),
    CARDIO(R.string.customex_type_cardio, MuscleGroup.CARDIO, "Cardio"),
    MOBILITY(R.string.customex_type_mobility, MuscleGroup.MOBILITY, "Mobility"),
    ;

    /** The chip's word. */
    val label: String get() = Words.text(UiText.res(labelRes))

    /** Cardio and mobility are activities in this codebase: a category, never gear-gated or generated. */
    val isActivity: Boolean get() = category.isNotBlank()

    companion object {
        fun of(group: MuscleGroup): CustomType? = entries.firstOrNull { it.group == group }
    }
}

/** The Logged-as chips: how a set of the movement is measured. */
enum class CustomLogged(@StringRes private val labelRes: Int, val metric: ExerciseMetric, @StringRes private val hintRes: Int) {
    REPS(R.string.customex_logged_reps, ExerciseMetric.REPS, R.string.customex_logged_reps_hint),
    HOLD(R.string.customex_logged_hold, ExerciseMetric.HOLD, R.string.customex_logged_hold_hint),
    TIME(R.string.customex_logged_time, ExerciseMetric.DURATION, R.string.customex_logged_time_hint),
    DISTANCE(R.string.customex_logged_distance, ExerciseMetric.DISTANCE_TIME, R.string.customex_logged_distance_hint),
    ;

    /** The chip's word. */
    val label: String get() = Words.text(UiText.res(labelRes))

    /** The line under the chips for the chosen one. */
    val hint: String get() = Words.text(UiText.res(hintRes))

    companion object {
        fun of(metric: ExerciseMetric): CustomLogged? = entries.firstOrNull { it.metric == metric }
    }
}

/** The Needs chips: the [Gear] list plus a machine or cable, and nothing at all. */
enum class CustomNeed(@StringRes private val labelRes: Int, val gear: Gear?) {
    MACHINE(R.string.customex_need_machine, null),
    BARBELL(R.string.program_gear_barbell, Gear.BARBELL),
    DUMBBELLS(R.string.program_gear_dumbbells, Gear.DUMBBELLS),
    BENCH(R.string.program_gear_bench, Gear.BENCH),
    PULL_UP_BAR(R.string.program_gear_pull_up_bar, Gear.PULL_UP_BAR),
    DIP_BARS(R.string.program_gear_dip_bars, Gear.DIP_BARS),
    RINGS(R.string.program_gear_rings, Gear.RINGS),
    PARALLETTES(R.string.program_gear_parallettes, Gear.PARALLETTES),
    AB_WHEEL(R.string.program_gear_ab_wheel, Gear.AB_WHEEL),
    NOTHING(R.string.customex_need_nothing, null),
    ;

    /** The chip's word. */
    val label: String get() = Words.text(UiText.res(labelRes))
}

/** What the exercise form hands the Repository: the chips, the name and the tapped muscles (see [CustomExercises.shares]). */
class CustomSpec(
    val name: String,
    val type: CustomType,
    val logged: CustomLogged,
    val needs: Set<CustomNeed>,
    val shares: Map<Muscle, Double>,
)

sealed interface CustomSave {
    class Saved(val exercise: Exercise) : CustomSave

    /** [message] says why, in the form's own words. */
    class Rejected(val message: String) : CustomSave
}

/**
 * Movements the lifter made themselves.
 *
 * The catalogue tables ([MuscleMap], [GearRequirements], [MovementDifficulty]) are name-keyed constants, so a
 * custom row's muscles, gear and price live here instead, in a registry the Repository refreshes from the
 * catalogue ([sync]) whenever it reads or changes it. Each table falls back to the registry for a name it does
 * not know. The registry never shadows a catalogue name: [nameProblem] refuses one, and [MuscleMap.keys] stays
 * catalogue-only so the coverage tests still walk back to real rows.
 *
 * SCORING (the choice, and why): a custom movement is priced at [TIER], the lowest tier, and a machine or cable
 * custom movement at [MACHINE_LOAD], the sled band. Catalogue isolation work (curls, raises) is tier I, so no
 * invented name can earn more per rep than the cheapest real movement, and nobody can mint XP by naming a heavy
 * lift something new. The lifter's own history is unaffected: they still log real kilos and reps and still see
 * their own records; only the per-rep price is pinned. Nothing a custom row does can reach an ally board
 * ([LiftBoards] matches catalogue names, and a custom name can never equal one). Cardio and mobility customs keep
 * the activity pricing the catalogue activities use (category MET, [ActivityScore]).
 */
object CustomExercises {

    /** Intensity 1 (XP per rep weight 1): never above the cheapest catalogue movement. */
    const val TIER = 1

    /** Machine and cable customs price their marked kilo at the sled band, the cheapest the catalogue states. */
    const val MACHINE_LOAD = MovementDifficulty.SLED_LOAD

    const val MAX_NAME = 40

    /** Main work credits a full set, assist half a set: the library rows' own scale. */
    const val MAIN_SHARE = 1.0
    const val ASSIST_SHARE = 0.5

    // ---------------------------------------------------------------- derivation

    /** Weighted is never asked: any loaded implement (barbell, dumbbells, a machine or cable) means kilos get logged. */
    fun isWeighted(needs: Set<CustomNeed>): Boolean =
        needs.any { it == CustomNeed.MACHINE || it == CustomNeed.BARBELL || it == CustomNeed.DUMBBELLS }

    /** MAIN first in the given order (the first 1.0 is the dominant muscle), then ASSIST; a muscle in both is MAIN. */
    fun shares(main: List<Muscle>, assist: List<Muscle>): Map<Muscle, Double> = linkedMapOf<Muscle, Double>().apply {
        main.forEach { put(it, MAIN_SHARE) }
        assist.filterNot { it in main }.forEach { put(it, ASSIST_SHARE) }
    }

    /** The one gear alternative a custom movement states: every chosen item at once; none means no requirement. */
    fun gearAlternatives(needs: Set<CustomNeed>): List<Set<Gear>> {
        val gear = needs.mapNotNull { it.gear }.toSet()
        return if (gear.isEmpty()) emptyList() else listOf(gear)
    }

    /**
     * The profile the figure, coverage and scoring read; null when no muscle was chosen (a CSV keep-as-new row), so
     * such a row stays unprofiled exactly as a lifter's unknown movement always was.
     *
     * The pattern comes from the type and the count of MAIN muscles: one main muscle is isolation work, several are
     * a compound of the type's pattern, core is core, and activities take the neutral core pattern like the
     * catalogue's own. Activities are practised submaximally, so their shares cap at assist and they lead with no
     * muscle; a movement with only ASSIST muscles chosen leads with none either.
     */
    fun profileFor(group: MuscleGroup, category: String, shares: Map<Muscle, Double>): ExerciseProfile? {
        if (shares.isEmpty()) return null
        val activity = category.isNotBlank()
        val main = shares.values.count { it >= MAIN_SHARE }
        val pattern = when {
            activity || group == MuscleGroup.CORE -> MovementPattern.CORE
            main < 2 -> MovementPattern.ISOLATION
            group == MuscleGroup.PULL -> MovementPattern.HORIZONTAL_PULL
            group == MuscleGroup.PUSH -> MovementPattern.HORIZONTAL_PUSH
            else -> MovementPattern.SQUAT
        }
        return ExerciseProfile(
            muscles = if (activity) shares.mapValues { minOf(it.value, ASSIST_SHARE) } else shares,
            pattern = pattern,
            compound = activity || (main >= 2 && pattern != MovementPattern.CORE),
            stretchBias = false,
            noLeadByDesign = activity || main == 0,
        )
    }

    // ---------------------------------------------------------------- the name

    /** "  Reverse   hyper " as "Reverse hyper". */
    fun cleanName(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")

    /**
     * Why [raw] cannot name a custom movement, or null when it can. A name that matches (case-insensitively) any
     * movement in [existing], the catalogue and the lifter's own, or a skill-tree, library or scored name, is
     * refused with a line pointing at the existing one - the check that also stops a custom row borrowing a real
     * movement's tier. [own] is the row being edited, which may keep its own name.
     */
    fun nameProblem(raw: String, existing: Collection<String>, own: String? = null): String? {
        val name = cleanName(raw)
        if (name.isEmpty()) return Words.text(UiText.res(R.string.customex_error_name_empty))
        if (name.length > MAX_NAME) return Words.text(UiText.plural(R.plurals.customex_error_name_long, MAX_NAME))
        val key = name.lowercase()
        if (own != null && key == cleanName(own).lowercase()) return null
        val hit = existing.firstOrNull { cleanName(it).lowercase() == key }
            ?: Skills.forName(name)?.name
            ?: MuscleMap.keys.firstOrNull { it == key }?.let { name }
            ?: ExerciseLibrary.names.firstOrNull { it == key }?.let { name }
            ?: LiftBoards.boardsFor(name).firstOrNull()?.let { name }
        return hit?.let { Lines.alreadyInLibrary(it) }
    }

    // ---------------------------------------------------------------- storage encoding

    fun encodeNeeds(needs: Set<CustomNeed>): String =
        CustomNeed.entries.filter { it in needs && it != CustomNeed.NOTHING }.joinToString(",") { it.name }

    /** Unknown tokens drop out, so a row from a newer build never fails the catalogue read. */
    fun decodeNeeds(stored: String): Set<CustomNeed> =
        stored.split(',').mapNotNull { token -> CustomNeed.entries.firstOrNull { it.name == token.trim() } }.toSet()

    fun encodeShares(shares: Map<Muscle, Double>): String =
        shares.entries.joinToString(";") { (muscle, share) -> "${muscle.name}=$share" }

    /** Order is kept (MAIN first); unknown muscles and unreadable shares drop out. */
    fun decodeShares(stored: String): Map<Muscle, Double> {
        val out = linkedMapOf<Muscle, Double>()
        stored.split(';').forEach { part ->
            val (name, value) = part.split('=', limit = 2).takeIf { it.size == 2 } ?: return@forEach
            val muscle = Muscle.entries.firstOrNull { it.name == name.trim() } ?: return@forEach
            val share = value.trim().toDoubleOrNull()?.takeIf { it > 0.0 && it <= 1.0 } ?: return@forEach
            out[muscle] = share
        }
        return out
    }

    // ---------------------------------------------------------------- the registry

    /** What the registry needs of one custom catalogue row. */
    class Row(
        val name: String,
        val group: MuscleGroup,
        val category: String,
        val needs: Set<CustomNeed>,
        val shares: Map<Muscle, Double>,
    )

    private class Entry(val gear: List<Set<Gear>>, val machine: Boolean, val profile: ExerciseProfile?)

    @Volatile
    private var entries: Map<String, Entry> = emptyMap()

    /** Replaces the registry with [rows] (idempotent; called on every catalogue read or change). */
    fun sync(rows: List<Row>) {
        entries = rows.associate { row ->
            row.name.trim().lowercase() to Entry(
                gear = gearAlternatives(row.needs),
                machine = CustomNeed.MACHINE in row.needs,
                profile = profileFor(row.group, row.category, row.shares),
            )
        }
    }

    /** For tests: forget every custom movement. */
    fun reset() {
        entries = emptyMap()
    }

    private fun entry(name: String): Entry? = entries[name.trim().lowercase()]

    fun isCustom(name: String): Boolean = entry(name) != null

    internal fun profile(name: String): ExerciseProfile? = entry(name)?.profile

    /** Null when it needs no listed gear, which [GearRequirements] reads as "allowed with nothing" (or machine). */
    internal fun gear(name: String): List<Set<Gear>>? = entry(name)?.gear?.takeIf { it.isNotEmpty() }

    internal fun loadFactor(name: String): Double? = entry(name)?.let { if (it.machine) MACHINE_LOAD else null }

    internal fun tier(name: String): Int? = entry(name)?.let { TIER }
}
