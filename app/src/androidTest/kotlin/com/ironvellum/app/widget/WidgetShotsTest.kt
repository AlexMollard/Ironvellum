package com.ironvellum.app.widget

import android.appwidget.AppWidgetHostView
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.ui.dashboard.DayKind
import com.ironvellum.app.ui.theme.ThemeMode
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Pictures of every widget face at the size of each cell, for a human to lay beside the v4 mockups. Opt-in, like
 * [com.ironvellum.app.ui.ShotRecorder]: the gate skips them (the test then passes at once); ask with
 * `-e shots true` on `am instrument`, then `adb pull /sdcard/Android/data/com.ironvellum.app/files/widget-shots`.
 *
 * A launcher is not needed: the faces are applied through an [AppWidgetHostView] of the cell's exact size, which
 * is what a launcher does, so the layout the size map picks is the one a phone would show. The view is drawn at
 * 320 dpi, twice the mockups' 1 dp = 1 px, so a crop scales to the mockup by exactly two.
 */
@RunWith(AndroidJUnit4::class)
class WidgetShotsTest {
    private val enabled get() = InstrumentationRegistry.getArguments().getString("shots") == "true"

    @Test
    fun everyFaceAtEverySize() {
        if (!enabled) return
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(app.getExternalFilesDir(null), "widget-shots").apply { deleteRecursively(); mkdirs() }
        val store = (app.applicationContext as IronvellumApp).appearanceStore
        val original = store.mode.value
        val config = android.content.res.Configuration(app.resources.configuration).apply { densityDpi = 320 }
        val context = app.createConfigurationContext(config)
        try {
            for ((theme, mode) in listOf("dark" to ThemeMode.DARK, "light" to ThemeMode.LIGHT)) {
                store.setMode(mode)
                for (kind in listOf(DayKind.BEGIN, DayKind.LIVE, DayKind.SEALED, DayKind.RESPITE, DayKind.NO_CYCLE)) {
                    val state = WidgetSamples.state(kind)
                    for ((widget, provider) in listOf("today" to TodayWidgetProvider::class.java, "week" to WeekWidgetProvider::class.java)) {
                        for ((name, size) in listOf("2x1" to SizeF(170f, 84f), "4x1" to SizeF(360f, 84f), "4x2" to SizeF(360f, 200f), "4x3" to SizeF(360f, 300f))) {
                            val views = TodayWidget.views(context, provider, state, null)
                            save(context, views, size, File(dir, "${widget}_${name}_${kind.name.lowercase()}_$theme.png"))
                        }
                        // Long streaks: the number must show whole at every width.
                        if (kind == DayKind.BEGIN) for (streak in listOf(69, 365)) for ((name, size) in listOf("2x1" to SizeF(170f, 84f), "4x1" to SizeF(360f, 84f), "4x2" to SizeF(360f, 200f))) {
                            val views = TodayWidget.views(context, provider, state.copy(streak = streak), null)
                            save(context, views, size, File(dir, "${widget}_${name}_streak${streak}_$theme.png"))
                        }
                    }
                }
            }
        } finally {
            store.setMode(original)
            // The pushes setMode started race each other; draw once more, last, for the theme that is back in force.
            kotlinx.coroutines.runBlocking { TodayWidget.update(app) }
        }
    }

    private fun save(context: android.content.Context, views: RemoteViews, size: SizeF, file: File) {
        val density = context.resources.displayMetrics.density
        val width = (size.width * density).toInt()
        val height = (size.height * density).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val host = AppWidgetHostView(context)
            host.updateAppWidgetSize(Bundle(), listOf(size))
            host.updateAppWidget(views)
            host.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            host.layout(0, 0, width, height)
            host.draw(Canvas(bitmap))
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
