package com.ironvellum.app.ui.components

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.data.ProgramAnswersStore
import com.ironvellum.app.data.Repository
import com.ironvellum.app.domain.Equipment
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.LastLogged
import com.ironvellum.app.domain.MovementDifficulty
import com.ironvellum.app.domain.MuscleGroup
import com.ironvellum.app.domain.Skills
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the picker reads besides the catalogue; all of it live. */
internal data class PickerData(
    /** Most-recent-first, from completed workouts; the order is the point. */
    val recentIds: List<Long> = emptyList(),
    val favouriteIds: Set<Long> = emptySet(),
    val lastLogged: Map<Long, LastLogged> = emptyMap(),
    /** The gear the lifter saved with the program answers; null = none saved. */
    val equipment: Equipment? = null,
)

/**
 * Feeds every picker host the same live data, so no screen snapshots recents
 * or carries its own copy. Scoped to the host's ViewModel store owner.
 */
internal class ExercisePickerViewModel(
    private val repo: Repository,
    context: Context,
) : ViewModel() {
    init {
        // Primes ProgramAnswersStore.equipment, which only fills on get/save.
        ProgramAnswersStore.get(context)
    }

    val data: StateFlow<PickerData> = combine(
        repo.observeRecentExerciseIds(),
        repo.observeFavouriteExerciseIds(),
        repo.observeLastLogged(),
        ProgramAnswersStore.equipment,
    ) { recents, favourites, last, equipment ->
        PickerData(recents, favourites, last, equipment)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PickerData(equipment = ProgramAnswersStore.equipment.value),
    )

    fun setFavourite(exerciseId: Long, favourite: Boolean) {
        viewModelScope.launch { repo.setFavourite(exerciseId, favourite) }
    }
}

@Composable
private fun rememberPickerViewModel(): ExercisePickerViewModel {
    val appContext = LocalContext.current.applicationContext
    return viewModel(
        key = "exercise_picker",
        factory = viewModelFactory { initializer { ExercisePickerViewModel(ironvellumRepository(), appContext) } },
    )
}

private class PickerControls(defaultMyGear: Boolean) {
    var query by mutableStateOf("")
    var filters by mutableStateOf(PickerFilters(myGear = defaultMyGear))
    var filtersOpen by mutableStateOf(false)
}

@Composable
private fun pickerView(exercises: List<Exercise>, controls: PickerControls, data: PickerData): PickerView =
    remember(exercises, controls.query, controls.filters, data) {
        buildPickerView(exercises, controls.query, controls.filters, data.equipment, data.recentIds, data.favouriteIds)
    }

/**
 * The universal exercise picker as a full-screen sheet: search pinned above
 * the keyboard, then the filter rails and the FAVOURITES / RECENT / grouped
 * rows. [topContent] adds host-specific rows above the filters (the import
 * review's "keep as new exercise"). [defaultMyGear] sets whether the MY GEAR
 * chip starts on; it only exists when the lifter has saved equipment.
 */
@Composable
fun ExercisePickerSheet(
    exercises: List<Exercise>,
    onPick: (Exercise) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Select exercise",
    defaultMyGear: Boolean = true,
    topContent: (LazyListScope.() -> Unit)? = null,
) {
    val vm = rememberPickerViewModel()
    val data by vm.data.collectAsStateWithLifecycle()
    val controls = remember { PickerControls(defaultMyGear) }
    val view = pickerView(exercises, controls, data)
    // Only computed when the list is already empty: a second catalogue pass,
    // run with MY GEAR off, is what proves the gear filter did the emptying.
    val gearHidAll = view.count == 0 &&
        remember(exercises, controls.query, controls.filters, data) {
            controls.filters.myGear && data.equipment != null &&
                buildPickerView(
                    exercises,
                    controls.query,
                    controls.filters.copy(myGear = false),
                    data.equipment,
                    data.recentIds,
                    data.favouriteIds,
                ).count > 0
        }
    InkPickerSheet(
        title = title,
        onDismiss = onDismiss,
        query = controls.query,
        onQueryChange = { controls.query = it },
        searchLabel = "Search exercises",
        count = view.count,
    ) {
        topContent?.invoke(this)
        item(key = "filters") {
            PickerFilterBar(exercises, controls, data.equipment)
            Spacer(Modifier.height(12.dp))
        }
        exerciseRows(view, data, controls.query, gearHidAll, browse = false, onPick, vm::setFavourite)
    }
}

/**
 * The same picker inline, for screens that ARE the picker (the explorer): the
 * host's own header names it and its own back leaves it. Same rails, rows,
 * favourites and last-logged lines as the sheet.
 */
@Composable
fun ExercisePickerPanel(
    exercises: List<Exercise>,
    onPick: (Exercise) -> Unit,
    modifier: Modifier = Modifier,
    defaultMyGear: Boolean = true,
) {
    val vm = rememberPickerViewModel()
    val data by vm.data.collectAsStateWithLifecycle()
    val controls = remember { PickerControls(defaultMyGear) }
    val view = pickerView(exercises, controls, data)
    // Same proof as the sheet: a second pass with MY GEAR off, only when empty.
    val gearHidAll = view.count == 0 &&
        remember(exercises, controls.query, controls.filters, data) {
            controls.filters.myGear && data.equipment != null &&
                buildPickerView(
                    exercises,
                    controls.query,
                    controls.filters.copy(myGear = false),
                    data.equipment,
                    data.recentIds,
                    data.favouriteIds,
                ).count > 0
        }
    Column(modifier.fillMaxWidth()) {
        Text(
            plural(view.count, "1 exercise", "${view.count} exercises"),
            style = MaterialTheme.typography.labelSmall,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.align(Alignment.End),
        )
        Spacer(Modifier.height(4.dp))
        PickerSearchField(controls.query, { controls.query = it }, "Search exercises")
        Spacer(Modifier.height(10.dp))
        PickerFilterBar(exercises, controls, data.equipment)
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            exerciseRows(view, data, controls.query, gearHidAll, browse = true, onPick, vm::setFavourite)
        }
    }
}

/**
 * One line while closed: "Filters" plus the filters that are on, each tapped
 * to clear, so a narrowed list always says why. Open, the choices wrap in two
 * labelled groups with no "All" chips (tapping an active chip turns it off); the
 * active chips are not repeated then, because the highlighted ones show the same.
 * Three stacked scrolling rails, each with its own ALL, took a third of the
 * sheet before a single exercise showed.
 *
 * Muscle groups and activity categories are one TYPE choice: a lifting group
 * and an activity never overlap, so holding both could only empty the list.
 */
@Composable
private fun PickerFilterBar(exercises: List<Exercise>, controls: PickerControls, equipment: Equipment?) {
    val f = controls.filters
    val categories = activityCategoryOrder(exercises).filter { it.isNotBlank() }
    val active = buildList<Pair<String, () -> Unit>> {
        f.group?.let { add(sentence(it.name) to { controls.filters = controls.filters.copy(group = null) }) }
        f.category?.let { add(sentence(it) to { controls.filters = controls.filters.copy(category = null) }) }
        if (equipment != null && f.myGear) add("My armoury" to { controls.filters = controls.filters.copy(myGear = false) })
        f.facet?.let { add(it.label to { controls.filters = controls.filters.copy(facet = null) }) }
    }
    Column {
        // The chips are 48dp targets around 32dp of ink, so the rows butt up with no gap of their own.
        FlowRow(
            Modifier.fillMaxWidth(),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            FiltersButton(open = controls.filtersOpen, activeCount = active.size) {
                controls.filtersOpen = !controls.filtersOpen
            }
            if (!controls.filtersOpen) active.forEach { (label, clear) -> FilterChip("$label ✕", true, clear) }
        }
        if (!controls.filtersOpen) return@Column
        FilterGroup("Type") {
            LIFTING_GROUPS.forEach { mg ->
                FilterChip(sentence(mg.name), f.group == mg) {
                    controls.filters = f.copy(group = if (f.group == mg) null else mg, category = null)
                }
            }
            categories.forEach { c ->
                FilterChip(sentence(c), f.category == c) {
                    controls.filters = f.copy(category = if (f.category == c) null else c, group = null)
                }
            }
        }
        FilterGroup("Armoury") {
            // Absent without saved equipment: nothing to filter by.
            if (equipment != null) {
                FilterChip("My armoury", f.myGear) { controls.filters = f.copy(myGear = !f.myGear) }
            }
            EquipmentFacet.entries.forEach { facet ->
                FilterChip(facet.label, f.facet == facet) {
                    controls.filters = f.copy(facet = if (f.facet == facet) null else facet)
                }
            }
        }
    }
}

@Composable
private fun FilterGroup(label: String, chips: @Composable () -> Unit) {
    Spacer(Modifier.height(8.dp))
    Text(
        label,
        style = MaterialTheme.typography.bodySmall,
        color = IronvellumColors.InkMuted,
    )
    FlowRow(Modifier.fillMaxWidth()) { chips() }
}

/** "PUSH" or "martial arts" as "Push" / "Martial arts"; chips and chip labels are sentence case. */
private fun sentence(word: String): String = word.lowercase().replaceFirstChar { it.uppercase() }

/** The one control that opens the filter groups: the app's outlined chip, with the count when some are on. */
@Composable
private fun FiltersButton(open: Boolean, activeCount: Int, onClick: () -> Unit) {
    InkChip(
        label = if (activeCount > 0) "Filters · $activeCount" else "Filters",
        clickLabel = if (open) "Hide filters" else "Show filters",
        icon = Icons.Outlined.Tune,
        onClick = onClick,
    )
}

private fun LazyListScope.exerciseRows(
    view: PickerView,
    data: PickerData,
    query: String,
    gearHidAll: Boolean,
    browse: Boolean,
    onPick: (Exercise) -> Unit,
    onFavourite: (Long, Boolean) -> Unit,
) {
    val now = System.currentTimeMillis()
    // One Vault card per section, its rows divided by InkDivider.
    // shortcut: a section is one lazy item, so a very long section composes whole; chunk the rows if the catalogue outgrows it.
    fun LazyListScope.section(id: String, header: String, prefix: String, list: List<Exercise>) {
        item(key = "header_$id") { SectionHeader(header, topPadding = 20.dp) }
        item(key = "rows_$id") {
            InkPanel(contentPadding = PaddingValues(0.dp)) {
                list.forEachIndexed { index, exercise ->
                    key("${prefix}_${exercise.id}") {
                        if (index > 0) InkDivider()
                        PickerRow(
                            exercise = exercise,
                            lastLine = data.lastLogged[exercise.id]?.let { lastLoggedLine(it, exercise, now) },
                            favourite = exercise.id in data.favouriteIds,
                            browse = browse,
                            onPick = onPick,
                            onFavourite = { onFavourite(exercise.id, it) },
                        )
                    }
                }
            }
        }
    }
    if (view.favourites.isNotEmpty()) {
        section("favourites", "Favourites", "fav", view.favourites)
    }
    if (view.recents.isNotEmpty()) {
        section("recent", "Recent", "recent", view.recents)
    }
    view.grouped.forEach { (cat, list) ->
        section("cat_$cat", if (cat.isBlank()) "Strength" else cat, "row", list)
    }
    if (view.count == 0) {
        item(key = "empty") {
            // Say WHY nothing matched: the gear filter emptying the list gets
            // its own line, because the fix (Filters) is not the words.
            Text(
                when {
                    gearHidAll -> "Nothing your armoury covers — tap Filters to show everything"
                    query.isNotBlank() -> "The Ledger holds no exercise matching \"$query\""
                    else -> "The Ledger holds no exercise for these filters"
                },
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }
}

/**
 * Where the load comes from, derived from data the catalogue already carries:
 * [Exercise.isWeighted] separates bodyweight work, and
 * [MovementDifficulty.loadFactor] separates machine/cable/smith/sled stations
 * (a marked discount off the free-weight reference) from barbell and dumbbell
 * loading. Activities (cardio, sport, climbing) carry no equipment facet:
 * the facet answers a gym-floor question, so selecting one hides activities.
 */
enum class EquipmentFacet(val label: String) {
    BODYWEIGHT("Bodyweight"),
    FREE_WEIGHT("Free weight"),
    MACHINE("Machine"),
}

fun equipmentFacet(exercise: Exercise): EquipmentFacet? = when {
    exercise.category.isNotBlank() -> null
    !exercise.isWeighted -> EquipmentFacet.BODYWEIGHT
    // Assisted machines are deliberately absent from the loadFactor table
    // (their marked weight SUBTRACTS), so loadFactor alone would file them
    // under free weights. The catalogue names them with the "assisted"
    // prefix, which is an existing fact, not a second table.
    MovementDifficulty.loadFactor(exercise.name) < MovementDifficulty.FREE_WEIGHT_LOAD ||
        exercise.name.trim().lowercase().startsWith("assisted") -> EquipmentFacet.MACHINE
    else -> EquipmentFacet.FREE_WEIGHT
}

/**
 * The muscle-group rail covers lifting only; the activity groups live on the
 * category rail, so showing both in one row duplicated the filter and overflowed.
 */
private val LIFTING_GROUPS = listOf(
    MuscleGroup.PULL,
    MuscleGroup.PUSH,
    MuscleGroup.LEGS,
    MuscleGroup.CORE,
)

@Composable
internal fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    // A 48dp target around 32dp of ink (InkChip's pattern); the underline marks the ink, not the margin.
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clickable { onClick() }
            // Same as the deeds filters: a chip that narrows the list still has
            // to tell a screen reader whether it is on.
            .semantics {
                role = Role.Checkbox
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .selectedUnderline(selected)
                .heightIn(min = 32.dp)
                .padding(horizontal = 9.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = if (selected) IronvellumColors.Ink else IronvellumColors.InkMuted,
                letterSpacing = 0.5.sp,
                // A chip label must never wrap: "Climbing" broke into one letter
                // per line when the row ran out of width.
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * One exercise as a list row: the whole row picks one exercise, like a menu entry, and the info
 * and star buttons sit at its right edge as 44dp targets. Rows are divided by InkDivider inside
 * one card per section; [browse] only changes what a screen reader says picking does.
 */
@Composable
private fun PickerRow(
    exercise: Exercise,
    lastLine: String?,
    favourite: Boolean,
    browse: Boolean,
    onPick: (Exercise) -> Unit,
    onFavourite: (Boolean) -> Unit,
) {
    val skill = Skills.forName(exercise.name)
    var showInfo by remember { mutableStateOf(false) }
    val pickLabel = if (browse) "Open ${exercise.name}" else "Add ${exercise.name}"
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = pickLabel) { onPick(exercise) }
            .heightIn(min = ListRowHeight)
            // The ListRow inset, minus the right edge: the two 44dp buttons sit there.
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                exercise.name,
                style = MaterialTheme.typography.bodyMedium,
                color = IronvellumColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // What the lifter will be asked to log sits with the muscle group,
            // so the right edge holds only the two things that can be pressed.
            Text(
                buildString {
                    append(exercise.muscleGroup.name.lowercase())
                    append(" · ")
                    append(metricWord(exercise.metric))
                    if (exercise.isWeighted) append(" · weighted")
                    if (skill != null) append(" · technique ${Skills.tierLabel(skill.tier)} ${skill.line}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (lastLine != null) {
                Text(
                    "last $lastLine",
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val infoLabel = "About ${exercise.name}"
        Box(
            Modifier
                .size(44.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .clickable(onClickLabel = infoLabel) { showInfo = true }
                .semantics {
                    contentDescription = infoLabel
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = IronvellumColors.InkMuted,
            )
        }
        val starLabel = if (favourite) "Remove ${exercise.name} from favourites" else "Add ${exercise.name} to favourites"
        Box(
            Modifier
                .size(44.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .clickable(onClickLabel = starLabel) { onFavourite(!favourite) }
                .semantics {
                    contentDescription = starLabel
                    role = Role.Checkbox
                    this.selected = favourite
                },
            contentAlignment = Alignment.Center,
        ) {
            // A favourite is a choice, not an achievement: Ink when on, never gold.
            Icon(
                if (favourite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = null,
                tint = if (favourite) IronvellumColors.Ink else IronvellumColors.InkMuted,
            )
        }
    }
    if (showInfo) {
        ExerciseInfoSheet(
            exercise = exercise,
            lastLine = lastLine,
            onDismiss = { showInfo = false },
            onPick = {
                showInfo = false
                onPick(exercise)
            },
            confirmLabel = if (browse) "Choose" else "Add",
        )
    }
}

internal fun metricWord(metric: ExerciseMetric): String = when (metric) {
    ExerciseMetric.REPS -> "reps"
    ExerciseMetric.HOLD -> "hold"
    ExerciseMetric.DURATION -> "time"
    ExerciseMetric.DISTANCE_TIME -> "distance"
    ExerciseMetric.ATTEMPTS_GRADE -> "attempts"
}
