package com.ironvellum.app.ui.idle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironvellum.app.domain.CrestCatalogue
import com.ironvellum.app.domain.CrestDef
import com.ironvellum.app.domain.CrestGroup
import com.ironvellum.app.domain.Crests
import com.ironvellum.app.domain.RelicHouse
import com.ironvellum.app.domain.Titles
import com.ironvellum.app.domain.Veil
import com.ironvellum.app.ui.components.CelebrationDock
import com.ironvellum.app.ui.components.CrestPlate
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.InkRail
import com.ironvellum.app.ui.components.PushedHeader
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.TileShape
import com.ironvellum.app.ui.theme.inkBorder

/** What the crest pages read: the crests held and the one worn, the lifter's level, and the measured worth of each. */
data class CrestUi(
    val owned: Set<String> = emptySet(),
    val worn: String? = null,
    val level: Int = 1,
    /** Essence an hour each crest would add at the lifter's own inputs ([Crests.worth]). */
    val worth: Map<String, Double> = emptyMap(),
    /** Relics held per house, for the house crests' progress. */
    val houseRelics: Map<RelicHouse, Int> = emptyMap(),
    /** Each of the six deeds' progress by deed id. */
    val deeds: Map<String, Titles.Progress> = emptyMap(),
    /** False once the worn crest has changed today: the swap rule says tomorrow. */
    val mayChange: Boolean = true,
)

/** A name the collection draws for something not yet held: legible, but quiet. */
private val UnheldName = androidx.compose.ui.graphics.Color(0xFFC9C6BE)

/**
 * The Crests tab: the crest worn, then the 28 grouped Ladder, Deeds, Houses and the Veil, each with its
 * perk or what it asks. A row opens that crest's sheet ([CrestSheet]).
 */
@Composable
internal fun CrestsPage(crests: CrestUi, animate: Boolean, onOpen: (String) -> Unit) {
    val worn = Crests.byId(crests.worn)
    val nextLadder = remember(crests.level, crests.owned) { Veil.nextMilestone(crests.level, crests.owned)?.second }
    if (worn != null) {
        InkPanel(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            onClick = { onOpen(worn.id) },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CrestPlate(worn.id, Modifier.size(40.dp), animate = animate)
                Column(Modifier.weight(1f)) {
                    Text(
                        "Wearing ${worn.sentenceName}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = IronvellumColors.Ink,
                    )
                    Text(
                        CrestCatalogue.perkLine(worn),
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.InkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text("Change", style = MaterialTheme.typography.labelLarge, color = IronvellumColors.SystemGreen)
            }
        }
    } else {
        Text(
            "No crest worn. Tap an earned crest to wear it; allies see it on your avatar.",
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.padding(top = 10.dp, start = 2.dp, end = 2.dp),
        )
    }
    CrestGroup.entries.forEach { group ->
        val inGroup = Crests.inGroup(group)
        val held = inGroup.count { it.id in crests.owned }
        SectionHeader("${groupLead(group)} · $held of ${inGroup.size}", topPadding = 14.dp)
        inGroup.forEach { def ->
            CrestRow(
                def = def,
                owned = def.id in crests.owned,
                worn = def.id == crests.worn,
                progress = CrestCatalogue.progress(
                    def, def.id in crests.owned, crests.level, nextLadder, crests.deeds, crests.houseRelics,
                ),
                animate = animate,
                onOpen = { onOpen(def.id) },
            )
        }
    }
}

private fun groupLead(group: CrestGroup): String = when (group) {
    CrestGroup.Ladder -> "Ladder · every ${Veil.MILESTONE_EVERY} levels"
    CrestGroup.Deeds -> "Deeds"
    CrestGroup.Houses -> "Houses · complete a house"
    CrestGroup.Veil -> "The Veil · chance draws"
}

/** One crest: its plate, name, perk or ask, and for one in reach a count over a short bar. */
@Composable
private fun CrestRow(
    def: CrestDef,
    owned: Boolean,
    worn: Boolean,
    progress: com.ironvellum.app.domain.CrestProgress,
    animate: Boolean,
    onOpen: () -> Unit,
) {
    val status = if (worn) "worn" else if (owned) "owned" else "locked"
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = "Open ${def.name}", onClick = onOpen)
            .padding(horizontal = 2.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = "${def.name}, $status. ${progress.line}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CrestPlate(def.id, Modifier.size(36.dp), owned = owned, animate = animate && owned)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    def.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (owned) IronvellumColors.Ink else UnheldName,
                    maxLines = 1,
                )
                if (worn) {
                    Text(
                        "Worn",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.background(IronvellumColors.Emerald).padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
            Text(
                progress.line,
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (progress.count != null && progress.fraction != null) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(progress.count, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
                InkRail(progress.fraction, Modifier.width(44.dp), height = 3.dp)
            }
        } else {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = IronvellumColors.InkMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
    InkDivider()
}

/**
 * One crest's page: its plate, where it comes from, its perk with what it is worth to THIS lifter, what it
 * would replace, and who sees it. A crest not yet held says how to earn it. Wearing is the dock's one action,
 * and it says what it does first: it collects the essence owed at the crest being replaced, and the crest
 * changes once a day.
 */
@Composable
internal fun CrestSheet(
    def: CrestDef,
    crests: CrestUi,
    animate: Boolean,
    notice: String?,
    onWear: () -> Unit,
    onBack: () -> Unit,
) {
    val owned = def.id in crests.owned
    val worn = def.id == crests.worn
    val replaced = Crests.byId(crests.worn)?.takeIf { !worn }
    val nextLadder = Veil.nextMilestone(crests.level, crests.owned)?.second
    val progress = CrestCatalogue.progress(def, owned, crests.level, nextLadder, crests.deeds, crests.houseRelics)
    Column(Modifier.fillMaxSize()) {
        PushedHeader("Crest", onBack = onBack, modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp))
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            CrestPlate(def.id, Modifier.size(132.dp), owned = owned, animate = animate)
            Spacer(Modifier.height(12.dp))
            Text(
                def.sentenceName.replaceFirstChar { it.uppercase() },
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                color = IronvellumColors.Ink,
                textAlign = TextAlign.Center,
            )
            Text(
                CrestCatalogue.sourceLine(def) + if (owned) "" else " · Locked",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (!owned) {
                InkPanel(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                    Text("HOW TO EARN IT", style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
                    Text(
                        if (def.group == CrestGroup.Deeds) progress.line else def.how,
                        style = MaterialTheme.typography.bodyLarge,
                        color = IronvellumColors.Ink,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        when (def.group) {
                            CrestGroup.Deeds -> "The deed ${def.deed?.name}. The crest is yours for good once it is earned."
                            CrestGroup.Ladder -> "Every level from here pays toward it. It is yours for good once earned."
                            CrestGroup.Houses -> "Hold all four relics of the house. The crest is yours for good once it is complete."
                            CrestGroup.Veil -> "It cannot be bought or levelled to: a Veil draw that pays a crest can pay this one."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.InkMuted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (progress.count != null && progress.fraction != null) {
                        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(progress.count, style = MaterialTheme.typography.titleMedium, color = IronvellumColors.Ink)
                            Spacer(Modifier.width(10.dp))
                            InkRail(progress.fraction, Modifier.weight(1f), height = 4.dp)
                        }
                    }
                }
            }
            InkPanel(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (owned) "VEIL PERK" else "PERK ONCE WORN",
                        style = MaterialTheme.typography.labelSmall,
                        color = IronvellumColors.InkMuted,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        def.kind.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = IronvellumColors.InkMuted,
                        modifier = Modifier
                            .inkBorder(IronvellumColors.Rune, TileShape, 1.dp)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                Text(
                    def.perk,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 19.sp,
                    color = IronvellumColors.Ink,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(def.text, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink, modifier = Modifier.padding(top = 4.dp))
                Text(
                    CrestCatalogue.worthNote(def, crests.worth[def.id] ?: 0.0),
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    CrestCatalogue.VEIL_ONLY,
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (owned && replaced != null) {
                InkPanel(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text("INSTEAD OF WHAT YOU WEAR NOW", style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CrestPlate(replaced.id, Modifier.size(32.dp))
                        Text(
                            "${replaced.perk} · ${replaced.short.replaceFirstChar { it.lowercase() }}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = IronvellumColors.Ink,
                        )
                    }
                }
            }
            InkPanel(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text("SEEN BY YOUR ALLIES", style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
                Text(
                    if (owned) {
                        "On your avatar wherever your allies see you."
                    } else {
                        "Allies see it once you wear it."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = IronvellumColors.Ink,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
        val name = def.sentenceName.replaceFirstChar { it.uppercase() }
        CelebrationDock(
            primary = when {
                !owned -> "Earn it to wear it"
                worn -> "Wearing $name"
                else -> "Wear $name"
            },
            onPrimary = onWear,
            primaryEnabled = owned && !worn && crests.mayChange,
            reserveLink = false,
            caption = {
                Text(
                    notice ?: when {
                        !owned -> "Allies see it once you wear it."
                        worn -> "This is the crest you wear."
                        !crests.mayChange -> "You changed your crest today. Come back tomorrow."
                        else -> "Wearing collects your essence first. One change a day."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                )
            },
        )
    }
}
