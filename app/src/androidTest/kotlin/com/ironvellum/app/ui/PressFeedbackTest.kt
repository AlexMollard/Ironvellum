package com.ironvellum.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.ListRow
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class PressFeedbackTest {
    @get:Rule val compose = createComposeRule()

    @Test fun aButtonHighlightsBothEdgesImmediatelyAndClearsOnRelease() = checkFeedback(row = false)
    @Test fun aRowHighlightsBothEdgesImmediatelyAndClearsOnRelease() = checkFeedback(row = true)
    @Test fun aDisabledButtonDoesNotHighlightOrClick() = checkFeedback(row = false, enabled = false)

    private fun checkFeedback(row: Boolean, enabled: Boolean = true) {
        var clicks = 0
        lateinit var layer: GraphicsLayer
        compose.setContent {
            IronvellumTheme {
                layer = rememberGraphicsLayer()
                val capture = Modifier.testTag("control").drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                }
                Box(Modifier.width(280.dp).background(IronvellumColors.Vault)) {
                    if (row) ListRow("Appearance", capture.background(IronvellumColors.Vault), onClick = { clicks++ })
                    else IronvellumButton("Apply", { clicks++ }, capture.width(280.dp), enabled = enabled)
                }
            }
        }
        compose.mainClock.autoAdvance = false
        val control = compose.onNodeWithTag("control")
        val before = runBlocking { layer.toImageBitmap() }.toPixelMap()
        control.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        val pressed = runBlocking { layer.toImageBitmap() }.toPixelMap()
        val y = if (row) before.height - 6 else before.height / 2
        listOf(10, before.width - 11).forEach { x ->
            if (enabled) assertTrue("Press feedback must reach both edges immediately", pressed[x, y] != before[x, y])
            else assertEquals(before[x, y], pressed[x, y])
        }
        control.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        val released = runBlocking { layer.toImageBitmap() }.toPixelMap()
        listOf(10, before.width - 11).forEach { x -> assertEquals(before[x, y], released[x, y]) }
        compose.runOnIdle { assertEquals(if (enabled) 1 else 0, clicks) }
    }
}
