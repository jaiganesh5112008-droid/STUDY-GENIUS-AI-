package com.example.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.auth.authStateFlow
import com.example.data.UserPreferences
import com.example.ui.components.LanguagePickerDialog
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatAssistantScreen
import com.example.ui.screens.FlashcardsScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.NotesSummarizerScreen
import com.example.ui.screens.ProfileAnalyticsScreen
import com.example.ui.screens.QuizBuilderScreen
import com.example.ui.screens.QuizEngineScreen
import com.example.ui.screens.StudyPlannerScreen
import com.example.ui.screens.ToolsHubScreen
import com.example.ui.theme.LocalStudyColors
import com.example.ui.theme.StudyGeniusTheme
import com.example.viewmodel.AppContainer
import com.example.viewmodel.ChatViewModel
import com.example.viewmodel.FlashcardsViewModel
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.NotesSummarizerViewModel
import com.example.viewmodel.PlannerLibraryViewModel
import com.example.viewmodel.QuizBuilderMode
import com.example.viewmodel.QuizEngineViewModel
import com.example.viewmodel.QuizHubViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

object StudyRoutes {
    const val HOME = "home"
    const val TOOLS = "tools"
    const val CHAT = "chat"
    const val LIBRARY = "library"
    const val PROFILE = "profile"

    const val QUIZ_BUILDER = "quiz_builder/{mode}"
    const val QUIZ_ENGINE = "quiz_engine/{quizId}"
    const val NOTES_SUMMARIZER = "notes_summarizer/{isSummarizer}"
    const val FLASHCARDS = "flashcards"
    const val PLANNER = "planner"
    const val DOUBT_SOLVER = "doubt_solver"

    fun quizBuilder(mode: QuizBuilderMode) = "quiz_builder/${mode.name}"
    fun quizEngine(quizId: String) = "quiz_engine/${Uri.encode(quizId)}"
    fun notesSummarizer(isSummarizer: Boolean) = "notes_summarizer/$isSummarizer"
}

private data class NavDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun StudyGeniusApp(
    container: AppContainer,
    auth: FirebaseAuth = container.firebaseAuth
) {
    val localPrefs by container.repository.userPreferences
        .collectAsStateWithLifecycle(initialValue = UserPreferences())
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    StudyGeniusTheme(darkTheme = localPrefs.darkTheme) {
        val user = currentUser
        if (user == null) {
            // Unauthenticated session: render Google Sign-In UI only.
            // Feature screens and Firestore listeners are NOT instantiated here.
            AuthScreen(authManager = container.authManager)
        } else {
            // Key composition and all ViewModels by user.uid so sign-out or account switching
            // tears down previous Firestore snapshot listeners cleanly.
            key(user.uid) {
                AuthenticatedStudyApp(
                    container = container,
                    user = user
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedStudyApp(
    container: AppContainer,
    user: FirebaseUser
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uid = user.uid
    val userFactory = remember(uid) { container.viewModelFactoryForUser(uid) }

    val mainViewModel: MainViewModel = viewModel(key = "main_$uid", factory = userFactory)
    val chatViewModel: ChatViewModel = viewModel(key = "chat_$uid", factory = userFactory)
    val quizHubViewModel: QuizHubViewModel = viewModel(key = "quiz_hub_$uid", factory = userFactory)
    val quizEngineViewModel: QuizEngineViewModel = viewModel(key = "quiz_engine_$uid", factory = userFactory)
    val notesViewModel: NotesSummarizerViewModel = viewModel(key = "notes_$uid", factory = userFactory)
    val flashcardsViewModel: FlashcardsViewModel = viewModel(key = "flashcards_$uid", factory = userFactory)
    val plannerLibraryViewModel: PlannerLibraryViewModel = viewModel(key = "planner_lib_$uid", factory = userFactory)

    val dashboardState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val migrationBanner by mainViewModel.migrationBanner.collectAsStateWithLifecycle()
    var showLanguageDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uid) {
        mainViewModel.syncSignedInProfileAndLegacyData(
            displayName = user.displayName,
            email = user.email,
            photoUrl = user.photoUrl?.toString()
        )
    }

    LaunchedEffect(migrationBanner) {
        migrationBanner?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            mainViewModel.clearMigrationBanner()
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val extended = LocalStudyColors.current

    val primaryDestinations = listOf(
        NavDestination(StudyRoutes.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
        NavDestination(StudyRoutes.TOOLS, "Tools", Icons.Filled.Apps, Icons.Outlined.Apps),
        NavDestination(StudyRoutes.CHAT, "AI Chat", Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat),
        NavDestination(StudyRoutes.LIBRARY, "Library", Icons.Filled.Folder, Icons.Outlined.Folder),
        NavDestination(StudyRoutes.PROFILE, "Profile", Icons.Filled.Person, Icons.Outlined.Person)
    )
    val primaryRoutes = primaryDestinations.map { it.route }.toSet()
    val showNavChrome = currentRoute in primaryRoutes

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun navigateTool(toolId: String) {
        when (toolId) {
            "chat" -> navigateTopLevel(StudyRoutes.CHAT)
            "doubt_solver" -> navController.navigate(StudyRoutes.DOUBT_SOLVER)
            "pdf_quiz" -> navController.navigate(StudyRoutes.quizBuilder(QuizBuilderMode.PDF_TO_QUIZ))
            "image_quiz" -> navController.navigate(StudyRoutes.quizBuilder(QuizBuilderMode.IMAGE_TO_QUIZ))
            "mock_test" -> navController.navigate(StudyRoutes.quizBuilder(QuizBuilderMode.MOCK_TEST))
            "notes" -> navController.navigate(StudyRoutes.notesSummarizer(false))
            "summarizer" -> navController.navigate(StudyRoutes.notesSummarizer(true))
            "flashcards" -> navController.navigate(StudyRoutes.FLASHCARDS)
            "planner" -> navController.navigate(StudyRoutes.PLANNER)
            "library" -> navigateTopLevel(StudyRoutes.LIBRARY)
        }
    }

    val backgroundBrush = if (extended.isDark) {
        Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.background,
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.background
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.background,
                MaterialTheme.colorScheme.surfaceContainerLow
            )
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        val isWideScreen = maxWidth >= 700.dp

        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (showNavChrome && !isWideScreen) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        primaryDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                modifier = Modifier.testTag("bottom_nav_${dest.route}"),
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label, style = MaterialTheme.typography.labelMedium) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = extended.emeraldGlow,
                                    selectedTextColor = extended.emeraldGlow,
                                    indicatorColor = extended.emeraldGlow.copy(alpha = 0.16f)
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showNavChrome && isWideScreen) {
                    NavigationRail(
                        modifier = Modifier
                            .fillMaxHeight()
                            .statusBarsPadding(),
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                    ) {
                        primaryDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) }
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = StudyRoutes.HOME,
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 760.dp)
                    ) {
                        composable(StudyRoutes.HOME) {
                            HomeDashboardScreen(
                                uiState = dashboardState,
                                onOpenLanguagePicker = { showLanguageDialog = true },
                                onToggleTheme = { mainViewModel.toggleDarkTheme(it) },
                                onNavigateTool = { navigateTool(it) },
                                onOpenQuiz = { quizId ->
                                    if (quizId.isNotBlank()) {
                                        navController.navigate(StudyRoutes.quizEngine(quizId))
                                    }
                                }
                            )
                        }

                        composable(StudyRoutes.TOOLS) {
                            ToolsHubScreen(
                                language = dashboardState.preferences.language,
                                onOpenLanguagePicker = { showLanguageDialog = true },
                                onNavigateTool = { navigateTool(it) }
                            )
                        }

                        composable(StudyRoutes.CHAT) {
                            ChatAssistantScreen(
                                viewModel = chatViewModel,
                                language = dashboardState.preferences.language,
                                initialDoubtMode = false,
                                onOpenLanguagePicker = { showLanguageDialog = true }
                            )
                        }

                        composable(StudyRoutes.DOUBT_SOLVER) {
                            ChatAssistantScreen(
                                viewModel = chatViewModel,
                                language = dashboardState.preferences.language,
                                initialDoubtMode = true,
                                onOpenLanguagePicker = { showLanguageDialog = true },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(StudyRoutes.LIBRARY) {
                            LibraryScreen(
                                viewModel = plannerLibraryViewModel,
                                onOpenQuiz = { quizId ->
                                    if (quizId.isNotBlank()) {
                                        navController.navigate(StudyRoutes.quizEngine(quizId))
                                    }
                                },
                                onOpenFlashcardDeck = { deckId ->
                                    flashcardsViewModel.openDeck(deckId)
                                    navController.navigate(StudyRoutes.FLASHCARDS)
                                }
                            )
                        }

                        composable(StudyRoutes.PROFILE) {
                            ProfileAnalyticsScreen(
                                uiState = dashboardState,
                                userEmail = user.email,
                                onToggleTheme = { mainViewModel.toggleDarkTheme(it) },
                                onSelectLanguage = { mainViewModel.selectLanguage(it) },
                                onUpdateName = { mainViewModel.updateStudentName(it) },
                                onOpenQuiz = { quizId ->
                                    if (quizId.isNotBlank()) {
                                        navController.navigate(StudyRoutes.quizEngine(quizId))
                                    }
                                },
                                onSignOut = {
                                    scope.launch {
                                        container.authManager.signOut(context)
                                    }
                                },
                                onExportBackup = { ctx, uri, cb ->
                                    mainViewModel.exportBackupToUri(ctx, uri, cb)
                                },
                                onImportBackup = { ctx, uri, cb ->
                                    mainViewModel.importBackupFromUri(ctx, uri, cb)
                                }
                            )
                        }

                        composable(
                            route = StudyRoutes.QUIZ_BUILDER,
                            arguments = listOf(navArgument("mode") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val modeName = backStackEntry.arguments?.getString("mode")
                            val mode = QuizBuilderMode.entries.firstOrNull { it.name == modeName }
                                ?: QuizBuilderMode.PDF_TO_QUIZ
                            QuizBuilderScreen(
                                viewModel = quizHubViewModel,
                                initialMode = mode,
                                language = dashboardState.preferences.language,
                                onOpenLanguagePicker = { showLanguageDialog = true },
                                onQuizGenerated = { quizId ->
                                    navController.navigate(StudyRoutes.quizEngine(quizId))
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = StudyRoutes.QUIZ_ENGINE,
                            arguments = listOf(navArgument("quizId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val rawQuizId = backStackEntry.arguments?.getString("quizId").orEmpty()
                            val quizId = Uri.decode(rawQuizId)
                            QuizEngineScreen(
                                quizId = quizId,
                                viewModel = quizEngineViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = StudyRoutes.NOTES_SUMMARIZER,
                            arguments = listOf(navArgument("isSummarizer") { type = NavType.BoolType })
                        ) { backStackEntry ->
                            val isSum = backStackEntry.arguments?.getBoolean("isSummarizer") ?: false
                            NotesSummarizerScreen(
                                viewModel = notesViewModel,
                                initialSummarizer = isSum,
                                language = dashboardState.preferences.language,
                                onOpenLanguagePicker = { showLanguageDialog = true },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(StudyRoutes.FLASHCARDS) {
                            FlashcardsScreen(
                                viewModel = flashcardsViewModel,
                                language = dashboardState.preferences.language,
                                onOpenLanguagePicker = { showLanguageDialog = true },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(StudyRoutes.PLANNER) {
                            StudyPlannerScreen(
                                plannerViewModel = plannerLibraryViewModel,
                                dashboardState = dashboardState,
                                onUpdateWeeklyGoal = { mainViewModel.updateWeeklyGoalMinutes(it) },
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }

        if (showLanguageDialog) {
            LanguagePickerDialog(
                selected = dashboardState.preferences.language,
                onSelect = { mainViewModel.selectLanguage(it) },
                onDismiss = { showLanguageDialog = false }
            )
        }
    }
}
