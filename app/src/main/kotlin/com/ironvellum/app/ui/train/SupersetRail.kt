package com.ironvellum.app.ui.train

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ironvellum.app.ui.theme.IronvellumColors

/** Where one member sits in its superset, which says which ends of its rail segment are capped. */
internal enum class RailPosition {
    FIRST, MIDDLE, LAST;

    companion object {
        fun of(index: Int, run: IntRange): RailPosition = when (index) {
            run.first -> FIRST
            run.last -> LAST
            else -> MIDDLE
        }
    }
}

/** Where an open card's name row is centred, from the top of the item that holds it (live trial: 12dp gap, 8dp padding, half a 48dp row). */
internal val OPEN_TITLE_CENTER = 44.dp

/** Where a folded row's name is centred: half its 60dp row. */
internal val FOLDED_TITLE_CENTER = 30.dp

/** Where a rite editor's folded row (56dp) is centred. */
internal val EDITOR_FOLDED_TITLE_CENTER = 28.dp

/** Where a rite editor's open card's name row is centred: a 6dp gap, the 1dp frame, 6dp padding and half a 48dp row. */
internal val EDITOR_OPEN_TITLE_CENTER = 37.dp

/**
 * One member's piece of the thin muted-green rail in the left gutter that joins a superset: a 2dp line in
 * `SystemGreen` at 60%, drawn in the gutter's 16dp so no row moves, between two square dots on the first
 * and last member's name row ([titleCenter] below the top of this member's item). Segments touch, so
 * the rail reads as one line down the group.
 */
internal fun Modifier.supersetRail(position: RailPosition, titleCenter: Dp): Modifier = drawBehind {
    val color = IronvellumColors.SystemGreen.copy(alpha = 0.6f)
    val centre = titleCenter.toPx()
    val top = if (position == RailPosition.FIRST) centre else 0f
    val bottom = if (position == RailPosition.LAST) centre else size.height
    val x = -10.dp.toPx()
    if (bottom > top) drawRect(color, Offset(x - 1.dp.toPx(), top), Size(2.dp.toPx(), bottom - top))
    val dot = Size(8.dp.toPx(), 8.dp.toPx())
    if (position == RailPosition.FIRST) drawRect(color, Offset(x - 4.dp.toPx(), top - 4.dp.toPx()), dot)
    if (position == RailPosition.LAST) drawRect(color, Offset(x - 4.dp.toPx(), bottom - 4.dp.toPx()), dot)
}
