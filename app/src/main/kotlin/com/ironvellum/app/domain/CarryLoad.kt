package com.ironvellum.app.domain

/** One set of a movement, as far as carrying a load is concerned. */
data class CarrySet(val id: Long, val setIndex: Int, val weightKg: Double?, val done: Boolean)

/**
 * The ids of the sets that should inherit [ticked]'s load now it is done:
 * later sets of the same movement that are not done and still have no load
 * (null or 0 kg). A load the lifter or the Forge already set is never
 * overwritten, and a tick without a load carries nothing. [siblings] may
 * include [ticked] itself.
 */
fun loadsToCarry(ticked: CarrySet, siblings: List<CarrySet>): List<Long> {
    if ((ticked.weightKg ?: 0.0) <= 0.0) return emptyList()
    return siblings
        .filter { it.setIndex > ticked.setIndex && !it.done && (it.weightKg ?: 0.0) <= 0.0 }
        .map { it.id }
}
