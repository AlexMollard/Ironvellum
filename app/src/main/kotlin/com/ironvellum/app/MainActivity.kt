package com.ironvellum.app

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ironvellum.app.data.Notifications
import android.content.Context
import android.content.res.Configuration
import com.ironvellum.app.ui.theme.FIXED_FONT_SCALE
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
import com.ironvellum.app.ui.TrialRequest
import com.ironvellum.app.ui.theme.IronvellumTheme

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
        // The app is always dark, so its bars are too. The default follows the
        // system theme and drew dark clock/battery icons on the dark header
        // whenever the phone was in light mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
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
        val sexes = (application as IronvellumApp).repository.observeSex()
        setContent {
            val bodySex by sexes.collectAsState(initial = Sex.MALE)
            IronvellumTheme {
                CompositionLocalProvider(LocalBodySex provides bodySex) {
                    IronvellumRoot(inboxRequest = inboxRequest, todayRequest = todayRequest, trialRequest = trialRequest)
                }
            }
        }
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
     * Bumped per notification tap so two taps in a row both navigate; the UI
     * compares counts, not values. Saved with the activity because the UI's
     * served counts are: a count reset to 0 by a rotation would sit below
     * them and the next tap would be ignored.
     */
    private var inboxRequest by mutableIntStateOf(0)
    private var todayRequest by mutableIntStateOf(0)
    private var trialRequest by mutableStateOf<TrialRequest?>(null)

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
