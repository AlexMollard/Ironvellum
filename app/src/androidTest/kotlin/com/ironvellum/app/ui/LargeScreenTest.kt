package com.ironvellum.app.ui

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironvellum.app.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LargeScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun rootContentIsCenteredAndNeverExceeds600Dp() {
        val bounds = compose.onNodeWithTag("rootContent").getUnclippedBoundsInRoot()
        val width = with(compose.density) { compose.activity.window.decorView.width.toDp() }
        assertTrue("content width ${bounds.right - bounds.left} exceeds 600dp", (bounds.right - bounds.left).value <= 600.5f)
        assertTrue("content is not centered: $bounds in $width", kotlin.math.abs((bounds.left + bounds.right - width).value) <= 2f)
    }
}
