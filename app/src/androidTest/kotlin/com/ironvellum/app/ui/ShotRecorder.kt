package com.ironvellum.app.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Real captures of a screen under test, for a human to look at: the device's own `screencap`, so what is saved
 * is what the display showed, status bar and all, not a recomposition of one node.
 *
 * The test activity is a bare ComponentActivity with a light window, which is what made earlier pictures pale.
 * [darkWindow] gives it what MainActivity gives the app (the Vault window background, edge to edge, dark bars)
 * before the content is set, so a capture shows the app's real theme. Pictures go to `/data/local/tmp` because
 * the suite uninstalls the app when it ends and takes the app's own files with it; pull them with
 * `adb pull /data/local/tmp/optimise-shots`.
 */
object ShotRecorder {

    private const val DIR = "/data/local/tmp/optimise-shots"

    /** Dress the test activity like MainActivity does. Call from `onActivity`, before `setContent`. */
    fun darkWindow(activity: ComponentActivity) {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        activity.window.setBackgroundDrawable(ColorDrawable(0xFF171715.toInt()))
    }

    /**
     * Saves the screen as [name].png. The test clock is held by the flow tests, so it is let run for a moment
     * first: a frame the held clock never drew would be captured stale.
     */
    fun take(compose: ComposeTestRule, name: String) {
        // The flow tests hold the clock, so let it run for a moment: a frame it never drew would be captured stale.
        compose.mainClock.autoAdvance = true
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        Thread.sleep(1200)
        // A screen that has dimmed or slept photographs as the wallpaper under a scrim, not as the app.
        shell("svc power stayon true")
        shell("input keyevent KEYCODE_WAKEUP")
        Thread.sleep(800)
        shell("mkdir -p $DIR")
        // A capture that fails must not leave an older picture standing in for it.
        shell("rm -f $DIR/$name.png")
        // The automation's own screenshot: unlike `screencap` it also draws sheet and dialog windows.
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val bitmap = checkNotNull(automation.takeScreenshot()) { "no screenshot" }
        // Handed to the shell through the app's external files dir, which both can reach; the shell then copies
        // it out to a folder that survives the uninstall. A shell redirect or pipe is not available here.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = java.io.File(checkNotNull(context.getExternalFilesDir(null)), "$name.png")
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        shell("cp ${file.absolutePath} $DIR/$name.png")
        file.delete()
        compose.mainClock.autoAdvance = false
    }

    private fun shell(command: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        // Reading to the end is what waits for the command to finish.
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
    }
}
