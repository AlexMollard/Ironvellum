package com.ironvellum.app

import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import com.ironvellum.app.ui.StartupGate
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ironvellum.app.data.Notifications
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.ironvellum.app.domain.Sex
import com.ironvellum.app.ui.program.LocalBodySex
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import android.graphics.Color
import com.ironvellum.app.ui.IronvellumRoot
import com.ironvellum.app.ui.RouteRequest
import com.ironvellum.app.ui.Routes
import com.ironvellum.app.ui.TrialRequest
import com.ironvellum.app.data.Words
import com.ironvellum.app.widget.loadWidgetState
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.ironvellum.app.ui.theme.IronvellumTheme
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.isSystemDark
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // The theme resolved before the first frame, so the window background and the bars wear it from the start.
        val app = application as IronvellumApp
        val lightNow = app.appearanceStore.mode.value.isLight(isSystemDark(this))
        IronvellumColors.light = lightNow
        setTheme(if (lightNow) R.style.Theme_Ironvellum_Light else R.style.Theme_Ironvellum_Dark)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = barStyle(lightNow), navigationBarStyle = barStyle(lightNow))
        // A rotation or process restore re-delivers the launch intent; only a
        // fresh launch may act on it, or the inbox would reopen on every turn.
        if (savedInstanceState == null) {
            noteOpenTab(intent)
        } else {
            inboxRequest = savedInstanceState.getInt(KEY_INBOX_REQUEST)
            todayRequest = savedInstanceState.getInt(KEY_TODAY_REQUEST)
            val serial = savedInstanceState.getInt(KEY_TRIAL_SERIAL)
            if (serial > 0) {
                trialRequest = TrialRequest(
                    serial = serial,
                    sessionId = savedInstanceState.getLong(KEY_TRIAL_SESSION),
                    seal = savedInstanceState.getBoolean(KEY_TRIAL_SEAL),
                )
            }
        }
        val sexes = app.repository.observeSex()
        setContent {
            val bodySex by sexes.collectAsState(initial = Sex.MALE)
            val mode by app.appearanceStore.mode.collectAsState()
            val light = mode.isLight(isSystemInDarkTheme())
            // The bars follow the theme in force: dark icons on parchment, light icons on charcoal.
            // Only when the resolved theme changes: not on every recomposition (an accent change recomposes this scope).
            LaunchedEffect(light) {
                enableEdgeToEdge(statusBarStyle = barStyle(light), navigationBarStyle = barStyle(light))
                window.decorView.setBackgroundColor(IronvellumColors.Vault.toArgb())
            }
            IronvellumTheme(light = light) {
                CompositionLocalProvider(LocalBodySex provides bodySex) {
                    IronvellumRoot(inboxRequest = inboxRequest, todayRequest = todayRequest, trialRequest = trialRequest, routeRequest = routeRequest)
                }
            }
        }
        holdLaunchScreen()
    }

    private fun barStyle(light: Boolean) =
        if (light) SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT) else SystemBarStyle.dark(Color.TRANSPARENT)

    /**
     * Cancels the first frames until [StartupGate] opens, so the launch screen (the icon on the
     * app's background) stays up instead of a half-filled Today. The platform's own hook: a
     * cancelled draw schedules the next frame, and composition and layout keep running.
     */
    private fun holdLaunchScreen() {
        val launchedAt = SystemClock.uptimeMillis()
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                val draw = StartupGate.ready || SystemClock.uptimeMillis() - launchedAt > StartupGate.MAX_HOLD_MS
                if (draw) content.viewTreeObserver.removeOnPreDrawListener(this)
                return draw
            }
        })
    }

    /** The notification tap while the activity is alive arrives here, not in onCreate. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        noteOpenTab(intent)
    }

    private fun noteOpenTab(intent: Intent?) {
        when (intent?.getStringExtra(Notifications.EXTRA_OPEN_TAB)) {
            Notifications.TAB_INBOX -> inboxRequest++
            Notifications.TAB_TODAY -> todayRequest++
            Notifications.TAB_BEGIN -> beginFromWidget(intent.getBooleanExtra(Notifications.EXTRA_EMPTY, false))
            Notifications.TAB_RESUME -> beginFromWidget(empty = false, start = false)
            Notifications.TAB_HOME -> goTo(Routes.DASHBOARD)
            Notifications.TAB_FORGE -> goTo(Routes.programBuilder("week", null))
            Notifications.TAB_RECAP -> {
                val week = intent.getLongExtra(Notifications.EXTRA_RECAP_WEEK, Long.MIN_VALUE)
                if (week != Long.MIN_VALUE) {
                    goTo(if (intent.getBooleanExtra(Notifications.EXTRA_RECAP_SHARE, false)) Routes.recapShare(week) else Routes.recap(week))
                }
            }
            Notifications.TAB_TRIAL -> {
                val id = intent.getLongExtra(Notifications.EXTRA_SESSION_ID, -1L)
                if (id > 0L) {
                    trialRequest = TrialRequest(
                        serial = (trialRequest?.serial ?: 0) + 1,
                        sessionId = id,
                        seal = intent.getBooleanExtra(Notifications.EXTRA_SEAL, false),
                    )
                }
            }
        }
    }

    /**
     * The widget's Start, resolved against the state as it is now: the live trial is opened, else today's
     * rite is begun by the same call Today's button makes; anything else (sealed since, day turned over)
     * just lands on Today. With [start] false (the Resume shortcut) nothing is begun: no live trial lands on Today.
     */
    private fun beginFromWidget(empty: Boolean, start: Boolean = true) {
        val repo = (application as IronvellumApp).repository
        lifecycleScope.launch {
            val state = runCatching { loadWidgetState(applicationContext, repo, forTap = true) }.getOrNull()
            val sessionId = runCatching {
                when {
                    state?.sessionId != null -> state.sessionId
                    !start -> null
                    empty -> repo.startFreeformSession(Words.openTrial.c)
                    state?.presetId != null -> repo.startSessionFromPreset(state.presetId)
                    else -> null
                }
            }.getOrNull()
            if (sessionId == null) {
                goTo(Routes.DASHBOARD)
            } else {
                trialRequest = TrialRequest(serial = (trialRequest?.serial ?: 0) + 1, sessionId = sessionId, seal = false)
            }
        }
    }

    /**
     * Bumped per notification tap so two taps in a row both navigate; the UI
     * compares counts, not values. Saved with the activity because the UI's
     * served counts are: a count reset to 0 by a rotation would sit below
     * them and the next tap would be ignored.
     */
    private var inboxRequest by mutableIntStateOf(0)
    private var todayRequest by mutableIntStateOf(0)
    private var trialRequest by mutableStateOf<TrialRequest?>(null)
    private var routeRequest by mutableStateOf<RouteRequest?>(null)

    /** A widget tap that lands on a screen of its own; see [RouteRequest]. */
    private fun goTo(route: String) {
        routeRequest = RouteRequest(serial = (routeRequest?.serial ?: 0) + 1, route = route)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_INBOX_REQUEST, inboxRequest)
        outState.putInt(KEY_TODAY_REQUEST, todayRequest)
        trialRequest?.let {
            outState.putInt(KEY_TRIAL_SERIAL, it.serial)
            outState.putLong(KEY_TRIAL_SESSION, it.sessionId)
            outState.putBoolean(KEY_TRIAL_SEAL, it.seal)
        }
    }

    private companion object {
        const val KEY_INBOX_REQUEST = "inbox_request"
        const val KEY_TODAY_REQUEST = "today_request"
        const val KEY_TRIAL_SERIAL = "trial_serial"
        const val KEY_TRIAL_SESSION = "trial_session"
        const val KEY_TRIAL_SEAL = "trial_seal"
    }
}
