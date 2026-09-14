package com.monarch.app.ui.titles

import androidx.compose.foundation.background
import com.monarch.app.domain.Exercise
import com.monarch.app.domain.HealthDay
import com.monarch.app.domain.PlayerProfile
import com.monarch.app.domain.SessionSet
import com.monarch.app.domain.SkillPractice
import com.monarch.app.domain.UnlockedTitle
import com.monarch.app.domain.WorkoutSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import com.monarch.app.ui.components.Achievement
import com.monarch.app.ui.components.AchievementOverlay
import com.monarch.app.domain.SkillClaimResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.monarch.app.data.Repository
import com.monarch.app.domain.Skills
import com.monarch.app.domain.Titles
import com.monarch.app.ui.components.SectionHeader
import com.monarch.app.ui.components.SystemWindow
import com.monarch.app.ui.components.formatDate
import com.monarch.app.ui.monarchRepository
import com.monarch.app.ui.theme.ChakraPetch
import com.monarch.app.ui.theme.MonarchColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
)

class TitlesViewModel(private val repo: Repository) : ViewModel() {

    private val _claim = MutableStateFlow<SkillClaimResult?>(null)
    val claim: StateFlow<SkillClaimResult?> = _claim.asStateFlow()

    val ui: StateFlow<TitlesUi> = combine(
        repo.observeUnlockedTitles(),
        repo.observeProfile(),
        repo.observeSkillPractices(),
        repo.observeHistory(),
        repo.observeHealthDays(),
        repo.observeExercises(),
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
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TitlesUi())

    fun equip(titleId: String) {
        viewModelScope.launch { repo.equipTitle(titleId) }
    }

    fun practice(skillName: String, value: Int, weightKg: Double?) {
        viewModelScope.launch { repo.logSkillPractice(skillName, value, weightKg) }
    }

    fun claim(skillName: String) {
        viewModelScope.launch { _claim.value = repo.claimSkill(skillName) }
    }

    fun unclaim(skillName: String) {
        viewModelScope.launch { repo.unclaimSkill(skillName) }
    }

    fun dismissClaim() {
        _claim.value = null
    }
}

@Composable
fun TitlesScreen(
    viewModel: TitlesViewModel =
        viewModel(factory = viewModelFactory { initializer { TitlesViewModel(monarchRepository()) } }),
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
            color = MonarchColors.InkMuted,
            letterSpacing = 6.sp,
        )
        Text(
            "${ui.unlocked.size} deeds · ${mastered.size} techniques",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            color = MonarchColors.SystemGreen,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TabPill("DEEDS", tab == TitlesTab.DEEDS) { tab = TitlesTab.DEEDS }
            TabPill("SKILL TREE", tab == TitlesTab.TREE) { tab = TitlesTab.TREE }
            TabPill("JOURNAL", tab == TitlesTab.JOURNAL) { tab = TitlesTab.JOURNAL }
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
            SectionHeader("Skill Tree")
            Text(
                "${mastered.count { it in Skills.BY_NAME }} of ${Skills.ALL.size} techniques mastered",
                style = MaterialTheme.typography.labelMedium,
                color = MonarchColors.InkMuted,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Skills.LINES.forEach { line ->
                    TabPill(line.uppercase(), line == treeLine) { treeLine = line }
                }
            }
            Spacer(Modifier.height(14.dp))
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
                            tagline = "HUNTER RANK",
                            name = "Level ${result.levelAfter}",
                            subtitle = "${result.totalXp} XP TOTAL",
                            accent = MonarchColors.SystemGreen,
                        ),
                    )
                }
                result.newTitles.forEach { title ->
                    add(
                        Achievement(
                            banner = "TITLE EARNED",
                            name = title.name,
                            subtitle = "${title.rarity.name.uppercase()} · ${title.description.uppercase()}",
                        ),
                    )
                }
            },
            onDone = { viewModel.dismissClaim() },
        )
    }

}

private enum class TitlesTab { DEEDS, TREE, JOURNAL }

@Composable
private fun TabPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        fontFamily = ChakraPetch,
        fontWeight = FontWeight.Bold,
        color = if (selected) MonarchColors.Abyss else MonarchColors.InkMuted,
        letterSpacing = 2.sp,
        modifier = Modifier
            .background(
                if (selected) {
                    Brush.verticalGradient(listOf(MonarchColors.SovereignGold, Color(0xFFB8860B)))
                } else {
                    Brush.verticalGradient(listOf(Color(0xFF141A18), Color(0xFF0E1312)))
                },
                CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
            )
            .border(
                1.dp,
                if (selected) MonarchColors.SovereignGold else MonarchColors.Rune,
                CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}


