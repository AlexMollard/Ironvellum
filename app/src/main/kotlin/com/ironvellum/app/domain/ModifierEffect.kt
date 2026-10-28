package com.ironvellum.app.domain

/**
 * Which way a modifier moves the difficulty of one exercise, for the arrow on its chip. It reads the two places the
 * app already scores a modifier and keeps no table of its own: [MovementDifficulty.modifierFactor] (the effort of a
 * rep, the same whatever the movement) and [BodyShare.modifierFactor] (the lever, which depends on the movement:
 * "elevated" is harder on a push-up, a row or a lunge, "incline" easier).
 */
object ModifierEffect {
    enum class Effect { HARDER, EASIER, NONE }

    /**
     * [modifier] on [exerciseName]. NONE when the scoring prices it at 1.0 ("weighted" and "banded" are left out
     * on purpose, an unknown word scores nothing), and when the lever pulls the other way from the effort.
     */
    fun of(exerciseName: String, modifier: String): Effect {
        val token = modifier.trim().lowercase()
        val effort = direction(MovementDifficulty.modifierFactor(token))
        if (effort == Effect.NONE) return Effect.NONE
        val lever = direction(BodyShare.modifierFactor(exerciseName, token))
        return if (lever == Effect.NONE || lever == effort) effort else Effect.NONE
    }

    private fun direction(factor: Double): Effect = when {
        factor > 1.0 -> Effect.HARDER
        factor < 1.0 -> Effect.EASIER
        else -> Effect.NONE
    }
}
