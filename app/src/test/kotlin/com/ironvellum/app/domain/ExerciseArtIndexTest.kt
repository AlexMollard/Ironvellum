package com.ironvellum.app.domain

import com.ironvellum.app.data.Seed
import java.io.File
import androidx.compose.ui.graphics.vector.PathParser
import com.ironvellum.app.ui.components.ExerciseArtFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The drawings against the real catalogue and the real files: every mapped movement's file exists, parses, holds the frame
 * count the index claims, every path in it is valid SVG path data, and nothing in the index points at a movement we do not ship.
 */
class ExerciseArtIndexTest {

    private val assets = File("src/main/assets/exercise_art")

    private fun refs(): Map<String, ArtRef> =
        (ExerciseArtIndex.names.map { ExerciseArtIndex.forName(it)!! } +
            ExerciseArtIndex.techniques.map { ExerciseArtIndex.forTechnique(it)!! })
            .associateBy { it.id }

    @Test
    fun `every mapped movement has its frames and they parse`() {
        val problems = refs().values.mapNotNull { ref ->
            val file = File(assets, ref.id + ".paths")
            if (!file.exists()) {
                "${ref.id}: file missing"
            } else {
                runCatching {
                    val art = ExerciseArtFile.parse(file.readText())
                    // Path data must be valid for the runtime parser; toNodes() is its pure-Kotlin half.
                    art.frames.forEach { PathParser().parsePathString(it.data).toNodes() }
                    when {
                        art.frames.size != ref.frames -> "${ref.id}: file holds ${art.frames.size} frames, index says ${ref.frames}"
                        art.width < 16f || art.height < 16f -> "${ref.id}: odd window ${art.width} x ${art.height}"
                        else -> null
                    }
                }.getOrElse { "${ref.id}: ${it.message}" }
            }
        }
        assertEquals("drawings with a problem", emptyList<String>(), problems)
    }

    @Test
    fun `no drawing file is orphaned`() {
        val used = refs().keys
        val orphans = assets.listFiles().orEmpty().map { it.nameWithoutExtension }.filter { it !in used }
        assertEquals("files in exercise_art that nothing uses", emptyList<String>(), orphans)
    }

    @Test
    fun `every drawn movement is a catalogue movement and every technique a skill`() {
        val catalogue = Seed.exercises.map { it.name.trim().lowercase() }.toSet()
        assertEquals("index names with no catalogue row", emptyList<String>(), ExerciseArtIndex.names.filter { it !in catalogue })
        val skills = Skills.ALL.map { it.name.trim().lowercase() }.toSet()
        assertEquals("techniques that are not skills", emptyList<String>(), ExerciseArtIndex.techniques.filter { it !in skills })
    }

    @Test
    fun `every catalogue movement is drawn`() {
        // Movements that ship before their drawing: each needs the owner's art approval first, so it is listed here
        // by name and leaves this list the moment its still lands.
        val awaitingArt = setOf(
            "Tuck Front Lever Row", "Front Lever Row", "Box Pistol Squat", "Elevated Pistol Squat", "Airborne Squat",
            "Advanced Shrimp Squat", "Deficit Advanced Shrimp Squat", "Cobra Squat",
        )
        val undrawn = Seed.exercises.map { it.name }.filter { it !in awaitingArt && ExerciseArtIndex.forName(it) == null }
        assertEquals("catalogue movements with no drawing", emptyList<String>(), undrawn)
    }

    @Test
    fun `every library movement is drawn`() {
        val undrawn = ExerciseLibrary.rows.map { it.name }.filter { ExerciseArtIndex.forName(it) == null }
        assertEquals("library movements with no drawing", emptyList<String>(), undrawn)
    }

    @Test
    fun `a proxy names the movement it shows and a movement's own drawing never does`() {
        val proxied = ExerciseArtIndex.techniques.filter { ExerciseArtIndex.shows(it) != null }
        // A proxy is not the catalogue row's own drawing, so the movement of that name is not drawn with it.
        proxied.forEach { assertNull("$it must not be drawn as a movement", ExerciseArtIndex.forName(it)) }
        assertNull(ExerciseArtIndex.shows("Back Squat"))
    }

    @Test
    fun `every drawing is one still`() {
        val moving = assets.listFiles { f -> f.name.endsWith(".paths") }!!
            .filter { ExerciseArtFile.parse(it.readText()).frames.size != 1 }
            .map { it.name }
        assertEquals("drawings with more than one pose", emptyList<String>(), moving)
    }
}
