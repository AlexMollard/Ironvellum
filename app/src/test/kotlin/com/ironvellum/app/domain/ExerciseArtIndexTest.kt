package com.ironvellum.app.domain

import com.ironvellum.app.data.Seed
import com.ironvellum.app.ui.components.artSequence
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The drawings against the real catalogue and the real files: every mapped movement's frames exist and are lossless
 * WebP strips of the frame count the index claims, and nothing in the index points at a movement we do not ship.
 */
class ExerciseArtIndexTest {

    private val assets = File("src/main/assets/exercise_art")

    /** Width and height of a lossless (VP8L) or extended (VP8X) WebP, read from its header bytes. */
    private fun dimensions(file: File): Pair<Int, Int> {
        val b = file.readBytes()
        require(b.size > 30 && String(b, 0, 4, Charsets.US_ASCII) == "RIFF" && String(b, 8, 4, Charsets.US_ASCII) == "WEBP") {
            "${file.name} is not a WebP"
        }
        fun u(i: Int) = b[i].toInt() and 0xFF
        return when (val chunk = String(b, 12, 4, Charsets.US_ASCII)) {
            "VP8L" -> {
                require(u(20) == 0x2F) { "${file.name} has no VP8L signature" }
                val bits = u(21) or (u(22) shl 8) or (u(23) shl 16) or (u(24) shl 24)
                ((bits and 0x3FFF) + 1) to (((bits shr 14) and 0x3FFF) + 1)
            }
            "VP8X" -> {
                (u(24) or (u(25) shl 8) or (u(26) shl 16)) + 1 to ((u(27) or (u(28) shl 8) or (u(29) shl 16)) + 1)
            }
            else -> error("${file.name}: unexpected chunk $chunk")
        }
    }

    private fun refs(): Map<String, ArtRef> =
        (ExerciseArtIndex.names.map { ExerciseArtIndex.forName(it)!! } +
            ExerciseArtIndex.techniques.map { ExerciseArtIndex.forTechnique(it)!! })
            .associateBy { it.id }

    @Test
    fun `every mapped movement has its frames and they parse`() {
        val problems = refs().values.mapNotNull { ref ->
            val file = File(assets, ref.id + ".webp")
            if (!file.exists()) {
                "${ref.id}: file missing"
            } else {
                runCatching {
                    val (w, h) = dimensions(file)
                    when {
                        w % ref.frames != 0 -> "${ref.id}: width $w is not ${ref.frames} equal cells"
                        w / ref.frames < 32 || h < 32 || w / ref.frames > 512 || h > 512 -> "${ref.id}: odd cell ${w / ref.frames} x $h"
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
    fun `every library movement is drawn`() {
        val undrawn = ExerciseLibrary.rows.map { it.name }.filter { ExerciseArtIndex.forName(it) == null }
        assertEquals("library movements with no drawing", emptyList<String>(), undrawn)
    }

    @Test
    fun `the loop returns the way it went`() {
        assertEquals(listOf(0, 1), artSequence(2))
        assertEquals(listOf(0, 1, 2, 1), artSequence(3))
        assertEquals(listOf(0), artSequence(1))
    }
}
