package com.monarch.app.domain

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
}
