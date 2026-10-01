package com.ironvellum.app.ui.titles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.ironvellum.app.ui.components.IronvellumButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.data.Repository
import com.ironvellum.app.data.SkillTrainingEvidence
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.Sex
import com.ironvellum.app.domain.HealthDay
import com.ironvellum.app.domain.PlayerProfile
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.SkillClaimResult
import com.ironvellum.app.domain.SkillPractice
import com.ironvellum.app.domain.SetRecords
import com.ironvellum.app.domain.StatEntry
import com.ironvellum.app.domain.Skills
import com.ironvellum.app.domain.Titles
import com.ironvellum.app.domain.UnlockedTitle
import com.ironvellum.app.domain.WorkoutSession
import com.ironvellum.app.ui.components.Achievement
import com.ironvellum.app.ui.components.AchievementOverlay
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.domain.WorkoutPreset
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.components.IronvellumTabPill
import com.ironvellum.app.ui.components.InkPickerSheet
import com.ironvellum.app.ui.components.plural
import com.ironvellum.app.domain.ExerciseSearch
import androidx.compose.ui.semantics.Role
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.theme.IronvellumColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import com.ironvellum.app.ui.launchGuarded
import kotlinx.coroutines.launch

data class TitlesUi(
    val unlocked: Map<String, Long> = emptyMap(),
    val currentTitleId: String? = null,
    /** Mastery is only ever an explicit claim. */
    val claimedSkills: Set<String> = emptySet(),
    val practiceCounts: Map<String, Int> = emptyMap(),
    /** Every logged entry, newest first — practice reps and mastery claims. */
    val log: List<SkillPractice> = emptyList(),
    val claimedAt: Map<String, Long> = emptyMap(),
    /** Live deed progress, so every locked title can show how far off it is. */
    val ledger: Titles.Ledger = Titles.Ledger(0, 0, 0, 0),
    /** Best logged training set per normalised movement name - evidence the lifter never had to re-log. */
    val training: Map<String, SkillTrainingEvidence> = emptyMap(),
    val sex: Sex = Sex.MALE,
)

class TitlesViewModel(private val repo: Repository) : ViewModel() {

    private val _claim = MutableStateFlow<SkillClaimResult?>(null)
    val claim: StateFlow<SkillClaimResult?> = _claim.asStateFlow()

    /** Why the last unclaim was refused; null when nothing to show. */
    private val _unclaimRefusal = MutableStateFlow<String?>(null)
    val unclaimRefusal: StateFlow<String?> = _unclaimRefusal.asStateFlow()

    val ui: StateFlow<TitlesUi> = combine(
        repo.observeUnlockedTitles(),
        repo.observeProfile(),
        repo.observeSkillPractices(),
        repo.observeHistory(),
        repo.observeHealthDays(),
        repo.observeExercises(),
        repo.observePresets(),
        repo.observeSkillTrainingEvidence(),
        repo.observeBodyProfile(),
        repo.observeStats(),
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val unlocked = values[0] as List<UnlockedTitle>
        val profile = values[1] as PlayerProfile?
        @Suppress("UNCHECKED_CAST")
        val practices = values[2] as List<SkillPractice>
        @Suppress("UNCHECKED_CAST")
        val history = values[3] as List<Pair<WorkoutSession, List<SessionSet>>>
        @Suppress("UNCHECKED_CAST")
        val healthDays = values[4] as List<HealthDay>
        @Suppress("UNCHECKED_CAST")
        val exercises = values[5] as List<Exercise>
        @Suppress("UNCHECKED_CAST")
        val presets = values[6] as List<WorkoutPreset>
        @Suppress("UNCHECKED_CAST")
        val training = values[7] as Map<String, SkillTrainingEvidence>
        @Suppress("UNCHECKED_CAST")
        val bodyProfile = values[8] as Pair<Double?, Sex>
        @Suppress("UNCHECKED_CAST")
        val stats = values[9] as List<StatEntry>

        val claimed = practices.filter { it.claimed }

        TitlesUi(
            unlocked = unlocked.associate { it.titleId to it.unlockedAtMs },
            currentTitleId = profile?.currentTitleId,
            claimedSkills = claimed.map { it.skillName }.toSet(),
            practiceCounts = practices.filterNot { it.claimed }
                .groupingBy { it.skillName }.eachCount(),
            log = practices.sortedByDescending { it.practicedAtMs },
            claimedAt = claimed.associate { it.skillName to it.practicedAtMs },
            // same builder the unlock path uses, so the board can never show
            // progress the awarder disagrees with
            ledger = Titles.ledgerOf(
                totalXp = profile?.totalXp ?: 0L,
                history = history,
                healthDays = healthDays,
                practices = practices,
                // metric/category live on the Exercise, so activity deeds read
                // zero without the catalogue
                exercises = exercises.associateBy { it.id },
                // Without these the codex drew every load deed against the
                // MALE bar at zero progress, so a woman reading the board saw
                // neither her own threshold nor how close she was to it.
                bodyweightAt = SetRecords.bodyweightLookup(stats),
                sex = bodyProfile.second,
            ),
            training = training,
            sex = bodyProfile.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TitlesUi())

    fun equip(titleId: String) {
        viewModelScope.launchGuarded("wear title") { repo.equipTitle(titleId) }
    }

    fun practice(skillName: String, value: Int, weightKg: Double?) {
        viewModelScope.launchGuarded("log practice") { repo.logSkillPractice(skillName, value, weightKg) }
    }

    fun claim(skillName: String) {
        viewModelScope.launchGuarded("claim skill") {
            val result = repo.claimSkill(skillName)
            // The roll is banked HERE, at the one place a level-up is produced.
            // Granting it from a LaunchedEffect keyed on the result double-paid
            // whenever composition restarted (a rotation) while the overlay was
            // still showing that same claim.
            if (result.levelAfter > result.levelBefore) repo.grantRoll()
            _claim.value = result
        }
    }

    fun unclaim(skillName: String) {
        viewModelScope.launchGuarded("unclaim skill") {
            try {
                repo.unclaimSkill(skillName)
            } catch (refused: IllegalStateException) {
                _unclaimRefusal.value = unclaimRefusalCopy(refused)
                throw refused
            }
        }
    }
    fun dismissUnclaimRefusal() {
        _unclaimRefusal.value = null
    }
    fun dismissClaim() {
        _claim.value = null
    }
}

/**
 * The lifter-facing text for a refused unclaim: the repository's own sentence
 * ("<skill> paid N XP but only M XP is left - it stays claimed"), with its
 * clause dash set as the em dash the rest of the app's copy uses.
 */
internal fun unclaimRefusalCopy(refused: IllegalStateException): String =
    refused.message?.replace(" - ", " — ") ?: "This technique stays claimed."

@Composable
fun TitlesScreen(
    viewModel: TitlesViewModel =
        viewModel(factory = viewModelFactory { initializer { TitlesViewModel(ironvellumRepository()) } }),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val claimResult by viewModel.claim.collectAsStateWithLifecycle()
    val mastered = ui.claimedSkills
    var tab by remember { mutableStateOf(TitlesTab.DEEDS) }
    var treeLine by remember { mutableStateOf(Skills.LINES.first()) }
    var openSkill by remember { mutableStateOf<String?>(null) }

    openSkill?.let { name ->
        Skills.forName(name)?.let { def ->
            SkillDetailDialog(
                skill = def,
                mastered = name in mastered,
                unlocked = Skills.unlocked(def, mastered),
                entries = ui.log.filter { it.skillName == name },
                training = ui.training[name.lowercase().trim()],
                sexBar = if (ui.sex == Sex.FEMALE) Skills.femaleStandard(name) else null,
                onLogPractice = { value, load ->
                    viewModel.practice(name, value, load)
                    openSkill = null
                },
                onClaim = { viewModel.claim(name) },
                onUnclaim = { viewModel.unclaim(name) },
                onDismiss = { openSkill = null },
            )
        }
    }

    // Unclaim refused (the XP it paid is already spent): say so rather than
    // leave the confirm row sitting there with nothing happening.
    val unclaimRefusal by viewModel.unclaimRefusal.collectAsStateWithLifecycle()
    unclaimRefusal?.let { message ->
        AlertDialog(
            shape = MaterialTheme.shapes.medium,
            containerColor = Color(0xFF0D1110),
            onDismissRequest = viewModel::dismissUnclaimRefusal,
            title = { Text("Still claimed") },
            text = { Text(message) },
            confirmButton = {
                IronvellumButton(label = "OK", onClick = viewModel::dismissUnclaimRefusal, quiet = true)
            },
        )
    }

    // The deeds board owns a LazyColumn so a growing catalogue stays lazy, and
    // a lazy list inside a verticalScroll parent is measured with infinite
    // height — which crashed the Codex outright. So DEEDS gets a non-scrolling
    // shell while the other tabs keep the scrolling one.
    val deedsTab = tab == TitlesTab.DEEDS
    Column(
        Modifier
            .fillMaxSize()
            .then(if (deedsTab) Modifier else Modifier.verticalScroll(rememberScrollState()))
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            "CODEX",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            letterSpacing = 6.sp,
        )
        Text(
            "${ui.unlocked.size} ${if (ui.unlocked.size == 1) "deed" else "deeds"} earned · " +
                "${mastered.size} ${if (mastered.size == 1) "technique" else "techniques"} mastered",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = ChakraPetch,
            color = IronvellumColors.SystemGreen,
        )
        Spacer(Modifier.height(12.dp))
        // One persistent selector row: the three tabs. The technique line is
        // chosen from the bar below, which opens the shared picker sheet.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IronvellumTabPill("DEEDS", tab == TitlesTab.DEEDS) { tab = TitlesTab.DEEDS }
            IronvellumTabPill("PATHS", tab == TitlesTab.TREE) { tab = TitlesTab.TREE }
            IronvellumTabPill("JOURNAL", tab == TitlesTab.JOURNAL) { tab = TitlesTab.JOURNAL }
        }

        if (tab == TitlesTab.JOURNAL) {
            SkillJournal(
                log = ui.log,
                claimed = ui.claimedSkills,
                practiceCounts = ui.practiceCounts,
                onOpenLine = { line ->
                    treeLine = line
                    tab = TitlesTab.TREE
                },
                onSelect = { openSkill = it },
            )
            Spacer(Modifier.height(28.dp))
            return@Column
        }


        if (tab == TitlesTab.TREE) {
            // No "Skill Tree" heading: the selected pill above already says it.
            // The tally is the line worth keeping here.
            Spacer(Modifier.height(12.dp))
            Text(
                "${mastered.count { it in Skills.BY_NAME }} of ${Skills.ALL.size} techniques mastered",
                style = MaterialTheme.typography.labelMedium,
                color = IronvellumColors.InkMuted,
            )
            Spacer(Modifier.height(4.dp))
            // The roman tier numerals on every node read as noise without one
            // line of explanation.
            Text(
                "Tiers I — V · a higher numeral is a harder standard",
                style = MaterialTheme.typography.labelMedium,
                color = IronvellumColors.InkMuted,
            )
            Spacer(Modifier.height(10.dp))
            LinePickerBar(
                line = treeLine,
                selected = treeLine,
                mastered = mastered,
                onPick = { treeLine = it },
            )
            Spacer(Modifier.height(10.dp))
            SkillTreeGraph(
                line = treeLine,
                mastered = mastered,
                onSelect = { openSkill = it },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(28.dp))
            return@Column
        }

        DeedsBoard(
            unlocked = ui.unlocked,
            equippedId = ui.currentTitleId,
            ledger = ui.ledger,
            onEquip = { viewModel.equip(it) },
            sex = ui.sex,
            // weight(1f) gives the lazy list a real height inside the
            // non-scrolling shell; fillMaxSize here would fight the header.
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
    claimResult?.let { result ->
        AchievementOverlay(
            items = buildList {
                add(
                    Achievement(
                        banner = "TECHNIQUE MASTERED",
                        tagline = "TIER ${Skills.tierLabel(result.skill.tier)}",
                        name = result.skill.name,
                        subtitle = "${result.skill.line.uppercase()} PATH",
                        xp = result.xpAwarded,
                        notes = result.unlockedNext.map { "TECHNIQUE OPENED · ${it.name}" },
                    ),
                )
                if (result.levelAfter > result.levelBefore) {
                    add(
                        Achievement(
                            banner = "LEVEL UP",
                            tagline = "XP LEVEL",
                            name = "Level ${result.levelAfter}",
                            subtitle = "${result.totalXp} XP TOTAL",
                            accent = IronvellumColors.SystemGreen,
                        ),
                    )
                }
                if (result.levelAfter > result.levelBefore) {
                    add(
                        Achievement(
                            banner = "THE VEIL STIRS",
                            tagline = "DRAW EARNED",
                            name = "Inscription Waiting",
                            subtitle = "SPEND IT BEYOND THE VEIL",
                            accent = IronvellumColors.SovereignGold,
                        ),
                    )
                }
                result.newTitles.forEach { title ->
                    add(
                        Achievement(
                            banner = "DEED EARNED",
                            name = title.name,
                            subtitle = "${title.rarity.label.uppercase()} · ${title.describeFor(ui.sex).uppercase()}",
                        ),
                    )
                }
            },
            onDone = { viewModel.dismissClaim() },
        )
    }

}

private enum class TitlesTab { DEEDS, TREE, JOURNAL }

/**
 * The line selector as one bar: tapping it opens the shared picker sheet,
 * the same pattern as the boards' lift picker. A toggle hiding a rail of
 * pills made the tree's primary navigation feel like a buried filter.
 */
@Composable
private fun LinePickerBar(line: String, selected: String, mastered: Set<String>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .background(Brush.verticalGradient(listOf(Color(0xFF17201C), Color(0xFF111815))))
            .inkBorder(IronvellumColors.SovereignGold, MaterialTheme.shapes.small, 1.dp)
            .clickable(onClickLabel = "Choose a path") { open = true }
            .padding(horizontal = 14.dp),
    ) {
        Text(
            "PATH",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            line.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = IronvellumColors.SovereignGold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = IronvellumColors.SovereignGold,
        )
    }
    if (!open) return

    var query by remember { mutableStateOf("") }
    // A line matches its own name and the names of its techniques, so
    // "lever" finds the Lever line via Front Lever and friends.
    val lines = Skills.LINES.mapNotNull { candidate ->
        val names = listOf(candidate) + Skills.ALL.filter { it.line == candidate }.map { it.name }
        names.mapNotNull { ExerciseSearch.rank(it, query) }.minOrNull()?.let { candidate to it }
    }
    val shown = if (query.isBlank()) lines.map { it.first } else lines.sortedBy { it.second }.map { it.first }
    InkPickerSheet(
        title = "CHOOSE A PATH",
        onDismiss = { open = false },
        query = query,
        onQueryChange = { query = it },
        searchLabel = "Search paths",
        count = shown.size,
    ) {
        shown.forEach { candidate ->
            val skills = Skills.ALL.filter { it.line == candidate }
            val done = skills.count { it.name in mastered }
            item(key = candidate) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(MaterialTheme.shapes.small)
                        .selectable(selected = candidate == selected, role = Role.RadioButton) {
                            open = false
                            onPick(candidate)
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            candidate,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (candidate == selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (candidate == selected) IronvellumColors.SovereignGold else IronvellumColors.Ink,
                        )
                        Text(
                            plural(done, "1 mastered", "$done mastered") + " · ${skills.size} techniques",
                            style = MaterialTheme.typography.labelSmall,
                            color = IronvellumColors.InkMuted,
                        )
                    }
                }
            }
        }
    }
}

// No line art on the tree's pills. Six of the ten lines had a mark and four did
// not, and the marks themselves read as bad clip-art at pill size - the owner's
// call was that no icon beats these icons. The name carries the line.



