package com.ironvellum.app.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.ironvellum.app.domain.ArtRef
import com.ironvellum.app.ui.theme.IronvellumColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A bundled exercise drawing: the frames of an exercise side by side in one grey mask
 * (`assets/exercise_art/<id>.webp`, see tools/exercise_art). The mask's value is the ink's coverage; it is
 * painted in [ink] at draw time, so the files carry no colour.
 */
internal class ExerciseArtFrames(private val bitmap: ImageBitmap, val frames: Int) {
    val cellWidth: Int get() = bitmap.width / frames
    val cellHeight: Int get() = bitmap.height
    val aspect: Float get() = cellWidth.toFloat() / cellHeight.toFloat()

    fun DrawScope.drawFrame(index: Int, alpha: Float, filter: ColorFilter) {
        if (alpha <= 0f) return
        drawImage(
            image = bitmap,
            srcOffset = IntOffset(index * cellWidth, 0),
            srcSize = IntSize(cellWidth, cellHeight),
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            alpha = alpha,
            colorFilter = filter,
            filterQuality = FilterQuality.Medium,
        )
    }
}

private object ExerciseArtCache {
    private val cache = LruCache<String, ImageBitmap>(24)

    fun load(context: Context, id: String): ImageBitmap? {
        cache.get(id)?.let { return it }
        val decoded = runCatching {
            context.assets.open("exercise_art/$id.webp").use { BitmapFactory.decodeStream(it) }
        }.getOrNull()?.asImageBitmap() ?: return null
        cache.put(id, decoded)
        return decoded
    }
}

@Composable
private fun rememberArtFrames(ref: ArtRef): State<ExerciseArtFrames?> {
    val context = LocalContext.current.applicationContext
    return produceState<ExerciseArtFrames?>(null, ref.id) {
        value = withContext(Dispatchers.IO) { ExerciseArtCache.load(context, ref.id) }
            ?.let { ExerciseArtFrames(it, ref.frames) }
    }
}

/** The grey mask's red channel becomes alpha and the colour is [ink]: one filter, so one tint for every drawing. */
private fun inkFilter(ink: Color): ColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            0f, 0f, 0f, 0f, ink.red * 255f,
            0f, 0f, 0f, 0f, ink.green * 255f,
            0f, 0f, 0f, 0f, ink.blue * 255f,
            1f, 0f, 0f, 0f, 0f,
        ),
    ),
)

/** Frame order of one loop: 1, 2 for a pair; 1, 2, 3, 2 for three, so the figure returns the way it went. */
internal fun artSequence(frames: Int): List<Int> = when {
    frames <= 1 -> listOf(0)
    frames == 2 -> listOf(0, 1)
    else -> (0 until frames) + (frames - 2 downTo 1)
}

private const val HOLD_MS = 650
private const val FADE_MS = 450
private val Ease = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

/**
 * An exercise's drawing looping through its frames: each pose is held, then eased into the next. With system
 * animations off ([animatorsOn]) it stays on the first frame, so it is still a picture and never motion.
 * [alpha] dims it (a locked technique). The figure is as tall as its parent allows, in the house ink.
 */
@Composable
internal fun ExerciseAnimation(ref: ArtRef, modifier: Modifier = Modifier, alpha: Float = 1f, ink: Color = IronvellumColors.Ink) {
    val art by rememberArtFrames(ref)
    val loaded = art ?: return
    val motion = animatorsOn(LocalContext.current) && loaded.frames > 1
    val filter = remember(ink) { inkFilter(ink) }
    val sequence = remember(ref.frames) { artSequence(ref.frames) }
    Box(modifier, contentAlignment = Alignment.Center) {
        val canvasModifier = Modifier.fillMaxHeight().aspectRatio(loaded.aspect, matchHeightConstraintsFirst = true)
        if (motion) {
            val transition = rememberInfiniteTransition(label = "exercise art")
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = sequence.size.toFloat(),
                animationSpec = infiniteRepeatable(tween((HOLD_MS + FADE_MS) * sequence.size, easing = LinearEasing), RepeatMode.Restart),
                label = "exercise art phase",
            )
            Canvas(canvasModifier) {
                val step = phase.toInt().coerceIn(0, sequence.size - 1)
                val within = phase - step
                val fadeStart = HOLD_MS.toFloat() / (HOLD_MS + FADE_MS)
                val t = if (within <= fadeStart) 0f else Ease.transform((within - fadeStart) / (1f - fadeStart))
                val from = sequence[step]
                val to = sequence[(step + 1) % sequence.size]
                with(loaded) {
                    drawFrame(from, alpha * (1f - t), filter)
                    drawFrame(to, alpha * t, filter)
                }
            }
        } else {
            Canvas(canvasModifier) { with(loaded) { drawFrame(0, alpha, filter) } }
        }
    }
}

/** The first frame only: a thumbnail that never moves. */
@Composable
internal fun ExerciseStill(ref: ArtRef, modifier: Modifier = Modifier, alpha: Float = 1f, ink: Color = IronvellumColors.Ink) {
    val art by rememberArtFrames(ref)
    val loaded = art ?: return
    val filter = remember(ink) { inkFilter(ink) }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxHeight().aspectRatio(loaded.aspect, matchHeightConstraintsFirst = true)) {
            with(loaded) { drawFrame(0, alpha, filter) }
        }
    }
}
