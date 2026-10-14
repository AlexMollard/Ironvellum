package com.ironvellum.app.ui

/**
 * Holds the system launch screen until the first screen has its data. Without it, Today drew its
 * empty defaults and then settled as each read arrived, a second or two of a half-built screen.
 * Opened once per process: a recreated activity (rotation, return from the background) finds the
 * data already read and draws at once.
 */
object StartupGate {
    /** Never hold the launch screen longer than this, however slow the first read is. */
    const val MAX_HOLD_MS = 1_500L

    @Volatile
    var ready: Boolean = false
        private set

    fun open() {
        ready = true
    }
}
