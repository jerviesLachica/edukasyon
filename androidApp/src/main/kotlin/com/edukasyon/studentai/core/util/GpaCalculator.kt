package com.edukasyon.studentai.core.util

import com.edukasyon.studentai.domain.model.GradeEntry

/**
 * GPA engine (offline, pure Kotlin, JVM-testable).
 *
 * Existing [GradeCalculator] reports 0-100% weighted percentages. This object
 * maps those percentages to academic scales and answers "what do I need?"
 * without any network or Room dependency.
 */
object GpaCalculator {

    enum class Scale { PH_1_5, US_4_0, PERCENT }

    /**
     * PH 1.00 (best) .. 5.00 (fail). Transmuted from weighted percent.
     * Boundaries: 97+ 1.00, 93+ 1.25, 89+ 1.50, 85+ 1.75, 82+ 2.00,
     * 79+ 2.25, 76+ 2.50, 73+ 2.75, 70+ 3.00, 65+ 4.00, else 5.00.
     */
    fun percentToPhGpa(percent: Double): Double = when {
        percent >= 97 -> 1.00
        percent >= 93 -> 1.25
        percent >= 89 -> 1.50
        percent >= 85 -> 1.75
        percent >= 82 -> 2.00
        percent >= 79 -> 2.25
        percent >= 76 -> 2.50
        percent >= 73 -> 2.75
        percent >= 70 -> 3.00
        percent >= 65 -> 4.00
        else -> 5.00
    }

    /** US 4.0 scale: 93+ 4.0, 90+ 3.7, 87+ 3.3, 83+ 3.0, 80+ 2.7, 77+ 2.3, 73+ 2.0, 70+ 1.7, 67+ 1.3, 65+ 1.0, else 0.0. */
    fun percentToUsGpa(percent: Double): Double = when {
        percent >= 93 -> 4.0
        percent >= 90 -> 3.7
        percent >= 87 -> 3.3
        percent >= 83 -> 3.0
        percent >= 80 -> 2.7
        percent >= 77 -> 2.3
        percent >= 73 -> 2.0
        percent >= 70 -> 1.7
        percent >= 67 -> 1.3
        percent >= 65 -> 1.0
        else -> 0.0
    }

    fun cumulativeGpa(entries: List<GradeEntry>, scale: Scale = Scale.PH_1_5): Double? {
        if (entries.isEmpty()) return null
        val bySubject = entries.groupBy { it.subjectId }
        val points = bySubject.values.map { subjectEntries ->
            val percent = GradeCalculator.calculateWeightedGrade(subjectEntries)
            when (scale) {
                Scale.PH_1_5 -> percentToPhGpa(percent)
                Scale.US_4_0 -> percentToUsGpa(percent)
                Scale.PERCENT -> percent
            }
        }
        if (points.isEmpty()) return null
        // PH is lower-is-better; keep a plain mean so a 1.25 average reads as 1.25.
        return points.average()
    }

    /**
     * Minimum average needed on remaining work to hit [target].
     * PH scale: result is a GPA point (lower is better) — clamp to [1.00, 5.00].
     * US/PERCENT: higher is better — clamp to [0.0, 4.0] / [0.0, 100.0].
     * Returns null when inputs are invalid.
     */
    fun forecastNeeded(
        target: Double,
        currentAverage: Double,
        completedWeight: Double,
        remainingWeight: Double,
        scale: Scale = Scale.PH_1_5,
    ): Double? {
        if (completedWeight < 0 || remainingWeight <= 0) return null
        val total = completedWeight + remainingWeight
        if (total <= 0) return null
        val needed = (target * total - currentAverage * completedWeight) / remainingWeight
        return when (scale) {
            Scale.PH_1_5 -> needed.coerceIn(1.00, 5.00)
            Scale.US_4_0 -> needed.coerceIn(0.0, 4.0)
            Scale.PERCENT -> needed.coerceIn(0.0, 100.0)
        }
    }

    /** What-if: append [hypothetical] to [entries] and recompute the cumulative value. */
    fun whatIf(entries: List<GradeEntry>, hypothetical: GradeEntry, scale: Scale = Scale.PH_1_5): Double? =
        cumulativeGpa(entries + hypothetical, scale)
}
