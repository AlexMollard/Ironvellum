package com.ironvellum.app.domain

import androidx.annotation.StringRes
import com.ironvellum.app.R
import com.ironvellum.app.data.Words

/** Feeds the Navy body-fat estimator; stored on the profile as its name. */
enum class Sex(@StringRes private val labelRes: Int) {
    MALE(R.string.sex_male),
    FEMALE(R.string.sex_female),
    ;

    /** The word the profile pickers show. */
    val label: String get() = Words.text(UiText.res(labelRes))
}

object BodyStats {

    fun bmi(weightKg: Double, heightCm: Double): Double? {
        if (weightKg <= 0.0 || heightCm <= 0.0) return null
        val m = heightCm / 100.0
        return round1(weightKg / (m * m))
    }

    fun bmiCategory(bmi: Double): String = Bands.categoryOf(Bands.BMI, bmi)

    /** Raw fat-free mass index: lean kg over height squared. */
    fun ffmi(weightKg: Double, heightCm: Double, bodyFatPct: Double): Double? {
        if (weightKg <= 0.0 || heightCm <= 0.0) return null
        if (bodyFatPct !in 3.0..60.0) return null
        val m = heightCm / 100.0
        val leanKg = weightKg * (1.0 - bodyFatPct / 100.0)
        return round1(leanKg / (m * m))
    }

    /**
     * Height-normalised FFMI (Kouri et al. 1995): the raw index plus
     * 6.1 x (1.8 - height in metres), so a 160 cm and a 195 cm lifter are
     * scored on one scale instead of the tall one reading high.
     */
    fun ffmiNormalised(weightKg: Double, heightCm: Double, bodyFatPct: Double): Double? {
        val raw = ffmi(weightKg, heightCm, bodyFatPct) ?: return null
        return round1(raw + NORMALISE_PER_M * (1.8 - heightCm / 100.0))
    }

    private const val NORMALISE_PER_M = 6.1

    /** Category for a normalised FFMI on the sex-specific table. */
    fun ffmiCategory(ffmi: Double, sex: Sex = Sex.MALE): String = Bands.categoryOf(Bands.ffmi(sex), ffmi)

    private fun round1(value: Double): Double = Math.round(value * 10.0) / 10.0

    /**
     * US Navy circumference method (Hodgdon & Beckett, 1984). All inputs in cm;
     * the hip circumference is only used for the FEMALE formula. Rounded to a
     * whole percent, because that is the precision it has. Returns null —
     * never a guess — when any required input is missing or non-positive, or
     * when the log argument goes non-positive (e.g. waist <= neck for males).
     */
    fun estimateBodyFatNavy(
        sex: Sex,
        heightCm: Double,
        neckCm: Double,
        waistCm: Double,
        hipCm: Double?,
    ): Double? {
        if (heightCm <= 0.0 || neckCm <= 0.0 || waistCm <= 0.0) return null
        val factor = when (sex) {
            Sex.MALE -> {
                if (waistCm - neckCm <= 0.0) return null
                1.0324 - 0.19077 * Math.log10(waistCm - neckCm) + 0.15456 * Math.log10(heightCm)
            }
            Sex.FEMALE -> {
                val hip = hipCm ?: return null
                if (hip <= 0.0) return null
                if (waistCm + hip - neckCm <= 0.0) return null
                1.29579 - 0.35004 * Math.log10(waistCm + hip - neckCm) + 0.22100 * Math.log10(heightCm)
            }
        }
        if (factor <= 0.0) return null
        // Whole percent: the tape method is good to a few points, and a decimal
        // would be stored and shown as though it were measured.
        return Math.round(495.0 / factor - 450.0).toDouble()
    }
}

/** One row of a classification scale: everything below [upTo] and from the previous row's [upTo]. */
data class Band(val upTo: Double, val labelText: UiText, val tone: BandTone) {
    /** The band name on screen, in the current language. */
    val label: String get() = Words.text(labelText)
}

/** Semantic colour of a band; the UI maps these to theme tokens. */
enum class BandTone { LOW, OK, GOOD, STRONG, WARN, DANGER }

/** A scale and where its drawn bar ends (the last band is open). */
data class BandTable(val bands: List<Band>, val scaleMax: Double, val noteText: UiText? = null) {
    /** The footnote on screen, in the current language. */
    val note: String? get() = noteText?.let { Words.text(it) }
}

/**
 * The one set of bands. The tiles, the category text and the drill-down
 * dialog all read from here, so a threshold cannot differ between them.
 */
object Bands {
    /** WHO adult BMI classes. */
    val BMI = BandTable(
        listOf(
            Band(18.5, UiText.res(R.string.bands_bmi_underweight), BandTone.LOW),
            Band(25.0, UiText.res(R.string.bands_bmi_healthy), BandTone.GOOD),
            Band(30.0, UiText.res(R.string.bands_bmi_overweight), BandTone.WARN),
            Band(Double.POSITIVE_INFINITY, UiText.res(R.string.bands_bmi_obese), BandTone.DANGER),
        ),
        scaleMax = 40.0,
    )

    private val FFMI_MALE = BandTable(
        listOf(
            Band(18.0, UiText.res(R.string.bands_ffmi_below_average), BandTone.LOW),
            Band(20.0, UiText.res(R.string.bands_ffmi_average_male), BandTone.OK),
            Band(22.0, UiText.res(R.string.bands_ffmi_above_average), BandTone.GOOD),
            Band(24.0, UiText.res(R.string.bands_ffmi_excellent), BandTone.STRONG),
            Band(Double.POSITIVE_INFINITY, UiText.res(R.string.bands_ffmi_ceiling), BandTone.WARN),
        ),
        scaleMax = 26.0,
        noteText = UiText.res(R.string.bands_ffmi_note_male),
    )

    /**
     * No published training-status table for women exists in the sources we
     * can verify, so the female bands are the male ones shifted by the gap
     * between the male and female medians of Schutz 2002 (18.9 vs 15.4 kg/m2,
     * ages 18-34): 3.5. A scaled estimate, and the screen says so.
     */
    private const val FEMALE_SHIFT = 3.5

    private val FFMI_FEMALE = BandTable(
        FFMI_MALE.bands.map { b ->
            Band(
                b.upTo - FEMALE_SHIFT,
                if (b.tone == BandTone.OK) UiText.res(R.string.bands_ffmi_average_female) else b.labelText,
                b.tone,
            )
        },
        scaleMax = FFMI_MALE.scaleMax - FEMALE_SHIFT,
        noteText = UiText.res(R.string.bands_ffmi_note_female),
    )

    fun ffmi(sex: Sex): BandTable = if (sex == Sex.FEMALE) FFMI_FEMALE else FFMI_MALE

    fun categoryOf(table: BandTable, value: Double): String = bandOf(table, value).label

    fun bandOf(table: BandTable, value: Double): Band =
        table.bands.firstOrNull { value < it.upTo } ?: table.bands.last()

    /** "below 18.5", "18.5 to under 25.0", "40.0 and over" - one decimal, no rounding surprises. */
    fun rangeText(table: BandTable, index: Int): String {
        val bands = table.bands
        fun f(v: Double) = String.format(java.util.Locale.US, "%.1f", v)
        return Words.text(
            when {
                index == 0 -> UiText.res(R.string.bands_range_below, f(bands[0].upTo))
                index == bands.lastIndex -> UiText.res(R.string.bands_range_over, f(bands[index - 1].upTo))
                else -> UiText.res(R.string.bands_range_between, f(bands[index - 1].upTo), f(bands[index].upTo))
            },
        )
    }
}
