package com.edukasyon.studentai.core.util

import com.edukasyon.studentai.domain.model.QuestionType
import com.edukasyon.studentai.domain.model.Quiz
import com.edukasyon.studentai.domain.model.QuizQuestion

object QuizValidator {
    private val OPTION_PREFIX_REGEX = Regex("""^(?:Option\s+)?[A-Da-d][.)\-:]\s*""")

    fun isValidQuestion(question: QuizQuestion): Boolean =
        question.question.isNotBlank() &&
            question.options.size >= 2 &&
            question.correctAnswer.isNotBlank() &&
            question.options.any { option ->
                question.isAnswerCorrect(option) || option.isNotBlank()
            }

    /**
     * Randomizes the option order of multiple choice questions so the correct answer
     * is uniformly distributed across positions (A, B, C, D) instead of always being 'A'.
     * Resolves letter-based correct answers (e.g. "A", "Option A") to the full text before
     * shuffling and strips embedded option prefixes (e.g. "A) ", "B. ") from option text.
     */
    fun randomizeQuestion(question: QuizQuestion): QuizQuestion {
        val isTrueFalse = question.type == QuestionType.TRUE_FALSE ||
            (question.options.size == 2 &&
                question.options.any { it.equals("true", ignoreCase = true) } &&
                question.options.any { it.equals("false", ignoreCase = true) })

        if (isTrueFalse || question.options.size <= 1) {
            val resolvedCorrect = resolveCorrectAnswerText(question.options, question.correctAnswer)
            return question.copy(
                options = listOf("True", "False"),
                correctAnswer = if (resolvedCorrect.equals("true", ignoreCase = true)) "True" else "False"
            )
        }

        // 1. Resolve correct answer to actual text first
        val resolvedCorrect = resolveCorrectAnswerText(question.options, question.correctAnswer)

        // 2. Clean leading option prefixes like "A) ", "B. ", "Option C: " from options and correct answer
        val cleanOptions = question.options.map { it.replace(OPTION_PREFIX_REGEX, "").trim() }
        val cleanCorrect = resolvedCorrect.replace(OPTION_PREFIX_REGEX, "").trim()

        // 3. Shuffle options using Fisher-Yates / Kotlin shuffled()
        val shuffledOptions = cleanOptions.shuffled()

        // 4. Ensure cleanCorrect is in shuffledOptions (fallback to original option match if needed)
        val finalCorrect = shuffledOptions.firstOrNull { it.equals(cleanCorrect, ignoreCase = true) }
            ?: shuffledOptions.firstOrNull { it.equals(resolvedCorrect, ignoreCase = true) }
            ?: cleanCorrect

        return question.copy(
            options = shuffledOptions,
            correctAnswer = finalCorrect,
        )
    }

    private fun resolveCorrectAnswerText(options: List<String>, correctAnswer: String): String {
        val trimmed = correctAnswer.trim()
        val cleanTrimmed = trimmed.replace(OPTION_PREFIX_REGEX, "").trim()

        // Exact match (raw or cleaned)
        val directMatch = options.firstOrNull {
            it.equals(trimmed, ignoreCase = true) ||
                it.replace(OPTION_PREFIX_REGEX, "").trim().equals(cleanTrimmed, ignoreCase = true)
        }
        if (directMatch != null) return directMatch.replace(OPTION_PREFIX_REGEX, "").trim()

        // Check if correctAnswer is just a letter ("A", "b", etc.)
        val singleLetter = trimmed.uppercase().singleOrNull()
        if (singleLetter != null && singleLetter in 'A'..'Z') {
            val idx = singleLetter - 'A'
            if (idx in options.indices) {
                return options[idx].replace(OPTION_PREFIX_REGEX, "").trim()
            }
        }

        // Check if correctAnswer is "Option A", "Option B", "A.", "A)"
        val match = Regex("""^(?:Option\s+)?([A-D])[.):\s]?$""", RegexOption.IGNORE_CASE).find(trimmed)
        if (match != null) {
            val letter = match.groupValues[1].uppercase().first()
            val idx = letter - 'A'
            if (idx in options.indices) {
                return options[idx].replace(OPTION_PREFIX_REGEX, "").trim()
            }
        }

        return cleanTrimmed.ifBlank { trimmed }
    }

    fun validate(quiz: Quiz): Quiz {
        val validQuestions = quiz.questions
            .filter(::isValidQuestion)
            .map(::randomizeQuestion)
        require(validQuestions.isNotEmpty()) { "Quiz has no valid questions." }
        return quiz.copy(questions = validQuestions)
    }
}
