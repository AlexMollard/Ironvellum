package com.ironvellum.app.domain

import kotlin.math.roundToLong

/** The four shelves of the crest catalogue, in the order the collection lists them. */
enum class CrestGroup(val label: String) { Ladder("Ladder"), Deeds("Deeds"), Houses("Houses"), Veil("The Veil") }

/** What a crest's perk is about, as the sheet's chip names it. */
enum class CrestKind(val label: String) { Rate("Rate"), Offering("Offering"), Echoes("Echoes"), Away("Away"), Vault("Vault") }

/**
 * The flat metal ramps a crest is drawn in. FIXED colours, never the lifter's accent: a crest looks the
 * same on every screen and in every ally's list. The UI maps each tone to its [com.ironvellum.app.ui.theme.Metal].
 */
enum class CrestTone { Iron, Bronze, Gold, Prism, Emerald, Red, Ink }

/**
 * One crest of the 28. Every number a perk uses lives in [Crests.effects], and the text here says
 * the same thing in the app's voice; CrestsTest reads one against the other.
 *
 * [art] is the tone of the mark, [ring] the tone of its rim (they differ for a few). Exactly one of
 * [level], [deedId], [house] is set for a ladder, deed or house crest; the Veil's six have none, for a
 * chance draw is their only way in.
 */
data class CrestDef(
    val id: String,
    val name: String,
    val group: CrestGroup,
    val kind: CrestKind,
    val perk: String,
    val text: String,
    val short: String,
    val art: CrestTone,
    val ring: CrestTone,
    val double: Boolean = false,
    val level: Int? = null,
    val deedId: String? = null,
    val house: RelicHouse? = null,
) {
    /** "Iron crest": the name inside a sentence. */
    val sentenceName: String get() = name.replace(" Crest", " crest")

    /** The deed this crest is paid for, as the codex names it. */
    val deed: TitleDef? get() = deedId?.let { Titles.byId(it) }

    /** How a lifter comes by it, for the locked sheet and the catalogue. */
    val how: String
        get() = when (group) {
            CrestGroup.Ladder -> "Reach level $level"
            CrestGroup.Deeds -> "Earn the deed ${deed?.name}"
            CrestGroup.Houses -> "Complete the ${house?.title}"
            CrestGroup.Veil -> "Chance: a Veil draw that pays a crest"
        }
}

/**
 * What the draws read from the worn crest. [NONE] is the lifter with nothing worn, and every field's
 * default is the rule as it stood before crests: the roller with [NONE] is the roller it always was.
 */
data class DrawPerks(
    /** Echo draws pay this many times their count (Gold: 1.5). */
    val echoPurse: Double = 1.0,
    /** Echo draws pay at least this share of their band (Aurora: 0.6). */
    val echoFloor: Double = 0.0,
    /** A NEW relic rolls this share of its band higher, capped at the top of the band (Masterwork: 0.1). */
    val newRelicLift: Double = 0.0,
) {
    /**
     * What an echo draw pays for a roll [t] in [0, 1) across the band [low]..[high]: the floor lifts the
     * band's bottom, then the purse scales the count. The one function the roller and the odds panel share.
     */
    fun echoes(low: Int, high: Int, t: Double): Int {
        val floorLow = low + ((high - low) * echoFloor).toInt()
        val raw = (floorLow + ((high - floorLow + 1) * t).toInt()).coerceAtMost(high)
        return (raw * echoPurse).roundToLong().toInt()
    }

    /** The least and most an echo draw can pay in the band [low]..[high], for the odds panel. */
    fun echoRange(low: Int, high: Int): IntRange = echoes(low, high, 0.0)..echoes(low, high, 0.999999)

    companion object {
        val NONE = DrawPerks()
    }
}

/**
 * Every effect of the worn crest on the rate, the absence and the Veil, in ONE value. It rides inside
 * [HouseEffects], which both [Idle.rate] and [Idle.accruedExact] already read, so what a screen shows
 * and what a collect banks cannot disagree. Ceilings and floors are terms here ([trainingCapBonus],
 * [floorBonus] ...) and never edits to [Idle]'s constants, so [Idle.MAX_TRAINING_FACTOR] stays what it is.
 *
 * Build it with [Crests.effects]; every default is the identity.
 */
data class CrestEffects(
    val wornId: String? = null,
    /** Flat factor on the whole rate: Iron's 1.03, Sovereign's hoard, Ledger's tribute. */
    val rateFactor: Double = 1.0,
    /** Factor on the whole rate while the oath stands at [OATH_DAYS] or more (Ashen King). */
    val oathFactor: Double = 1.0,
    /** Added to the 3 each trial sealed this week counts (Obsidian). */
    val sessionWeightBonus: Double = 0.0,
    /** Added to the 1 each day of the oath counts (Watchfire). */
    val streakWeightBonus: Double = 0.0,
    /** Added to the 0.05 each unit of volume counts (Throne: 20% more). */
    val volumeWeightBonus: Double = 0.0,
    /** Added to the training ceiling of 30 (Anvil). */
    val trainingCapBonus: Double = 0.0,
    /** Added to the technique ceiling of 1.0 above x1 (Sage). */
    val skillCeilingBonus: Double = 0.0,
    /** Factor on how fast techniques approach the ceiling (Verdant). */
    val skillRateScale: Double = 1.0,
    /** Essence an hour added before the multipliers (Dawnroad). */
    val flatPerHour: Double = 0.0,
    /** Factor on a house's term, on top of its pair bonus (the four house crests). */
    val houseTermScale: Map<RelicHouse, Double> = emptyMap(),
    /** The pair bonus: 5% by default, 8% with Vellum. */
    val pairBonus: Double = RelicHouses.PAIR_BONUS,
    /** Echoes lift the rate by this much per 100 held (Silver: 0.015). */
    val echoStep: Double = Idle.ECHO_BONUS_STEP,
    /** The most the echoes can add (Silver: 0.30). */
    val echoCap: Double = Idle.ECHO_BONUS_CAP,
    /** Hours added to the full-strength day (Jade). */
    val fullHoursBonus: Double = 0.0,
    /** Added to the taper floor of 10% (Crimson). */
    val floorBonus: Double = 0.0,
    /** Hours added to the taper window of 48 (Void). */
    val taperHoursBonus: Double = 0.0,
    /** Factor on the essence an absence banks (Marathon). */
    val awayPay: Double = 1.0,
    /** Factor on the relic stack, read off the vault rows by [Crests.effects] (Ironvellum, Margin). */
    val relicFactor: Double = 1.0,
    /** Factor on the price of an extra inscription (Bronze). */
    val offeringScale: Double = 1.0,
    /** What each inscription bought adds to the next price (Ember: 400). */
    val offeringStep: Long = Veil.OFFERING_STEP,
    val draw: DrawPerks = DrawPerks.NONE,
) {
    companion object {
        /** Days of oath at which Ashen King pays. */
        const val OATH_DAYS = 7

        val NONE = CrestEffects()
    }
}

/**
 * How a crest came to be held, stored beside it so the retro grant can tell a spent inscription from an
 * award. [Legacy] is a row from before sources existed (or an archive written before them): it could have
 * been a draw or a ladder crest, so it counts as a draw, which can only ever under-pay the retro pass.
 */
enum class CrestSource(val id: String) {
    Draw("draw"), Ladder("ladder"), Deed("deed"), House("house"), Legacy("legacy");

    /** Whether a crest held this way stands for an inscription the lifter spent. */
    val countsAsDraw: Boolean get() = this == Draw || this == Legacy

    companion object {
        fun of(id: String?): CrestSource = entries.firstOrNull { it.id == id } ?: Legacy

        /** The source a crest is awarded under outside a draw. */
        fun awardedFor(def: CrestDef): CrestSource = when (def.group) {
            CrestGroup.Ladder -> Ladder
            CrestGroup.Deeds -> Deed
            CrestGroup.Houses -> House
            CrestGroup.Veil -> Draw
        }
    }
}

/** The 28 crests and the rules that turn the worn one into [CrestEffects]. Never touches XP, strength, levels or deeds. */
object Crests {

    private fun ladder(
        id: String, name: String, level: Int, kind: CrestKind, perk: String, text: String, short: String,
        art: CrestTone, ring: CrestTone = art, double: Boolean = false,
    ) = CrestDef(id, name, CrestGroup.Ladder, kind, perk, text, short, art, ring, double, level = level)

    private fun deed(
        id: String, name: String, deedId: String, kind: CrestKind, perk: String, text: String, short: String,
        art: CrestTone, ring: CrestTone = art,
    ) = CrestDef(id, name, CrestGroup.Deeds, kind, perk, text, short, art, ring, deedId = deedId)

    private fun house(
        id: String, name: String, house: RelicHouse, kind: CrestKind, perk: String, text: String, short: String,
        art: CrestTone, ring: CrestTone = art,
    ) = CrestDef(id, name, CrestGroup.Houses, kind, perk, text, short, art, ring, house = house)

    private fun chance(
        id: String, name: String, kind: CrestKind, perk: String, text: String, short: String,
        art: CrestTone, ring: CrestTone = art, double: Boolean = false,
    ) = CrestDef(id, name, CrestGroup.Veil, kind, perk, text, short, art, ring, double)

    /** All 28, in the order the collection lists them. */
    val ALL: List<CrestDef> = listOf(
        ladder("iron", "Iron Crest", 5, CrestKind.Rate, "Steady Hand", "Your essence rate is 3% higher.", "Essence rate +3%", CrestTone.Iron),
        ladder("bronze", "Bronze Crest", 10, CrestKind.Offering, "Frugal Offering", "Extra inscriptions cost 5% less. The first one is 4,750 essence, not 5,000.", "Offerings 5% cheaper", CrestTone.Bronze),
        ladder("silver", "Silver Crest", 15, CrestKind.Echoes, "Echo Trim", "Echoes lift your rate by 1.5% per 100 held, not 1%, and the ceiling rises from 25% to 30%.", "Echoes lift rate 50% more", CrestTone.Iron),
        ladder("gold", "Gold Crest", 20, CrestKind.Echoes, "Echo Purse", "Echo draws pay 50% more echoes.", "Echo draws pay +50%", CrestTone.Gold),
        ladder("jade", "Jade Crest", 25, CrestKind.Away, "Long Day", "Your roll works at full strength for 26 hours before it tires, not 24.", "Full strength 26 h, not 24", CrestTone.Emerald),
        ladder("crimson", "Crimson Crest", 30, CrestKind.Away, "Slow Ember", "On a long absence the roll never falls below 13% of its rate, not 10%.", "Long-absence floor 13%", CrestTone.Red),
        ladder("obsidian", "Obsidian Crest", 35, CrestKind.Rate, "Heavy Week", "Each trial sealed this week counts 3.5 toward your rate, not 3.", "Each trial counts 3.5, not 3", CrestTone.Ink),
        ladder("verdant", "Verdant Crest", 40, CrestKind.Rate, "Quick Study", "Mastered techniques lift your rate 15% faster, so early unlocks count for more.", "Techniques lift rate faster", CrestTone.Emerald, CrestTone.Gold),
        ladder("ember", "Ember Crest", 45, CrestKind.Offering, "Slow Tithe", "Each inscription you buy raises the next price by 400 essence, not 500.", "Price step 400, not 500", CrestTone.Red, CrestTone.Gold),
        ladder("vellum", "Vellum Crest", 50, CrestKind.Vault, "Set Rite", "House pair bonuses are +8%, not +5%, on every house where you hold two relics.", "Pair bonuses +8%, not +5%", CrestTone.Ink, CrestTone.Gold),
        ladder("sovereign", "Sovereign Crest", 55, CrestKind.Vault, "Hoard", "+0.3% essence for every relic you hold, up to +4.8% with all sixteen.", "+0.3% per relic held", CrestTone.Gold, double = true),
        ladder("ironvellum", "Ironvellum Crest", 60, CrestKind.Vault, "Deep Vault", "Relics beyond your best weigh more: the stacking ceiling rises from x3 to x5.", "Relic stacking ceiling x5", CrestTone.Emerald, CrestTone.Gold, double = true),

        deed("watchfire", "Watchfire Crest", "fortnight_vigil", CrestKind.Rate, "Kept Flame", "Each day of your oath counts 1.5 toward your rate, not 1.", "Oath days count 1.5, not 1", CrestTone.Bronze),
        deed("anvil", "Anvil Crest", "unbroken", CrestKind.Rate, "Steady Count", "The ceiling on training rises from 30 to 31.5, so a committed week reaches x4.15, not x4.", "Training ceiling +1.5", CrestTone.Iron, CrestTone.Gold),
        deed("sage", "Sage Crest", "keeper_of_twenty_five", CrestKind.Rate, "Keen Study", "The technique ceiling rises from x2.0 to x2.06.", "Technique ceiling x2.06", CrestTone.Emerald, CrestTone.Gold),
        deed("dawnroad", "Dawnroad Crest", "eternal_vanguard", CrestKind.Rate, "Walker's Trickle", "A trickle of 1 more essence an hour is added before your multipliers.", "+1 essence/h base", CrestTone.Prism),
        deed("throne", "Throne Crest", "throne_of_iron", CrestKind.Rate, "Heavy Hand", "Every rep you log counts 20% more toward your rate.", "Volume counts 20% more", CrestTone.Gold),
        deed("marathon", "Marathon Crest", "gate_marathon", CrestKind.Away, "Second Wind", "Essence banked while you are away is 5% greater.", "Away-pay +5%", CrestTone.Red, CrestTone.Gold),

        house("ironhold", "Ironhold Crest", RelicHouse.Iron, CrestKind.Rate, "Ironhold Rite", "The lifting part of your rate is 6% higher, on top of the house bonus.", "Lifting term +6%", CrestTone.Iron),
        house("bellwarden", "Bellwarden Crest", RelicHouse.Vigil, CrestKind.Rate, "Bellwarden's Watch", "The consistency part of your rate is 15% higher, on top of the house bonus.", "Consistency term +15%", CrestTone.Bronze),
        house("masterwright", "Masterwright Crest", RelicHouse.Craft, CrestKind.Rate, "Masterwright's Hand", "The technique part of your rate is 8% higher, on top of the house bonus.", "Technique term +8%", CrestTone.Prism),
        house("hearthgate", "Hearthgate Crest", RelicHouse.Return, CrestKind.Away, "Open Gate", "The comeback part of your rate is 10% higher: time paid after the full day counts more.", "Comeback term +10%", CrestTone.Emerald),

        chance("aurora", "Aurora Crest", CrestKind.Echoes, "Brighter Echo", "Echo draws always pay at least 60% of their band, so the weak ones vanish.", "Echo draws pay 60%+ of band", CrestTone.Prism),
        chance("void", "Void Crest", CrestKind.Away, "Long Hush", "The slow fall after a day away lasts 54 hours, not 48.", "Taper lasts 54 h, not 48", CrestTone.Ink),
        chance("masterwork", "Masterwork Crest", CrestKind.Vault, "Fine Entry", "New relics roll 10% of their band higher.", "New relics roll higher", CrestTone.Prism, double = true),
        chance("ledger", "Ledger Crest", CrestKind.Vault, "Tribute", "+2% essence for every house you have completed, up to +8%.", "+2% per completed house", CrestTone.Gold),
        // Margin: this was "Full Rite" (four-relic house bonuses x1.5), which only a lifter with a finished
        // house ever felt, in the currency the four house crests already pay. It now moves the one part of
        // the vault no other crest touches: how much each backing relic weighs in the stack
        // (Relics.effectiveMultiplier's restWeight, 1.25). Measured on the draw simulation
        // (tools/crest_margin_sim.py: the real drop table and pity, 4,000 simulated vaults per row) the
        // vault stack is +3.5% at 20 draws, +4.2% at 39 and +4.5% at 59, and the stack multiplies the whole
        // rate: about +3% to +4.5% for a lifter who has a typical vault, +1.8% at 3 relics. Deep Vault's
        // ceiling gives +1.9 / +2.7 / +3.1% on the same vaults, so the two overlap in kind (the stack)
        // and differ in mechanism (a ceiling against a weight).
        chance("margin", "Margin Crest", CrestKind.Vault, "Close Ranks", "Relics beyond your strongest each weigh a quarter more when they stack.", "Backing relics weigh +25%", CrestTone.Iron),
        chance("ashenking", "Ashen King's Crest", CrestKind.Rate, "Reigning", "+5% essence while your oath stands at 7 days or more.", "+5% while oath is 7+ days", CrestTone.Red, CrestTone.Gold),
    )

    private val BY_ID: Map<String, CrestDef> = ALL.associateBy { it.id }

    fun byId(id: String?): CrestDef? = BY_ID[id]

    fun inGroup(group: CrestGroup): List<CrestDef> = ALL.filter { it.group == group }

    /** The ladder in the order it is won: a crest every [Veil.MILESTONE_EVERY] levels, L5 to L60. */
    val LADDER: List<CrestDef> = inGroup(CrestGroup.Ladder)

    /** The six a Veil draw can pay. Nothing else is ever drawn. */
    val CHANCE: List<CrestDef> = inGroup(CrestGroup.Veil)

    /** The crest a deed pays, or null when no crest follows it. */
    fun forDeed(deedId: String): CrestDef? = ALL.firstOrNull { it.deedId == deedId }

    /** The crest a completed house pays. */
    fun forHouse(house: RelicHouse): CrestDef? = ALL.firstOrNull { it.house == house }

    private const val HOARD_PER_RELIC = 0.003
    private const val LEDGER_PER_HOUSE = 0.02
    private const val DEEP_VAULT_CAP = 5.0
    private const val CLOSE_RANKS_WEIGHT = 1.25

    /**
     * The effects of wearing [worn] with the vault [relics] held. An id that is not in the catalogue
     * (a crest from a newer build, a corrupt row) wears nothing. Vault perks are read off the rows
     * here, not off the stored stack: swapping a crest never rewrites a relic.
     */
    fun effects(worn: String?, relics: List<OwnedRelic>): CrestEffects {
        val def = byId(worn) ?: return CrestEffects.NONE
        val base = CrestEffects(wornId = def.id)
        val multipliers = relics.map { it.multiplier }
        val houses = RelicHouses.effects(relics)
        fun scale(house: RelicHouse, by: Double) = mapOf(house to by)
        return when (def.id) {
            "iron" -> base.copy(rateFactor = 1.03)
            "bronze" -> base.copy(offeringScale = 0.95)
            "silver" -> base.copy(echoStep = 0.015, echoCap = 0.30)
            "gold" -> base.copy(draw = DrawPerks(echoPurse = 1.5))
            "jade" -> base.copy(fullHoursBonus = 2.0)
            "crimson" -> base.copy(floorBonus = 0.03)
            "obsidian" -> base.copy(sessionWeightBonus = 0.5)
            "verdant" -> base.copy(skillRateScale = 1.15)
            "ember" -> base.copy(offeringStep = 400L)
            "vellum" -> base.copy(pairBonus = 0.08)
            "sovereign" -> base.copy(rateFactor = 1.0 + HOARD_PER_RELIC * relics.size.coerceAtMost(RelicHouses.CATALOGUE.size))
            "ironvellum" -> base.copy(
                relicFactor = ratio(Relics.effectiveMultiplier(multipliers, excessCap = DEEP_VAULT_CAP), Relics.effectiveMultiplier(multipliers)),
            )
            "watchfire" -> base.copy(streakWeightBonus = 0.5)
            "anvil" -> base.copy(trainingCapBonus = 1.5)
            "sage" -> base.copy(skillCeilingBonus = 0.06)
            "dawnroad" -> base.copy(flatPerHour = 1.0)
            "throne" -> base.copy(volumeWeightBonus = 0.01)
            "marathon" -> base.copy(awayPay = 1.05)
            "ironhold" -> base.copy(houseTermScale = scale(RelicHouse.Iron, 1.06))
            "bellwarden" -> base.copy(houseTermScale = scale(RelicHouse.Vigil, 1.15))
            "masterwright" -> base.copy(houseTermScale = scale(RelicHouse.Craft, 1.08))
            "hearthgate" -> base.copy(houseTermScale = scale(RelicHouse.Return, 1.10))
            "aurora" -> base.copy(draw = DrawPerks(echoFloor = 0.6))
            "void" -> base.copy(taperHoursBonus = 6.0)
            "masterwork" -> base.copy(draw = DrawPerks(newRelicLift = 0.10))
            "ledger" -> base.copy(rateFactor = 1.0 + LEDGER_PER_HOUSE * houses.standings.count { it.fullReached })
            "margin" -> base.copy(
                relicFactor = ratio(Relics.effectiveMultiplier(multipliers, restWeight = CLOSE_RANKS_WEIGHT), Relics.effectiveMultiplier(multipliers)),
            )
            "ashenking" -> base.copy(oathFactor = 1.05)
            else -> base
        }
    }

    /**
     * What each crest would add to the rate right now, in essence an hour against wearing none: the same
     * [Idle.rate] the Veil shows, run once per crest on the lifter's own inputs. A crest whose perk does not
     * touch the rate (an offering price, a draw) is 0. The sheet and Today's chip print these, so a crest's
     * worth is measured, never promised.
     */
    fun worth(
        state: IdleState,
        sessionsLast7d: Int,
        volumeLast7d: Double,
        skillsUnlocked: Int,
        streakDays: Int,
        relics: List<OwnedRelic>,
    ): Map<String, Double> {
        fun perHour(worn: String?) =
            Idle.rate(state, sessionsLast7d, volumeLast7d, skillsUnlocked, streakDays, RelicHouses.effects(relics, worn)).perHour
        val none = perHour(null)
        return ALL.associate { it.id to (perHour(it.id) - none) }
    }

    private fun ratio(a: Double, b: Double) = if (b > 0.0 && a.isFinite() && b.isFinite()) a / b else 1.0

    // ------------------------------------------------------------------ what is owed, and the swap rule

    /** Ladder crests a lifter at [level] has reached, in order. */
    fun ladderFor(level: Int): List<CrestDef> = LADDER.take((level / Veil.MILESTONE_EVERY).coerceAtLeast(0))

    /**
     * Every crest a lifter is owed by what they have already done: the ladder up to [level], the crest
     * of each deed in [earnedDeeds], the crest of each house in [completedHouses]. Pure and additive;
     * the repository inserts what is not held, and nothing here is ever taken back.
     */
    fun earned(level: Int, earnedDeeds: Set<String>, completedHouses: Set<RelicHouse>): List<CrestDef> =
        ladderFor(level) +
            ALL.filter { it.group == CrestGroup.Deeds && it.deedId in earnedDeeds } +
            ALL.filter { it.group == CrestGroup.Houses && it.house in completedHouses }

    /** The worn crest can change once a local calendar day; the first change ever is always allowed. */
    fun canChange(lastChangeMs: Long?, nowMs: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Boolean {
        if (lastChangeMs == null) return true
        fun day(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        return day(nowMs) != day(lastChangeMs)
    }

    /** What changing the worn crest did. */
    enum class Equip { Worn, TooSoon, NotOwned }
}
