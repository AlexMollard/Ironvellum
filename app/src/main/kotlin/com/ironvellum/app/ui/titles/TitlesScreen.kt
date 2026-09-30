package com.ironvellum.app.ui.titles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
        viewModelScope.launchGuarded("equip title") { repo.equipTitle(titleId) }
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
            "${ui.unlocked.size} ${if (ui.unlocked.size == 1) "deed" else "deeds"} · " +
                "${mastered.size} ${if (mastered.size == 1) "technique" else "techniques"}",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            color = IronvellumColors.SystemGreen,
        )
        Spacer(Modifier.height(12.dp))
        // One persistent selector row: the three tabs, plus — only while the
        // skill tree is open — the LINE toggle that hides the technique-line
        // pills, so a second rail never stacks under the first.
        var linesOpen by rememberSaveable { mutableStateOf(false) }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IronvellumTabPill("DEEDS", tab == TitlesTab.DEEDS) { tab = TitlesTab.DEEDS }
            IronvellumTabPill("SKILL TREE", tab == TitlesTab.TREE) { tab = TitlesTab.TREE }
            IronvellumTabPill("JOURNAL", tab == TitlesTab.JOURNAL) { tab = TitlesTab.JOURNAL }
            if (tab == TitlesTab.TREE) {
                LineFiltersButton(open = linesOpen) { linesOpen = !linesOpen }
            }
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
            if (linesOpen) {
                // Expanded state, not a second persistent rail: the pills only
                // exist while the LINE toggle above is open.
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Skills.LINES.forEach { line ->
                        IronvellumTabPill(line.uppercase(), line == treeLine) { treeLine = line }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
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
                        subtitle = "${result.skill.line.uppercase()} LINE",
                        xp = result.xpAwarded,
                        notes = result.unlockedNext.map { "PATH OPENED · ${it.name}" },
                    ),
                )
                if (result.levelAfter > result.levelBefore) {
                    add(
                        Achievement(
                            banner = "LEVEL UP",
                            tagline = "STRENGTH RANK",
                            name = "Level ${result.levelAfter}",
                            subtitle = "${result.totalXp} XP TOTAL",
                            accent = IronvellumColors.SystemGreen,
                        ),
                    )
                }
                if (result.levelAfter > result.levelBefore) {
                    add(
                        Achievement(
                            banner = "A FIGURE STIRS",
                            tagline = "DRAW EARNED",
                            name = "Inscription Waiting",
                            subtitle = "SPEND IT IN THE GARRISON",
                            accent = IronvellumColors.SovereignGold,
                        ),
                    )
                }
                result.newTitles.forEach { title ->
                    add(
                        Achievement(
                            banner = "TITLE UNLOCKED",
                            name = title.name,
                            subtitle = "${title.rarity.name.uppercase()} · ${title.describeFor(ui.sex).uppercase()}",
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
 * The one control that opens the technique-line pills, built like the exercise
 * picker's FILTERS button so both read as the same mechanism: a button, not a
 * second rail of tabs.
 */
@Composable
private fun LineFiltersButton(open: Boolean, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.small
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF141C18), Color(0xFF101714))))
            .inkBorder(
                if (open) IronvellumColors.SystemGreen else IronvellumColors.Rune,
                shape,
                1.dp,
            )
            .clickable(onClickLabel = if (open) "Hide technique lines" else "Show technique lines", onClick = onClick)
            .padding(horizontal = 12.dp),
    ) {
        Icon(
            Icons.Outlined.Tune,
            contentDescription = null,
            tint = IronvellumColors.SystemGreen,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "LINE",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = IronvellumColors.SystemGreen,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = IronvellumColors.SystemGreen,
            modifier = Modifier.size(18.dp),
        )
    }
}

// No line art on the tree's pills. Six of the ten lines had a mark and four did
// not, and the marks themselves read as bad clip-art at pill size - the owner's
// call was that no icon beats these icons. The name carries the line.



