package com.ironvellum.app.domain

/** The modifier tag a bodyweight movement carries while it holds added load. */
const val WEIGHTED_MODIFIER = "weighted"

/**
 * A bodyweight movement's [current] modifiers after a load edit: "weighted"
 * joins when a set [becameLoaded] (none or 0 kg to more), and leaves once no
 * set of the movement is [anyLoaded]. Other tags keep their order; anything
 * else returns [current] untouched, so a tag removed by hand is not forced
 * back by the next change of kilos.
 */
fun modifiersAfterLoadChange(current: String, becameLoaded: Boolean, anyLoaded: Boolean): String {
    val tags = current.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val has = tags.any { it.equals(WEIGHTED_MODIFIER, ignoreCase = true) }
    return when {
        !anyLoaded && has -> tags.filterNot { it.equals(WEIGHTED_MODIFIER, ignoreCase = true) }.joinToString(", ")
        becameLoaded && !has -> (tags + WEIGHTED_MODIFIER).joinToString(", ")
        else -> current
    }
}
