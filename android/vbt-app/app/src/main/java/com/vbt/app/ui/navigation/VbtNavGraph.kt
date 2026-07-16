package com.vbt.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vbt.app.data.local.PreferencesManager
import com.vbt.app.data.remote.SessionExpiredNotifier
import com.vbt.app.ui.screen.analytics.AnalyticsScreen
import com.vbt.app.ui.screen.athletes.AthleteListScreen
import com.vbt.app.ui.screen.athletes.AthleteProfileScreen
import com.vbt.app.ui.screen.connect.ConnectScreen
import com.vbt.app.ui.screen.exercises.ExerciseListScreen
import com.vbt.app.ui.screen.history.HistoryScreen
import com.vbt.app.ui.screen.history.SessionDetailScreen
import com.vbt.app.ui.screen.home.HomeScreen
import com.vbt.app.ui.screen.login.LoginScreen
import com.vbt.app.ui.screen.login.RegisterScreen
import com.vbt.app.ui.screen.plans.PlanEditScreen
import com.vbt.app.ui.screen.plans.PlanListScreen
import com.vbt.app.ui.screen.recording.RecordingScreen
import com.vbt.app.ui.screen.schedule.ScheduleScreen
import com.vbt.app.ui.screen.workout.WorkoutScreen
import com.vbt.app.ui.theme.VbtSurface
import com.vbt.app.ui.theme.VbtTeal
import com.vbt.app.ui.theme.VbtTextSecondary

object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val HOME = "home"
    const val CONNECT = "connect"
    const val WORKOUT = "workout"
    const val WORKOUT_WITH_PLAN = "workout?planId={planId}"
    const val WORKOUT_FROM_SCHEDULE = "workout?planId={planId}&calendarEntryId={calendarEntryId}&athleteId={athleteId}"
    const val WORKOUT_RESUME = "workout?resumeSessionId={resumeSessionId}"
    const val PLAN_LIST = "plan_list"
    const val PLAN_EDIT = "plan_edit/{planId}"
    const val SCHEDULE = "schedule"
    const val HISTORY = "history"
    const val SESSION_DETAIL = "session_detail/{sessionId}"
    const val ATHLETE_LIST = "athlete_list"
    const val ATHLETE_PROFILE = "athlete_profile/{athleteId}"
    const val EXERCISE_LIST = "exercise_list"
    const val ANALYTICS = "analytics"
    const val RECORD = "record?exercise={exercise}&load={load}&athlete={athlete}"
}

@Composable
fun VbtNavGraph(
    preferencesManager: PreferencesManager,
    sessionExpiredNotifier: SessionExpiredNotifier? = null
) {
    val navController = rememberNavController()
    val isLoggedIn by preferencesManager.isLoggedIn().collectAsState(initial = false)
    val userRole by preferencesManager.getRole().collectAsState(initial = null)

    val startDestination = if (isLoggedIn) Routes.HOME else Routes.LOGIN

    // Reakcja na 401 z API: token wyczyszczony przez interceptor,
    // przekieruj na logowanie z komunikatem.
    val context = LocalContext.current
    LaunchedEffect(sessionExpiredNotifier) {
        sessionExpiredNotifier?.events?.collect {
            Toast.makeText(
                context,
                "Sesja wygasła, zaloguj się ponownie",
                Toast.LENGTH_LONG
            ).show()
            navController.navigate(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate(Routes.REGISTER)
                }
            )
        }

        composable(Routes.REGISTER) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.HOME) {
            VbtBottomNavBarLayout(
                navController = navController,
                userRole = userRole,
                content = {
                    HomeScreen(
                        onStartWorkout = { navController.navigate(Routes.WORKOUT) { launchSingleTop = true } },
                        onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                        onNavigateToPlans = { navController.navigate(Routes.PLAN_LIST) },
                        onNavigateToConnect = { navController.navigate(Routes.CONNECT) },
                        onNavigateToAthletes = { navController.navigate(Routes.ATHLETE_LIST) },
                        onNavigateToSchedule = { navController.navigate(Routes.SCHEDULE) },
                        onNavigateToAnalytics = { navController.navigate(Routes.ANALYTICS) },
                        onLogout = {
                            navController.navigate(Routes.LOGIN) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }
            )
        }

        composable(Routes.CONNECT) {
            VbtBottomNavBarLayout(
                navController = navController,
                userRole = userRole,
                content = {
                    ConnectScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            )
        }

        composable(Routes.PLAN_LIST) {
            VbtBottomNavBarLayout(
                navController = navController,
                userRole = userRole,
                content = {
                    PlanListScreen(
                        onCreatePlan = { navController.navigate("plan_edit/0") },
                        onEditPlan = { id -> navController.navigate("plan_edit/$id") },
                        onStartWorkout = { plan -> navController.navigate("workout?planId=${plan.id}") { launchSingleTop = true } }
                    )
                }
            )
        }

        composable(
            Routes.PLAN_EDIT,
            arguments = listOf(navArgument("planId") { type = NavType.IntType })
        ) { backStackEntry ->
            val rawPlanId = backStackEntry.arguments?.getInt("planId") ?: 0
            val planId = if (rawPlanId == 0) null else rawPlanId
            VbtBottomNavBarLayout(
                navController = navController,
                userRole = userRole,
                content = {
                    PlanEditScreen(
                        planId = planId,
                        onBack = { navController.popBackStack() },
                        onSaved = { navController.popBackStack() }
                    )
                }
            )
        }

        composable(
            route = "workout?planId={planId}",
            arguments = listOf(navArgument("planId") {
                type = NavType.IntType
                defaultValue = -1
            })
        ) { backStackEntry ->
            VbtBottomNavBarLayout(
                navController = navController,
                userRole = userRole,
                content = {
                    WorkoutScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onRecordSet = { ex, load, athlete -> navController.navigateToRecord(ex, load, athlete) }
                    )
                }
            )
        }

        // Ekran nagrywania podejścia (pełnoekranowy, bez dolnej nawigacji) -
        // wypala parametry VBT w wideo i zapisuje do galerii.
        composable(
            route = Routes.RECORD,
            arguments = listOf(
                navArgument("exercise") { type = NavType.StringType; defaultValue = "" },
                navArgument("load") { type = NavType.StringType; defaultValue = "0" },
                navArgument("athlete") { type = NavType.StringType; defaultValue = "" }
            )
        ) {
            RecordingScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.WORKOUT_FROM_SCHEDULE,
            arguments = listOf(
                navArgument("planId") { type = NavType.IntType; defaultValue = -1 },
                navArgument("calendarEntryId") { type = NavType.IntType; defaultValue = -1 },
                navArgument("athleteId") { type = NavType.IntType; defaultValue = -1 }
            )
        ) {
            VbtBottomNavBarLayout(
                navController = navController,
                userRole = userRole,
                content = {
                    WorkoutScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            )
        }

        composable(Routes.SCHEDULE) {
            VbtBottomNavBarLayout(
                navController = navController,
                userRole = userRole,
                content = {
                    ScheduleScreen(
                        onStartWorkout = { planId, calendarEntryId, athleteId ->
                            navController.navigate(
                                "workout?planId=${planId ?: -1}&calendarEntryId=$calendarEntryId&athleteId=$athleteId"
                            ) { launchSingleTop = true }
                        }
                    )
                }
            )
        }

        composable(Routes.HISTORY) {
            VbtBottomNavBarLayout(
                navController = navController,
                userRole = userRole,
                content = {
                    HistoryScreen(
                        onSessionDetail = { sessionId ->
                            navController.navigate("session_detail/$sessionId")
                        },
                        onResumeSession = { localSessionId ->
                            navController.navigate("workout?resumeSessionId=$localSessionId") { launchSingleTop = true }
                        }
                    )
                }
            )
        }

        composable(
            route = "workout?resumeSessionId={resumeSessionId}",
            arguments = listOf(
                navArgument("resumeSessionId") { type = NavType.LongType; defaultValue = 0L }
            )
        ) {
            VbtBottomNavBarLayout(
                navController = navController,
                userRole = userRole,
                content = {
                    WorkoutScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            )
        }

        composable(
            Routes.SESSION_DETAIL,
            arguments = listOf(navArgument("sessionId") { type = NavType.IntType })
        ) {
            VbtBottomNavBarLayout(
                navController = navController,
                userRole = userRole,
                content = {
                    SessionDetailScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            )
        }

        composable(Routes.EXERCISE_LIST) {
            VbtBottomNavBarLayout(
                navController = navController,
                userRole = userRole,
                content = {
                    ExerciseListScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            )
        }

        composable(Routes.ATHLETE_LIST) {
            VbtBottomNavBarLayout(
                navController = navController,
                userRole = userRole,
                content = {
                    AthleteListScreen(
                        onAthleteProfile = { athleteId ->
                            navController.navigate("athlete_profile/$athleteId")
                        }
                    )
                }
            )
        }

        composable(
            Routes.ATHLETE_PROFILE,
            arguments = listOf(navArgument("athleteId") { type = NavType.IntType })
        ) { backStack ->
            val athleteId = backStack.arguments!!.getInt("athleteId")
            VbtBottomNavBarLayout(
                navController = navController,
                userRole = userRole,
                content = {
                    AthleteProfileScreen(
                        athleteId = athleteId,
                        onBack = { navController.popBackStack() },
                        onSessionDetail = { sessionId ->
                            navController.navigate("session_detail/$sessionId")
                        }
                    )
                }
            )
        }

        composable(Routes.ANALYTICS) {
            VbtBottomNavBarLayout(
                navController = navController,
                userRole = userRole,
                content = {
                    AnalyticsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            )
        }
    }
}

@Composable
private fun VbtBottomNavBarLayout(
    navController: NavController,
    userRole: String?,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.background(VbtSurface)
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .weight(1f)
        ) {
            content()
        }
        VbtBottomNavBar(navController, userRole)
    }
}

@Composable
fun VbtBottomNavBar(navController: NavController, role: String?) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(
        containerColor = VbtSurface
    ) {
        NavigationBarItem(
            icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
            label = { Text("Home") },
            selected = currentRoute == Routes.HOME,
            onClick = {
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.HOME) { inclusive = true }
                }
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = VbtTeal,
                selectedTextColor = VbtTeal,
                unselectedIconColor = VbtTextSecondary,
                unselectedTextColor = VbtTextSecondary
            )
        )

        NavigationBarItem(
            icon = { Icon(Icons.Filled.FitnessCenter, contentDescription = "Workout") },
            label = { Text("Workout") },
            selected = currentRoute == Routes.WORKOUT,
            onClick = { navController.navigate(Routes.WORKOUT) { launchSingleTop = true } },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = VbtTeal,
                selectedTextColor = VbtTeal,
                unselectedIconColor = VbtTextSecondary,
                unselectedTextColor = VbtTextSecondary
            )
        )

        NavigationBarItem(
            icon = { Icon(Icons.Filled.BarChart, contentDescription = "Plans") },
            label = { Text("Plans") },
            selected = currentRoute == Routes.PLAN_LIST,
            onClick = { navController.navigate(Routes.PLAN_LIST) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = VbtTeal,
                selectedTextColor = VbtTeal,
                unselectedIconColor = VbtTextSecondary,
                unselectedTextColor = VbtTextSecondary
            )
        )

        NavigationBarItem(
            icon = { Icon(Icons.Filled.Schedule, contentDescription = "Schedule") },
            label = { Text("Schedule") },
            selected = currentRoute == Routes.SCHEDULE,
            onClick = { navController.navigate(Routes.SCHEDULE) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = VbtTeal,
                selectedTextColor = VbtTeal,
                unselectedIconColor = VbtTextSecondary,
                unselectedTextColor = VbtTextSecondary
            )
        )

        NavigationBarItem(
            icon = { Icon(Icons.Filled.History, contentDescription = "History") },
            label = { Text("History") },
            selected = currentRoute == Routes.HISTORY,
            onClick = { navController.navigate(Routes.HISTORY) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = VbtTeal,
                selectedTextColor = VbtTeal,
                unselectedIconColor = VbtTextSecondary,
                unselectedTextColor = VbtTextSecondary
            )
        )

        if (role == "coach") {
            NavigationBarItem(
                icon = { Icon(Icons.Filled.Group, contentDescription = "Athletes") },
                label = { Text("Athletes") },
                selected = currentRoute == Routes.ATHLETE_LIST,
                onClick = { navController.navigate(Routes.ATHLETE_LIST) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = VbtTeal,
                    selectedTextColor = VbtTeal,
                    unselectedIconColor = VbtTextSecondary,
                    unselectedTextColor = VbtTextSecondary
                )
            )
        }
    }
}
