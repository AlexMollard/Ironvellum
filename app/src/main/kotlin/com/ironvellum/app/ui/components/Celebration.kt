package com.ironvellum.app.ui.components

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.ironvellum.app.R
import com.ironvellum.app.data.Lines
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.ArmyClass
import com.ironvellum.app.domain.CrestDef
import com.ironvellum.app.domain.CrestGroup
import com.ironvellum.app.domain.Crests
import com.ironvellum.app.domain.Gacha
import com.ironvellum.app.domain.RelicHouses
import com.ironvellum.app.domain.RelicReveal
import com.ironvellum.app.domain.Reward
import com.ironvellum.app.domain.RewardRarity
import com.ironvellum.app.domain.RollResult
import com.ironvellum.app.domain.Sex
import com.ironvellum.app.domain.SkillClaimResult
import com.ironvellum.app.domain.Skills
import com.ironvellum.app.domain.TitleDef
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.domain.Veil
import com.ironvellum.app.domain.VaultSlot
import com.ironvellum.app.domain.VaultState
import com.ironvellum.app.domain.VeilGrant
import com.ironvellum.app.domain.fmt
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors

/** A plain-only string as text for the current language, for builders that run outside composition. */
internal fun uiString(@StringRes id: Int, vararg args: Any): String = Words.text(UiText.res(id, *args))

/** A plain/themed pair as text for the current mode and language, for builders that run outside composition. */
internal fun uiPair(@StringRes plain: Int, @StringRes themed: Int, vararg args: Any): String =
    Words.text(UiText.themed(plain, themed, *args))

/** A plural as text for the current language; [count] is the first argument unless [args] are given. */
internal fun uiPlural(@PluralsRes id: Int, count: Int, vararg args: Any): String = Words.text(UiText.plural(id, count, *args))

/**
 * One full-screen moment the celebration host can show. Each is one step page; the
 * host numbers them ("1 of 3") and ends every one in the same dock.
 */
internal sealed interface CelebrationPage {

    /** A level gained. [inscriptionWaiting] adds the one line saying a draw was earned with it. */
    data class Level(val levelUp: LevelUp, val inscriptionWaiting: Boolean = false) : CelebrationPage

    /** Every deed earned together, as one page. Several share it as a list. */
    data class Deeds(val deeds: List<TitleDef>, val sex: Sex) : CelebrationPage

    /** A technique claimed, with the names of the techniques it opened. */
    data class TechniqueMastered(
        val name: String,
        val tier: String,
        val path: String,
        val xp: Int,
        val opened: List<String> = emptyList(),
    ) : CelebrationPage

    /**
     * A reward drawn beyond the Veil. [sigilSeed] is set for a relic only. A house relic carries its
     * [relic] reveal and the four [houseSlots] of its house; a crest carries its [crestId] and the
     * [crestLine] under its name. Without them the page is the plain stamp it always was.
     */
    data class Inscribed(
        val rarity: RewardRarity,
        val name: String,
        val sigilSeed: String? = null,
        val relicId: String? = null,
        val notes: List<String> = emptyList(),
        val relic: RelicReveal? = null,
        val houseSlots: List<VaultSlot> = emptyList(),
        val crestId: String? = null,
        val crestLine: String? = null,
    ) : CelebrationPage

    /** A circle's weekly goal met, and the XP its member's share paid. */
    data class GoalMet(val name: String, val xp: Int) : CelebrationPage
}

/** The deeds page for [deeds] that have a name to show; nothing when none do. */
internal fun deedPages(deeds: List<TitleDef>, sex: Sex): List<CelebrationPage> =
    deeds.filter { it.name.isNotBlank() }
        .takeIf { it.isNotEmpty() }
        ?.let { listOf(CelebrationPage.Deeds(it, sex)) }
        .orEmpty()

/** The level a claim reached, or null when it stayed on the same one. */
internal fun levelUpOf(result: SkillClaimResult): LevelUp? =
    if (result.levelAfter <= result.levelBefore) {
        null
    } else {
        LevelUp(
            levelBefore = result.levelBefore,
            levelAfter = result.levelAfter,
            classBefore = ArmyClass.forLevel(result.levelBefore).title,
            classAfter = ArmyClass.forLevel(result.levelAfter).title,
            totalXp = result.totalXp,
            xpAwarded = result.xpAwarded,
        )
    }

/**
 * What claiming a technique tells, in order: the technique, then the level it
 * reached (every level earns an inscription), then any deeds it earned.
 */
internal fun techniquePages(result: SkillClaimResult, sex: Sex): List<CelebrationPage> = buildList {
    add(
        CelebrationPage.TechniqueMastered(
            name = result.skill.name,
            tier = Skills.tierLabel(result.skill.tier),
            path = uiPair(R.string.celebration_technique_path_name, R.string.celebration_technique_path_name__t, result.skill.line),
            xp = result.xpAwarded,
            opened = result.unlockedNext.map { it.name },
        ),
    )
    levelUpOf(result)?.let { add(CelebrationPage.Level(it, inscriptionWaiting = true)) }
    addAll(deedPages(result.newTitles, sex))
}

/** The rarity as the glossary names it: Epic reads as Fabled. */
internal fun rarityWord(rarity: RewardRarity): String = if (rarity == RewardRarity.Epic) "Fabled" else rarity.name

/**
 * An inscription's draw as a page: its name, rarity, relic art and what it did. [vault] is the vault
 * AFTER the draw, which a house relic's reveal reads its set progress from; [crestsOwned] is how many
 * crests the lifter holds after it.
 */
internal fun inscribedPage(result: RollResult, vault: VaultState? = null, crestsOwned: Int? = null): CelebrationPage.Inscribed {
    val relic = (result.reward as? Reward.Relic)?.let { reward -> vault?.let { RelicHouses.reveal(reward, it) } }
    val notes = when (val reward = result.reward) {
        is Reward.Figures -> listOf(uiString(R.string.always_celebration_added_to_veil))
        is Reward.Relic -> listOf(uiString(R.string.celebration_relic_rate_note, "%.2f".fmt(reward.multiplier)))
        is Reward.CrestFrame -> listOf(
            uiString(R.string.celebration_crest_inscribed_note),
            uiPair(R.string.celebration_crest_wear_hint, R.string.celebration_crest_wear_hint__t),
        )
    }
    return CelebrationPage.Inscribed(
        rarity = result.rarity,
        name = when (val reward = result.reward) {
            is Reward.Figures -> uiPlural(R.plurals.celebration_echoes_count, reward.count)
            is Reward.Relic -> reward.name
            is Reward.CrestFrame -> reward.name
        },
        // Only relics. A figures payout is a number, not an object, and a crest
        // already has its own plate in the collection: a generic sigil there
        // would misrepresent the frame that was won.
        sigilSeed = (result.reward as? Reward.Relic)?.name,
        relicId = (result.reward as? Reward.Relic)?.relicId?.takeIf { RelicHouses.byId(it) != null },
        notes = notes,
        relic = relic,
        houseSlots = relic?.let { r -> vault?.houses?.firstOrNull { it.house == r.relic.house }?.slots }.orEmpty(),
        crestId = (result.reward as? Reward.CrestFrame)?.id,
        crestLine = (result.reward as? Reward.CrestFrame)?.let { crestLine(uiString(R.string.celebration_crest_lead_chance), crestsOwned) },
    )
}

/** "Chance draw · 5 of 28 crests", or just the lead when the count is not known. */
internal fun crestLine(lead: String, owned: Int?): String =
    if (owned == null) lead else uiPlural(R.plurals.celebration_crest_line_count, Crests.ALL.size, lead, owned, Crests.ALL.size)

/**
 * What the Veil paid outside a draw, as moments. The one-time catch-up is a single page that
 * lists everything it paid; a milestone crest is a page of its own, one per crest.
 */
internal fun veilGrantPages(grant: VeilGrant?, crestsOwned: Int? = null): List<CelebrationPage> {
    if (grant == null || grant.isEmpty) return emptyList()
    val retro = if (grant.retro) {
        val notes = buildList {
            if (grant.inscriptions > 0) {
                add(uiPlural(R.plurals.celebration_retro_inscriptions, grant.inscriptions))
            }
            if (grant.crests.isNotEmpty()) add(uiPlural(R.plurals.celebration_retro_crests, grant.crests.size))
            if (grant.relics.isNotEmpty()) add(uiPlural(R.plurals.celebration_retro_relics, grant.relics.size))
        }
        listOf(CelebrationPage.Inscribed(rarity = RewardRarity.Rare, name = uiString(R.string.always_celebration_veil_remembered), notes = notes))
    } else {
        emptyList()
    }
    // A crest already named by the catch-up page is not told twice; one won at a milestone since is.
    val milestones = if (grant.retro) emptyList() else grant.crests.map { name ->
        val crest = Crests.ALL.firstOrNull { it.name == name }
        val id = crest?.id
        CelebrationPage.Inscribed(
            rarity = RewardRarity.Rare,
            name = name,
            notes = if (id == null) {
                listOf(
                    uiString(R.string.celebration_crest_earned),
                    uiPair(R.string.celebration_crest_wear_hint, R.string.celebration_crest_wear_hint__t),
                )
            } else {
                emptyList()
            },
            crestId = id,
            crestLine = crest?.let { earnedLine(it, crestsOwned) },
        )
    }
    return retro + milestones
}

/**
 * What a crest awarded outside a draw says under its name: "Level 20 milestone" for a ladder rung, and for a
 * deed or a house what earned it and how many of the 28 are held now ("Deed crest · Unbroken · 10 of 28 crests").
 */
internal fun earnedLine(crest: CrestDef, crestsOwned: Int?): String = when (crest.group) {
    CrestGroup.Ladder -> uiString(R.string.celebration_crest_lead_level, crest.level ?: 0)
    CrestGroup.Deeds -> crestLine(uiString(R.string.always_celebration_crest_lead_deed, crest.deed?.name.orEmpty()), crestsOwned)
    CrestGroup.Houses -> crestLine(uiString(R.string.celebration_crest_lead_house, crest.house?.title.orEmpty()), crestsOwned)
    CrestGroup.Veil -> crestLine(uiString(R.string.celebration_crest_lead_chance), crestsOwned)
}

/** The one narrator line of an inscription, in the voice of Today's: a short sentence with a full stop. */
internal fun inscribedNarrator(rarity: RewardRarity): String = when (rarity) {
    RewardRarity.Common -> uiString(R.string.celebration_narrator_common)
    RewardRarity.Rare -> uiString(R.string.celebration_narrator_rare)
    RewardRarity.Epic -> uiString(R.string.celebration_narrator_fabled)
    RewardRarity.Masterwork -> Lines.masterworkAnswer
}

private const val SEAL_AT = 550L
private const val MOMENT_END = 2_000L

/** Haptics keep the beats the old reveals had: a tick for Common, a confirm for Rare, two for Fabled, a long press for Masterwork. */
internal fun inscribedBeats(rarity: RewardRarity): List<Pair<Long, HapticFeedbackType>> = when (rarity) {
    RewardRarity.Common -> listOf(SEAL_AT to HapticFeedbackType.SegmentTick)
    RewardRarity.Rare -> listOf(SEAL_AT to HapticFeedbackType.Confirm)
    RewardRarity.Epic -> listOf(SEAL_AT to HapticFeedbackType.Confirm, SEAL_AT + 133 to HapticFeedbackType.Confirm)
    RewardRarity.Masterwork -> listOf(SEAL_AT to HapticFeedbackType.LongPress)
}

private val MasteredBeats = listOf(SEAL_AT to HapticFeedbackType.Confirm, SEAL_AT + 133 to HapticFeedbackType.Confirm)
private val GoalBeats = listOf(SEAL_AT to HapticFeedbackType.Confirm)

/**
 * The celebration for moments outside a trial's seal, one step page at a time
 * inside one full-screen dialog. Every page ends in the same dock - an emerald
 * Continue and at most one quiet link - and nothing else is a tap target. With
 * animations off every page lands in its final state at once.
 */
@Composable
internal fun AchievementOverlay(
    pages: List<CelebrationPage>,
    onDone: () -> Unit,
    /** The title worn now, so a deed already worn says so instead of offering it. */
    wornTitleId: String? = null,
    /** Wears a deed's title; null hides Wear title. */
    onWear: ((titleId: String) -> Unit)? = null,
    /** Opens the one technique a claim unlocked, then moves on; null hides the link. */
    onOpenTechnique: ((name: String) -> Unit)? = null,
    /** The crest worn now, so a crest reveal offers to swap to the new one or keep this. */
    wornCrestId: String? = null,
    /** Wears a crest; null makes a crest reveal a plain Continue. */
    onWearCrest: ((crestId: String) -> Unit)? = null,
) {
    if (pages.isEmpty()) return
    var index by remember(pages) { mutableIntStateOf(0) }
    val at = index.coerceIn(0, pages.lastIndex)
    val advance = { if (at < pages.lastIndex) index = at + 1 else onDone() }
    CappedDialog(
        // Back is Continue, never a skip past the pages still to show.
        onDismissRequest = advance,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(IronvellumColors.Abyss),
        ) {
            // Keyed so each page starts its own clock and haptics.
            key(at) {
                when (val page = pages[at]) {
                    is CelebrationPage.Level -> LevelUpPage(
                        levelUp = page.levelUp,
                        step = at,
                        steps = pages.size,
                        canSkip = false,
                        onContinue = advance,
                        onSkip = {},
                        note = stringResource(R.string.celebration_level_inscription_waiting).takeIf { page.inscriptionWaiting },
                    )
                    is CelebrationPage.Deeds -> DeedsPage(
                        deeds = page.deeds,
                        sex = page.sex,
                        step = at,
                        steps = pages.size,
                        wornTitleId = wornTitleId,
                        onContinue = advance,
                        onWear = { onWear?.invoke(it) },
                        canWear = onWear != null,
                    )
                    is CelebrationPage.TechniqueMastered -> {
                        val open = page.opened.singleOrNull()?.takeIf { onOpenTechnique != null }
                        MomentPage(
                            step = at,
                            steps = pages.size,
                            tag = UiText.themed(R.string.celebration_technique_mastered_tag, R.string.celebration_technique_mastered_tag__t).asString(),
                            name = page.name,
                            beats = MasteredBeats,
                            dock = {
                                CelebrationDock(
                                    primary = stringResource(R.string.common_continue),
                                    onPrimary = advance,
                                    link = open?.let { stringResource(R.string.celebration_technique_open_link, it) },
                                    onLink = {
                                        if (open != null) onOpenTechnique?.invoke(open)
                                        advance()
                                    },
                                )
                            },
                            subline = {
                                Text(stringResource(R.string.celebration_technique_tier_line, page.tier, page.path), style = MaterialTheme.typography.bodySmall, color = Dim, modifier = it)
                            },
                            xp = page.xp,
                            notes = page.opened.map { UiText.themed(R.string.celebration_technique_opened_note, R.string.celebration_technique_opened_note__t, it).asString() },
                        )
                    }
                    is CelebrationPage.Inscribed -> if (page.crestId != null) {
                        val id = page.crestId
                        val canWear = onWearCrest != null && id != wornCrestId
                        val keep = Crests.byId(wornCrestId)?.name?.replace(" Crest", "")
                        CrestRevealPage(
                            step = at,
                            steps = pages.size,
                            crestId = id,
                            line = page.crestLine,
                            dock = {
                                CelebrationDock(
                                    primary = if (canWear) stringResource(R.string.celebration_crest_wear_button) else stringResource(R.string.common_continue),
                                    onPrimary = {
                                        if (canWear) onWearCrest?.invoke(id)
                                        advance()
                                    },
                                    link = if (canWear) (keep?.let { stringResource(R.string.celebration_crest_keep_link, it) } ?: stringResource(R.string.celebration_crest_not_now_link)) else null,
                                    onLink = advance,
                                )
                            },
                        )
                    } else MomentPage(
                        step = at,
                        steps = pages.size,
                        tag = stringResource(R.string.celebration_inscribed_tag),
                        name = page.name,
                        beats = inscribedBeats(page.rarity),
                        dock = { CelebrationDock(primary = stringResource(R.string.common_continue), onPrimary = advance) },
                        mark = { t ->
                            val seed = page.sigilSeed
                            if (seed != null) RelicMark(seed, page.relicId, page.rarity, t) else DeedSeal(t, 156.dp)
                        },
                        subline = {
                            Column(it, horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(rarityWord(page.rarity), style = MaterialTheme.typography.bodySmall, color = IronvellumColors.SovereignGold)
                                Text(inscribedNarrator(page.rarity), style = MaterialTheme.typography.bodySmall, color = Dim)
                            }
                        },
                        notes = page.notes,
                    )
                    is CelebrationPage.GoalMet -> MomentPage(
                        step = at,
                        steps = pages.size,
                        tag = UiText.themed(R.string.celebration_goal_met_tag, R.string.celebration_goal_met_tag__t).asString(),
                        name = page.name,
                        beats = GoalBeats,
                        dock = { CelebrationDock(primary = stringResource(R.string.common_continue), onPrimary = advance) },
                        subline = {
                            Text(
                                UiText.themed(R.string.celebration_goal_met_subline, R.string.celebration_goal_met_subline__t).asString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = Dim,
                                textAlign = TextAlign.Center,
                                modifier = it,
                            )
                        },
                        xp = page.xp,
                    )
                }
            }
        }
    }
}

/**
 * A moment page: the gold tag, a seal (or [mark]), the name, a subline, the XP
 * in gold and quiet notes, each revealed in turn. One clock, one haptic list.
 */
@Composable
private fun MomentPage(
    step: Int,
    steps: Int,
    tag: String,
    name: String,
    beats: List<Pair<Long, HapticFeedbackType>>,
    dock: @Composable () -> Unit,
    subline: @Composable (Modifier) -> Unit,
    mark: @Composable (Long) -> Unit = { t -> DeedSeal(t, 156.dp) },
    xp: Int? = null,
    notes: List<String> = emptyList(),
) {
    val motion = animatorsOn(LocalContext.current)
    val t = rememberClock(MOMENT_END, motion)
    Haptics(motion, *beats.toTypedArray())
    StepPage(step = step, steps = steps, dock = dock) {
        Text(
            tag,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            letterSpacing = 0.5.sp,
            color = IronvellumColors.SovereignGold,
            modifier = Modifier.reveal(t >= 100, motion),
        )
        Spacer(Modifier.height(20.dp))
        mark(t)
        Spacer(Modifier.height(26.dp))
        Text(
            name,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            textAlign = TextAlign.Center,
            color = IronvellumColors.Ink,
            modifier = Modifier.reveal(t >= 650, motion),
        )
        Spacer(Modifier.height(6.dp))
        subline(Modifier.reveal(t >= 800, motion))
        if (xp != null && xp > 0) {
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.xp_gain_label, count(xp)),
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                style = TextStyle(fontFeatureSettings = "tnum"),
                color = IronvellumColors.SovereignGold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().reveal(t >= 950, motion),
            )
        }
        Column(Modifier.fillMaxWidth().padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            notes.forEachIndexed { i, note ->
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = Dim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.reveal(t >= 1_100 + 150L * i, motion),
                )
            }
        }
    }
}

/** A relic's generated art where the seal would be, settling with the same stamp. */
@Composable
private fun RelicMark(seed: String, relicId: String?, tier: RewardRarity, t: Long, size: Dp = 132.dp) {
    val mark = Modifier.size(size).graphicsLayer {
        alpha = phase(t, 0, 400)
        val s = stamp(t, SEAL_AT)
        scaleX = s
        scaleY = s
    }
    if (relicId != null) {
        HouseRelicSigil(relicId, tier, mark)
    } else {
        // Old rewards without a catalogue identity retain their original name-derived art.
        RelicSigil(name = seed, accent = IronvellumColors.Emerald, modifier = mark)
    }
}
