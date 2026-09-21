package com.edukasyon.studentai.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.edukasyon.studentai.domain.model.GizmoConstants
import com.edukasyon.studentai.domain.model.QuizQuestion
import com.edukasyon.studentai.ui.components.mascot.MascotMood
import com.edukasyon.studentai.ui.components.mascot.SchedMateMascot
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
        Text(session.quiz.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

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
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(question.question, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
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
    val mascotMood = when {
        session.scorePercent >= 80 -> MascotMood.Submitting
        session.scorePercent >= 50 -> MascotMood.Motivated
        else -> MascotMood.Learning
    }
    val mascotSpeech = when {
        session.scorePercent >= 80 -> "Awesome job! You've mastered this topic! 🌟"
        session.scorePercent >= 50 -> "Solid effort! A quick review of weak topics will get you to 100%! 🚀"
        else -> "Good practice! Reviewing your mistakes is how memory sticks! 💡"
    }

    StudentAiCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SchedMateMascot(
                mood = mascotMood,
                size = 120.dp,
                customSpeechText = mascotSpeech,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Quiz Complete! 🎉", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            BouncyIconButton(onClick = onCopyResults) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy quiz results")
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Score: ${session.correctCount}/${session.totalQuestions} (${session.scorePercent}%)",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        LinearProgressIndicator(
            progress = { session.scorePercent / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .height(8.dp),
            strokeCap = StrokeCap.Round,
        )
        if (session.wrongAnswers.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Areas to review:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            session.weakTopics.forEach { topic ->
                Text("• $topic", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
    val haptic = LocalHapticFeedback.current
    val labels = listOf("A", "B", "C", "D")
    question.options.forEachIndexed { index, option ->
        val selected = session.selectedAnswer == option
        val isCorrect = session.revealed && question.isAnswerCorrect(option)
        val isWrong = session.revealed && selected && !question.isAnswerCorrect(option)
        val targetContainer = when {
            isCorrect -> MaterialTheme.colorScheme.primaryContainer
            isWrong -> MaterialTheme.colorScheme.errorContainer
            selected -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
            else -> MaterialTheme.colorScheme.surface
        }
        val targetBorder = when {
            isCorrect -> MaterialTheme.colorScheme.primary
            isWrong -> MaterialTheme.colorScheme.error
            selected -> MaterialTheme.colorScheme.secondary
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        }
        val containerColor by animateColorAsState(targetContainer, animationSpec = tween(300), label = "optionContainer")
        val borderColor by animateColorAsState(targetBorder, animationSpec = tween(300), label = "optionBorder")
        val prefix = labels.getOrElse(index) { "${index + 1}" }

        OutlinedCard(
            onClick = {
                if (!session.revealed) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelectAnswer(option)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
            border = BorderStroke(if (selected || isCorrect || isWrong) 1.5.dp else 1.dp, borderColor),
            shape = RoundedCornerShape(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = when {
                        isCorrect -> MaterialTheme.colorScheme.primary
                        isWrong -> MaterialTheme.colorScheme.error
                        selected -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = prefix,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isCorrect -> MaterialTheme.colorScheme.onPrimary
                                isWrong -> MaterialTheme.colorScheme.onError
                                selected -> MaterialTheme.colorScheme.onSecondary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                Text(
                    text = option,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                if (isCorrect) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Correct",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                } else if (isWrong) {
                    Icon(
                        Icons.Default.Cancel,
                        contentDescription = "Incorrect",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
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
            fontWeight = FontWeight.SemiBold,
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
        BouncyButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onNext()
            },
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(
                if (session.currentIndex + 1 >= session.totalQuestions) "See Results"
                else "Next Question",
            )
        }
    } else {
        BouncyButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onReveal()
            },
            enabled = session.selectedAnswer != null,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text("Check Answer")
        }
    }
}
