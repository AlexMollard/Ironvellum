package com.ironvellum.app.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.MovementDifficulty
import com.ironvellum.app.domain.MuscleGroup
import com.ironvellum.app.domain.Skills
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.theme.IronvellumColors

/**
 * One picker for every place an exercise is chosen: search, group filters,
 * grouped rows, skill tiers marked. Replaces the two raw scrolling lists.
 */
@Composable
fun ExercisePickerPanel(
    exercises: List<Exercise>,
    recentIds: List<Long>,
    onPick: (Exercise) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<MuscleGroup?>(null) }
    var category by remember { mutableStateOf<String?>(null) }
    var equipment by remember { mutableStateOf<EquipmentFacet?>(null) }

    // Every whitespace-separated token must match somewhere in the name, so
    // "ext leg" finds Leg Extension regardless of word order. A single
    // contains() on the whole string made "pulldown lat" find nothing.
    val tokens = query.trim().split(WHITESPACE).filter { it.isNotEmpty() }
    val filtered = exercises
        .filter { group == null || it.muscleGroup == group }
        .filter { category == null || it.category == category }
        .filter { equipment == null || equipmentFacet(it) == equipment }
        .filter { tokens.all { token -> it.name.contains(token, ignoreCase = true) } }
        .sortedWith(compareBy({ it.muscleGroup.ordinal }, { it.name }))
    // Grouped for display; a search term filters every group, so only non-empty groups appear.
    val grouped = activityCategoryOrder(exercises)
        .map { c -> c to filtered.filter { it.category == c } }
        .filter { (_, list) -> list.isNotEmpty() }

    // Recents only pin when nothing narrows the list; under a query or filter
    // they would float above results that already answer the question. The id
    // list arrives most-recent-first and is NOT re-sorted: the order is the
    // only thing that makes it useful. Catalogue rows the lifter no longer
    // has (removed or imported under a new id) drop out silently, and no
    // history means an empty list, so no RECENT heading renders.
    val showRecents = query.isBlank() && group == null && category == null && equipment == null
    val recents = if (showRecents) {
        recentIds.distinct().mapNotNull { id -> exercises.firstOrNull { it.id == id } }
    } else {
        emptyList()
    }

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "SELECT MOVEMENT",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = IronvellumColors.SovereignGold,
                letterSpacing = 3.sp,
            )
            Text(
                "${filtered.size}",
                style = MaterialTheme.typography.labelSmall,
                color = IronvellumColors.InkMuted,
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF141A18), MaterialTheme.shapes.extraSmall)
                .inkBorder(IronvellumColors.Rune, MaterialTheme.shapes.extraSmall, 1.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = IronvellumColors.InkMuted)
            Spacer(Modifier.height(0.dp))
            Box(Modifier.padding(start = 8.dp).fillMaxWidth()) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = IronvellumColors.Ink),
                    cursorBrush = SolidColor(IronvellumColors.SystemGreen),
                    // The placeholder below is a SIBLING Text, so the field
                    // itself announced nothing and a screen reader landed on an
                    // unlabelled input. The magnifier stays decorative: naming
                    // both would read the same thing twice.
                    modifier = Modifier
                        .fillMaxWidth()
                        // A single line of text measured 20dp, under the WCAG AA
                        // floor; the row's own padding supplies the visual height.
                        .heightIn(min = 24.dp)
                        .semantics { contentDescription = "Search movements" },
                )
                if (query.isEmpty()) {
                    Text(
                        "search movements",
                        style = MaterialTheme.typography.bodyMedium,
                        color = IronvellumColors.InkMuted,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Both rails scroll horizontally: muscle groups plus activity
        // categories no longer fit a phone width, and a fixed Row squeezed the
        // last chips into one letter per line off the screen edge.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip("ALL", group == null) { group = null }
            // Only lifting groups belong here — CARDIO/SPORT/CLIMBING/WATER/
            // MOBILITY are the category rail below, so listing them twice both
            // overflowed the row and duplicated the same filter.
            LIFTING_GROUPS.forEach { mg ->
                FilterChip(mg.name, group == mg) { group = if (group == mg) null else mg }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip("ALL", category == null) { category = null }
            activityCategoryOrder(exercises).filter { it.isNotBlank() }.forEach { c ->
                FilterChip(c.uppercase(), category == c) { category = if (category == c) null else c }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip("ALL", equipment == null) { equipment = null }
            EquipmentFacet.entries.forEach { facet ->
                FilterChip(facet.label, equipment == facet) {
                    equipment = if (equipment == facet) null else facet
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
            if (recents.isNotEmpty()) {
                item(key = "header_recent") {
                    PickerSectionHeader("RECENT")
                }
                items(recents, key = { "recent_${it.id}" }) { exercise ->
                    PickerRow(exercise = exercise, onPick = onPick)
                }
            }
            grouped.forEach { (cat, list) ->
                item(key = "header_$cat") {
                    PickerSectionHeader(if (cat.isBlank()) "STRENGTH" else cat.uppercase())
                }
                items(list, key = { it.id }) { exercise ->
                    PickerRow(exercise = exercise, onPick = onPick)
                }
            }
        }

        if (filtered.isEmpty()) {
            // Say WHY nothing matched: a blank query with the catalogue present
            // means the filters did it, not the words.
            Text(
                if (query.isNotBlank()) "No movement matches \"$query\""
                else "No movement matches the filters set",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }

        Spacer(Modifier.height(6.dp))
        Text(
            "CLOSE",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            letterSpacing = 3.sp,
            modifier = Modifier
                .align(Alignment.End)
                .clickable { onDismiss() }
                .padding(8.dp),
        )
    }
}

/** Header strip shared by RECENT and the category groups, so the pinned section reads as a peer. */
@Composable
private fun PickerSectionHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = ChakraPetch,
        fontWeight = FontWeight.Bold,
        color = IronvellumColors.SovereignGold,
        letterSpacing = 2.sp,
        modifier = Modifier
            .fillMaxWidth()
            // Inked after all: the full-bleed rule protects
            // surfaces whose edges are the SCREEN's edge. This
            // strip sits inside the picker panel with both ends
            // visible, so a square bar just reads as old chrome.
            // The fill sits ~2 luminance units from the panel, so
            // its hand-drawn edge was invisible however much the
            // shape wandered. The brushed border is what actually
            // reads as drawn here.
            .background(Color(0xFF101512), MaterialTheme.shapes.extraSmall)
            .inkBorder(IronvellumColors.Bracket, MaterialTheme.shapes.extraSmall, 1.dp)
            .padding(vertical = 6.dp, horizontal = 4.dp),
    )
}

private val WHITESPACE = Regex("\\s+")

/**
 * Where the load comes from, derived from data the catalogue already carries:
 * [Exercise.isWeighted] separates bodyweight work, and
 * [MovementDifficulty.loadFactor] separates machine/cable/smith/sled stations
 * (a marked discount off the free-weight reference) from barbell and dumbbell
 * loading. Activities (cardio, sport, climbing) carry no equipment facet:
 * the facet answers a gym-floor question, so selecting one hides activities.
 */
enum class EquipmentFacet(val label: String) {
    BODYWEIGHT("BODYWEIGHT"),
    FREE_WEIGHT("FREE WEIGHT"),
    MACHINE("MACHINE"),
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
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .background(
                if (selected) {
                    Brush.verticalGradient(listOf(Color(0xFF2C7A5A), Color(0xFF1B4D3A)))
                } else {
                    Brush.verticalGradient(listOf(Color(0xFF161C1A), Color(0xFF111614)))
                },
                MaterialTheme.shapes.extraSmall,
            )
            .inkBorder(if (selected) IronvellumColors.SystemGreen else IronvellumColors.Rune, MaterialTheme.shapes.extraSmall, 1.dp)
            .clickable { onClick() }
            // Same as the deeds filters: a chip that narrows the list still has
            // to tell a screen reader whether it is on.
            .semantics {
                role = Role.Checkbox
                this.selected = selected
            }
            // 23dp sat under even the WCAG AA 24dp floor. 32dp matches the
            // Material chip height and the deeds board's filter pills, and only
            // adds a few density pixels here.
            .heightIn(min = 32.dp)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = if (selected) IronvellumColors.Ink else IronvellumColors.InkMuted,
            letterSpacing = 1.sp,
            // A chip label must never wrap: "CLIMBING" broke into one letter
            // per line when the row ran out of width.
            maxLines = 1,
            softWrap = false,
        )
    }
}
@Composable
private fun PickerRow(exercise: Exercise, onPick: (Exercise) -> Unit) {
    val skill = Skills.forName(exercise.name)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onPick(exercise) }
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                exercise.name,
                style = MaterialTheme.typography.bodyLarge,
                color = IronvellumColors.Ink,
            )
            Text(
                buildString {
                    append(exercise.muscleGroup.name.lowercase())
                    if (exercise.isWeighted) append("  ·  weighted")
                    if (skill != null) append("  ·  skill ${Skills.tierLabel(skill.tier)} ${skill.line}")
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (skill != null) IronvellumColors.SystemGreen else IronvellumColors.InkMuted,
            )
        }
        // Metric glyph: shows what the user will be asked to log before they commit.
        Text(
            when (exercise.metric) {
                ExerciseMetric.REPS -> "× reps"
                ExerciseMetric.HOLD -> "◷ hold"
                ExerciseMetric.DURATION -> "◷ time"
                ExerciseMetric.DISTANCE_TIME -> "→ distance"
                ExerciseMetric.ATTEMPTS_GRADE -> "◇ attempts"
            },
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "+",
            style = MaterialTheme.typography.titleMedium,
            fontFamily = ChakraPetch,
            color = IronvellumColors.SovereignGold,
        )
    }
}

/** "" (strength) first, then the known activity groups, then anything novel alphabetically. */
private fun activityCategoryOrder(exercises: List<Exercise>): List<String> {
    val present = exercises.map { it.category }.distinct()
    val known = listOf("Cardio", "Sport", "Climbing", "Water", "Mobility").filter { it in present }
    val extra = present.filter { it.isNotBlank() && it !in known }.sorted()
    return listOf("") + known + extra
}
