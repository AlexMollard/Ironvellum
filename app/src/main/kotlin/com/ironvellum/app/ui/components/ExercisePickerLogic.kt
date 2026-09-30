package com.ironvellum.app.ui.components

import com.ironvellum.app.domain.Equipment
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.ExerciseSearch
import com.ironvellum.app.domain.GearRequirements
import com.ironvellum.app.domain.MuscleGroup

/** What the picker's chips currently narrow the catalogue by. */
internal data class PickerFilters(
    val group: MuscleGroup? = null,
    val category: String? = null,
    val facet: EquipmentFacet? = null,
    /** Hide what the lifter's saved gear cannot do; ignored without saved gear. */
    val myGear: Boolean = false,
) {
    /** Chips that narrow by content. "My armoury" is a standing default, not a search step. */
    val narrows: Boolean get() = group != null || category != null || facet != null
}

/** The lists the picker draws; [count] is the number of distinct exercises that pass every filter. */
internal class PickerView(
    val favourites: List<Exercise>,
    val recents: List<Exercise>,
    val grouped: List<Pair<String, List<Exercise>>>,
    val count: Int,
)

/** True when a lifter with [equipment] (null = none saved) can do [exercise], or when that is not knowable. */
internal fun gearFits(exercise: Exercise, equipment: Equipment?): Boolean =
    equipment == null ||
        GearRequirements.fits(exercise.name, exercise.isWeighted, exercise.category, equipment)

/**
 * One pass over the catalogue.
 * Results sort favourites first, then (under a query) by
 * [ExerciseSearch.rank] and name, or (no query) by muscle group and name.
 * FAVOURITES and RECENT only pin when nothing narrows the list: under a
 * query or a content chip they would float above rows that already answer
 * the question. A pinned row is left out of the groups below, so no
 * exercise shows twice. Recents keep their most-recent-first order and are
 * NOT re-sorted; ids the catalogue no longer has drop out silently. Pinned
 * rows still respect the gear filter, so "All gear" is the one way to see them all.
 */
internal fun buildPickerView(
    exercises: List<Exercise>,
    query: String,
    filters: PickerFilters,
    equipment: Equipment?,
    recentIds: List<Long>,
    favouriteIds: Set<Long>,
): PickerView {
    val gear = if (filters.myGear) equipment else null
    val searching = query.isNotBlank()
    val hits = ArrayList<Pair<Exercise, Int>>()
    for (exercise in exercises) {
        if (filters.group != null && exercise.muscleGroup != filters.group) continue
        if (filters.category != null && exercise.category != filters.category) continue
        if (filters.facet != null && equipmentFacet(exercise) != filters.facet) continue
        if (!gearFits(exercise, gear)) continue
        val rank = ExerciseSearch.rank(exercise.name, query) ?: continue
        hits += exercise to rank
    }
    val ordered = hits.sortedWith(
        compareBy<Pair<Exercise, Int>>(
            { it.first.id !in favouriteIds },
            { if (searching) it.second else 0 },
            { if (searching) 0 else it.first.muscleGroup.ordinal },
            { it.first.name },
        ),
    ).map { it.first }

    val pinned = !searching && !filters.narrows
    val visible = if (pinned) ordered.mapTo(HashSet()) { it.id } else emptySet()
    val byId = if (pinned) exercises.associateBy { it.id } else emptyMap()
    val favourites = if (pinned) ordered.filter { it.id in favouriteIds } else emptyList()
    val recents = if (pinned) {
        recentIds.distinct().mapNotNull { byId[it] }.filter { it.id in visible && it.id !in favouriteIds }
    } else {
        emptyList()
    }
    val shown = (favourites + recents).mapTo(HashSet()) { it.id }
    val grouped = activityCategoryOrder(exercises)
        .map { c -> c to ordered.filter { it.category == c && it.id !in shown } }
        .filter { (_, list) -> list.isNotEmpty() }
    return PickerView(favourites, recents, grouped, ordered.size)
}

/** "" (strength) first, then the known activity groups, then anything novel alphabetically. */
internal fun activityCategoryOrder(exercises: List<Exercise>): List<String> {
    val present = exercises.map { it.category }.distinct()
    val known = listOf("Cardio", "Sport", "Climbing", "Water", "Mobility").filter { it in present }
    val extra = present.filter { it.isNotBlank() && it !in known }.sorted()
    return listOf("") + known + extra
}
