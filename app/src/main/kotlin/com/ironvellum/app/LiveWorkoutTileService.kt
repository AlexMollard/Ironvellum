package com.ironvellum.app

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.SystemClock
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.ironvellum.app.data.Notifications
import com.ironvellum.app.data.RestClock
import com.ironvellum.app.domain.LiveWorkout
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.dashboard.DayKind
import com.ironvellum.app.widget.loadWidgetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The Quick Settings tile. Idle it names today's routine and a tap begins it; while a workout runs it shows the set (or
 * the rest counting down) and a tap opens the workout. Both taps are the widgets' own Start ([Notifications.TAB_BEGIN]),
 * which MainActivity resolves against the state as it is when the tap lands. What the tile says is
 * [LiveWorkout.tile]'s; this only listens while the panel is open and paints it.
 */
class LiveWorkoutTileService : TileService() {

    private var scope: CoroutineScope? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onStartListening() {
        super.onStartListening()
        val repo = (applicationContext as IronvellumApp).repository
        val job = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = job
        job.launch {
            val running = repo.observeLiveSession().flatMapLatest { session ->
                if (session == null) flowOf(null) else repo.observeSessionSets(session.id).map { session.id to it }
            }
            // A rest counts down in whole seconds, so the panel redraws once a second while it is open.
            val ticks = flow { while (true) { emit(Unit); delay(1000) } }
            var today: String? = null
            var todayRead = false
            var painted: List<Any?>? = null
            combine(running, RestClock.timer, ticks) { live, rest, _ -> live to rest }.collect { (live, rest) ->
                if (live == null && !todayRead) {
                    today = runCatching { loadWidgetState(applicationContext, repo, forTap = true) }.getOrNull()
                        ?.takeIf { it.kind == DayKind.BEGIN }?.title
                    todayRead = true
                } else if (live != null) {
                    todayRead = false
                }
                val now = SystemClock.elapsedRealtime()
                val resting = rest?.takeIf { live != null && it.sessionId == live.first && !it.isOver(now) }
                val sets: List<SessionSet>? = live?.second
                val tile = LiveWorkout.tile(today, sets, resting?.label(now))
                val label = tile.label.asString(this@LiveWorkoutTileService)
                val subtitle = tile.subtitle.asString(this@LiveWorkoutTileService)
                val key = listOf(label, subtitle, tile.active, resting != null)
                if (key != painted) {
                    painted = key
                    paint(label, subtitle, tile.active, resting != null)
                }
            }
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    private fun paint(label: String, subtitle: String, active: Boolean, resting: Boolean) {
        val tile = qsTile ?: return
        tile.label = label
        tile.subtitle = subtitle
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(this, if (resting) R.drawable.ic_tile_rest else R.drawable.ic_tile_workout)
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(Notifications.EXTRA_OPEN_TAB, Notifications.TAB_BEGIN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        // Beginning a workout from a locked phone must not happen behind the keyguard: unlock first.
        val open = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(this, Notifications.REQUEST_TILE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
                )
            } else {
                @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated") // the PendingIntent form needs API 34
                startActivityAndCollapse(intent)
            }
        }
        if (isLocked) unlockAndRun(open) else open()
    }
}
