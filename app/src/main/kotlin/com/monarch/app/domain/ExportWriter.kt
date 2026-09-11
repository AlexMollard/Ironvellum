package com.monarch.app.domain

/**
 * Minimal hand-rolled JSON writer for the data export. Pure Kotlin so it is
 * unit-testable on the JVM with no Android or org.json dependency.
 */
object ExportWriter {

    const val FORMAT_VERSION = 4

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
        measurementGoals: List<MeasurementGoal> = emptyList(),
        exportedAtMs: Long,
    ): String = buildString {
        append("{")
        append("\"formatVersion\":$FORMAT_VERSION,")
        append("\"exportedAtMs\":$exportedAtMs,")
        append("\"profile\":")
        appendProfile(profile)
        append(",\"trainingMode\":")
        appendEscaped(trainingMode.name)
        append(",\"presets\":")
        appendPresets(presets)
        append(",\"sessions\":")
        appendSessions(sessions)
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
        append(",\"measurementGoals\":")
        appendMeasurementGoals(measurementGoals)
        append("}")
    }

    private fun StringBuilder.appendProfile(profile: PlayerProfile) {
        append("{\"name\":")
        appendEscaped(profile.name)
        append(",\"totalXp\":")
        append(profile.totalXp)
        append(",\"currentTitleId\":")
        appendNullable(profile.currentTitleId) { appendEscaped(it) }
        append("}")
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

    private fun StringBuilder.appendSessions(sessions: List<Pair<WorkoutSession, List<SessionSet>>>) {
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
            append(",\"privateNote\":").appendEscaped(session.privateNote)
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

    private fun StringBuilder.appendMeasurementGoals(goals: List<MeasurementGoal>) {
        append("[")
        goals.forEachIndexed { i, g ->
            if (i > 0) append(",")
            append("{\"site\":").appendEscaped(g.site.name)
            append(",\"targetCm\":").append(g.targetCm)
            append(",\"setAtMs\":").append(g.setAtMs)
            append(",\"startCm\":").append(g.startCm)
            append(",\"achievedAtMs\":").appendNullable(g.achievedAtMs) { append(it) }
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
