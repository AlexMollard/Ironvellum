package com.ironvellum.app.ui.preparation

import android.os.Bundle
import com.ironvellum.app.domain.*
import com.ironvellum.app.ui.train.EditorEntry
import com.ironvellum.app.ui.train.EditorUi

/** Navigation arguments and editor recovery use only Bundle-supported values. Never save a preview to Room. */
internal fun EditorUi.preparationDraft() = Bundle().apply {
    putString("name", name); putString("note", note); scheduledDay?.let { putInt("day", it) }
    putParcelableArrayList("entries", ArrayList(entries.map { entry -> Bundle().apply {
        putLong("id", entry.exerciseId); putString("name", entry.exerciseName); putString("sets", entry.sets)
        putString("reps", entry.reps); putString("weight", entry.weight); putString("modifiers", entry.modifiers)
        entry.supersetGroup?.let { putInt("group", it) }; putString("scheme", entry.scheme.encode()); putBoolean("warmup", entry.warmup)
    } }))
    PreparationKind.entries.forEach { kind ->
        val choice = preparation[kind] ?: PreparationChoice()
        putString("${kind}_mode", choice.mode.name); choice.routineId?.let { putLong("${kind}_routine", it) }
    }
}

@Suppress("DEPRECATION")
internal fun EditorUi.withPreparationDraft(bundle: Bundle): EditorUi = copy(
    name = bundle.getString("name").orEmpty(), note = bundle.getString("note").orEmpty(),
    scheduledDay = if (bundle.containsKey("day")) bundle.getInt("day") else null,
    entries = bundle.getParcelableArrayList<Bundle>("entries").orEmpty().map { entry ->
        EditorEntry(
            exerciseId = entry.getLong("id"), exerciseName = entry.getString("name").orEmpty(),
            sets = entry.getString("sets").orEmpty(), reps = entry.getString("reps").orEmpty(),
            weight = entry.getString("weight").orEmpty(), modifiers = entry.getString("modifiers").orEmpty(),
            supersetGroup = if (entry.containsKey("group")) entry.getInt("group") else null,
            scheme = SetScheme.decode(entry.getString("scheme")), warmup = entry.getBoolean("warmup"),
        )
    },
    preparation = PreparationKind.entries.associateWith { kind -> PreparationChoice(
        bundle.getString("${kind}_mode")?.let(PreparationMode::valueOf) ?: PreparationMode.AUTO,
        if (bundle.containsKey("${kind}_routine")) bundle.getLong("${kind}_routine") else null,
    ) },
)
