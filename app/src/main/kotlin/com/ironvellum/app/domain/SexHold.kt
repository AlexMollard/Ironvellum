package com.ironvellum.app.domain

/**
 * The hold state of a score that is scaled by sex while no sex has been chosen. A Skip before the sex question
 * leaves the stored default (male) in place, but that is not a choice, so nothing scaled by it is shown: the score
 * reads [TEXT] and the line is a link to Profile, the way a score waits for height and weight. Once a sex is picked
 * the scores appear; installs that already stored a sex never hold.
 */
object SexHold {
    const val TEXT = "Add sex in Profile"

    /** Strength Rank's band, or the hold text while the sex is unset. */
    fun rank(band: String, sexUnset: Boolean): String = if (sexUnset) TEXT else band

    /** A sex-scaled figure's muted line (FFMI's "from 3 Oct"), or the hold text while the sex is unset. */
    fun line(normal: String, sexUnset: Boolean): String = if (sexUnset) TEXT else normal

    /** A sex-scaled figure itself: a dash while the sex is unset. */
    fun figure(normal: String, sexUnset: Boolean): String = if (sexUnset) "—" else normal
}
