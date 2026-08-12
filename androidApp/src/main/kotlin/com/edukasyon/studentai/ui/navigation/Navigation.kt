package com.edukasyon.studentai.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.ui.graphics.vector.ImageVector

enum class MainTab(val route: String, val label: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    HOME("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
    SCHEDULE("schedule", "Schedule", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    PLANNER("planner", "Planner", Icons.Filled.TaskAlt, Icons.Outlined.TaskAlt),
    AI("ai", "AI", Icons.Filled.Psychology, Icons.Outlined.Psychology),
    PROFILE("profile", "Profile", Icons.Filled.Person, Icons.Outlined.Person)
}

object Routes {
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"
    const val ADD_TASK = "add_task"
    const val ADD_CLASS = "add_class"
    const val ADD_NOTE = "add_note"
    const val ADD_EXAM = "add_exam"
    const val ADD_ASSIGNMENT = "add_assignment"
    const val NOTE_DETAIL = "note_detail/{noteId}"
    const val AI_TUTOR = "ai_tutor"
    const val AI_SCANNER = "ai_scanner"
    const val AI_SUMMARIZER = "ai_summarizer"
    const val GRADES = "grades"
    const val CALENDAR = "calendar"
    const val SEARCH = "search"
    fun noteDetail(id: String) = "note_detail/$id"
}
