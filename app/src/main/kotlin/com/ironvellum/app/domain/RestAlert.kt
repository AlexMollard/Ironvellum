package com.ironvellum.app.domain

/**
 * How the app tells the lifter a rest is over, and cues the last seconds of a
 * timed set. One setting for both, so a lifter who wants quiet gets quiet
 * everywhere. Vibrate is what the app always did.
 */
enum class RestAlert(
    /** Never renamed: it is what the preferences store. */
    val wire: String,
    val label: String,
    val caption: String,
) {
    VIBRATE("vibrate", "Vibrate", "One short buzz. No sound."),
    SOUND_VIBRATE("sound_vibrate", "Sound + vibrate", "The phone's default notification sound, with a buzz."),
    SILENT("silent", "Silent", "A quiet notice. No sound and no buzz.");

    val vibrates: Boolean get() = this != SILENT
    val sounds: Boolean get() = this == SOUND_VIBRATE

    /** What one cue plays: a tone and a buzz, each in milliseconds, 0 for none. */
    data class CuePlan(val toneMs: Int, val buzzMs: Long)

    /**
     * The cue for the last seconds of a timed set under this mode: Silent plays nothing, Vibrate
     * a buzz only, Sound + vibrate both. The ticks are short and the end is longer.
     */
    fun cuePlan(cue: SetClock.Cue): CuePlan = when (cue) {
        SetClock.Cue.NONE -> CuePlan(0, 0)
        SetClock.Cue.TICK -> CuePlan(if (sounds) TICK_TONE_MS else 0, if (vibrates) TICK_BUZZ_MS else 0)
        SetClock.Cue.DONE -> CuePlan(if (sounds) DONE_TONE_MS else 0, if (vibrates) DONE_BUZZ_MS else 0)
    }

    companion object {
        val DEFAULT = VIBRATE
        private const val TICK_TONE_MS = 90
        private const val TICK_BUZZ_MS = 40L
        private const val DONE_TONE_MS = 400
        private const val DONE_BUZZ_MS = 300L

        /** An unknown or missing word reads as the default. */
        fun fromWire(wire: String?): RestAlert = entries.firstOrNull { it.wire == wire } ?: DEFAULT
    }
}
