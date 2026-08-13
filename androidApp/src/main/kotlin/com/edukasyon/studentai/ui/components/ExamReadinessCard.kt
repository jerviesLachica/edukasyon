package com.edukasyon.studentai.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.edukasyon.studentai.core.util.DateUtils
import com.edukasyon.studentai.domain.model.EXAM_READINESS_DISCLAIMER
import com.edukasyon.studentai.domain.model.Exam
import com.edukasyon.studentai.domain.model.ExamReadiness
import com.edukasyon.studentai.domain.model.ExamReadinessStatus
import com.edukasyon.studentai.domain.model.TopicStrength

@Composable
fun ExamReadinessCard(
    exam: Exam,
    readiness: ExamReadiness?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    expanded: Boolean = false,
    onLinkStudy: (() -> Unit)? = null,
) {
    ModernCard(
        modifier = modifier.padding(horizontal = if (compact) 0.dp else 20.dp, vertical = 5.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = exam.title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = DateUtils.formatCountdown(exam.examDate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when (readiness?.status) {
                ExamReadinessStatus.READY -> {
                    val percent = readiness.readinessPercent ?: 0
                    Text(
                        text = "Readiness: $percent%",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    ReadinessBlockBar(percent = percent)
                    if (!compact || expanded) {
                        ReadinessTopicSection(
                            label = "Strong",
                            icon = "✓",
                            topics = readiness.strongTopics,
                            tint = Color(0xFF43A047),
                        )
                        ReadinessTopicSection(
                            label = "Moderate",
                            icon = "△",
                            topics = readiness.moderateTopics,
                            tint = Color(0xFFFB8C00),
                        )
                        ReadinessTopicSection(
                            label = "Weak",
                            icon = "⚠",
                            topics = readiness.weakTopics,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        readiness.recommendations?.let { rec ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Recommended:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text("${rec.cardsToReview} cards", style = MaterialTheme.typography.bodySmall)
                                Text("${rec.quizQuestionCount}-question quiz", style = MaterialTheme.typography.bodySmall)
                                Text("${rec.reviewMinutes}-minute review", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        Text(
                            text = "${readiness.dueCards} cards due · tap for details",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                ExamReadinessStatus.EMPTY_DECK -> {
                    Text(
                        text = "Start studying",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Linked deck has no cards yet. Add flashcards in JEVI to track readiness.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    Text(
                        text = "Connect JEVI study data",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Link a subject and deck to see your study progress estimate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    onLinkStudy?.let { onClick ->
                        TextButton(onClick = onClick) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                androidx.compose.material3.Icon(
                                    Icons.Default.Link,
                                    contentDescription = null,
                                    modifier = Modifier.height(16.dp),
                                )
                                Text("Link subject & deck")
                            }
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                androidx.compose.material3.Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.height(12.dp),
                )
                Text(
                    text = EXAM_READINESS_DISCLAIMER,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReadinessBlockBar(percent: Int) {
    val filled = (percent / 10).coerceIn(0, 10)
    val bar = buildString {
        repeat(filled) { append('█') }
        repeat(10 - filled) { append('░') }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = bar,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        LinearProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }
}

@Composable
private fun ReadinessTopicSection(
    label: String,
    icon: String,
    topics: List<TopicStrength>,
    tint: Color,
) {
    if (topics.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = tint,
        )
        topics.forEach { topic ->
            Text(
                text = "$icon ${topic.name}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
