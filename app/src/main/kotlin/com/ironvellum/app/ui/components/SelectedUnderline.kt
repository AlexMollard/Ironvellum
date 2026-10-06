package com.ironvellum.app.ui.components

import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.ironvellum.app.ui.theme.IronvellumColors

/** The selected mark every picker shares: a 2dp Emerald line along the bottom edge. Nothing is drawn when [on] is false. */
fun Modifier.selectedUnderline(on: Boolean): Modifier =
    if (!on) this else drawBehind {
        val thick = 2.dp.toPx()
        drawRect(IronvellumColors.Emerald, Offset(0f, size.height - thick), Size(size.width, thick))
    }
