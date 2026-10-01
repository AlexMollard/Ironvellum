package com.ironvellum.app.ui.titles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.ironvellum.app.ui.components.IronvellumButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
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
import com.ironvellum.app.ui.components.IronvellumTabBar
import com.ironvellum.app.ui.components.IronvellumTabItem
import com.ironvellum.app.ui.components.TapRow
import com.ironvellum.app.ui.components.plural
import androidx.compose.foundation.layout.wrapContentHeight
import com.ironvellum.app.ui.theme.ChakraPetch
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
    /** Latest weigh-in, the yardstick for loads set as a share of bodyweight. */
    val bodyweightKg: Double? = null,
    /** Best logged effort per technique name, practice or training, for the tree's progress cue. */
    val bestEffort: Map<String, SkillGuidance.Effort> = emptyMap(),
    /** Every rite as id to name, for "Train it". */
    val rites: List<Pair<Long, String>> = emptyList(),
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
            bodyweightKg = SetRecords.bodyweightLookup(stats)(Long.MAX_VALUE).takeIf { it > 0.0 },
            bestEffort = bestEfforts(practices, training),
            rites = presets.map { it.id to it.name },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TitlesUi())

    /** Why the last "Train it" failed, or which rite it landed in; null when nothing to show. */
    private val _trainResult = MutableStateFlow<String?>(null)
    val trainResult: StateFlow<String?> = _trainResult.asStateFlow()

    /**
     * Adds [skillName] to rite [presetId], or to a new rite named after it
     * when null: three sets at the standard's own figure (seconds for a
     * hold, as the seeded rites store them).
     */
    fun addToRite(skillName: String, presetId: Long?) {
        val def = Skills.forName(skillName) ?: return
        viewModelScope.launchGuarded("add to rite") {
            val rite = repo.addExerciseToRite(skillName, presetId, targetSets = 3, targetReps = def.target.coerceAtLeast(1))
            _trainResult.value = "$skillName is in $rite."
        }
    }
    fun dismissTrainResult() {
        _trainResult.value = null
    }

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

/** Best practice or training effort per technique, by the reps or seconds it reached. */
internal fun bestEfforts(
    practices: List<SkillPractice>,
    training: Map<String, SkillTrainingEvidence>,
): Map<String, SkillGuidance.Effort> {
    val practiceBest = practices.filterNot { it.claimed }
        .groupBy { it.skillName }
        .mapValues { (_, list) -> list.maxBy { it.value }.let { SkillGuidance.Effort(it.value, it.weightKg) } }
    return Skills.ALL.mapNotNull { def ->
        val trained = training[Titles.normaliseName(def.name)]?.let { SkillGuidance.Effort(it.value, it.weightKg) }
        listOfNotNull(practiceBest[def.name], trained).maxByOrNull { it.value }?.let { def.name to it }
    }.toMap()
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
    // The path opened from the grid, null while the grid shows. Saveable so it
    // survives a tab switch and a rotation: PATHS reopens where it was left.
    var openLine by rememberSaveable { mutableStateOf<String?>(null) }
    // The grid marks the path of the most recent attempt or claim; it never opens it.
    val recentLine = remember(ui.log) { SkillGuidance.initialLine(ui.log) }
    BackHandler(enabled = tab == TitlesTab.TREE && openLine != null) { openLine = null }
    var openSkill by remember { mutableStateOf<String?>(null) }
    var trainSkill by remember { mutableStateOf<String?>(null) }
    var pendingOpen by remember { mutableStateOf<String?>(null) }

    openSkill?.let { name ->
        Skills.forName(name)?.let { def ->
            val entries = remember(ui.log, name) { ui.log.filter { it.skillName == name } }
            SkillDetailDialog(
                skill = def,
                mastered = name in mastered,
                unlocked = Skills.unlocked(def, mastered),
                entries = entries,
                training = ui.training[Titles.normaliseName(name)],
                sexBar = if (ui.sex == Sex.FEMALE) Skills.femaleStandard(name) else null,
                masteredSkills = mastered,
                bodyweightKg = ui.bodyweightKg,
                female = ui.sex == Sex.FEMALE,
                refundXp = Skills.claimRefund(name, entries.firstOrNull { it.claimed }?.value ?: 0),
                onLogPractice = { value, load ->
                    viewModel.practice(name, value, load)
                    openSkill = null
                },
                // Close on claim: the overlay plays next, and coming back from
                // it to this dialog, now reading MASTERED, was a stale stop.
                onClaim = {
                    viewModel.claim(name)
                    openSkill = null
                },
                onUnclaim = { viewModel.unclaim(name) },
                onDismiss = { openSkill = null },
                onOpenSkill = { openSkill = it },
                onTrain = {
                    trainSkill = name
                    openSkill = null
                },
            )
        }
    }

    trainSkill?.let { name ->
        RiteChoiceDialog(
            skillName = name,
            rites = ui.rites,
            onPick = { presetId ->
                viewModel.addToRite(name, presetId)
                trainSkill = null
            },
            onDismiss = { trainSkill = null },
        )
    }
    val trainResult by viewModel.trainResult.collectAsStateWithLifecycle()
    trainResult?.let { message ->
        AlertDialog(
            shape = MaterialTheme.shapes.medium,
            containerColor = Color(0xFF0D1110),
            onDismissRequest = viewModel::dismissTrainResult,
            title = { Text("Added") },
            text = { Text(message) },
            confirmButton = {
                IronvellumButton(label = "OK", onClick = viewModel::dismissTrainResult, quiet = true)
            },
        )
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
    // A fresh scroll for each path, so the graph opens at its own top (and its
    // next node) rather than wherever the grid was scrolled to.
    val pageScroll = key(openLine) { rememberScrollState() }
    Column(
        Modifier
            .fillMaxSize()
            .then(if (deedsTab) Modifier else Modifier.verticalScroll(pageScroll))
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
            // The deed count lives on the Deeds board now, beside its overall bar.
            "${mastered.size} ${if (mastered.size == 1) "technique" else "techniques"} mastered",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = ChakraPetch,
            color = IronvellumColors.SystemGreen,
        )
        Spacer(Modifier.height(12.dp))
        // One persistent selector row: the three tabs. Paths are chosen from
        // the grid below.
        IronvellumTabBar(
            items = listOf(IronvellumTabItem("DEEDS"), IronvellumTabItem("PATHS"), IronvellumTabItem("JOURNAL")),
            selectedIndex = tab.ordinal,
            onSelect = { tab = TitlesTab.entries[it] },
        )

        if (tab == TitlesTab.JOURNAL) {
            SkillJournal(
                log = ui.log,
                claimed = ui.claimedSkills,
                practiceCounts = ui.practiceCounts,
                onOpenLine = { line ->
                    openLine = line
                    tab = TitlesTab.TREE
                },
                onSelect = { openSkill = it },
            )
            Spacer(Modifier.height(28.dp))
            return@Column
        }


        if (tab == TitlesTab.TREE) {
            val line = openLine
            Spacer(Modifier.height(12.dp))
            if (line == null) {
                // No "Skill Tree" heading: the selected pill above already says it.
                // The tally is the line worth keeping here.
                Text(
                    "${mastered.count { it in Skills.BY_NAME }} of ${Skills.ALL.size} techniques mastered",
                    style = MaterialTheme.typography.labelMedium,
                    color = IronvellumColors.InkMuted,
                )
                Spacer(Modifier.height(10.dp))
                PathGrid(
                    mastered = mastered,
                    recentLine = recentLine,
                    onOpen = { openLine = it },
                )
            } else {
                PathHeader(line = line, mastered = mastered, onBack = { openLine = null })
                Spacer(Modifier.height(6.dp))
                SkillTreeGraph(
                    line = line,
                    mastered = mastered,
                    onSelect = { openSkill = it },
                    modifier = Modifier.fillMaxWidth(),
                    best = ui.bestEffort,
                    bodyweightKg = ui.bodyweightKg,
                    female = ui.sex == Sex.FEMALE,
                )
            }
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
            onDone = {
                viewModel.dismissClaim()
                // A technique tapped on the way opens once every page is seen.
                pendingOpen?.let { openSkill = it }
                pendingOpen = null
            },
            // Only the mastery page has notes: each names a technique it opened.
            onNote = { item, index ->
                if (item.banner == "TECHNIQUE MASTERED") {
                    result.unlockedNext.getOrNull(index)?.let { pendingOpen = it.name }
                }
            },
        )
    }

}

private enum class TitlesTab { DEEDS, TREE, JOURNAL }

/**
 * Where "Train it" puts a technique: one of the rites, or a new rite named
 * after it. Appends to the chosen rite rather than opening its editor, which
 * cannot be handed an exercise to start with.
 */
@Composable
private fun RiteChoiceDialog(
    skillName: String,
    rites: List<Pair<Long, String>>,
    onPick: (presetId: Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        shape = MaterialTheme.shapes.medium,
        containerColor = Color(0xFF0D1110),
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Train $skillName",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = ChakraPetch,
                color = IronvellumColors.Ink,
            )
        },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(
                    "Add it to the end of a rite, three sets at the standard.",
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
                Spacer(Modifier.height(8.dp))
                rites.forEach { (id, name) ->
                    RiteChoiceDivider()
                    RiteChoiceRow(name) { onPick(id) }
                }
                RiteChoiceDivider()
                RiteChoiceRow("New rite · $skillName", accent = IronvellumColors.SystemGreen) { onPick(null) }
            }
        },
        confirmButton = {
            IronvellumButton(label = "Cancel", onClick = onDismiss, quiet = true)
        },
    )
}

@Composable
private fun RiteChoiceRow(label: String, accent: Color = IronvellumColors.Ink, onClick: () -> Unit) {
    // The shared tap row, with a chevron so every choice reads as a destination.
    TapRow(onClickLabel = label, onClick = onClick) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = accent,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = IronvellumColors.InkMuted,
        )
    }
}

@Composable
private fun RiteChoiceDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(IronvellumColors.Rune.copy(alpha = 0.6f)))
}

// No line art on the tree's pills. Six of the ten lines had a mark and four did
// not, and the marks themselves read as bad clip-art at pill size - the owner's
// call was that no icon beats these icons. The name carries the line.



