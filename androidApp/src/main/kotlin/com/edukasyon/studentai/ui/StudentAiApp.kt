package com.edukasyon.studentai.ui

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.edukasyon.studentai.BuildConfig
import com.edukasyon.studentai.ui.adaptive.AdaptiveScaffold
import com.edukasyon.studentai.ui.components.LoadingScreen
import com.edukasyon.studentai.ui.components.StarfieldScaffold
import com.edukasyon.studentai.ui.navigation.MainTab
import com.edukasyon.studentai.ui.navigation.Routes
import com.edukasyon.studentai.ui.navigation.mainTabComposable
import com.edukasyon.studentai.ui.navigation.navigateToTab
import com.edukasyon.studentai.ui.navigation.routeToSelectedTab
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
        when {
            !preferencesReady -> {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ) {
                    LoadingScreen(title = "SchedMate")
                }
            }
            onboardingComplete -> {
                StarfieldScaffold {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                    ) {
                        MainNavigation(
                            initialTabRoute = initialTabRoute,
                            onInitialTabConsumed = onInitialTabConsumed,
                        )
                    }
                }
            }
            else -> {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ) {
                    OnboardingScreen(onComplete = viewModel::markOnboardingFinished)
                }
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
    var forceHideBottomBar by remember { mutableStateOf(false) }

    LaunchedEffect(currentRoute) {
        if (routeToSelectedTab(currentRoute) != MainTab.JEVI) {
            forceHideBottomBar = false
        }
    }

    LaunchedEffect(initialTabRoute) {
        val route = initialTabRoute ?: return@LaunchedEffect
        MainTab.entries.find { it.route == route }?.let { tab ->
            navController.navigateToTab(tab)
        } ?: run {
            routeToSelectedTab(route)?.let { tab -> navController.navigateToTab(tab) }
            navController.navigate(route) { launchSingleTop = true }
        }
        onInitialTabConsumed()
    }

    AdaptiveScaffold(
        currentRoute = currentRoute,
        tabs = tabs,
        forceHideBottomBar = forceHideBottomBar,
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
                    onAddNote = { navController.navigate(Routes.noteEditor(Routes.NEW_NOTE_ID)) },
                    onAskAi = { navController.navigateToTab(MainTab.JEVI) },
                    onNavigateToTab = { tab -> navController.navigateToTab(tab) },
                    onNavigateToRoute = { route -> navController.navigate(route) },
                    onOpenFeaturesGuide = { navController.navigate(Routes.FEATURES_GUIDE) },
                    onNavigateFocus = { navController.navigate(Routes.FOCUS) },
                )
            }
            mainTabComposable(MainTab.SCHEDULE) {
                ScheduleScreen(onOpenScanner = { navController.navigate(Routes.SCHEDULE_SCANNER) })
            }
            mainTabComposable(MainTab.PLANNER) {
                PlannerScreen(
                    onNavigateGrades = { navController.navigate(Routes.GRADES) },
                    onNavigateCalendar = { navController.navigate(Routes.CALENDAR) },
                    onOpenAssignmentIntelligence = { navController.navigate(Routes.ASSIGNMENT_INTELLIGENCE) },
                    onNavigateFocus = { navController.navigate(Routes.FOCUS) },
                )
            }
            mainTabComposable(MainTab.JEVI) {
                JeviHubScreen(
                    onOpenDecks = { navController.navigate(Routes.JEVI_DECKS) },
                    onOpenReview = { navController.navigate(Routes.JEVI_REVIEW) },
                    onOpenCreate = { navController.navigate(Routes.JEVI_CREATE) },
                    onOpenTutor = { navController.navigate(Routes.JEVI_TUTOR) },
                    onOpenQuiz = { navController.navigate(Routes.JEVI_QUIZ) },
                )
            }
            mainTabComposable(MainTab.PROFILE) {
                ProfileScreen(
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
            composable(Routes.NOTES) {
                NotesScreen(
                    onOpenEditor = { noteId -> navController.navigate(Routes.noteEditor(noteId)) },
                    onCreateNote = { navController.navigate(Routes.noteEditor(Routes.NEW_NOTE_ID)) },
                )
            }
            composable(Routes.NOTE_EDITOR) {
                NoteEditorScreen(onBack = { navController.popBackStack() })
            }
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
            composable(Routes.FOCUS) {
                FocusScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SCHEDULE_SCANNER) {
                ScheduleScannerScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ASSIGNMENT_INTELLIGENCE) {
                val plannerBackStackEntry = remember(it) {
                    navController.getBackStackEntry(MainTab.PLANNER.route)
                }
                AssignmentIntelligenceScreen(
                    onBack = { navController.popBackStack() },
                    onAddedToPlanner = { navController.popBackStack() },
                    viewModel = hiltViewModel(plannerBackStackEntry),
                )
            }
            composable(Routes.JEVI_DECKS) {
                JeviDecksScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDeck = { deckId ->
                        navController.navigate(Routes.jeviDeckDetail(deckId))
                    },
                )
            }
            composable(
                route = Routes.JEVI_DECK_DETAIL,
                arguments = listOf(
                    navArgument("deckId") { type = NavType.StringType },
                ),
            ) { entry ->
                val deckId = entry.arguments?.getString("deckId")
                if (deckId != null) {
                    JeviDeckDetailScreen(
                        onBack = { navController.popBackStack() },
                        onReviewDue = { id ->
                            navController.navigate(Routes.jeviReviewDeck(id, studyAll = false))
                        },
                        onStudyAll = { id ->
                            navController.navigate(Routes.jeviReviewDeck(id, studyAll = true))
                        },
                    )
                }
            }
            composable(Routes.JEVI_CREATE) {
                JeviCreateScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.JEVI_QUIZ) {
                JeviQuizArenaScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.JEVI_TUTOR) {
                AiScreen(
                    onOpenHistory = { filter ->
                        navController.navigate(Routes.aiConversationHistory(filter))
                    },
                    onChatInputActive = { active -> forceHideBottomBar = active },
                )
            }
            composable(Routes.JEVI_REVIEW) {
                FlashcardStudyScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.JEVI_REVIEW_DECK,
                arguments = listOf(
                    navArgument("deckId") { type = NavType.StringType },
                    navArgument("studyAll") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
            ) { entry ->
                val deckId = entry.arguments?.getString("deckId")
                val studyAll = entry.arguments?.getBoolean("studyAll") ?: false
                FlashcardStudyScreen(
                    deckId = deckId,
                    studyAll = studyAll,
                    onBack = { navController.popBackStack() },
                    onStudyAll = { id ->
                        navController.navigate(Routes.jeviReviewDeck(id, studyAll = true))
                    },
                )
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
