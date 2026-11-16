package com.app.buscandohuellas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.app.buscandohuellas.ui.screens.createReport.CreateReportScreen
import com.app.buscandohuellas.ui.screens.editReport.EditReportScreen
import com.app.buscandohuellas.ui.screens.home.HomeScreen
import com.app.buscandohuellas.ui.screens.login.LoginScreen
import com.app.buscandohuellas.ui.screens.moderator.ModeratorScreen
import com.app.buscandohuellas.ui.screens.register.RegisterScreen
import com.app.buscandohuellas.ui.screens.reportDetail.ReportDetailScreen
import com.app.buscandohuellas.ui.theme.BuscandoHuellasTheme
import dagger.hilt.android.AndroidEntryPoint

private const val REPORT_UPDATED_KEY = "report_updated"


@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OsmDroidInitializer.initialize(this)
        enableEdgeToEdge()
        setContent {
            BuscandoHuellasTheme {
                AppNavHost()
            }
        }
    }
}

@Composable
@Preview(showSystemUi = true)
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = "login",
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(300))
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ) + fadeOut(tween(200))
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(300))
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ) + fadeOut(tween(200))
        }
    ) {
        composable("login") {
            LoginScreen(
                onLoginSuccess = { user ->
                    val destino = if (user.role.equals("MODERATOR", ignoreCase = true)) {
                        "moderator_dashboard"
                    } else {
                        "home"
                    }
                    navController.navigate(destino) {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onNavigateToRegister = { navController.navigate("register") }
            )
        }

        composable("register") {
            RegisterScreen(
                onRegisterSuccess = { _ ->
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("home") {
            HomeScreen(
                onNavigateToCreateReport = { navController.navigate("create_report") },
                onReportClick = { reportId ->
                    if (reportId.isNotBlank()) {
                        navController.navigate("report_detail/$reportId")
                    }
                },
                onProfileClick = {


                },
                onLogout = {
                    navController.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }

        composable("create_report") {
            CreateReportScreen(
                onReportCreated = { navController.popBackStack() },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "report_detail/{reportId}",
            arguments = listOf(
                navArgument("reportId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val reportId = backStackEntry.arguments?.getString("reportId").orEmpty()
            val reportUpdated by backStackEntry.savedStateHandle
                .getStateFlow(REPORT_UPDATED_KEY, 0)
                .collectAsStateWithLifecycle()

            ReportDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onEditClick = {
                    if (reportId.isNotBlank()) {
                        navController.navigate("edit_report/$reportId")
                    }
                },
                refreshSignal = reportUpdated
            )
        }

        composable(
            route = "edit_report/{reportId}",
            arguments = listOf(
                navArgument("reportId") { type = NavType.StringType }
            )
        ) {
            EditReportScreen(
                onNavigateBack = { navController.popBackStack() },
                onReportUpdated = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.let { handle ->
                            val current = handle.get<Int>(REPORT_UPDATED_KEY) ?: 0
                            handle[REPORT_UPDATED_KEY] = current + 1
                        }
                    navController.popBackStack()
                }
            )
        }

        composable("moderator_dashboard") {
            ModeratorScreen(
                onReportClick = { reportId ->
                    if (reportId.isNotBlank()) {
                        navController.navigate("report_detail/$reportId")
                    }
                },
                onLogout = {
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
