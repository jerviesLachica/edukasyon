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
    const val SCHEDULE_SCANNER = "schedule_scanner"
    const val CHAT_LIST = "chat_list"
    const val CHAT_THREAD = "chat_thread/{conversationId}"
    const val FLASHCARD_STUDY = "flashcard_study"
    const val AI_SUMMARIZER = "ai_summarizer"
    const val GRADES = "grades"
    const val CALENDAR = "calendar"
    const val NOTES = "notes"
    const val FEATURES_GUIDE = "features-guide"
    const val SEARCH = "search"
    const val NOTIFICATION_SETTINGS = "notification_settings"
    const val NOTIFICATION_SETTINGS_DETAIL = "notification_settings_detail"
    const val LECTURE_FILES = "lecture_files"
    const val AI_CONVERSATION_HISTORY = "ai_conversation_history/{filterScope}"
    fun aiConversationHistory(filterScope: String) = "ai_conversation_history/$filterScope"
    fun noteDetail(id: String) = "note_detail/$id"
    fun chatThread(id: String) = "chat_thread/$id"
}

fun routeToSelectedTab(route: String?): MainTab? {
    if (route == null) return null
    return when {
        route == Routes.NOTES -> MainTab.HOME
        route == Routes.GRADES || route == Routes.CALENDAR -> MainTab.PLANNER
        route == Routes.SCHEDULE_SCANNER -> MainTab.SCHEDULE
        route == Routes.FLASHCARD_STUDY -> MainTab.AI
        route.startsWith("ai_conversation_history/") -> MainTab.AI
        route == Routes.CHAT_LIST || route.startsWith("chat_thread/") || route == Routes.FEATURES_GUIDE -> MainTab.PROFILE
        route == Routes.NOTIFICATION_SETTINGS || route == Routes.NOTIFICATION_SETTINGS_DETAIL -> MainTab.PROFILE
        route == Routes.LECTURE_FILES -> MainTab.HOME
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
