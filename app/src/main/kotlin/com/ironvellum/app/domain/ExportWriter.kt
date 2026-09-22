package com.ironvellum.app.domain

/**
 * Minimal hand-rolled JSON writer for the data export. Pure Kotlin so it is
 * unit-testable on the JVM with no Android or org.json dependency.
 */
object ExportWriter {

    // 5: adds idle/gacha/cosmetic state, profile height/sex/inkStyle and real
    // per-exercise metadata. ExportReader still accepts 4 and below.
    const val FORMAT_VERSION = 5

    /** Real catalogue attributes for one movement, matched by name on import. */
    data class ExerciseMeta(
        val name: String,
        val muscleGroup: String,
        val isWeighted: Boolean,
        val metric: String,
        val category: String,
    )

    data class IdleSnapshot(
        val essence: Long,
        val shadows: Int,
        val relicMultiplier: Double,
        val lastCollectedAtMs: Long,
    )

    data class GachaSnapshot(
        val rolls: Int,
        val equippedFrame: String?,
    )

    data class CrestFrameSnapshot(
        val frameId: String,
        val ownedAtMs: Long,
    )

    data class RelicSnapshot(
        val name: String,
        val multiplier: Double,
        val drawnAtMs: Long,
    )

    fun write(
        profile: PlayerProfile,
        trainingMode: TrainingMode,
        presets: List<WorkoutPreset>,
        sessions: List<Pair<WorkoutSession, List<SessionSet>>>,
        stats: List<StatEntry>,
        titles: List<UnlockedTitle>,
        skills: List<SkillPractice>,
        healthDays: List<HealthDay>,
        measurements: List<MeasurementEntry> = emptyList(),
        // v5 sections; null/empty keeps the keys out so the writer stays the
        // single source of truth for what each formatVersion contains.
        exercises: List<ExerciseMeta> = emptyList(),
        heightCm: Double? = null,
        sex: String? = null,
        idle: IdleSnapshot? = null,
        gacha: GachaSnapshot? = null,
        crestFrames: List<CrestFrameSnapshot> = emptyList(),
        relics: List<RelicSnapshot> = emptyList(),
        exportedAtMs: Long,
        // false = cloud copy. The app promises in user-visible UI
        // (SessionScreen.kt:1081) that the private note never leaves this
        // device; a backup that quietly broke that promise would be worse
        // than no backup. The key is omitted entirely — not written empty —
        // so the archive carries no trace of the field. ExportReader
        // tolerates its absence (defaults to ""). Default true keeps every
        // existing caller byte-identical: the LOCAL archive keeps the note.
        includePrivateNotes: Boolean = true,
    ): String = buildString {
        append("{")
        append("\"formatVersion\":$FORMAT_VERSION,")
        append("\"exportedAtMs\":$exportedAtMs,")
        append("\"profile\":")
        appendProfile(profile, heightCm, sex)
        append(",\"trainingMode\":")
        appendEscaped(trainingMode.name)
        append(",\"presets\":")
        appendPresets(presets)
        append(",\"sessions\":")
        appendSessions(sessions, includePrivateNotes)
        append(",\"stats\":")
        appendStats(stats)
        append(",\"titles\":")
        appendTitles(titles)
        append(",\"skills\":")
        appendSkills(skills)
        append(",\"healthDays\":")
        appendHealthDays(healthDays)
        append(",\"measurements\":")
        appendMeasurements(measurements)
        append(",\"exercises\":")
        appendExercises(exercises)
        if (idle != null) {
            append(",\"idle\":")
            appendIdle(idle)
        }
        if (gacha != null) {
            append(",\"gacha\":")
            appendGacha(gacha)
            append(",\"crestFrames\":")
            appendCrestFrames(crestFrames)
            append(",\"relics\":")
            appendRelics(relics)
        }
        append("}")
    }

    private fun StringBuilder.appendProfile(profile: PlayerProfile, heightCm: Double?, sex: String?) {
        append("{\"name\":")
        appendEscaped(profile.name)
        append(",\"totalXp\":")
        append(profile.totalXp)
        append(",\"currentTitleId\":")
        appendNullable(profile.currentTitleId) { appendEscaped(it) }
        // Height and sex feed every BMI/FFMI/calorie estimate — dropping them
        // silently degraded the owner's headline metrics on restore.
        append(",\"heightCm\":")
        appendNullable(heightCm) { append(it) }
        append(",\"sex\":")
        appendNullable(sex) { appendEscaped(it) }
        append(",\"inkStyle\":")
        append(profile.inkStyle)
        append("}")
    }

    private fun StringBuilder.appendExercises(exercises: List<ExerciseMeta>) {
        append("[")
        exercises.forEachIndexed { i, e ->
            if (i > 0) append(",")
            append("{\"name\":").appendEscaped(e.name)
            append(",\"muscleGroup\":").appendEscaped(e.muscleGroup)
            append(",\"isWeighted\":").append(e.isWeighted)
            append(",\"metric\":").appendEscaped(e.metric)
            append(",\"category\":").appendEscaped(e.category)
            append("}")
        }
        append("]")
    }

    private fun StringBuilder.appendIdle(idle: IdleSnapshot) {
        append("{\"essence\":").append(idle.essence)
        append(",\"shadows\":").append(idle.shadows)
        append(",\"relicMultiplier\":").append(idle.relicMultiplier)
        append(",\"lastCollectedAtMs\":").append(idle.lastCollectedAtMs)
        append("}")
    }

    private fun StringBuilder.appendGacha(gacha: GachaSnapshot) {
        append("{\"rolls\":").append(gacha.rolls)
        append(",\"equippedFrame\":")
        appendNullable(gacha.equippedFrame) { appendEscaped(it) }
        append("}")
    }

    private fun StringBuilder.appendCrestFrames(frames: List<CrestFrameSnapshot>) {
        append("[")
        frames.forEachIndexed { i, f ->
            if (i > 0) append(",")
            append("{\"frameId\":").appendEscaped(f.frameId)
            append(",\"ownedAtMs\":").append(f.ownedAtMs)
            append("}")
        }
        append("]")
    }

    private fun StringBuilder.appendRelics(relics: List<RelicSnapshot>) {
        append("[")
        relics.forEachIndexed { i, r ->
            if (i > 0) append(",")
            append("{\"name\":").appendEscaped(r.name)
            append(",\"multiplier\":").append(r.multiplier)
            append(",\"drawnAtMs\":").append(r.drawnAtMs)
            append("}")
        }
        append("]")
    }

    private fun StringBuilder.appendPresets(presets: List<WorkoutPreset>) {
        append("[")
        presets.forEachIndexed { pi, preset ->
            if (pi > 0) append(",")
            append("{\"id\":").append(preset.id)
            append(",\"name\":").appendEscaped(preset.name)
            append(",\"note\":").appendEscaped(preset.note)
            append(",\"scheduledDay\":").appendNullable(preset.scheduledDay) { append(it) }
            append(",\"entries\":[")
            preset.entries.forEachIndexed { ei, entry ->
                if (ei > 0) append(",")
                append("{\"exerciseId\":").append(entry.exerciseId)
                append(",\"exerciseName\":").appendEscaped(entry.exerciseName)
                append(",\"targetSets\":").append(entry.targetSets)
                append(",\"targetReps\":").append(entry.targetReps)
                append(",\"targetWeightKg\":")
                appendNullable(entry.targetWeightKg) { append(it) }
                append(",\"modifiers\":").appendEscaped(entry.modifiers)
                append(",\"position\":").append(entry.position)
                append("}")
            }
            append("]}")
        }
        append("]")
    }

    private fun StringBuilder.appendSessions(
        sessions: List<Pair<WorkoutSession, List<SessionSet>>>,
        includePrivateNotes: Boolean,
    ) {
        append("[")
        sessions.forEachIndexed { si, (session, sets) ->
            if (si > 0) append(",")
            append("{\"id\":").append(session.id)
            append(",\"presetId\":").appendNullable(session.presetId) { append(it) }
            append(",\"label\":").appendEscaped(session.label)
            append(",\"startedAtMs\":").append(session.startedAtMs)
            append(",\"completedAtMs\":").appendNullable(session.completedAtMs) { append(it) }
            append(",\"xpAwarded\":").append(session.xpAwarded)
            append(",\"strengthScore\":").append(session.strengthScore)
            append(",\"title\":").appendEscaped(session.title)
            append(",\"note\":").appendEscaped(session.note)
            // The private note belongs in the user's own archive — it is kept
            // out of the CLOUD, not out of their backup. Dropping it here meant
            // a restore silently destroyed it.
            if (includePrivateNotes) {
                append(",\"privateNote\":").appendEscaped(session.privateNote)
            }
            append(",\"sets\":[")
            sets.forEachIndexed { ti, set ->
                if (ti > 0) append(",")
                append("{\"id\":").append(set.id)
                append(",\"exerciseId\":").append(set.exerciseId)
                append(",\"exerciseName\":").appendEscaped(set.exerciseName)
                append(",\"exercisePosition\":").append(set.exercisePosition)
                append(",\"setIndex\":").append(set.setIndex)
                append(",\"reps\":").append(set.reps)
                append(",\"weightKg\":").appendNullable(set.weightKg) { append(it) }
                // Timed/distance/graded sets: dropping these erased every
                // non-lifting measurement on a restore.
                append(",\"durationSec\":").appendNullable(set.durationSec) { append(it) }
                append(",\"distanceM\":").appendNullable(set.distanceM) { append(it) }
                append(",\"grade\":").appendNullable(set.grade) { appendEscaped(it) }
                append(",\"modifiers\":").appendEscaped(set.modifiers)
                append(",\"done\":").append(set.done)
                append("}")
            }
            append("]}")
        }
        append("]")
    }

    private fun StringBuilder.appendStats(stats: List<StatEntry>) {
        append("[")
        stats.forEachIndexed { i, stat ->
            if (i > 0) append(",")
            append("{\"takenAtMs\":").append(stat.takenAtMs)
            append(",\"weightKg\":").append(stat.weightKg)
            append(",\"heightCm\":").append(stat.heightCm)
            append(",\"bodyFatPct\":").appendNullable(stat.bodyFatPct) { append(it) }
            append("}")
        }
        append("]")
    }

    private fun StringBuilder.appendTitles(titles: List<UnlockedTitle>) {
        append("[")
        titles.forEachIndexed { i, title ->
            if (i > 0) append(",")
            append("{\"titleId\":").appendEscaped(title.titleId)
            append(",\"unlockedAtMs\":").append(title.unlockedAtMs)
            append("}")
        }
        append("]")
    }

    private fun StringBuilder.appendSkills(skills: List<SkillPractice>) {
        append("[")
        skills.forEachIndexed { i, skill ->
            if (i > 0) append(",")
            append("{\"skillName\":").appendEscaped(skill.skillName)
            append(",\"practicedAtMs\":").append(skill.practicedAtMs)
            append(",\"claimed\":").append(skill.claimed)
            append(",\"value\":").append(skill.value)
            append(",\"weightKg\":").appendNullable(skill.weightKg) { append(it) }
            append("}")
        }
        append("]")
    }

    private fun StringBuilder.appendHealthDays(healthDays: List<HealthDay>) {
        append("[")
        healthDays.forEachIndexed { i, day ->
            if (i > 0) append(",")
            append("{\"date\":").appendEscaped(day.date.toString())
            append(",\"steps\":").append(day.steps)
            append(",\"distanceKm\":").append(day.distanceKm)
            append(",\"activeKcal\":").append(day.activeKcal)
            append(",\"sleepMinutes\":").append(day.sleepMinutes)
            append(",\"restingHr\":").appendNullable(day.restingHr) { append(it) }
            append("}")
        }
        append("]")
    }

    private fun StringBuilder.appendMeasurements(measurements: List<MeasurementEntry>) {
        append("[")
        measurements.forEachIndexed { i, m ->
            if (i > 0) append(",")
            append("{\"site\":").appendEscaped(m.site.name)
            append(",\"valueCm\":").append(m.valueCm)
            append(",\"takenAtMs\":").append(m.takenAtMs)
            append("}")
        }
        append("]")
    }

    private inline fun <T> StringBuilder.appendNullable(value: T?, block: StringBuilder.(T) -> Unit) {
        if (value == null) append("null") else block(value)
    }

    private fun StringBuilder.appendEscaped(value: String) {
        append('"')
        for (ch in value) {
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch < ' ') append("\\u%04x".format(ch.code)) else append(ch)
            }
        }
        append('"')
    }
}
