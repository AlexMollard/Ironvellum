package com.ironvellum.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A launch effect that navigates (resume a workout, a tapped notification, a widget) can answer before the
 * NavHost has set its graph: the NavHost is composed at layout time, inside Scaffold. Navigating then crashes
 * with "Navigation graph has not been set". On a cold start the gap is about a second, so it is held open here
 * rather than raced.
 */
@RunWith(AndroidJUnit4::class)
class NavWhenReadyTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun navigationWaitsForTheGraph() {
        var nav: NavHostController? = null
        var graphShown by mutableStateOf(false)
        compose.setContent {
            val controller = rememberNavController()
            nav = controller
            if (graphShown) {
                NavHost(controller, startDestination = "a") {
                    composable("a") {}
                    composable("b") {}
                }
            }
        }
        compose.waitForIdle()
        var ran = false
        val lifecycle = compose.activity.lifecycle
        assertTrue(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        CoroutineScope(Dispatchers.Main).launch {
            nav!!.whenReady(lifecycle) {
                nav!!.navigate("b")
                ran = true
            }
        }
        compose.waitForIdle()
        assertFalse("must not navigate before the graph is set", ran)

        graphShown = true
        compose.waitUntil(5_000) { ran }
        compose.waitForIdle()
        assertEquals("b", nav!!.currentDestination?.route)
    }
}
