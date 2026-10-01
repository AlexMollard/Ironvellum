package com.ironvellum.app.domain

import com.ironvellum.app.data.Seed
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Dumps the exercise catalogue as JSON for tools/exercise_atlas.py. Skipped
 * unless IRONVELLUM_EXPORT names the output file, so the normal suite is
 * unaffected.
 */
class CatalogueExport {

    @Test
    fun exportCatalogue() {
        val out = System.getenv("IRONVELLUM_EXPORT")
        assumeTrue("IRONVELLUM_EXPORT not set", !out.isNullOrBlank())
        val commit = System.getenv("IRONVELLUM_COMMIT").orEmpty()

        val sb = StringBuilder()
        sb.append("{\"commit\":").append(str(commit)).append(",\"muscles\":{")
        sb.append(Muscle.entries.joinToString(",") { "${str(it.name)}:${str(it.label)}" })
        sb.append("},\"exercises\":[")
        sb.append(Seed.exercises.joinToString(",") { e ->
            exercise(
                Exercise(
                    name = e.name,
                    muscleGroup = MuscleGroup.valueOf(e.muscleGroup),
                    isWeighted = e.isWeighted,
                    metric = ExerciseMetric.valueOf(e.metric),
                    category = e.category,
                ),
            )
        })
        sb.append("]}")
        File(out!!).apply { parentFile?.mkdirs() }.writeText(sb.toString(), Charsets.UTF_8)
    }

    private fun exercise(ex: Exercise): String {
        val profile = MuscleMap.profile(ex.name)
        val gear = GearRequirements.needs(ex.name)
            .joinToString(" or ") { set -> set.joinToString(" + ") { it.label.lowercase() } }
        val guide = ExerciseGuides.forName(ex.name)
        val skill = Skills.forName(ex.name)
        return buildString {
            append("{\"name\":").append(str(ex.name))
            append(",\"group\":").append(str(ex.muscleGroup.name))
            append(",\"metric\":").append(str(ex.metric.name))
            append(",\"weighted\":").append(ex.isWeighted)
            append(",\"pattern\":").append(profile?.let { str(it.pattern.name) } ?: "null")
            append(",\"compound\":").append(profile?.compound?.toString() ?: "null")
            append(",\"stretchBias\":").append(profile?.stretchBias?.toString() ?: "null")
            append(",\"muscles\":{")
            append(profile?.muscles.orEmpty().entries.joinToString(",") { "${str(it.key.name)}:${it.value}" })
            append("},\"modifiers\":[")
            append(applicableModifiers(ex).joinToString(",") { str(it) })
            append("],\"gear\":").append(str(gear))
            append(",\"guide\":")
            if (guide == null) append("null") else {
                append("{\"setup\":").append(str(guide.setup))
                append(",\"steps\":").append(list(guide.steps))
                append(",\"cues\":").append(list(guide.cues))
                append(",\"commonMistakes\":").append(list(guide.commonMistakes)).append("}")
            }
            append(",\"skill\":")
            if (skill == null) append("null") else {
                append("{\"tier\":").append(str(Skills.tierLabel(skill.tier)))
                append(",\"line\":").append(str(skill.line))
                append(",\"standard\":").append(str(skill.standard)).append("}")
            }
            append("}")
        }
    }

    private fun list(items: List<String>) = items.joinToString(",", "[", "]") { str(it) }

    private fun str(s: String): String = buildString {
        append('"')
        for (c in s) when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c == '\n' -> append("\\n")
            c == '\r' -> append("\\r")
            c == '\t' -> append("\\t")
            c < ' ' -> append("\\u%04x".format(c.code))
            else -> append(c)
        }
        append('"')
    }
}
