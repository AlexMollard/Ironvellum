package com.ironvellum.app.widget

import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.R
import com.ironvellum.app.ui.dashboard.DayKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Both widgets' RemoteViews inflate and fill for every face, in both sizes, with the real layouts,
 * fonts and drawables. A launcher applies them in its own process, so a bad id, a missing font or an
 * unsupported remote method shows up here as an exception and not as a blank widget.
 */
@RunWith(AndroidJUnit4::class)
class TodayWidgetRenderTest {

    private fun state(kind: DayKind) = WidgetState(
        kind = kind,
        title = "Push".takeIf { kind != DayKind.RESPITE && kind != DayKind.NO_CYCLE },
        presetId = 1L.takeIf { kind == DayKind.BEGIN },
        sessionId = 2L.takeIf { kind == DayKind.LIVE },
        exercises = 5, sets = 18, streak = 4, dayOfWeek = 5, estimateMinutes = 45,
        liveMinutes = 23, setsDone = 7, setsTotal = 18, sealedSets = 18, sealedMinutes = 52,
        next = NextRite("Legs", 2),
        week = listOf(DayMark.DONE, DayMark.PLANNED, DayMark.FREE, DayMark.DONE, DayMark.PLANNED, DayMark.FREE, DayMark.PLANNED),
    )

    private val faces = listOf<(android.content.Context, WidgetState) -> android.widget.RemoteViews>(
        WidgetFaces::day, WidgetFaces::dayCompact, WidgetFaces::week, WidgetFaces::weekCompact,
    )

    @Test
    fun everyFaceInflatesInBothSizes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (kind in DayKind.entries.filter { it != DayKind.PLANNED }) {
            for (face in faces) {
                val views = face(context, state(kind))
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    val root = views.apply(context, FrameLayout(context))
                    assertNotNull("$kind has a content description", root.contentDescription)
                }
            }
        }
    }

    @Test
    fun theDayCardShowsOneBottomControlPerFace() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun bottom(kind: DayKind): Triple<Int, Int, Int> {
            var seen = Triple(0, 0, 0)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val root = WidgetFaces.day(context, state(kind)).apply(context, FrameLayout(context))
                fun vis(id: Int) = root.findViewById<View>(id).visibility
                seen = Triple(vis(R.id.btn_fill), vis(R.id.btn_out), vis(R.id.a_footer))
            }
            return seen
        }
        assertEquals(Triple(View.VISIBLE, View.GONE, View.GONE), bottom(DayKind.BEGIN))
        assertEquals(Triple(View.VISIBLE, View.GONE, View.GONE), bottom(DayKind.LIVE))
        assertEquals(Triple(View.GONE, View.GONE, View.VISIBLE), bottom(DayKind.SEALED))
        assertEquals(Triple(View.GONE, View.VISIBLE, View.GONE), bottom(DayKind.RESPITE))
        assertEquals(Triple(View.GONE, View.VISIBLE, View.GONE), bottom(DayKind.NO_CYCLE))
    }

    @Test
    fun theWeekStripBoxesToday() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val root = WidgetFaces.week(context, state(DayKind.BEGIN)).apply(context, FrameLayout(context))
            assertEquals(View.VISIBLE, root.findViewById<View>(R.id.b_cell_bg5).visibility)
            assertEquals(View.GONE, root.findViewById<View>(R.id.b_cell_bg1).visibility)
            assertEquals("4", root.findViewById<TextView>(R.id.b_number).text.toString())
        }
    }
}
