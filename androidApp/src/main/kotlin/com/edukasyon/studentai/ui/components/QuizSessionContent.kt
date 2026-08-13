package com.edukasyon.studentai.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.edukasyon.studentai.domain.model.GizmoConstants
import com.edukasyon.studentai.domain.model.QuizQuestion
import com.edukasyon.studentai.ui.viewmodel.QuizSessionState

@Composable
fun QuizSessionContent(
    session: QuizSessionState,
    quizSaved: Boolean,
    onSelectAnswer: (String) -> Unit,
    onReveal: () -> Unit,
    onNext: () -> Unit,
    onRestart: () -> Unit,
    onSave: () -> Unit,
    onReviewMistakes: (() -> Unit)? = null,
    onCopyResults: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(session.quiz.title, style = MaterialTheme.typography.titleMedium)

        if (session.finished) {
            QuizResultsCard(
                session = session,
                quizSaved = quizSaved,
                onRestart = onRestart,
                onSave = onSave,
                onReviewMistakes = onReviewMistakes,
                onCopyResults = onCopyResults,
            )
            return
        }

        val question = session.currentQuestion ?: return
        StudentAiCard {
            Text(
                "Question ${session.currentIndex + 1} of ${session.totalQuestions}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(question.question, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            QuizAnswerOptions(
                question = question,
                session = session,
                onSelectAnswer = onSelectAnswer,
                onReveal = onReveal,
                onNext = onNext,
            )
        }
    }
}

@Composable
private fun QuizResultsCard(
    session: QuizSessionState,
    quizSaved: Boolean,
    onRestart: () -> Unit,
    onSave: () -> Unit,
    onReviewMistakes: (() -> Unit)?,
    onCopyResults: () -> Unit,
) {
    StudentAiCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Quiz Complete! 🎉", style = MaterialTheme.typography.titleSmall)
            BouncyIconButton(onClick = onCopyResults) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy quiz results")
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Score: ${session.correctCount}/${session.totalQuestions} (${session.scorePercent}%)")
        if (session.wrongAnswers.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Areas to review:", style = MaterialTheme.typography.labelMedium)
            session.weakTopics.forEach { topic ->
                Text("• $topic", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BouncyOutlinedButton(onClick = onRestart) { Text("Retry") }
            if (session.wrongAnswers.isNotEmpty() && onReviewMistakes != null) {
                BouncyOutlinedButton(onClick = onReviewMistakes) { Text("Review Mistakes") }
            }
            if (!quizSaved) {
                BouncyButton(onClick = onSave) { Text("Save Quiz") }
            } else {
                AssistChip(
                    onClick = {},
                    label = { Text("Quiz saved") },
                    leadingIcon = {
                        Icon(Icons.Default.Check, contentDescription = null, Modifier.size(16.dp))
                    },
                )
            }
        }
    }
}

@Composable
fun QuizAnswerOptions(
    question: QuizQuestion,
    session: QuizSessionState,
    onSelectAnswer: (String) -> Unit,
    onReveal: () -> Unit,
    onNext: () -> Unit,
) {
    val labels = listOf("A", "B", "C", "D")
    question.options.forEachIndexed { index, option ->
        val selected = session.selectedAnswer == option
        val isCorrect = session.revealed && question.isAnswerCorrect(option)
        val isWrong = session.revealed && selected && !question.isAnswerCorrect(option)
        val containerColor = when {
            isCorrect -> MaterialTheme.colorScheme.primaryContainer
            isWrong -> MaterialTheme.colorScheme.errorContainer
            selected -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surface
        }
        val prefix = labels.getOrElse(index) { "${index + 1}" }
        OutlinedCard(
            onClick = { if (!session.revealed) onSelectAnswer(option) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
        ) {
            Text("$prefix. $option", Modifier.padding(12.dp))
        }
    }

    if (session.revealed) {
        val isCorrect = session.selectedAnswer?.let { question.isAnswerCorrect(it) } == true
        val feedback = if (isCorrect) {
            "Correct! +${GizmoConstants.XP_CORRECT_ANSWER} XP 🎉"
        } else {
            "Wrong answer"
        }
        Text(
            feedback,
            color = if (isCorrect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (!isCorrect) {
            val correctLabel = question.options.firstOrNull { question.isAnswerCorrect(it) } ?: question.correctAnswer
            Text(
                "Correct answer: $correctLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        BouncyButton(onClick = onNext, modifier = Modifier.padding(top = 8.dp)) {
            Text(
                if (session.currentIndex + 1 >= session.totalQuestions) "See Results"
                else "Next Question",
            )
        }
    } else {
        BouncyButton(
            onClick = onReveal,
            enabled = session.selectedAnswer != null,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text("Check Answer")
        }
    }
}
