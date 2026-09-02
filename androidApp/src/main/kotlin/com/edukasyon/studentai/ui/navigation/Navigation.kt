package com.edukasyon.studentai.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
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
    JEVI("jevi", "JEVI", Icons.Filled.Psychology, Icons.Outlined.Psychology),
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
    const val NOTE_EDITOR = "note_editor/{noteId}"
    const val NEW_NOTE_ID = "new"
    const val AI_TUTOR = "ai_tutor"
    const val AI_SCANNER = "ai_scanner"
    const val SCHEDULE_SCANNER = "schedule_scanner"
    const val FLASHCARD_STUDY = "flashcard_study"
    const val JEVI_DECKS = "jevi_decks"
    const val JEVI_CREATE = "jevi_create"
    const val JEVI_TUTOR = "jevi_tutor"
    const val JEVI_REVIEW = "jevi_review"
    const val JEVI_QUIZ = "jevi_quiz"
    const val JEVI_DECK_DETAIL = "jevi_deck/{deckId}"
    const val JEVI_REVIEW_DECK = "jevi_review/{deckId}?studyAll={studyAll}"
    fun jeviDeckDetail(deckId: String) = "jevi_deck/$deckId"
    fun jeviReviewDeck(deckId: String, studyAll: Boolean = false) =
        "jevi_review/$deckId?studyAll=$studyAll"
    const val AI_SUMMARIZER = "ai_summarizer"
    const val GRADES = "grades"
    const val CALENDAR = "calendar"
    const val NOTES = "notes"
    const val FEATURES_GUIDE = "features-guide"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
    const val NOTIFICATION_SETTINGS = "notification_settings"
    const val NOTIFICATION_SETTINGS_DETAIL = "notification_settings_detail"
    const val LECTURE_FILES = "lecture_files"
    const val FOCUS = "focus"
    const val ASSIGNMENT_INTELLIGENCE = "assignment_intelligence"
    const val CHANGELOG = "changelog"
    const val AI_CONVERSATION_HISTORY = "ai_conversation_history/{filterScope}"
    fun aiConversationHistory(filterScope: String) = "ai_conversation_history/$filterScope"
    fun noteDetail(id: String) = "note_detail/$id"
    fun noteEditor(noteId: String) = "note_editor/$noteId"
}

fun routeToSelectedTab(route: String?): MainTab? {
    if (route == null) return null
    return when {
        route == Routes.NOTES || route.startsWith("note_editor/") -> MainTab.HOME
        route == Routes.GRADES || route == Routes.CALENDAR || route == Routes.FOCUS -> MainTab.PLANNER
        route == Routes.SCHEDULE_SCANNER -> MainTab.SCHEDULE
        route == Routes.FLASHCARD_STUDY || route == Routes.JEVI_REVIEW ||
            route.startsWith("jevi_review/") || route.startsWith("jevi_deck/") ||
            route == Routes.JEVI_DECKS ||
            route == Routes.JEVI_CREATE || route == Routes.JEVI_TUTOR ||
            route == Routes.JEVI_QUIZ -> MainTab.JEVI
        route.startsWith("ai_conversation_history/") -> MainTab.JEVI
        route == Routes.FEATURES_GUIDE -> MainTab.PROFILE
        route == Routes.SETTINGS -> MainTab.PROFILE
        route == Routes.NOTIFICATION_SETTINGS || route == Routes.NOTIFICATION_SETTINGS_DETAIL -> MainTab.PROFILE
        route == Routes.CHANGELOG -> MainTab.PROFILE
        route == Routes.LECTURE_FILES || route == Routes.NOTES -> MainTab.HOME
        route == Routes.ASSIGNMENT_INTELLIGENCE -> MainTab.PLANNER
        else -> MainTab.entries.find { it.route == route }
    }
}

fun NavController.navigateToTab(tab: MainTab, resetHomeRoot: Boolean = false) {
    if (tab == MainTab.HOME && resetHomeRoot) {
        navigate(MainTab.HOME.route) {
            popUpTo(MainTab.HOME.route) { inclusive = true }
            launchSingleTop = true
        }
    } else {
        navigate(tab.route) {
            popUpTo(graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
}

private const val TAB_ENTER_MS = 320
private const val TAB_EXIT_MS = 260
private val mainTabRouteSet = MainTab.entries.map { it.route }.toSet()

private fun AnimatedContentTransitionScope<NavBackStackEntry>.mainTabIndices(): Pair<Int, Int>? {
    val from = initialState.destination.route ?: return null
    val to = targetState.destination.route ?: return null
    if (from !in mainTabRouteSet || to !in mainTabRouteSet) return null
    val fromIndex = MainTab.entries.indexOfFirst { it.route == from }
    val toIndex = MainTab.entries.indexOfFirst { it.route == to }
    if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) return null
    return fromIndex to toIndex
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabEnterTransition(): EnterTransition {
    val indices = mainTabIndices()
    val slideOffset: (Int) -> Int = if (indices != null) {
        val (from, to) = indices
        if (to > from) ({ it / 3 }) else ({ -it / 3 })
    } else {
        ({ it / 4 })
    }
    return fadeIn(tween(TAB_ENTER_MS)) +
        slideInHorizontally(tween(TAB_ENTER_MS), slideOffset) +
        scaleIn(
            initialScale = 0.92f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        )
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabExitTransition(): ExitTransition {
    val indices = mainTabIndices()
    val slideOffset: (Int) -> Int = if (indices != null) {
        val (from, to) = indices
        if (to > from) ({ -it / 3 }) else ({ it / 3 })
    } else {
        ({ -it / 4 })
    }
    return fadeOut(tween(TAB_EXIT_MS)) +
        slideOutHorizontally(tween(TAB_EXIT_MS), slideOffset) +
        scaleOut(
            targetScale = 0.94f,
            animationSpec = tween(TAB_EXIT_MS),
        )
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabPopEnterTransition(): EnterTransition {
    val indices = mainTabIndices()
    val slideOffset: (Int) -> Int = if (indices != null) {
        val (from, to) = indices
        if (to < from) ({ -it / 3 }) else ({ it / 3 })
    } else {
        ({ -it / 4 })
    }
    return fadeIn(tween(TAB_ENTER_MS)) +
        slideInHorizontally(tween(TAB_ENTER_MS), slideOffset) +
        scaleIn(
            initialScale = 0.92f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        )
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabPopExitTransition(): ExitTransition {
    val indices = mainTabIndices()
    val slideOffset: (Int) -> Int = if (indices != null) {
        val (from, to) = indices
        if (to < from) ({ it / 3 }) else ({ -it / 3 })
    } else {
        ({ it / 4 })
    }
    return fadeOut(tween(TAB_EXIT_MS)) +
        slideOutHorizontally(tween(TAB_EXIT_MS), slideOffset) +
        scaleOut(
            targetScale = 0.94f,
            animationSpec = tween(TAB_EXIT_MS),
        )
}

fun NavGraphBuilder.mainTabComposable(
    tab: MainTab,
    content: @Composable () -> Unit,
) {
    composable(
        route = tab.route,
        enterTransition = { tabEnterTransition() },
        exitTransition = { tabExitTransition() },
        popEnterTransition = { tabPopEnterTransition() },
        popExitTransition = { tabPopExitTransition() },
    ) {
        content()
    }
}
