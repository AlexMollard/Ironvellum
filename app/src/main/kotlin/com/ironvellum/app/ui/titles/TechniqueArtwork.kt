package com.ironvellum.app.ui.titles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.ironvellum.app.domain.ArtRef
import com.ironvellum.app.domain.ExerciseArtIndex
import com.ironvellum.app.ui.components.ExerciseDrawing
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.METAL_RIM
import com.ironvellum.app.ui.theme.METAL_WASH
import com.ironvellum.app.ui.theme.Metal
import com.ironvellum.app.ui.theme.emeraldRamp
import com.ironvellum.app.ui.theme.inkBorder

/**
 * The drawing a technique shows (see [ExerciseArtIndex.forTechnique]): its own movement's, or a proxy's. Null means the
 * technique has none, and the tree's pictogram ([SkillGlyph]) stands in. Upstream draws no lever, planche or flag, and
 * the art here is never invented.
 */
internal fun techniqueDrawing(name: String): ArtRef? = ExerciseArtIndex.forTechnique(name)

/** What a proxy drawing's caption says, e.g. "Shown: Pull-up"; null when the drawing is the technique's own movement. */
internal fun techniqueCaption(name: String): String? = ExerciseArtIndex.shows(name)?.let { "Shown: $it" }

/** Where a technique stands for the lifter, as its art shows it: earned, open to claim, or out of reach. */
internal enum class ArtState { MASTERED, OPEN, LOCKED }

/** The state's own metal at [alpha]: the gold ramp earned, the emerald ramp open, Iron locked. The same ring the tree draws. */
internal fun artBrush(state: ArtState, alpha: Float = 1f): Brush = when (state) {
    ArtState.MASTERED -> Metal.Fabled.bounds(alpha)
    ArtState.OPEN -> emeraldRamp(alpha)
    ArtState.LOCKED -> Metal.Common.bounds(alpha)
}

/** A locked technique's drawing is dimmed, so the art says "not yet" in the tree and the sheet alike. */
internal fun artAlpha(state: ArtState): Float = if (state == ArtState.LOCKED) 0.5f else 1f

/** A technique's drawing set in a plate rather than left floating on the sheet: a dark ground under a faint wash of the state's metal and a gradient rim. */
@Composable
internal fun TechniqueDrawingPlate(animation: ArtRef, state: ArtState, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(IronvellumColors.Abyss)
            // A flat wash of the state's metal behind the figure and a gradient rim; no wash for a locked one.
            .then(if (state != ArtState.LOCKED) Modifier.background(artBrush(state, METAL_WASH)) else Modifier)
            .inkBorder(artBrush(state, METAL_RIM), shape, 1.dp),
    ) {
        ExerciseDrawing(animation, Modifier.fillMaxSize().padding(8.dp), animated = true, alpha = artAlpha(state))
    }
}
