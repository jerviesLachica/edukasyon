package com.edukasyon.studentai.ui

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import com.edukasyon.studentai.BuildConfig
import com.edukasyon.studentai.ui.adaptive.AdaptiveScaffold
import com.edukasyon.studentai.ui.components.LoadingScreen
import com.edukasyon.studentai.ui.navigation.MainTab
import com.edukasyon.studentai.ui.navigation.Routes
import com.edukasyon.studentai.ui.navigation.mainTabComposable
import com.edukasyon.studentai.ui.navigation.navigateToTab
import com.edukasyon.studentai.ui.screens.*
import com.edukasyon.studentai.ui.theme.StudentAiTheme
import com.edukasyon.studentai.ui.viewmodel.MainViewModel

private const val TAG = "StudentAiApp"

@Composable
fun StudentAiAppContent(
    initialTabRoute: String? = null,
    onInitialTabConsumed: () -> Unit = {},
) {
    val viewModel: MainViewModel = hiltViewModel()
    val preferencesReady by viewModel.preferencesReady.collectAsStateWithLifecycle()
    val onboardingComplete by viewModel.onboardingComplete.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val primaryColorHex by viewModel.primaryColorHex.collectAsStateWithLifecycle()
    val secondaryColorHex by viewModel.secondaryColorHex.collectAsStateWithLifecycle()

    LaunchedEffect(onboardingComplete, themeMode, primaryColorHex, secondaryColorHex) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Root state: onboardingComplete=$onboardingComplete themeMode=$themeMode primary=$primaryColorHex")
        }
    }

    StudentAiTheme(
        themeMode = themeMode,
        primaryColorHex = primaryColorHex,
        secondaryColorHex = secondaryColorHex
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground
        ) {
            when {
                !preferencesReady -> LoadingScreen(title = "StudentAI")
                onboardingComplete -> MainNavigation(
                    initialTabRoute = initialTabRoute,
                    onInitialTabConsumed = onInitialTabConsumed,
                )
                else -> OnboardingScreen(onComplete = viewModel::markOnboardingFinished)
            }
        }
    }
}

@Composable
fun MainNavigation(
    initialTabRoute: String? = null,
    onInitialTabConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val tabs = MainTab.entries
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    LaunchedEffect(initialTabRoute) {
        val route = initialTabRoute ?: return@LaunchedEffect
        MainTab.entries.find { it.route == route }?.let { tab ->
            navController.navigateToTab(tab)
        }
        onInitialTabConsumed()
    }

    AdaptiveScaffold(
        currentRoute = currentRoute,
        tabs = tabs,
        onTabSelected = { tab ->
            navController.navigateToTab(tab, resetHomeRoot = tab == MainTab.HOME)
        }
    ) { contentModifier ->
        NavHost(
            navController = navController,
            startDestination = MainTab.HOME.route,
            modifier = contentModifier.fillMaxSize()
        ) {
            mainTabComposable(MainTab.HOME) {
                HomeScreen(
                    onAddTask = { navController.navigateToTab(MainTab.PLANNER) },
                    onAddClass = { navController.navigateToTab(MainTab.SCHEDULE) },
                    onAddNote = { navController.navigate(Routes.NOTES) },
                    onAskAi = { navController.navigateToTab(MainTab.AI) },
                    onNavigateToTab = { tab -> navController.navigateToTab(tab) },
                    onNavigateToRoute = { route -> navController.navigate(route) },
                    onOpenFeaturesGuide = { navController.navigate(Routes.FEATURES_GUIDE) },
                )
            }
            mainTabComposable(MainTab.SCHEDULE) {
                ScheduleScreen(onOpenScanner = { navController.navigate(Routes.SCHEDULE_SCANNER) })
            }
            mainTabComposable(MainTab.PLANNER) {
                PlannerScreen(
                    onNavigateGrades = { navController.navigate(Routes.GRADES) },
                    onNavigateCalendar = { navController.navigate(Routes.CALENDAR) }
                )
            }
            mainTabComposable(MainTab.AI) {
                AiScreen(
                    onOpenScanner = { navController.navigate(Routes.SCHEDULE_SCANNER) },
                    onOpenFlashcardStudy = { navController.navigate(Routes.FLASHCARD_STUDY) },
                    onOpenHistory = { filter ->
                        navController.navigate(Routes.aiConversationHistory(filter))
                    },
                )
            }
            mainTabComposable(MainTab.PROFILE) {
                ProfileScreen(
                    onNavigateChat = { navController.navigate(Routes.CHAT_LIST) },
                    onNavigateFeaturesGuide = { navController.navigate(Routes.FEATURES_GUIDE) },
                    onNavigateNotificationSettings = { navController.navigate(Routes.NOTIFICATION_SETTINGS) },
                    onRequestNotificationPermission = { /* handled in ProfileScreen */ }
                )
            }
            composable(Routes.NOTIFICATION_SETTINGS) {
                NotificationSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDetail = { navController.navigate(Routes.NOTIFICATION_SETTINGS_DETAIL) }
                )
            }
            composable(Routes.NOTIFICATION_SETTINGS_DETAIL) {
                NotificationSettingsDetailScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.LECTURE_FILES) {
                LectureFilesScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToNotes = { navController.navigate(Routes.NOTES) },
                    onNavigateToPlanner = { navController.navigateToTab(MainTab.PLANNER) }
                )
            }
            composable(Routes.NOTES) { NotesScreen() }
            composable(Routes.FEATURES_GUIDE) {
                FeaturesGuideScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToTab = { tab ->
                        navController.navigateToTab(tab)
                    },
                    onNavigateToRoute = { route -> navController.navigate(route) }
                )
            }
            composable(Routes.GRADES) { GradesScreen() }
            composable(Routes.CALENDAR) { CalendarScreen() }
            composable(Routes.SCHEDULE_SCANNER) {
                ScheduleScannerScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.CHAT_LIST) {
                ChatListScreen(
                    onOpenChat = { id -> navController.navigate(Routes.chatThread(id)) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.CHAT_THREAD) { entry ->
                val id = entry.arguments?.getString("conversationId") ?: return@composable
                ChatThreadScreen(conversationId = id, onBack = { navController.popBackStack() })
            }
            composable(Routes.FLASHCARD_STUDY) {
                FlashcardStudyScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.AI_CONVERSATION_HISTORY) { entry ->
                val filterScope = entry.arguments?.getString("filterScope") ?: "all"
                AiConversationHistoryScreen(
                    filterScope = filterScope,
                    onBack = { navController.popBackStack() },
                    onConversationSelected = { navController.popBackStack() },
                )
            }
        }
    }
}
