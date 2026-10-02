package com.ironvellum.app.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/**
 * Saves the open dialog to the app's external files dir as `<name>.png`, for a
 * human to look at. Best effort: a capture can miss its redraw on a busy
 * emulator ("Failed waiting for PixelCopy"), and a picture for review must
 * never fail the assertions it sits beside. Two tries, then a log line.
 */
fun ComposeContentTestRule.saveDialogScreenshot(name: String) {
    // Capture forces a redraw, which a paused clock never delivers.
    mainClock.autoAdvance = true
    repeat(2) { attempt ->
        val image = runCatching { onNode(isDialog()).captureToImage() }.getOrNull()
        if (image != null) {
            val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
            File(dir, "$name.png").outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
            return
        }
        Log.w("DialogScreenshot", "capture of $name failed (attempt ${attempt + 1})")
    }
}
