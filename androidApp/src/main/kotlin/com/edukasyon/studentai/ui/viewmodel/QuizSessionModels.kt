package com.edukasyon.studentai.ui.viewmodel

import com.edukasyon.studentai.domain.model.Quiz
import com.edukasyon.studentai.domain.model.QuizQuestion

data class QuizWrongAnswer(
    val question: QuizQuestion,
    val selectedAnswer: String,
)

data class QuizSessionState(
    val quiz: Quiz,
    val currentIndex: Int = 0,
    val selectedAnswer: String? = null,
    val revealed: Boolean = false,
    val correctCount: Int = 0,
    val finished: Boolean = false,
    val wrongAnswers: List<QuizWrongAnswer> = emptyList(),
) {
    val currentQuestion: QuizQuestion? get() = quiz.questions.getOrNull(currentIndex)
    val totalQuestions: Int get() = quiz.questions.size
    val scorePercent: Int get() = if (totalQuestions == 0) 0 else (correctCount * 100) / totalQuestions

    val weakTopics: List<String>
        get() = wrongAnswers
            .mapNotNull { it.question.question.take(48).trim().takeIf(String::isNotEmpty) }
            .distinct()
            .take(5)
}
