package com.ironvellum.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Asks the launcher to pin a widget (`-e pin today` or `-e pin week`), which shows its own "Add to home screen"
 * dialog: the way a picture of the real widget on the launcher is arranged without dragging. Without the argument
 * it does nothing and passes, so the gate runs it for free.
 */
@RunWith(AndroidJUnit4::class)
class WidgetPinTest {
    @Test
    fun pin() {
        val which = InstrumentationRegistry.getArguments().getString("pin") ?: return
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = if (which == "week") WeekWidgetProvider::class.java else TodayWidgetProvider::class.java
        AppWidgetManager.getInstance(context).requestPinAppWidget(ComponentName(context, provider), null, null)
    }
}
