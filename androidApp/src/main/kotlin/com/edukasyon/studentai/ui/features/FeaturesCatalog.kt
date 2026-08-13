package com.edukasyon.studentai.ui.features

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.edukasyon.studentai.ui.navigation.MainTab
import com.edukasyon.studentai.ui.navigation.Routes

enum class FeatureFilterCategory(val chipLabel: String) {
    SCHEDULE("Schedule"),
    AI("AI"),
    STUDY("Study"),
    SETTINGS("Settings")
}

enum class FeatureSection(val title: String) {
    SCHEDULE_PLANNING("Schedule & Planning"),
    AI_TOOLS("AI Study Tools"),
    PERSONALIZATION("Personalization"),
    DATA_SYNC("Data & Sync"),
    WIDGETS_EXTRAS("Widgets & Extras")
}

sealed class FeatureDestination {
    data class Tab(val tab: MainTab) : FeatureDestination()
    data class Route(val route: String) : FeatureDestination()
    data object WidgetInstructions : FeatureDestination()
}

data class FeatureItem(
    val id: String,
    val title: String,
    val description: String,
    val filterCategory: FeatureFilterCategory,
    val section: FeatureSection,
    val icon: ImageVector,
    val destination: FeatureDestination,
    val isNew: Boolean = false
)

object FeaturesCatalog {
    val all: List<FeatureItem> = listOf(
        // Core tabs
        FeatureItem(
            id = "home",
            title = "Home Dashboard",
            description = "Your daily overview with stats, next class, upcoming tasks, exams, and quick actions.",
            filterCategory = FeatureFilterCategory.STUDY,
            section = FeatureSection.SCHEDULE_PLANNING,
            icon = Icons.Default.Home,
            destination = FeatureDestination.Tab(MainTab.HOME)
        ),
        FeatureItem(
            id = "schedule",
            title = "Class Schedule",
            description = "View and manage your weekly class timetable. Add, edit, or delete classes by day.",
            filterCategory = FeatureFilterCategory.SCHEDULE,
            section = FeatureSection.SCHEDULE_PLANNING,
            icon = Icons.Default.CalendarMonth,
            destination = FeatureDestination.Tab(MainTab.SCHEDULE)
        ),
        FeatureItem(
            id = "schedule_weekly",
            title = "Weekly Schedule View",
            description = "See your full week at a glance. Switch to Weekly mode on the Schedule tab.",
            filterCategory = FeatureFilterCategory.SCHEDULE,
            section = FeatureSection.SCHEDULE_PLANNING,
            icon = Icons.Default.ViewWeek,
            destination = FeatureDestination.Tab(MainTab.SCHEDULE)
        ),
        FeatureItem(
            id = "schedule_monthly",
            title = "Monthly Schedule View",
            description = "Browse your schedule month by month. Tap Monthly on the Schedule tab filter chips.",
            filterCategory = FeatureFilterCategory.SCHEDULE,
            section = FeatureSection.SCHEDULE_PLANNING,
            icon = Icons.Default.DateRange,
            destination = FeatureDestination.Tab(MainTab.SCHEDULE)
        ),
        FeatureItem(
            id = "schedule_colors",
            title = "Day Color Customization",
            description = "Personalize each day of the week with custom colors in your schedule grid.",
            filterCategory = FeatureFilterCategory.SCHEDULE,
            section = FeatureSection.SCHEDULE_PLANNING,
            icon = Icons.Default.Palette,
            destination = FeatureDestination.Tab(MainTab.SCHEDULE)
        ),
        FeatureItem(
            id = "planner",
            title = "Planner",
            description = "Organize tasks, assignments, and exams in one place with tabs for each type.",
            filterCategory = FeatureFilterCategory.SCHEDULE,
            section = FeatureSection.SCHEDULE_PLANNING,
            icon = Icons.Default.TaskAlt,
            destination = FeatureDestination.Tab(MainTab.PLANNER)
        ),
        FeatureItem(
            id = "tasks",
            title = "Tasks & Subtasks",
            description = "Create to-do items with priorities and due dates. Mark complete or add subtasks.",
            filterCategory = FeatureFilterCategory.STUDY,
            section = FeatureSection.SCHEDULE_PLANNING,
            icon = Icons.Default.CheckCircle,
            destination = FeatureDestination.Tab(MainTab.PLANNER)
        ),
        FeatureItem(
            id = "assignments",
            title = "Assignments",
            description = "Track homework and project deadlines. Open the Assignments tab in Planner.",
            filterCategory = FeatureFilterCategory.STUDY,
            section = FeatureSection.SCHEDULE_PLANNING,
            icon = Icons.Default.Assignment,
            destination = FeatureDestination.Tab(MainTab.PLANNER)
        ),
        FeatureItem(
            id = "exams",
            title = "Exams",
            description = "Keep exam dates and subjects organized. Manage them from the Exams tab in Planner.",
            filterCategory = FeatureFilterCategory.STUDY,
            section = FeatureSection.SCHEDULE_PLANNING,
            icon = Icons.Default.School,
            destination = FeatureDestination.Tab(MainTab.PLANNER)
        ),
        FeatureItem(
            id = "grades",
            title = "Grades Tracker",
            description = "Log scores by category and see your weighted grade average at a glance.",
            filterCategory = FeatureFilterCategory.STUDY,
            section = FeatureSection.SCHEDULE_PLANNING,
            icon = Icons.Default.Grade,
            destination = FeatureDestination.Route(Routes.GRADES)
        ),
        FeatureItem(
            id = "calendar",
            title = "Calendar Events",
            description = "View all tasks, exams, and assignments on a unified calendar timeline.",
            filterCategory = FeatureFilterCategory.SCHEDULE,
            section = FeatureSection.SCHEDULE_PLANNING,
            icon = Icons.Default.Event,
            destination = FeatureDestination.Route(Routes.CALENDAR)
        ),
        FeatureItem(
            id = "holidays",
            title = "Philippine Holidays",
            description = "See official Philippine holidays alongside your events in the Calendar screen.",
            filterCategory = FeatureFilterCategory.SCHEDULE,
            section = FeatureSection.SCHEDULE_PLANNING,
            icon = Icons.Default.Celebration,
            destination = FeatureDestination.Route(Routes.CALENDAR)
        ),
        FeatureItem(
            id = "notes",
            title = "Notes",
            description = "Capture study notes with search. Create, edit, and organize your written materials.",
            filterCategory = FeatureFilterCategory.STUDY,
            section = FeatureSection.SCHEDULE_PLANNING,
            icon = Icons.Default.Note,
            destination = FeatureDestination.Route(Routes.NOTES)
        ),

        // AI tools
        FeatureItem(
            id = "ai_hub",
            title = "Gizmo AI Hub",
            description = "Your AI study buddy with hearts, XP, streaks, tutor chat, tools, and scanner.",
            filterCategory = FeatureFilterCategory.AI,
            section = FeatureSection.AI_TOOLS,
            icon = Icons.Default.Psychology,
            destination = FeatureDestination.Tab(MainTab.AI),
            isNew = true
        ),
        FeatureItem(
            id = "gizmo_hearts",
            title = "Hearts & Memorise Mode",
            description = "Quiz with 15 hearts like Gizmo — wrong answers cost a heart. Super Hearts protect you!",
            filterCategory = FeatureFilterCategory.AI,
            section = FeatureSection.AI_TOOLS,
            icon = Icons.Default.Favorite,
            destination = FeatureDestination.Tab(MainTab.AI),
            isNew = true
        ),
        FeatureItem(
            id = "gizmo_xp",
            title = "XP & Study Streaks",
            description = "Earn XP from chatting, quizzes, and flashcards. Build daily streaks to level up Gizmo.",
            filterCategory = FeatureFilterCategory.AI,
            section = FeatureSection.AI_TOOLS,
            icon = Icons.Default.Star,
            destination = FeatureDestination.Tab(MainTab.AI),
            isNew = true
        ),
        FeatureItem(
            id = "ai_tutor",
            title = "Gizmo AI Tutor",
            description = "Chat with Gizmo — ask questions, get explanations, and use quick study prompts.",
            filterCategory = FeatureFilterCategory.AI,
            section = FeatureSection.AI_TOOLS,
            icon = Icons.Default.Chat,
            destination = FeatureDestination.Tab(MainTab.AI)
        ),
        FeatureItem(
            id = "ai_summarizer",
            title = "AI Summarizer",
            description = "Paste long text and get concise summaries. Find it under the Tools tab in AI.",
            filterCategory = FeatureFilterCategory.AI,
            section = FeatureSection.AI_TOOLS,
            icon = Icons.Default.Summarize,
            destination = FeatureDestination.Tab(MainTab.AI)
        ),
        FeatureItem(
            id = "ai_flashcards",
            title = "AI Flashcards",
            description = "Generate flashcards from your notes or text, then study them with spaced repetition.",
            filterCategory = FeatureFilterCategory.AI,
            section = FeatureSection.AI_TOOLS,
            icon = Icons.Default.Style,
            destination = FeatureDestination.Tab(MainTab.AI)
        ),
        FeatureItem(
            id = "flashcard_study",
            title = "Flashcard Study Mode",
            description = "Review generated flashcards with SM-2 spaced repetition for better retention.",
            filterCategory = FeatureFilterCategory.STUDY,
            section = FeatureSection.AI_TOOLS,
            icon = Icons.Default.AutoStories,
            destination = FeatureDestination.Route(Routes.FLASHCARD_STUDY)
        ),
        FeatureItem(
            id = "ai_quiz",
            title = "AI Quiz Generator",
            description = "Turn your study material into practice quizzes. Available in the AI Tools tab.",
            filterCategory = FeatureFilterCategory.AI,
            section = FeatureSection.AI_TOOLS,
            icon = Icons.Default.Quiz,
            destination = FeatureDestination.Tab(MainTab.AI)
        ),
        FeatureItem(
            id = "schedule_scanner",
            title = "Camera Schedule Scanner",
            description = "Photograph your printed schedule and let AI extract classes automatically.",
            filterCategory = FeatureFilterCategory.AI,
            section = FeatureSection.AI_TOOLS,
            icon = Icons.Default.CameraAlt,
            destination = FeatureDestination.Route(Routes.SCHEDULE_SCANNER),
            isNew = true
        ),
        FeatureItem(
            id = "study_groups",
            title = "Study Groups & Chat",
            description = "Collaborate with classmates in group or direct chats for shared study sessions.",
            filterCategory = FeatureFilterCategory.STUDY,
            section = FeatureSection.AI_TOOLS,
            icon = Icons.Default.Groups,
            destination = FeatureDestination.Route(Routes.CHAT_LIST)
        ),

        // Personalization
        FeatureItem(
            id = "profile",
            title = "Profile & Settings",
            description = "Manage your account, preferences, and app configuration from the Profile tab.",
            filterCategory = FeatureFilterCategory.SETTINGS,
            section = FeatureSection.PERSONALIZATION,
            icon = Icons.Default.Person,
            destination = FeatureDestination.Tab(MainTab.PROFILE)
        ),
        FeatureItem(
            id = "theme",
            title = "Theme & Appearance",
            description = "Switch light/dark/system themes and customize primary and accent colors.",
            filterCategory = FeatureFilterCategory.SETTINGS,
            section = FeatureSection.PERSONALIZATION,
            icon = Icons.Default.DarkMode,
            destination = FeatureDestination.Tab(MainTab.PROFILE)
        ),
        FeatureItem(
            id = "notifications",
            title = "Notifications & Reminders",
            description = "Enable class, task, and exam reminders so you never miss a deadline.",
            filterCategory = FeatureFilterCategory.SETTINGS,
            section = FeatureSection.PERSONALIZATION,
            icon = Icons.Default.Notifications,
            destination = FeatureDestination.Route(Routes.NOTIFICATION_SETTINGS)
        ),
        FeatureItem(
            id = "lecture_files",
            title = "Lecture Files",
            description = "Organize PDFs, slides, and photos by subject. Stored locally on your device.",
            filterCategory = FeatureFilterCategory.STUDY,
            section = FeatureSection.SCHEDULE_PLANNING,
            icon = Icons.Default.FolderCopy,
            destination = FeatureDestination.Route(Routes.LECTURE_FILES),
            isNew = true
        ),

        // Data & sync
        FeatureItem(
            id = "export_import",
            title = "Data Export & Import",
            description = "Back up your data as JSON or export schedule and grades as CSV. Restore from backup.",
            filterCategory = FeatureFilterCategory.SETTINGS,
            section = FeatureSection.DATA_SYNC,
            icon = Icons.Default.CloudUpload,
            destination = FeatureDestination.Tab(MainTab.PROFILE)
        ),

        // Widgets
        FeatureItem(
            id = "widgets",
            title = "Home Screen Widgets",
            description = "Pin 2×2 or 2×3 widgets showing today's schedule or upcoming tasks on your home screen.",
            filterCategory = FeatureFilterCategory.SETTINGS,
            section = FeatureSection.WIDGETS_EXTRAS,
            icon = Icons.Default.Widgets,
            destination = FeatureDestination.WidgetInstructions,
            isNew = true
        )
    )

    val sections: List<FeatureSection> = FeatureSection.entries

    /** Key features surfaced on the home dashboard for discoverability. */
    val homeDashboardTiles: List<FeatureItem> = listOf(
        "schedule_scanner",
        "lecture_files",
        "ai_tutor",
        "calendar",
        "grades",
        "flashcard_study",
        "study_groups",
    ).mapNotNull { id -> all.find { it.id == id } }
}
