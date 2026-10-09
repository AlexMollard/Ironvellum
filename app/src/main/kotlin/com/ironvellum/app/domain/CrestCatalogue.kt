package com.ironvellum.app.domain

import java.util.Locale

/** What a row of the crest catalogue says about progress: a line, and for a crest in reach a count and a bar. */
data class CrestProgress(val line: String, val count: String? = null, val fraction: Float? = null)

/**
 * The words of the crest collection, as pure functions so a test can read every one: the line under a
 * crest's name, its progress, where it came from and what it is worth. The screens only lay them out.
 */
object CrestCatalogue {

    /** "Steady Hand: essence rate +3%": the perk's name and its short form. */
    fun perkLine(def: CrestDef): String = "${def.perk}: ${def.short.replaceFirstChar { it.lowercase() }}"

    /** "Deed crest · Fortnight Vigil · Rare", "Ladder crest · Level 20", "House crest · House of Iron", "Veil crest · Chance draw". */
    fun sourceLine(def: CrestDef): String = when (def.group) {
        CrestGroup.Ladder -> "Ladder crest · Level ${def.level}"
        CrestGroup.Deeds -> "Deed crest · ${def.deed?.name} · ${def.deed?.rarity?.label}"
        CrestGroup.Houses -> "House crest · ${def.house?.title}"
        CrestGroup.Veil -> "Veil crest · Chance draw"
    }

    /**
     * The line under a crest's name, with a count and bar when it is in reach. A held crest tells its perk;
     * one not yet held tells what it asks: a level, a deed, a house, or a draw.
     *
     * [nextLadder] is the id of the next ladder crest the lifter has yet to reach: only it wears a bar.
     * [deeds] is each deed's progress by id; [houseRelics] the relics held per house.
     */
    fun progress(
        def: CrestDef,
        owned: Boolean,
        level: Int,
        nextLadder: String?,
        deeds: Map<String, Titles.Progress>,
        houseRelics: Map<RelicHouse, Int>,
    ): CrestProgress {
        if (owned) return CrestProgress(perkLine(def))
        return when (def.group) {
            CrestGroup.Ladder -> {
                val at = def.level ?: 0
                if (def.id == nextLadder) {
                    val toGo = (at - level).coerceAtLeast(0)
                    CrestProgress(
                        "Level $at · $toGo ${if (toGo == 1) "level" else "levels"} to go",
                        "${level.coerceAtMost(at)}/$at",
                        (level.toFloat() / at).coerceIn(0f, 1f),
                    )
                } else {
                    CrestProgress("Level $at · ${def.perk}")
                }
            }
            CrestGroup.Deeds -> {
                val deed = def.deed
                val p = deed?.let { deeds[it.id] }
                val line = "${deed?.description?.trimEnd('.')} (${deed?.name})"
                if (p == null) CrestProgress(line) else CrestProgress(line, countOf(p), p.fraction)
            }
            CrestGroup.Houses -> {
                val have = houseRelics[def.house] ?: 0
                CrestProgress(
                    "${def.house?.title} · $have of ${RelicHouses.FULL} relics",
                    "$have/${RelicHouses.FULL}",
                    (have.toFloat() / RelicHouses.FULL).coerceIn(0f, 1f),
                )
            }
            CrestGroup.Veil -> CrestProgress("Veil draw · ${def.perk}")
        }
    }

    /** "64/100", "12/42 km" (tenths), "1.6/2×" (hundredths of a bodyweight multiple). */
    fun countOf(p: Titles.Progress): String = when {
        p.unit.startsWith("km") -> "%.0f/%.0f km".format(Locale.ROOT, p.current / p.scale.toDouble(), p.target / p.scale.toDouble())
        p.unit.startsWith("%") -> "%.1f/%.1f×".format(Locale.ROOT, p.current / 100.0, p.target / 100.0)
        else -> "${p.current}/${p.target}"
    }

    /**
     * What a crest is worth to this lifter, one sentence. A perk on the rate says how much essence an hour it
     * adds at the lifter's own inputs ([perHour], measured by [Crests.worth]); the others say where they pay.
     */
    fun worthNote(def: CrestDef, perHour: Double): String = when {
        def.kind == CrestKind.Offering -> "Pays when you buy an inscription: the price you are shown is the price you pay."
        def.id in DRAW_PERKS -> "Pays on every draw: the odds panel shows what it adds."
        def.kind == CrestKind.Away -> "Pays when you have been away: it changes what an absence banks, not your rate."
        perHour >= 0.05 -> "At your rate now this adds about +${"%.1f".format(Locale.ROOT, perHour)} essence an hour."
        else -> "At your rate now this adds almost nothing. It pays when its part of the rate grows."
    }

    private val DRAW_PERKS = setOf("gold", "aurora", "masterwork")

    const val VEIL_ONLY = "The Veil only: it never touches XP, strength, levels or deeds."
}
