package com.monarch.app

import android.content.Context
import android.content.res.Configuration
import com.monarch.app.ui.theme.FIXED_FONT_SCALE
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.monarch.app.ui.MonarchRoot
import com.monarch.app.ui.theme.MonarchTheme

class MainActivity : ComponentActivity() {

    /**
     * Pins the text scale for EVERY window this activity opens.
     *
     * Overriding `LocalDensity` in the theme only covers the tree it is
     * provided to. A `Dialog`, an `AlertDialog`, a popup or a menu composes in
     * its own window, which re-reads the platform configuration — measured: with
     * the theme pinned, a dialog's title still grew 409px -> 756px between
     * system 1.0x and 2.0x. Wrapping each dialog worked but had to be
     * remembered at every future call site, and the first sweep for them
     * already missed three `AlertDialog`s.
     *
     * Overriding the configuration here is the one place that cannot be
     * forgotten: every window inherits this context. Only `fontScale` is
     * touched, so display size (density) still applies.
     */
    override fun attachBaseContext(newBase: Context) {
        val pinned = Configuration(newBase.resources.configuration).apply {
            fontScale = FIXED_FONT_SCALE
        }
        super.attachBaseContext(newBase.createConfigurationContext(pinned))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MonarchTheme {
                MonarchRoot()
            }
        }
    }
}
