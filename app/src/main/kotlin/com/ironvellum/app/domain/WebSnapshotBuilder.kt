package com.ironvellum.app.domain

import java.time.ZoneId

/** Phone wording is supplied by the caller so building remains independent of UI state. */
interface FatigueText {
    fun cause(c: CauseShare, nowMs: Long, zone: ZoneId): String
    fun freshBy(by: LightBy): String?
}

/** Figure and crest are explicit inputs: neither is present in PlayerProfile. */
data class SnapshotInput(
    val history: List<Pair<WorkoutSession, List<SessionSet>>>, val exercises: Map<Long, Exercise>,
    val stats: List<StatEntry>, val profile: PlayerProfile, val titles: List<UnlockedTitle>,
    val presets: List<WorkoutPreset>, val unit: LoadUnit, val effort: EffortScale, val themed: Boolean,
    val zone: ZoneId, val nowMs: Long, val appearance: WebSnapshot.Appearance, val fatigueText: FatigueText,
    val displayName: String, val figure: String, val crestId: String?,
)

object WebSnapshotBuilder {
    internal fun windowState(value: Double): String = when {
        value <= 0 -> "none"
        value < ProgramRules.COVERED_MIN -> "below"
        value >= ProgramRules.HEAVY_WEEK -> "heavy"
        else -> "covered"
    }

    fun build(input: SnapshotInput): WebSnapshot {
        val (profile, deeds) = profileAndDeeds(input)
        val (week, trends) = weekAndTrends(input)
        return WebSnapshot(
            builtAtMs = input.nowMs, zone = input.zone.id, unit = input.unit.name.lowercase(),
            effort = input.effort.name.lowercase(), words = if (input.themed) "themed" else "plain",
            rir = Effort.CHIPS.associate { it.toString() to Effort.rir(it) }, appearance = input.appearance,
            profile = profile, deeds = deeds, muscles = Muscle.entries.associate { it.name to it.label },
            week = week, months = monthEntries(input), sessions = sessionEntries(input).mapKeys { it.key.toString() },
            lifts = liftEntries(input), fatigue = fatigueSection(input), trends = trends,
        )
    }

    private fun date(input: SnapshotInput, session: WorkoutSession): java.time.LocalDate =
        java.time.Instant.ofEpochMilli(session.completedAtMs ?: session.startedAtMs).atZone(input.zone).toLocalDate()

    private fun monday(day: java.time.LocalDate): java.time.LocalDate =
        day.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))

    private fun ms(day: java.time.LocalDate, input: SnapshotInput): Long =
        day.atStartOfDay(input.zone).toInstant().toEpochMilli()

    internal fun monthEntries(input: SnapshotInput): Map<String, WebSnapshot.Month> {
        val today = java.time.Instant.ofEpochMilli(input.nowMs).atZone(input.zone).toLocalDate()
        val now = java.time.YearMonth.from(today)
        val logged = input.history.flatMap { (s, sets) -> HardSets.logged(s.completedAtMs ?: s.startedAtMs, sets, input.exercises) }
        val present = input.history.map { java.time.YearMonth.from(date(input, it.first)) }.toSet()
        val first = input.history.minOfOrNull { date(input, it.first) }
        return (WebSnapshotTuning.MONTHS - 1 downTo 0).map { now.minusMonths(it.toLong()) }.filter { it in present }.associate { month ->
            val from = month.atDay(1)
            val to = month.plusMonths(1).atDay(1)
            val inside = input.history.filter { (s, _) -> date(input, s) >= from && date(input, s) < to }
            val weeks = PeriodStats.weeks(input.history, input.exercises, from, to, input.zone)
            val top = inside.flatMap { (s, sets) ->
                sets.filter { it.done && it.kind.countsAsWork && input.exercises[it.exerciseId]?.metric?.isStrength == true }
                    .map { (it.exerciseName.ifBlank { input.exercises[it.exerciseId]?.name.orEmpty() }) to s.id }.distinct()
            }.groupBy({ it.first }, { it.second }).map { (name, sessions) ->
                WebSnapshot.TopLift(name, ExerciseArtIndex.forName(name)?.id?.removePrefix("own_"), sessions.distinct().size)
            }.sortedWith(compareByDescending<WebSnapshot.TopLift> { it.sessions }.thenBy { it.name })
            val activeFrom = maxOf(from, first ?: from)
            val activeTo = minOf(today, to.minusDays(1))
            val activeDays = (java.time.temporal.ChronoUnit.DAYS.between(activeFrom, activeTo) + 1).coerceAtLeast(1)
            month.toString() to WebSnapshot.Month(inside.size, inside.size / (activeDays / 7.0),
                weeks.map { w -> WebSnapshot.MonthWeek(w.weekStart.toString(),
                    HardSets.inRange(logged, ms(w.weekStart, input), ms(w.weekStart.plusDays(7), input)).values.sum()) }, top)
        }
    }

    internal fun weekAndTrends(input: SnapshotInput): Pair<WebSnapshot.Week, WebSnapshot.Trends> {
        val today = java.time.Instant.ofEpochMilli(input.nowMs).atZone(input.zone).toLocalDate()
        val start = monday(today)
        val logged = input.history.flatMap { (s, sets) -> HardSets.logged(s.completedAtMs ?: s.startedAtMs, sets, input.exercises) }
        val byWeek = input.history.groupBy { monday(date(input, it.first)) }
        fun weekVolume(day: java.time.LocalDate) = byWeek[day].orEmpty().sumOf { WorkoutShare.totals(it.second, input.exercises).movedKg }.toDouble()
        val weeks = (WebSnapshotTuning.WEEKS - 1 downTo 0).map { offset ->
            val day = start.minusWeeks(offset.toLong())
            val credit = HardSets.inRange(logged, ms(day, input), ms(day.plusDays(7), input))
            val groups = WebSnapshotTuning.Group.entries.associate { group ->
                group.name to credit.filterKeys { WebSnapshotTuning.GROUPS.getValue(it) == group }.values.sum()
            }.filterValues { it != 0.0 }
            WebSnapshot.TrendWeek(day.toString(), credit.values.sum(), weekVolume(day), byWeek[day].orEmpty().size, groups)
        }
        val thisWeek = byWeek[start].orEmpty()
        val week = WebSnapshot.Week(start.toString(), start.plusDays(6).toString(), thisWeek.size,
            weeks.last().hardSets, weekVolume(start), weekVolume(start.minusWeeks(1)),
            thisWeek.sumOf { it.first.xpAwarded }, input.presets.mapNotNull { it.scheduledDay }.distinct().size.takeIf { it > 0 },
            thisWeek.map { date(input, it.first).toString() }.distinct().sorted())
        val rolling = HardSets.fromLog(logged, input.nowMs)
        val floor = ProgramRules.COVERED_MIN
        val heavy = ProgramRules.HEAVY_WEEK
        val values = Muscle.entries.associate { it.name to (rolling[it] ?: 0.0) }
        val states = values.mapValues { (_, value) -> windowState(value) }
        val dates = input.history.map { date(input, it.first) }.toSet()
        val days = dates.filter { !it.isAfter(today) && it >= today.minusDays((WebSnapshotTuning.DAYS - 1).toLong()) }.sorted()
        val trainingDays = dates.count { !it.isAfter(today) && it >= today.minusDays(83) }
        val body = SetRecords.bodyweightLookup(input.stats)
        val recentFrom = input.nowMs - 30L * 86_400_000
        val gains = LiftRecords.gains(input.history, input.exercises, body, recentFrom, input.nowMs + 1)
        val recent = gains.map { gain ->
            val session = input.history.first { (s, sets) ->
                (s.completedAtMs ?: s.startedAtMs) == gain.atMs && gain.set in sets
            }.first
            WebSnapshot.RecentRecord(gain.atMs, session.id, gain.name, gain.label,
                ExerciseArtIndex.forName(gain.name)?.id?.removePrefix("own_"), gain.e1rmKg, gain.gainKg)
        }
        val monthsWithHistory = dates.map { java.time.YearMonth.from(it) }.toSet()
        val months = (WebSnapshotTuning.MONTHS - 1 downTo 0)
            .map { java.time.YearMonth.from(today).minusMonths(it.toLong()) }
            .filter { it in monthsWithHistory }.map { month ->
            val count = LiftRecords.gains(input.history, input.exercises, body, ms(month.atDay(1), input),
                ms(month.plusMonths(1).atDay(1), input)).size
            WebSnapshot.RecordsMonth(month.toString(), count)
        }
        return week to WebSnapshot.Trends(floor, heavy,
            mapOf("7" to WebSnapshot.TrendWindow(input.nowMs - 7L * 86_400_000, input.nowMs, values, states)),
            weeks, days.map { it.toString() }, kotlin.math.round(trainingDays / 12.0 * 10) / 10, months, gains.size, recent)
    }

    internal fun liftEntries(input: SnapshotInput): List<WebSnapshot.Lift> {
        val bodyAt = SetRecords.bodyweightLookup(input.stats)
        val rows = linkedMapOf<String, Pair<Exercise, MutableList<ExerciseSetRow>>>()
        input.history.forEach { (session, sets) ->
            val at = session.completedAtMs ?: session.startedAtMs
            sets.filter { it.done && it.kind.countsAsWork }.forEach { set ->
                val exercise = input.exercises[set.exerciseId] ?: return@forEach
                if (!exercise.metric.isStrength) return@forEach
                val name = set.exerciseName.ifBlank { exercise.name }.trim().lowercase()
                rows.getOrPut(name) { exercise to mutableListOf() }.second.add(ExerciseSetRow(
                    session.id, at, SetRecords.position(set, sets), set.reps, set.weightKg,
                    set.modifiers, set.done, set.durationSec, set.kind.name,
                ))
            }
        }
        return rows.values.map { (exercise, allRows) ->
            val shares = if (exercise.custom) exercise.shares else MuscleMap.profile(exercise.name)?.muscles.orEmpty()
            val variations = allRows.map { Variation.key(it.modifiers) }.distinct()
            val records = SetRecords.recordsOf(exercise.name, input.history, bodyAt) { input.exercises[it.exerciseId]?.metric }
            val entries = variations.map { key ->
                val currentBody = input.stats.maxByOrNull { it.takenAtMs }?.weightKg
                val history = ExerciseHistoryCalculator.build(exercise, allRows, currentBody, input.nowMs, key, bodyAt)
                var previous: Double? = null
                val pointIds = allRows.filter { Variation.key(it.modifiers) == key }
                    .groupBy { it.atMs }.mapValues { (_, rows) -> ArrayDeque(rows.map { it.sessionId }.distinct()) }
                val points = history.series.map { point ->
                    val value = kotlin.math.round(point.peakValue * 100) / 100
                    val best = previous
                    val pr = best != null && value > best
                    previous = maxOf(best ?: value, value)
                    kotlinx.serialization.json.JsonArray(listOf(
                        kotlinx.serialization.json.JsonPrimitive(point.atMs),
                        kotlinx.serialization.json.JsonPrimitive(pointIds.getValue(point.atMs).removeFirst()),
                        kotlinx.serialization.json.JsonPrimitive(value),
                        kotlinx.serialization.json.JsonPrimitive(if (pr) 1 else 0),
                    ))
                }
                val peak = history.peak?.let {
                    val original = allRows.firstOrNull { row ->
                        row.sessionId == it.sessionId && row.setIndex == it.setIndex && row.modifiers == it.modifiers
                    }
                    WebSnapshot.LiftPeak(if (exercise.metric == ExerciseMetric.HOLD) 0 else it.reps, it.weightKg,
                        if (exercise.metric == ExerciseMetric.HOLD) it.reps else original?.durationSec, it.atMs, it.e1rmKg)
                }
                val heaviest = if (history.heaviestWeightKg != null && history.heaviestAtMs != null)
                    WebSnapshot.Heaviest(history.heaviestReps, history.heaviestWeightKg, history.heaviestAtMs) else null
                val span = history.firstLoggedAtMs?.let { first -> (input.nowMs - first).coerceAtLeast(7L * 86_400_000) }
                WebSnapshot.LiftVariation(
                    key, Variation.label(exercise.name, key), history.sessions, history.completedSets, history.lastLoggedAtMs,
                    span?.takeIf { history.sessions >= 2 }?.let { history.sessions * 7.0 * 86_400_000 / it }, history.totalVolumeKg, peak, heaviest,
                    if (history.peakIsE1rm) "e1rm" else "strength", if (history.peakIsE1rm) "kg" else "score", points,
                    records.values.filter { it.variation == key }.sortedBy { it.setIndex }.map {
                        WebSnapshot.SetRecord(it.setIndex, it.reps, it.weightKg, it.achievedAtMs)
                    },
                )
            }
            WebSnapshot.Lift(exercise.name, if (exercise.custom) null else ExerciseArtIndex.forName(exercise.name)?.id?.removePrefix("own_"),
                exercise.metric.name.lowercase(), BodyShare.addsToBody(exercise),
                entries.any { it.peak?.e1rmKg != null },
                com.ironvellum.app.ui.program.exerciseMuscleSummary(shares),
                shares.filterValues { it > 0 }.mapKeys { it.key.name }.mapValues { (_, v) ->
                    if (com.ironvellum.app.ui.program.shareLevel(v) == com.ironvellum.app.ui.program.ShareLevel.MAIN) 3 else 2
                }, entries)
        }
    }

    internal fun profileAndDeeds(input: SnapshotInput): Pair<WebSnapshot.Profile, List<WebSnapshot.Deed>> {
        val today = java.time.Instant.ofEpochMilli(input.nowMs).atZone(input.zone).toLocalDate()
        val dates = input.history.map { (s, _) ->
            java.time.Instant.ofEpochMilli(s.completedAtMs ?: s.startedAtMs).atZone(input.zone).toLocalDate()
        }.toSet()
        val earned = input.titles.sortedByDescending { it.unlockedAtMs }.mapNotNull { unlocked ->
            Titles.byId(unlocked.titleId)?.let { title ->
                WebSnapshot.Deed(title.id, title.name, title.rarity.name, Titles.category(title.rule),
                    title.describeFor(if (input.figure == "female") Sex.FEMALE else Sex.MALE), unlocked.unlockedAtMs)
            }
        }
        val xp = Xp.progress(input.profile.totalXp)
        return WebSnapshot.Profile(
            input.displayName, xp.level, input.profile.totalXp, xp.intoLevel, xp.needed,
            Streak.current(dates, today), Streak.longest(dates, today), input.titles.size, Titles.ALL.size,
            input.figure, input.profile.currentTitleId, input.crestId, input.history.size,
            input.history.minOfOrNull { it.first.startedAtMs },
        ) to earned
    }

    internal fun prTags(sets: List<SessionSet>, book: RepBook, exercises: Map<Long, Exercise>): List<WebSnapshot.PrTag> =
        SessionPeaks.of(sets, book, null) { exercises[it.exerciseId] }.take(6).map {
            WebSnapshot.PrTag(it.label, if (it.isHold) 0 else it.figure, it.weightKg, if (it.isHold) it.figure else null)
        }

    internal fun sessionEntries(input: SnapshotInput): Map<Long, WebSnapshot.SessionEntry> {
        val history = input.history.sortedBy { it.first.completedAtMs ?: it.first.startedAtMs }
        val ledger = RepMax.Ledger(history) { input.exercises[it.exerciseId]?.metric }
        val bodyAt = SetRecords.bodyweightLookup(input.stats)
        return history.mapIndexed { index, (session, sets) ->
            val at = session.completedAtMs ?: session.startedAtMs
            val body = bodyAt(session.startedAtMs).takeIf { it > 0.0 }
            val prior = ledger.first(index)
            val peaks = SessionPeaks.of(sets, prior, body) { input.exercises[it.exerciseId] }
            val logged = HardSets.logged(at, sets, input.exercises)
            val hard = logged.count { HardSets.isHard(it.kind, it.rpe, it.done, it.reps, it.weightKg, it.bodyweight) }
            val muscleCredit = HardSets.inRange(logged, at, at + 1)
            val muscleLevels = muscleCredit.mapKeys { it.key.name }.mapValues { (_, n) ->
                when { n >= WebSnapshotTuning.LEVEL_MOST -> 3; n >= WebSnapshotTuning.LEVEL_SOME -> 2; n > 0 -> 1; else -> 0 }
            }
            val groups = muscleCredit.entries.groupBy { WebSnapshotTuning.GROUPS.getValue(it.key).name }
                .mapValues { (_, entries) -> entries.maxOf { muscleLevels.getValue(it.key.name) } }
            val wire = com.ironvellum.app.data.cloud.CloudSync.wireSetIndexes(sets)
            val peakHits = RepMax.blocks(sets).flatMap { block ->
                val exercise = input.exercises[block.first().exerciseId]
                if (exercise?.metric?.isStrength == false) emptyList()
                else RepMax.hits(block, prior[RepMax.key(block.first())].orEmpty(), exercise?.metric == ExerciseMetric.HOLD)
            }.map { it.set }
            val peakKeys = sets.indices.filter { i -> peakHits.any { it === sets[i] } }
                .map { i -> "${sets[i].exerciseName}#${wire[i]}" }
            val estimates = linkedMapOf<String, Double>()
            sets.forEachIndexed { i, set ->
                val exercise = input.exercises[set.exerciseId] ?: return@forEachIndexed
                if (set.done && set.kind.countsAsWork && exercise.metric == ExerciseMetric.REPS && set.reps > 0) {
                    val value = BodyShare.e1rmAddedKg(set.weightKg ?: 0.0, set.reps, BodyShare.of(exercise, set.modifiers), body)
                    estimates["${set.exerciseName}#${wire[i]}"] = kotlin.math.round(value * 10) / 10
                }
            }
            session.id to WebSnapshot.SessionEntry(
                origin = if (session.imported) "imported" else "synced",
                min = session.completedAtMs?.let { ((it - session.startedAtMs) / 60_000).coerceAtLeast(1).toInt() } ?: 0,
                exercises = sets.filter { it.done }.map { it.exerciseId }.distinct().size,
                hardSets = hard, volumeKg = WorkoutShare.totals(sets, input.exercises).movedKg.toDouble(),
                peaks = peaks.sumOf { it.count }, xp = session.xpAwarded, strength = session.strengthScore,
                muscleLevels = muscleLevels, groupLevels = groups,
                prs = peaks.take(6).map { WebSnapshot.PrTag(it.label, if (it.isHold) 0 else it.figure, it.weightKg, if (it.isHold) it.figure else null) },
                peakSets = peakKeys, e1rm = estimates,
            )
        }.toMap()
    }
}

internal fun WebSnapshotBuilder.fatigueSection(input: SnapshotInput): WebSnapshot.Fatigue {
    val report = FatigueModel.estimate(
        FatigueInput.sessions(input.history, null, input.exercises, input.nowMs), input.nowMs,
        FatigueInput.firstWorkoutMs(input.history, null),
    )
    return WebSnapshot.Fatigue(
        atMs = input.nowMs,
        newLifter = report.newLifter,
        hasHistory = input.history.isNotEmpty(),
        levels = Muscle.entries.associate { it.name to report.levelAt(it, input.nowMs).ordinal },
        worked = report.mostWorked(input.zone).map { reading ->
            WebSnapshot.FatigueWorked(
                muscle = reading.muscle.name, level = reading.level.ordinal,
                cause = reading.causes.firstOrNull()?.let { input.fatigueText.cause(it, input.nowMs, input.zone) },
                freshBy = input.fatigueText.freshBy(reading.freshBy),
            )
        },
    )
}
