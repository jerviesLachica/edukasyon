package com.edukasyon.studentai.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.edukasyon.studentai.BuildConfig
import com.edukasyon.studentai.di.FirebaseEntryPoint
import com.edukasyon.studentai.ui.adaptive.AdaptiveScaffold
import com.edukasyon.studentai.ui.navigation.MainTab
import com.edukasyon.studentai.ui.navigation.Routes
import com.edukasyon.studentai.ui.screens.*
import com.edukasyon.studentai.ui.theme.StudentAiTheme
import com.edukasyon.studentai.ui.viewmodel.MainViewModel
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

private const val TAG = "StudentAiApp"

@Composable
fun StudentAiAppContent() {
    val viewModel: MainViewModel = hiltViewModel()
    val onboardingComplete by viewModel.onboardingComplete.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

    LaunchedEffect(onboardingComplete, themeMode) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Root state: onboardingComplete=$onboardingComplete themeMode=$themeMode")
        }
    }

    StudentAiTheme(themeMode = themeMode) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground
        ) {
            if (onboardingComplete) {
                MainNavigation()
            } else {
                LightweightOnboarding(onFinished = viewModel::markOnboardingFinished)
            }
        }
    }
}

@Composable
private fun LightweightOnboarding(onFinished: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }

    fun completeGuest() {
        scope.launch {
            runCatching {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    FirebaseEntryPoint::class.java
                )
                entryPoint.firebaseAuthManager().ensureAnonymousSession()
                entryPoint.userPreferences().setOnboardingComplete(true)
            }.onFailure { Log.e(TAG, "Failed to complete onboarding", it) }
            onFinished()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (step) {
            0 -> {
                Text(
                    "Welcome to StudentAI",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Your intelligent student companion for schedules, tasks, notes, and AI-powered study tools.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) {
                    Text("Get Started")
                }
                TextButton(onClick = { completeGuest() }) {
                    Text("Continue Offline as Guest")
                }
            }
            1 -> {
                Text(
                    "School Setup",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "You can add school details later from Profile.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { step = 2 }, modifier = Modifier.fillMaxWidth()) {
                    Text("Continue")
                }
            }
            else -> {
                Text(
                    "You're all set!",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Start by adding your schedule or exploring the dashboard.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { completeGuest() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Enter StudentAI")
                }
            }
        }
    }
}

@Composable
fun MainNavigation() {
    val navController = rememberNavController()
    val tabs = MainTab.entries
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    AdaptiveScaffold(
        currentRoute = currentRoute,
        tabs = tabs,
        onTabSelected = { tab ->
            navController.navigate(tab.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    ) { contentModifier ->
        NavHost(
            navController = navController,
            startDestination = MainTab.HOME.route,
            modifier = contentModifier.fillMaxSize()
        ) {
            composable(MainTab.HOME.route) {
                HomeScreen(
                    onAddTask = { navController.navigate(MainTab.PLANNER.route) },
                    onAddClass = { navController.navigate(MainTab.SCHEDULE.route) },
                    onAddNote = { navController.navigate("notes") },
                    onAskAi = { navController.navigate(MainTab.AI.route) }
                )
            }
            composable(MainTab.SCHEDULE.route) { ScheduleScreen() }
            composable(MainTab.PLANNER.route) {
                PlannerScreen(
                    onNavigateGrades = { navController.navigate(Routes.GRADES) },
                    onNavigateCalendar = { navController.navigate(Routes.CALENDAR) }
                )
            }
            composable(MainTab.AI.route) { AiScreen() }
            composable(MainTab.PROFILE.route) { ProfileScreen() }
            composable("notes") { NotesScreen() }
            composable(Routes.GRADES) { GradesScreen() }
            composable(Routes.CALENDAR) { CalendarScreen() }
        }
    }
}
