package com.ironvellum.app.domain

/** Feeds the Navy body-fat estimator; stored on the profile as its name. */
enum class Sex { MALE, FEMALE }

object BodyStats {

    fun bmi(weightKg: Double, heightCm: Double): Double? {
        if (weightKg <= 0.0 || heightCm <= 0.0) return null
        val m = heightCm / 100.0
        return round1(weightKg / (m * m))
    }

    fun bmiCategory(bmi: Double): String = when {
        bmi < 18.5 -> "Underweight"
        bmi < 25.0 -> "Healthy range"
        bmi < 30.0 -> "Overweight"
        else -> "Obese"
    }

    fun ffmi(weightKg: Double, heightCm: Double, bodyFatPct: Double): Double? {
        if (weightKg <= 0.0 || heightCm <= 0.0) return null
        if (bodyFatPct !in 3.0..60.0) return null
        val m = heightCm / 100.0
        val leanKg = weightKg * (1.0 - bodyFatPct / 100.0)
        return round1(leanKg / (m * m))
    }

    /** Kouri et al. 1995 norms (male). The one muscle metric that doesn't drift. */
    fun ffmiCategory(ffmi: Double): String = when {
        ffmi < 18.0 -> "Below average"
        ffmi < 20.0 -> "Average (active male)"
        ffmi < 22.0 -> "Above average (1-3 yrs training)"
        ffmi < 24.0 -> "Excellent (3-5 yrs training)"
        else -> "Approaching natural ceiling"
    }

    private fun round1(value: Double): Double = Math.round(value * 10.0) / 10.0

    /**
     * US Navy circumference method (Hodgdon & Beckett, 1984). All inputs in cm;
     * the hip circumference is only used for the FEMALE formula. Returns null —
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
        return round1(495.0 / factor - 450.0)
    }
}
