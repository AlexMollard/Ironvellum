package com.ironvellum.app.ui

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Runs a repository call from a view model without letting it kill the process.
 *
 * The repository enforces its invariants by THROWING — `completeSession` checks
 * the session is not already complete, `claimSkill` checks the skill is not
 * already mastered, `startSessionFromPreset` errors on a missing preset. That is
 * the right shape for the data layer: the alternative is a silent no-op that
 * lets a double payment through.
 *
 * It is the wrong shape for a `viewModelScope.launch` with no catch, which is
 * how every one of those was called. A second tap landing before the UI moved
 * on took the exception straight into the coroutine scope and took the app with
 * it. The user's action in every one of these cases is "that already happened",
 * for which the correct behaviour is to do nothing.
 *
 * Failures are logged rather than shown: these are all double-tap and
 * stale-state paths where a message would explain a mistake the lifter did not
 * make. A genuine failure the user must see gets its own state, as the import
 * and sync paths already do.
 */
fun CoroutineScope.launchGuarded(what: String, block: suspend () -> Unit): Job = launch {
    runCatching { block() }.onFailure { error ->
        Log.w("Ironvellum", "$what refused: ${error.message}")
    }
}
