package com.ironvellum.app.domain

/**
 * Why a lifter would pick a movement: [purpose] says what it builds and why that matters, [pickIt] when it is
 * the right choice over its nearest alternatives and what it trades away, and [tags] two or three short benefits.
 */
data class ExerciseWhy(
    val purpose: String,
    val pickIt: String,
    val tags: List<String>,
)

/**
 * A "why do it" for every catalogue movement, keyed like [ExerciseGuides] by the trimmed, lowercased name.
 * Null means a user-created movement. One builder per group keeps each method under the JVM's 64 KB limit.
 */
object ExerciseWhys {

    private fun key(exerciseName: String) = exerciseName.trim().lowercase()

    fun forName(name: String): ExerciseWhy? = whys[key(name)]

    /** Every entry's key, for the catalogue coverage tests. */
    internal val keys: Set<String> get() = whys.keys

    private val whys: Map<String, ExerciseWhy> by lazy {
        buildMap {
            pushPullWhys()
            legsCoreWhys()
            libraryWhysA()
            libraryWhysB()
        }
    }
}
