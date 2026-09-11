package com.monarch.app.ui.components

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import com.monarch.app.ui.theme.ChakraPetch
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

    val filtered = exercises
        .filter { group == null || it.muscleGroup == group }
        .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
        .sortedWith(compareBy({ it.muscleGroup.ordinal }, { it.name }))

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
                .background(Color(0xFF141A18), CutCornerShape(topStart = 5.dp, bottomEnd = 5.dp))
                .border(1.dp, MonarchColors.Rune, CutCornerShape(topStart = 5.dp, bottomEnd = 5.dp))
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
                    modifier = Modifier.fillMaxWidth(),
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

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip("ALL", group == null) { group = null }
            MuscleGroup.entries.forEach { mg ->
                FilterChip(mg.name, group == mg) { group = if (group == mg) null else mg }
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
            items(filtered, key = { it.id }) { exercise ->
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
                    Text(
                        "+",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = ChakraPetch,
                        color = MonarchColors.SovereignGold,
                    )
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
                CutCornerShape(topStart = 4.dp, bottomEnd = 4.dp),
            )
            .border(1.dp, if (selected) MonarchColors.SystemGreen else MonarchColors.Rune, CutCornerShape(topStart = 4.dp, bottomEnd = 4.dp))
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = if (selected) MonarchColors.Ink else MonarchColors.InkMuted,
            letterSpacing = 1.sp,
        )
    }
}
