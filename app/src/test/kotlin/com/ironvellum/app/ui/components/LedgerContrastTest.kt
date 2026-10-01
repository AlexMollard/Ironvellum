package com.ironvellum.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.ironvellum.app.ui.theme.IronvellumColors
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerContrastTest {
    private fun ratio(fg: Color, bg: Color): Double {
        val a = fg.compositeOver(bg).luminance().toDouble() + 0.05
        val b = bg.luminance().toDouble() + 0.05
        return maxOf(a, b) / minOf(a, b)
    }

    @Test
    fun `future dates are readable text on the panel`() {
        val r = ratio(LedgerContrast.FutureText, IronvellumColors.Vault)
        assertTrue("future text $r:1", r >= 4.5)
    }

    @Test
    fun `marks that carry meaning clear 3 to 1 on the panel`() {
        val r = ratio(LedgerContrast.Graphic, IronvellumColors.Vault)
        assertTrue("mark $r:1", r >= 3.0)
    }
}
