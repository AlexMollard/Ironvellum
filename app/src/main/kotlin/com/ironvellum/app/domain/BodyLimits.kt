package com.ironvellum.app.domain

/**
 * Plausible bounds for hand-typed body figures.
 *
 * Every entry point used to check only `> 0.0`, which let a fat-fingered
 * "500" for body fat through to Katch-McArdle: lean mass came out at **-320 kg**
 * and the resting burn at **-6542 kcal**, shown to the lifter as a fact about
 * their own body, with a chart drawn to match. Nothing crashed, which is why
 * nothing caught it.
 *
 * These are deliberately wide — wider than any real lifter — because the job is
 * to reject typos, not to police physiques. The tallest recorded human was
 * 272 cm; the heaviest around 635 kg. Body fat below 3% is not survivable and
 * above 75% is not measurable by any method this app can offer.
 */
object BodyLimits {

    val WEIGHT_KG = 20.0..400.0
    val HEIGHT_CM = 50.0..272.0
    val BODY_FAT_PCT = 2.0..75.0

    fun validWeight(kg: Double?): Boolean = kg != null && kg in WEIGHT_KG

    fun validHeight(cm: Double?): Boolean = cm != null && cm in HEIGHT_CM

    /** Body fat is optional: absent is valid, present-and-absurd is not. */
    fun validBodyFat(pct: Double?): Boolean = pct == null || pct in BODY_FAT_PCT

    /** How a typed figure stands against its range, so a typo reads as out of range and not as missing. */
    enum class Reading { EMPTY, VALID, OUT_OF_RANGE }

    fun readHeight(raw: String): Reading = read(raw, HEIGHT_CM)

    fun readWeight(raw: String): Reading = read(raw, WEIGHT_KG)

    /** "50 to 272": a range as a person says it, whole numbers without their ".0". */
    fun say(range: ClosedFloatingPointRange<Double>): String = "${plain(range.start)} to ${plain(range.endInclusive)}"

    private fun read(raw: String, range: ClosedFloatingPointRange<Double>): Reading {
        val value = DecimalInput.parse(raw) ?: return Reading.EMPTY
        return if (value in range) Reading.VALID else Reading.OUT_OF_RANGE
    }

    private fun plain(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
}
