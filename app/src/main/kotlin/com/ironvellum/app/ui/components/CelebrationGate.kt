package com.ironvellum.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * The one owner of "a full-screen celebration is on screen". A seal tells its story in order inside one dialog (the XP
 * page and its level moment, then deeds, then the summary, then the routine question), while the pages that wait for
 * Today (a milestone crest the level banked, deeds a reconcile found) are a dialog of their own. Two dialogs shown at
 * once stack, so a level-up could land on top of the XP progress. Whoever tells a story holds the gate for as long as
 * it is composed ([HoldsCelebration]); every other host shows nothing until it is released and then shows what waited.
 */
object CelebrationGate {

    private val holders = MutableStateFlow(0)

    /** How many celebrations are on screen now; more than 0 means every other host keeps its pages back. */
    val held: StateFlow<Int> = holders

    fun enter() = holders.update { it + 1 }

    fun leave() = holders.update { (it - 1).coerceAtLeast(0) }

    /** [pages] while nothing holds the gate, nothing while something does. */
    fun <T> visible(pages: List<T>, held: Int): List<T> = if (held > 0) emptyList() else pages
}

/** Holds [CelebrationGate] for as long as this is in the composition: put it in a celebration's own host. */
@Composable
internal fun HoldsCelebration() {
    DisposableEffect(Unit) {
        CelebrationGate.enter()
        onDispose { CelebrationGate.leave() }
    }
}

/** [AchievementOverlay] that waits its turn behind a seal's own celebration, then shows every page that waited. */
@Composable
internal fun GatedAchievementOverlay(
    pages: List<CelebrationPage>,
    onDone: () -> Unit,
    wornTitleId: String? = null,
    onWear: ((titleId: String) -> Unit)? = null,
    wornCrestId: String? = null,
    onWearCrest: ((crestId: String) -> Unit)? = null,
) {
    val held by CelebrationGate.held.collectAsState()
    AchievementOverlay(
        pages = CelebrationGate.visible(pages, held),
        onDone = onDone,
        wornTitleId = wornTitleId,
        onWear = onWear,
        wornCrestId = wornCrestId,
        onWearCrest = onWearCrest,
    )
}
