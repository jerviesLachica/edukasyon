package com.edukasyon.studentai.core.util

import com.edukasyon.studentai.domain.model.Quiz
import com.edukasyon.studentai.domain.model.QuizQuestion

object QuizValidator {
    fun isValidQuestion(question: QuizQuestion): Boolean =
        question.question.isNotBlank() &&
            question.options.size >= 2 &&
            question.correctAnswer.isNotBlank() &&
            question.options.any { option ->
                question.isAnswerCorrect(option) || option.isNotBlank()
            }

    fun validate(quiz: Quiz): Quiz {
        val validQuestions = quiz.questions.filter(::isValidQuestion)
        require(validQuestions.isNotEmpty()) { "Quiz has no valid questions." }
        return quiz.copy(questions = validQuestions)
    }
}
