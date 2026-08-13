package com.edukasyon.studentai.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AssignmentBreakdown(
    val title: String,
    val deadline: String?,
    val requirements: List<String>,
    val deliverables: List<String>,
    val rubric: List<String>,
    val subtasks: List<AssignmentSubtaskBreakdown>,
    val estimatedEffortHours: Double,
    val notes: String,
)

@Serializable
data class AssignmentSubtaskBreakdown(
    val title: String,
    val estimatedMinutes: Int,
    val dueOffsetDays: Int,
)

data class AssignmentAnalysisInput(
    val text: String? = null,
    val attachmentText: String? = null,
    val imageBase64: String? = null,
)
