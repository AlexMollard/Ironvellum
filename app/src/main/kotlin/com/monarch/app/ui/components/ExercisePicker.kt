package com.monarch.app.ui.components

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
import com.monarch.app.domain.Exercise
import com.monarch.app.domain.MuscleGroup
import com.monarch.app.domain.Skills
import com.monarch.app.domain.ExerciseMetric
import com.monarch.app.ui.theme.ChakraPetch
import com.monarch.app.ui.theme.inkBorder
import com.monarch.app.ui.theme.MonarchColors

/**
 * One picker for every place an exercise is chosen: search, group filters,
 * grouped rows, skill tiers marked. Replaces the two raw scrolling lists.
 */
@Composable
fun ExercisePickerPanel(
    exercises: List<Exercise>,
    onPick: (Exercise) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<MuscleGroup?>(null) }
    var category by remember { mutableStateOf<String?>(null) }

    val filtered = exercises
        .filter { group == null || it.muscleGroup == group }
        .filter { category == null || it.category == category }
        .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
        .sortedWith(compareBy({ it.muscleGroup.ordinal }, { it.name }))
    // Grouped for display; a search term filters every group, so only non-empty groups appear.
    val grouped = activityCategoryOrder(exercises)
        .map { c -> c to filtered.filter { it.category == c } }
        .filter { (_, list) -> list.isNotEmpty() }

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
                color = MonarchColors.SovereignGold,
                letterSpacing = 3.sp,
            )
            Text(
                "${filtered.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MonarchColors.InkMuted,
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF141A18), MaterialTheme.shapes.extraSmall)
                .inkBorder(MonarchColors.Rune, MaterialTheme.shapes.extraSmall, 1.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = MonarchColors.InkMuted)
            Spacer(Modifier.height(0.dp))
            Box(Modifier.padding(start = 8.dp).fillMaxWidth()) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MonarchColors.Ink),
                    cursorBrush = SolidColor(MonarchColors.SystemGreen),
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
                        color = MonarchColors.InkMuted,
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

        Spacer(Modifier.height(12.dp))

        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
            grouped.forEach { (cat, list) ->
                item(key = "header_$cat") {
                    Text(
                        if (cat.isBlank()) "STRENGTH" else cat.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        color = MonarchColors.SovereignGold,
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
                            .inkBorder(MonarchColors.Bracket, MaterialTheme.shapes.extraSmall, 1.dp)
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                    )
                }
                items(list, key = { it.id }) { exercise ->
                    PickerRow(exercise = exercise, onPick = onPick)
                }
            }
        }

        if (filtered.isEmpty()) {
            Text(
                "No movement matches \"$query\"",
                style = MaterialTheme.typography.bodySmall,
                color = MonarchColors.InkMuted,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }

        Spacer(Modifier.height(6.dp))
        Text(
            "CLOSE",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = MonarchColors.InkMuted,
            letterSpacing = 3.sp,
            modifier = Modifier
                .align(Alignment.End)
                .clickable { onDismiss() }
                .padding(8.dp),
        )
    }
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
            .inkBorder(if (selected) MonarchColors.SystemGreen else MonarchColors.Rune, MaterialTheme.shapes.extraSmall, 1.dp)
            .clickable { onClick() }
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
            color = if (selected) MonarchColors.Ink else MonarchColors.InkMuted,
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
                color = MonarchColors.Ink,
            )
            Text(
                buildString {
                    append(exercise.muscleGroup.name.lowercase())
                    if (exercise.isWeighted) append("  ·  weighted")
                    if (skill != null) append("  ·  skill ${Skills.tierLabel(skill.tier)} ${skill.line}")
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (skill != null) MonarchColors.SystemGreen else MonarchColors.InkMuted,
            )
        }
        // Metric glyph: shows what the user will be asked to log before they commit.
        Text(
            when (exercise.metric) {
                ExerciseMetric.REPS -> "× reps"
                ExerciseMetric.DURATION -> "◷ time"
                ExerciseMetric.DISTANCE_TIME -> "→ distance"
                ExerciseMetric.ATTEMPTS_GRADE -> "◇ attempts"
            },
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = MonarchColors.InkMuted,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "+",
            style = MaterialTheme.typography.titleMedium,
            fontFamily = ChakraPetch,
            color = MonarchColors.SovereignGold,
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
