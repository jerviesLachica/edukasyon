package com.edukasyon.studentai.core.util

import com.edukasyon.studentai.domain.model.GradeEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GpaCalculatorTest {

    private fun entry(subject: String, score: Double, max: Double = 100.0, weight: Double = 1.0) =
        GradeEntry(
            id = "$subject-$score",
            subjectId = subject,
            assessment = "Quiz",
            category = "Quiz",
            score = score,
            maxScore = max,
            weight = weight,
            term = "1st",
        )

    @Test
    fun percentToPh_mapsBoundaries() {
        assertEquals(1.00, GpaCalculator.percentToPhGpa(98.0), 0.0)
        assertEquals(1.75, GpaCalculator.percentToPhGpa(85.0), 0.0)
        assertEquals(3.00, GpaCalculator.percentToPhGpa(70.0), 0.0)
        assertEquals(5.00, GpaCalculator.percentToPhGpa(40.0), 0.0)
    }

    @Test
    fun cumulativeGpa_averagesSubjects_phScale() {
        // Math 95 -> 1.25, Science 80 -> 2.25 => mean 1.75
        val gpa = GpaCalculator.cumulativeGpa(
            listOf(entry("math", 95.0), entry("sci", 80.0)),
            GpaCalculator.Scale.PH_1_5,
        )
        assertNotNull(gpa)
        assertEquals(1.75, gpa!!, 0.001)
    }

    @Test
    fun forecastNeeded_computesRemainingAverage() {
        // PH scale is lower-is-better: target 1.75 with current 2.0 over weight 70,
        // remaining 30 => (175-140)/30 = 1.166..
        val needed = GpaCalculator.forecastNeeded(
            target = 1.75,
            currentAverage = 2.0,
            completedWeight = 70.0,
            remainingWeight = 30.0,
            scale = GpaCalculator.Scale.PH_1_5,
        )
        assertNotNull(needed)
        assertEquals(1.166, needed!!, 0.01)
    }

    @Test
    fun whatIf_appendsHypothetical() {
        val base = listOf(entry("math", 80.0))
        val result = GpaCalculator.whatIf(
            base,
            entry("math", 100.0),
            GpaCalculator.Scale.PERCENT,
        )
        assertNotNull(result)
        assertEquals(90.0, result!!, 0.001)
    }
}
