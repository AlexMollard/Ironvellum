package com.ironvellum.app.ui.components

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import com.ironvellum.app.domain.ArtRef
import com.ironvellum.app.domain.ExerciseArtIndex
import com.ironvellum.app.domain.MuscleGroup
import com.ironvellum.app.domain.Skills
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.titles.GlyphFamily
import com.ironvellum.app.ui.titles.SkillGlyph
import com.ironvellum.app.ui.titles.glyphFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * One exercise's drawings as read from `assets/exercise_art/<id>.paths` (tools/exercise_art): a shared window and, per
 * frame, the translation that registers it and its SVG path data (even-odd). Pure data, so it parses in a unit test.
 */
internal class ExerciseArtFile(val width: Float, val height: Float, val frames: List<Frame>) {
    class Frame(val dx: Float, val dy: Float, val data: String)

    companion object {
        /** Throws [IllegalArgumentException] for a file that is not the format, so a bad asset fails loudly. */
        fun parse(text: String): ExerciseArtFile {
            val rows = text.lineSequence().filter { it.isNotBlank() }.toList()
            val head = rows.first().trim().split(' ')
            require(head.size == 5 && head[0] == "v1") { "not an exercise art file" }
            val (x, y, w, h) = head.drop(1).map { it.toFloat() }
            val frames = rows.drop(1).map { row ->
                val parts = row.trim().split(' ', limit = 3)
                require(parts.size == 3) { "a frame needs dx, dy and path data" }
                Frame(parts[0].toFloat() - x, parts[1].toFloat() - y, parts[2])
            }
            require(w > 0f && h > 0f && frames.isNotEmpty()) { "empty window or no frames" }
            return ExerciseArtFile(w, h, frames)
        }
    }
}

/** A parsed file with its paths built once, already moved into the window. */
internal class ExerciseArt(val file: ExerciseArtFile, val paths: List<Path>)

private object ExerciseArtCache {
    private val cache = LruCache<String, ExerciseArt>(32)

    fun load(context: Context, id: String): ExerciseArt? {
        cache.get(id)?.let { return it }
        val art = runCatching {
            val file = context.assets.open("exercise_art/$id.paths").use { ExerciseArtFile.parse(it.readBytes().decodeToString()) }
            ExerciseArt(
                file,
                file.frames.map { f ->
                    PathParser().parsePathString(f.data).toPath().apply {
                        fillType = PathFillType.EvenOdd
                        translate(Offset(f.dx, f.dy))
                    }
                },
            )
        }.getOrNull() ?: return null
        cache.put(id, art)
        return art
    }
}

@Composable
private fun rememberArt(ref: ArtRef): State<ExerciseArt?> {
    val context = LocalContext.current.applicationContext
    return produceState<ExerciseArt?>(null, ref.id) {
        value = withContext(Dispatchers.IO) { ExerciseArtCache.load(context, ref.id) }
    }
}

/** Frame order of one loop: 1, 2 for a pair; 1, 2, 3, 2 for three, so the figure returns the way it went. */
internal fun artSequence(frames: Int): List<Int> = when {
    frames <= 1 -> listOf(0)
    frames == 2 -> listOf(0, 1)
    else -> (0 until frames) + (frames - 2 downTo 1)
}

/** How long each pose is held before the next replaces it: a cut, never a blend. */
internal const val HOLD_MS = 700L

/** Share of the slot's shorter side left empty on every edge, so a drawing never touches its slot's border. */
internal const val ART_INSET = 0.06f

/** Where a drawing sits in its slot: a uniform [scale] and the offset of its top-left corner. */
internal data class ArtFit(val scale: Float, val left: Float, val top: Float)

/**
 * Where a drawing of [artWidth] x [artHeight] sits in a slot of [slotWidth] x [slotHeight]: the largest uniform scale that
 * fits inside the slot less its inset (ContentScale.Fit), centred. It never overflows, however wide or tall either is.
 */
internal fun fitArt(artWidth: Float, artHeight: Float, slotWidth: Float, slotHeight: Float): ArtFit {
    val inset = minOf(slotWidth, slotHeight) * ART_INSET
    val scale = minOf((slotWidth - 2 * inset) / artWidth, (slotHeight - 2 * inset) / artHeight).coerceAtLeast(0f)
    return ArtFit(scale, (slotWidth - artWidth * scale) / 2f, (slotHeight - artHeight * scale) / 2f)
}

/**
 * The one drawing slot: every place an exercise's picture appears (sheet, picker row, tree node, technique plate) is this.
 * The art is fitted inside whatever size the caller gives it, in the house ink, and clipped to it.
 *
 * [animated] loops the poses, each held [HOLD_MS] and then cut to the next (1, 2 or 1, 2, 3, 2); with system animations off,
 * with one frame, or with [animated] false, it shows the first pose, still. [alpha] dims it (a locked technique).
 */
@Composable
internal fun ExerciseDrawing(
    ref: ArtRef,
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    alpha: Float = 1f,
    ink: Color = IronvellumColors.Ink,
) {
    val art by rememberArt(ref)
    val loaded = art
    Box(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        if (loaded == null) return@Box
        val context = LocalContext.current
        val sequence = remember(loaded) { artSequence(loaded.paths.size) }
        val motion = animated && sequence.size > 1 && animatorsOn(context)
        var step by remember(loaded) { mutableIntStateOf(0) }
        LaunchedEffect(motion, sequence) {
            step = 0
            while (motion) {
                delay(HOLD_MS)
                step = (step + 1) % sequence.size
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val fit = fitArt(loaded.file.width, loaded.file.height, size.width, size.height)
            withTransform({
                translate(fit.left, fit.top)
                scale(fit.scale, fit.scale, pivot = Offset.Zero)
            }) { drawPath(loaded.paths[sequence[step]], ink, alpha) }
        }
    }
}

/** The drawing looping through its poses. */
@Composable
internal fun ExerciseAnimation(ref: ArtRef, modifier: Modifier = Modifier, alpha: Float = 1f, ink: Color = IronvellumColors.Ink) =
    ExerciseDrawing(ref, modifier, animated = true, alpha = alpha, ink = ink)

/** The first pose only: a thumbnail that never moves. */
@Composable
internal fun ExerciseStill(ref: ArtRef, modifier: Modifier = Modifier, alpha: Float = 1f, ink: Color = IronvellumColors.Ink) =
    ExerciseDrawing(ref, modifier, animated = false, alpha = alpha, ink = ink)

/** The pictogram that stands in for a movement nobody has drawn: its technique's own, else its muscle group's. */
internal fun placeholderFamily(name: String, group: MuscleGroup): GlyphFamily =
    Skills.forName(name)?.let { glyphFamily(it) } ?: when (group) {
        MuscleGroup.PUSH -> GlyphFamily.PUSH
        MuscleGroup.PULL -> GlyphFamily.PULL
        MuscleGroup.LEGS -> GlyphFamily.SQUAT
        MuscleGroup.CORE -> GlyphFamily.CORE
        MuscleGroup.CLIMBING -> GlyphFamily.HANG
        MuscleGroup.MOBILITY, MuscleGroup.CARDIO, MuscleGroup.SPORT, MuscleGroup.WATER -> GlyphFamily.MOBILITY
    }

/**
 * A movement's picture in a slot of the caller's size: its drawing, or, where upstream has none, the house pictogram in
 * muted ink, so a row without a drawing is never an empty gap.
 */
@Composable
internal fun ExerciseArtSlot(
    name: String,
    group: MuscleGroup,
    modifier: Modifier = Modifier,
    animated: Boolean = false,
) {
    val ref = ExerciseArtIndex.forName(name)
    if (ref != null) {
        ExerciseDrawing(ref, modifier, animated = animated)
    } else {
        Box(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
            SkillGlyph(placeholderFamily(name, group), IronvellumColors.InkMuted.copy(alpha = 0.55f), Modifier.fillMaxSize(0.62f))
        }
    }
}
