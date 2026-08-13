package com.edukasyon.studentai.core.notifications

enum class ReminderType(val channelId: String, val channelName: String) {
    CLASS("class_reminders", "Class Reminders"),
    TASK("task_reminders", "Task Reminders"),
    ASSIGNMENT("assignment_reminders", "Assignment Reminders"),
    EXAM("exam_reminders", "Exam Reminders")
}
