package com.ironvellum.app.widget

import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.R
import com.ironvellum.app.ui.dashboard.DayKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Both widgets' RemoteViews inflate and fill for every face, in every size, with the real layouts,
 * fonts and drawables. A launcher applies them in its own process, so a bad id, a missing font or an
 * unsupported remote method shows up here as an exception and not as a blank widget.
 */
@RunWith(AndroidJUnit4::class)
class TodayWidgetRenderTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val kinds = DayKind.entries.filter { it != DayKind.PLANNED }

    private fun applied(views: android.widget.RemoteViews): View {
        var root: View? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { root = views.apply(context, FrameLayout(context)) }
        return root!!
    }

    @Test
    fun everyFaceInflatesInEverySize() {
        for (kind in kinds) {
            val state = WidgetSamples.state(kind)
            for (size in WidgetSize.entries) {
                for (views in listOf(WidgetFaces.today(context, state, size), WidgetFaces.week(context, state, size))) {
                    assertNotNull("$kind $size has a content description", applied(views).contentDescription)
                }
            }
        }
    }

    @Test
    fun theDayCardListsTheRoutine() {
        fun lines(kind: DayKind, size: WidgetSize) =
            (applied(WidgetFaces.today(context, WidgetSamples.state(kind), size)).findViewById<LinearLayout>(R.id.body)).childCount
        assertEquals(5, lines(DayKind.BEGIN, WidgetSize.M))
        assertEquals(5, lines(DayKind.LIVE, WidgetSize.L))
        // Done at 4x3: five exercises and the next line (the rows share the height).
        assertEquals(6, lines(DayKind.SEALED, WidgetSize.L))
        // Rest: the label and the next routine's four exercises.
        assertEquals(5, lines(DayKind.RESPITE, WidgetSize.M))
        // No plan: the label, three recent workouts and the button.
        assertEquals(5, lines(DayKind.NO_CYCLE, WidgetSize.M))
    }

    @Test
    fun theLadderHasOneSegmentPerSet() {
        val root = applied(WidgetFaces.today(context, WidgetSamples.state(DayKind.LIVE), WidgetSize.W))
        assertEquals(16, root.findViewById<LinearLayout>(R.id.ladder).childCount)
    }

    @Test
    fun theWeekDrawsSevenDays() {
        for (size in WidgetSize.entries) {
            val cols = applied(WidgetFaces.week(context, WidgetSamples.state(DayKind.BEGIN), size)).findViewById<LinearLayout>(R.id.cols)
            assertEquals("$size", 7, cols.childCount)
            // Friday is today: its cell is boxed, Monday's is not.
            assertEquals(View.VISIBLE, cols.getChildAt(4).findViewById<View>(R.id.c_fill).visibility)
            assertEquals(View.GONE, cols.getChildAt(0).findViewById<View>(R.id.c_fill).visibility)
        }
    }

    @Test
    fun aLauncherReapplyingTheViewsDoesNotDuplicateTheLists() {
        val week = WidgetFaces.week(context, WidgetSamples.state(DayKind.BEGIN), WidgetSize.M)
        val day = WidgetFaces.today(context, WidgetSamples.state(DayKind.LIVE), WidgetSize.M)
        val small = WidgetFaces.today(context, WidgetSamples.state(DayKind.LIVE), WidgetSize.W)
        for (views in listOf(week, day, small)) {
            val root = applied(views)
            InstrumentationRegistry.getInstrumentation().runOnMainSync { views.reapply(context, root) }
        }
        val root = applied(week)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { week.reapply(context, root) }
        assertEquals(7, root.findViewById<LinearLayout>(R.id.cols).childCount)
        val body = applied(day)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { day.reapply(context, body) }
        assertEquals(5, body.findViewById<LinearLayout>(R.id.body).childCount)
        val ladder = applied(small)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { small.reapply(context, ladder) }
        assertEquals(16, ladder.findViewById<LinearLayout>(R.id.ladder).childCount)
    }

    @Test
    fun theLauncherPicksALayoutForEachCell() {
        fun size(w: Float, h: Float) = WidgetSize.of(w, h)
        assertEquals(WidgetSize.S, size(170f, 84f))
        assertEquals(WidgetSize.W, size(360f, 84f))
        assertEquals(WidgetSize.M, size(360f, 200f))
        assertEquals(WidgetSize.M, size(360f, 240f))
        assertEquals(WidgetSize.L, size(360f, 300f))
        assertTrue(WidgetSize.of(0f, 0f) == WidgetSize.S)
    }
}
