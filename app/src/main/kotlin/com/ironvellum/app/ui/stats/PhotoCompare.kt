package com.ironvellum.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.ui.components.PhotoImage
import com.ironvellum.app.ui.theme.DotShape
import com.ironvellum.app.ui.theme.IronvellumColors
import java.io.File
import kotlin.math.roundToInt

/** Where the divider goes after one accessibility nudge: [direction] is -1 (left) or 1 (right), a tenth of the width each, kept on the photo. */
internal fun nudgedDivider(fraction: Float, direction: Int): Float =
    (((fraction + direction * DIVIDER_STEP) * 100f).roundToInt() / 100f).coerceIn(0f, 1f)

private const val DIVIDER_STEP = 0.1f
internal const val DIVIDER_START = 0.5f
private val HandleWidth = 48.dp

/**
 * One pose, two weeks, one full-width photo: [leftFile] shows on the left of a draggable vertical divider and
 * [rightFile] on the right, both cropped the same way so two photos of different shapes line up. The divider starts at
 * half. Its 48dp-wide handle is also a slider for a screen reader (a progress range, set-progress and two custom
 * actions). With a photo for only one week, that photo shows alone with a note saying which week it is.
 */
@Composable
internal fun PhotoCompare(
    pose: String,
    leftWeek: String,
    rightWeek: String,
    leftFile: File?,
    rightFile: File?,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.small
    if (leftFile == null || rightFile == null) {
        val only = leftFile ?: rightFile
        if (only == null) {
            EmptyField(height, stringResource(R.string.photos_no_photo))
            return
        }
        val week = if (leftFile != null) leftWeek else rightWeek
        Column(modifier) {
            Box(Modifier.fillMaxWidth().height(height).clip(shape)) {
                PhotoImage(only, stringResource(R.string.photos_compare_description, pose, week), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                WeekLabel(week, Modifier.align(if (leftFile != null) Alignment.TopStart else Alignment.TopEnd))
            }
            Text(
                stringResource(R.string.photos_compare_only, week, pose.lowercase()),
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        return
    }

    var fraction by rememberSaveable { mutableFloatStateOf(DIVIDER_START) }
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth().height(height).clip(shape)) {
        val widthPx = with(density) { maxWidth.toPx() }
        PhotoImage(leftFile, stringResource(R.string.photos_compare_description, pose, leftWeek), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        // The right week over the left, drawn only to the right of the divider.
        Box(Modifier.fillMaxSize().drawWithContent { clipRect(left = size.width * fraction) { this@drawWithContent.drawContent() } }) {
            PhotoImage(rightFile, stringResource(R.string.photos_compare_description, pose, rightWeek), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        WeekLabel(leftWeek, Modifier.align(Alignment.TopStart))
        WeekLabel(rightWeek, Modifier.align(Alignment.TopEnd))

        val description = stringResource(R.string.photos_compare_handle_description, leftWeek, rightWeek)
        val state = stringResource(R.string.photos_compare_state, (fraction * 100).roundToInt())
        val moveLeft = stringResource(R.string.photos_compare_move_left)
        val moveRight = stringResource(R.string.photos_compare_move_right)
        Box(
            Modifier
                // Kept whole on the photo at either end, so the 48dp target never slides out of reach.
                .offset { IntOffset((fraction * widthPx - HandleWidth.toPx() / 2f).coerceIn(0f, widthPx - HandleWidth.toPx()).roundToInt(), 0) }
                .width(HandleWidth)
                .fillMaxHeight()
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta -> fraction = (fraction + delta / widthPx).coerceIn(0f, 1f) },
                )
                .semantics {
                    contentDescription = description
                    stateDescription = state
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                    setProgress { target -> fraction = target.coerceIn(0f, 1f); true }
                    customActions = listOf(
                        CustomAccessibilityAction(moveLeft) { fraction = nudgedDivider(fraction, -1); true },
                        CustomAccessibilityAction(moveRight) { fraction = nudgedDivider(fraction, 1); true },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            // A fixed white and black over a photo: the picture, not the theme, is what these sit on.
            Box(Modifier.width(2.dp).fillMaxHeight().background(Color.White))
            Box(Modifier.size(36.dp).clip(DotShape).background(Color.White), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = Color.Black, modifier = Modifier.size(24.dp))
            }
        }
    }
}

/** A week's date in a corner of the photo, on a dark chip so it reads over any picture. */
@Composable
private fun WeekLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        modifier = modifier.padding(8.dp).clip(MaterialTheme.shapes.small).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
