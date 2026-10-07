package com.ironvellum.app.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal val LocalRowPadding = compositionLocalOf { PaddingValues(vertical = 8.dp) }

/** Grouped rows own their padding so both the tap target and press wash reach the panel edges. */
@Composable
fun InkRowPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    CompositionLocalProvider(LocalRowPadding provides PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        InkPanel(modifier, contentPadding = PaddingValues(0.dp), content = content)
    }
}
