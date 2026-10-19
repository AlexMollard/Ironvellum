package com.ironvellum.app.domain

import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import kotlin.math.max

/**
 * Two movements of one session that do one job: the same pattern leading with
 * the same [muscle] at near-identical muscle profiles. [alternatives] are up
 * to three movements that could replace the one with fewer sets, best first;
 * empty when no catalogue was supplied.
 */
data class OverlapFinding(
    val a: PlannedEntry,
    val b: PlannedEntry,
    val muscle: Muscle,
    val alternatives: Ranked,
)

/** Finds rows of one session that duplicate each other. */
object Overlap {

    /** Cosine similarity of two same-job movements from which they count as overlapping. */
    const val MIN_SIMILARITY = 0.75

    /** Most replacements offered per finding. */
    private const val MAX_ALTERNATIVES = 3

    /**
     * Every pair in [preset] that shares a pattern and a lead muscle
     * ([ProgramGenerator.sharesJob], the predicate the generator's own
     * redundancy rule uses) and is at least [MIN_SIMILARITY] alike. With a
     * [request] and [catalogue] each finding also carries replacements for
     * the entry with fewer sets (the later one on a tie).
     */
    fun detect(
        preset: PlannedPreset,
        request: ProgramRequest? = null,
        catalogue: List<Exercise> = emptyList(),
    ): List<OverlapFinding> {
        val pool = if (request != null) ProgramGenerator.prescribable(catalogue, request) else emptyList()
        val findings = mutableListOf<OverlapFinding>()
        val entries = preset.entries
        for (i in entries.indices) for (j in i + 1 until entries.size) {
            val a = entries[i]
            val b = entries[j]
            val pa = MuscleMap.profile(a) ?: continue
            val pb = MuscleMap.profile(b) ?: continue
            if (!ProgramGenerator.sharesJob(pa, pb) || ProgramGenerator.similarity(pa, pb) < MIN_SIMILARITY) continue
            val muscle = ProgramGenerator.dominantMuscle(pa) ?: continue
            val (replaced, kept) = if (a.sets < b.sets) a to b else b to a
            findings += OverlapFinding(a, b, muscle, alternativesFor(preset, replaced, kept, muscle, pool))
        }
        return findings
    }

    private fun alternativesFor(
        preset: PlannedPreset,
        replaced: PlannedEntry,
        kept: PlannedEntry,
        muscle: Muscle,
        pool: List<Exercise>,
    ): Ranked {
        val keptProfile = MuscleMap.profile(kept) ?: return Ranked(emptyList())
        val taken = preset.entries.map { it.exerciseName.lowercase() }.toSet()
        val options = pool
            .filter { it.name.lowercase() !in taken }
            .mapNotNull { exercise ->
                val profile = MuscleMap.profile(exercise.name) ?: return@mapNotNull null
                val share = profile.muscles[muscle] ?: 0.0
                val similarity = ProgramGenerator.similarity(profile, keptProfile)
                if (share < MIN_SHARE || similarity >= MIN_SIMILARITY) return@mapNotNull null
                val tags = buildList {
                    if (share >= DIRECT_SHARE) add(ReasonTag(Reason.DIRECT_MUSCLE, muscle = muscle))
                    if (similarity < LOW_OVERLAP_BELOW) add(ReasonTag(Reason.LOW_OVERLAP, other = kept.exerciseName))
                }.take(2)
                val swapped = preset.copy(entries = preset.entries.map { if (it === replaced) replaced.copy(exerciseName = exercise.name, modifiers = "") else it })
                Candidate(
                    exercise = exercise,
                    tags = tags,
                    score = share + (1.0 - similarity) * 0.5,
                    coverageDelta = Easing.delta(listOf(preset), listOf(swapped)),
                )
            }
            .sortedWith(compareByDescending<Candidate> { it.score }.thenBy { it.exercise.name })
            .take(MAX_ALTERNATIVES)
        return Ranked(options)
    }

    /** Lowest share of the lead muscle a replacement must carry, and the share that reads as direct work. */
    private const val MIN_SHARE = 0.5
    private const val DIRECT_SHARE = 0.9

    private const val LOW_OVERLAP_BELOW = 0.5
}

/** What an easing option does. */
enum class EasingKind { TRIM_SET, SWAP, MOVE_DAY }

/**
 * One way to ease a heavy muscle: the pure-data [op] (applied to the session
 * named [presetName]), why it is suggested, the [coverageDelta] it causes on
 * every muscle it touches, and for a swap the three ranked [alternatives]
 * (the first is the one [op] swaps to). Defined here rather than reusing
 * PlanChange so the Temper work can land first; unify them later.
 */
data class EasingOption(
    val id: String,
    val kind: EasingKind,
    val op: ChangeOp,
    val tags: List<ReasonTag>,
    val coverageDelta: Map<Muscle, Double>,
    val alternatives: Ranked?,
    val presetName: String,
)

/** A heavy muscle with its ranked easing [options]; empty when nothing safe eases it. */
data class EasingSuggestion(val muscle: Muscle, val load: MuscleLoad, val options: List<EasingOption>)

/**
 * Eases heavy muscles (over 20 weekly or 10 per-session fractional hard sets):
 * trim a set from the biggest contributor, swap it for a movement that loads
 * the muscle less, or move it to a quieter day. An option that would leave
 * any muscle it takes sets from under four hard sets is never suggested, and
 * neither is one that adds heavy load somewhere else.
 */
object Easing {

    private const val EPS = 1e-9
    private const val MAX_ALTERNATIVES = 3

    /** Fractional sets by which [load] is past the heavy lines. */
    fun excess(load: MuscleLoad): Double =
        max(0.0, load.weekly - ProgramRules.HEAVY_WEEK) + max(0.0, load.maxSession - ProgramRules.HEAVY_SESSION)

    /** Per-muscle weekly volume change from [before] to [after]; zero entries dropped. */
    internal fun delta(before: List<PlannedPreset>, after: List<PlannedPreset>): Map<Muscle, Double> {
        val was = ProgramRules.weeklyVolume(before)
        val now = ProgramRules.weeklyVolume(after)
        return (was.keys + now.keys)
            .associateWith { (now[it] ?: 0.0) - (was[it] ?: 0.0) }
            .filterValues { kotlin.math.abs(it) > EPS }
    }

    private class Contributor(val presetIndex: Int, val entry: PlannedEntry, val credit: Double)

    private class Trial(
        val after: List<PlannedPreset>,
        val delta: Map<Muscle, Double>,
        val removed: Double,
        val collateral: Double,
    )

    /**
     * Heavy muscles of [week], most excess first, each with its options best
     * first (most heavy load removed, then least collateral coverage lost).
     * Empty when nothing is heavy. [strength] only prices a swap's load.
     */
    fun suggest(
        week: List<PlannedPreset>,
        request: ProgramRequest,
        catalogue: List<Exercise>,
        strength: StrengthProfile = StrengthProfile(),
    ): List<EasingSuggestion> {
        val coverage = ProgramRules.coverage(week)
        val heavy = coverage.values
            .filter { it.heavy }
            .sortedWith(compareByDescending<MuscleLoad> { excess(it) }.thenBy { it.muscle.ordinal })
        if (heavy.isEmpty()) return emptyList()
        val pool by lazy { ProgramGenerator.prescribable(catalogue, request) }
        return heavy.map { load -> EasingSuggestion(load.muscle, load, optionsFor(load, week, request, { pool }, strength)) }
    }

    private fun optionsFor(
        load: MuscleLoad,
        week: List<PlannedPreset>,
        request: ProgramRequest,
        pool: () -> List<Exercise>,
        strength: StrengthProfile,
    ): List<EasingOption> {
        val muscle = load.muscle
        val contributors = contributorsOf(load, week)
        val volumeBefore = ProgramRules.weeklyVolume(week)
        val excessBefore = totalExcess(week)
        val targetBefore = excessOf(week, muscle)

        fun trial(after: List<PlannedPreset>): Trial? {
            val volumeAfter = ProgramRules.weeklyVolume(after)
            for ((m, was) in volumeBefore) {
                val now = volumeAfter[m] ?: 0.0
                if (now < was - EPS && now < ProgramRules.COVERED_MIN - EPS) return null
            }
            val removed = targetBefore - excessOf(after, muscle)
            if (removed <= EPS || totalExcess(after) >= excessBefore - EPS) return null
            val delta = delta(week, after)
            val collateral = delta.filter { it.key != muscle && it.value < 0.0 }.values.sumOf { -it }
            return Trial(after, delta, removed, collateral)
        }

        val found = mutableListOf<Pair<EasingOption, Trial>>()

        // 1. Trim one set from the biggest contributor that can spare it.
        for (c in contributors) {
            if (c.entry.sets <= 1) continue
            val after = replaceEntry(week, c.presetIndex, c.entry, c.entry.copy(sets = c.entry.sets - 1))
            val t = trial(after) ?: continue
            found += EasingOption(
                id = "ease:${muscle.name}:trim:${week[c.presetIndex].name}:${c.entry.exerciseName}",
                kind = EasingKind.TRIM_SET,
                op = ChangeOp.SetSets(c.entry.exerciseName, c.entry.sets - 1),
                tags = listOf(ReasonTag(Reason.DIRECT_MUSCLE, muscle = muscle)),
                coverageDelta = t.delta,
                alternatives = null,
                presetName = week[c.presetIndex].name,
            ) to t
            break
        }

        // 2. Swap it for a movement that loads the muscle less, three ranked alternatives.
        swap@ for (c in contributors) {
            val fromProfile = MuscleMap.profile(c.entry) ?: continue
            val fromShare = fromProfile.muscles[muscle] ?: 0.0
            val preset = week[c.presetIndex]
            val taken = preset.entries.map { it.exerciseName.lowercase() }.toSet()
            val trials = pool()
                .filter { it.name.lowercase() !in taken }
                .mapNotNull { exercise ->
                    val profile = MuscleMap.profile(exercise.name) ?: return@mapNotNull null
                    if (profile.pattern != fromProfile.pattern) return@mapNotNull null
                    if ((profile.muscles[muscle] ?: 0.0) >= fromShare - EPS) return@mapNotNull null
                    val replacement = entryFor(c.entry, exercise, request, strength)
                    val t = trial(replaceEntry(week, c.presetIndex, c.entry, replacement)) ?: return@mapNotNull null
                    Triple(exercise, replacement, t)
                }
                .sortedWith(
                    compareByDescending<Triple<Exercise, PlannedEntry, Trial>> { it.third.removed }
                        .thenBy { it.third.collateral }
                        .thenByDescending { ProgramGenerator.similarity(fromProfile, MuscleMap.profile(it.first.name)!!) }
                        .thenBy { it.first.name },
                )
                .take(MAX_ALTERNATIVES)
            if (trials.isEmpty()) continue@swap
            val ranked = Ranked(
                trials.map { (exercise, _, t) ->
                    val profile = MuscleMap.profile(exercise.name)!!
                    Candidate(
                        exercise = exercise,
                        tags = buildList {
                            add(ReasonTag(Reason.STAND_IN_FOR, other = c.entry.exerciseName))
                            if (ProgramGenerator.similarity(fromProfile, profile) < LOW_OVERLAP_BELOW) {
                                add(ReasonTag(Reason.LOW_OVERLAP, muscle = muscle))
                            }
                        }.take(2),
                        score = t.removed - t.collateral * 0.01,
                        coverageDelta = t.delta,
                    )
                },
            )
            val (_, best, bestTrial) = trials.first()
            found += EasingOption(
                id = "ease:${muscle.name}:swap:${preset.name}:${c.entry.exerciseName}",
                kind = EasingKind.SWAP,
                op = ChangeOp.SwapEntry(c.entry.exerciseName, best),
                tags = ranked.options.first().tags,
                coverageDelta = bestTrial.delta,
                alternatives = ranked,
                presetName = preset.name,
            ) to bestTrial
            break
        }

        // 3. Move it to the best-fitting other day.
        move@ for (c in contributors) {
            val preset = week[c.presetIndex]
            for (day in Recovery.bestFitDays(c.entry, week, request, fromPreset = preset.name).options) {
                val target = week.indexOfFirst { it.scheduledDay == day.day && it.name == day.presetName }
                if (target < 0) continue
                val after = week.mapIndexed { i, p ->
                    when (i) {
                        c.presetIndex -> p.copy(entries = p.entries.filter { it !== c.entry })
                        target -> p.copy(entries = p.entries + c.entry)
                        else -> p
                    }
                }
                val t = trial(after) ?: continue
                found += EasingOption(
                    id = "ease:${muscle.name}:move:${preset.name}:${c.entry.exerciseName}",
                    kind = EasingKind.MOVE_DAY,
                    op = ChangeOp.MoveEntryToDay(c.entry.exerciseName, day.day),
                    tags = day.tags,
                    coverageDelta = t.delta,
                    alternatives = null,
                    presetName = preset.name,
                ) to t
                break@move
            }
        }

        return found
            .sortedWith(
                compareByDescending<Pair<EasingOption, Trial>> { it.second.removed }
                    .thenBy { it.second.collateral }
                    .thenBy { it.first.kind.ordinal }
                    .thenBy { it.first.id },
            )
            .map { it.first }
    }

    /**
     * The entries that load [load]'s muscle, biggest first: sets times share,
     * ties to the later day, then name. When only a session is heavy, just the
     * sessions over the line count (moving or trimming elsewhere cannot help).
     */
    private fun contributorsOf(load: MuscleLoad, week: List<PlannedPreset>): List<Contributor> {
        val weeklyHeavy = load.weekly > ProgramRules.HEAVY_WEEK + EPS
        val list = mutableListOf<Contributor>()
        week.forEachIndexed { index, preset ->
            if (!weeklyHeavy && (HardSets.planned(listOf(preset))[load.muscle] ?: 0.0) <= ProgramRules.HEAVY_SESSION + EPS) {
                return@forEachIndexed
            }
            for (entry in preset.entries) {
                val share = MuscleMap.profile(entry)?.muscles?.get(load.muscle) ?: 0.0
                if (share > 0.0) list += Contributor(index, entry, entry.sets * share)
            }
        }
        return list.sortedWith(
            compareByDescending<Contributor> { it.credit }
                .thenByDescending { week[it.presetIndex].scheduledDay ?: 0 }
                .thenBy { it.entry.exerciseName },
        )
    }

    private fun excessOf(week: List<PlannedPreset>, muscle: Muscle): Double =
        ProgramRules.coverage(week).getValue(muscle).let { excess(it) }

    private fun totalExcess(week: List<PlannedPreset>): Double = ProgramRules.coverage(week).values.sumOf { excess(it) }

    private fun replaceEntry(week: List<PlannedPreset>, index: Int, from: PlannedEntry, to: PlannedEntry): List<PlannedPreset> =
        week.mapIndexed { i, p -> if (i == index) p.copy(entries = p.entries.map { if (it === from) to else it }) else p }

    /**
     * [from]'s slot refilled with [exercise]: same sets and rep anchor, load
     * priced from [strength], the dosing fields set by [ProgramRules.withPlan].
     */
    fun entryFor(from: PlannedEntry, exercise: Exercise, request: ProgramRequest, strength: StrengthProfile): PlannedEntry {
        val profile = MuscleMap.profile(exercise.name)
        val fill = ProgramGenerator.fillLoad(
            exercise, strength, from.reps, ProgramRules.loadRir(profile?.compound ?: true), request.equipment,
        )
        return ProgramRules.withPlan(
            PlannedEntry(
                exerciseName = exercise.name,
                sets = from.sets,
                reps = fill?.reps ?: from.reps,
                targetWeightKg = fill?.kg,
                why = Words.text(UiText.res(R.string.program_extra_eases_load, from.exerciseName.lowercase())),
                loadNote = fill?.note,
            ),
            request.volume, request.focus,
        )
    }

    private const val LOW_OVERLAP_BELOW = 0.5
}
