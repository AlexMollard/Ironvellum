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

    companion object {
        val DEFAULT = VIBRATE

        /** An unknown or missing word reads as the default. */
        fun fromWire(wire: String?): RestAlert = entries.firstOrNull { it.wire == wire } ?: DEFAULT
    }
}
