package com.app.buscandohuellas.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.app.buscandohuellas.SupabaseClient
import com.app.buscandohuellas.ui.screens.chat.ChatScreen
import com.app.buscandohuellas.ui.screens.createReport.CreateReportScreen
import com.app.buscandohuellas.ui.screens.editProfile.EditProfileScreen
import com.app.buscandohuellas.ui.screens.editReport.EditReportScreen
import com.app.buscandohuellas.ui.screens.home.HomeScreen
import com.app.buscandohuellas.ui.screens.home.HomeTab
import com.app.buscandohuellas.ui.screens.login.LoginScreen
import com.app.buscandohuellas.ui.screens.moderator.ModeratorScreen
import com.app.buscandohuellas.ui.screens.myReports.MyReportsScreen
import com.app.buscandohuellas.ui.screens.register.RegisterScreen
import com.app.buscandohuellas.ui.screens.reportDetail.ReportDetailScreen
import com.app.buscandohuellas.ui.screens.settings.NotificationSettingsScreen
import com.app.buscandohuellas.ui.screens.settings.SettingsScreen
import com.app.buscandohuellas.ui.theme.BrandColors
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.first

/** Claves usadas para avisar a la pantalla anterior que debe recargar. */
private const val PROFILE_UPDATED_KEY = "profile_updated"
private const val REPORT_UPDATED_KEY = "report_updated"

/** Transición suave para las cuatro pestañas principales. */
private val fadeEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    fadeIn(tween(220))
}

private val fadeExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    fadeOut(tween(220))
}

/**
 * NavHost central de la aplicación.
 *
 * Es la única fuente de verdad de la navegación:
 * - Resuelve la ruta inicial según la sesión de Supabase (splash → home/login).
 * - Compone la barra inferior solo en las rutas principales.
 * - Procesa deep links de notificaciones, tanto el extra `report_id` como
 *   `buscandohuellas://report/{reportId}`.
 * - Escucha el cierre de sesión global para volver al login limpiando el
 *   backstack.
 */
@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    pendingReportId: String? = null,
    onPendingReportIdConsumed: () -> Unit = {}
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val sessionStatus by SupabaseClient.client.auth.sessionStatus
        .collectAsStateWithLifecycle(initialValue = SessionStatus.Initializing)

    // 1. Ruta inicial: se espera a que Supabase termine de cargar la sesión.
    LaunchedEffect(Unit) {
        val status = SupabaseClient.client.auth.sessionStatus
            .first { it !is SessionStatus.Initializing }

        // Si un deep link ya navegó antes que este efecto, no se pisa la ruta.
        if (navController.currentBackStackEntry?.destination?.route != Screen.SPLASH) {
            return@LaunchedEffect
        }

        val destination = if (status is SessionStatus.Authenticated) {
            Screen.HOME
        } else {
            Screen.LOGIN
        }

        navController.navigate(destination) {
            popUpTo(Screen.SPLASH) { inclusive = true }
            launchSingleTop = true
        }
    }

    // 2. Deep link pendiente (extra `report_id` o URI). Se espera a que el
    // NavHost tenga una ruta real para no competir con el splash.
    LaunchedEffect(pendingReportId, sessionStatus, currentRoute) {
        val reportId = pendingReportId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (sessionStatus !is SessionStatus.Authenticated) return@LaunchedEffect
        if (currentRoute == null || currentRoute == Screen.SPLASH) return@LaunchedEffect

        navController.navigate(Screen.reportDetail(reportId)) {
            launchSingleTop = true
        }
        onPendingReportIdConsumed()
    }

    // 3. Cierre de sesión global (logout manual, token revocado o cuenta
    // eliminada): se vuelve al login limpiando todo el backstack.
    LaunchedEffect(Unit) {
        SupabaseClient.client.auth.sessionStatus.collect { status ->
            if (status !is SessionStatus.NotAuthenticated) return@collect

            val route = navController.currentBackStackEntry?.destination?.route
            if (route == null ||
                route == Screen.SPLASH ||
                route == Screen.LOGIN ||
                route == Screen.REGISTER
            ) {
                return@collect
            }

            navController.navigate(Screen.LOGIN) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    // La barra inferior no navega a la ruta actual y conserva el estado de cada
    // pestaña mediante saveState/restoreState.
    val navigateToMain: (String) -> Unit = { route ->
        if (currentRoute != route) {
            navController.navigate(route) {
                popUpTo(Screen.HOME) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        // Las pantallas manejan sus propios insets; aquí solo se reserva el
        // espacio de la barra inferior cuando está visible.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (currentRoute != null && currentRoute in Screen.MAIN_ROUTES) {
                AppBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = navigateToMain
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.SPLASH,
            modifier = Modifier.padding(innerPadding),
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
            composable(
                route = Screen.SPLASH,
                enterTransition = fadeEnter,
                exitTransition = fadeExit,
                popEnterTransition = fadeEnter,
                popExitTransition = fadeExit
            ) {
                SplashContent()
            }

            composable(
                route = Screen.LOGIN,
                enterTransition = fadeEnter,
                exitTransition = fadeExit,
                popEnterTransition = fadeEnter,
                popExitTransition = fadeExit
            ) {
                LoginScreen(
                    onLoginSuccess = { user ->
                        val destination =
                            if (user.role.equals("MODERATOR", ignoreCase = true)) {
                                Screen.MODERATOR_DASHBOARD
                            } else {
                                Screen.HOME
                            }

                        navController.navigate(destination) {
                            popUpTo(Screen.LOGIN) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onNavigateToRegister = { navController.navigate(Screen.REGISTER) }
                )
            }

            composable(
                route = Screen.REGISTER,
                enterTransition = fadeEnter,
                exitTransition = fadeExit,
                popEnterTransition = fadeEnter,
                popExitTransition = fadeExit
            ) {
                RegisterScreen(
                    onRegisterSuccess = {
                        navController.navigate(Screen.HOME) {
                            popUpTo(Screen.LOGIN) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onNavigateBack = { navController.navigateUp() }
                )
            }

            composable(
                route = Screen.HOME,
                enterTransition = fadeEnter,
                exitTransition = fadeExit,
                popEnterTransition = fadeEnter,
                popExitTransition = fadeExit
            ) { entry ->
                HomeDestination(
                    backStackEntry = entry,
                    tab = HomeTab.HOME,
                    navController = navController,
                    onNavigateToMain = navigateToMain
                )
            }

            composable(
                route = Screen.MAP,
                enterTransition = fadeEnter,
                exitTransition = fadeExit,
                popEnterTransition = fadeEnter,
                popExitTransition = fadeExit
            ) { entry ->
                HomeDestination(
                    backStackEntry = entry,
                    tab = HomeTab.MAP,
                    navController = navController,
                    onNavigateToMain = navigateToMain
                )
            }

            composable(
                route = Screen.ALERTS,
                enterTransition = fadeEnter,
                exitTransition = fadeExit,
                popEnterTransition = fadeEnter,
                popExitTransition = fadeExit
            ) { entry ->
                HomeDestination(
                    backStackEntry = entry,
                    tab = HomeTab.ALERTS,
                    navController = navController,
                    onNavigateToMain = navigateToMain
                )
            }

            composable(
                route = Screen.PROFILE,
                enterTransition = fadeEnter,
                exitTransition = fadeExit,
                popEnterTransition = fadeEnter,
                popExitTransition = fadeExit
            ) { entry ->
                HomeDestination(
                    backStackEntry = entry,
                    tab = HomeTab.PROFILE,
                    navController = navController,
                    onNavigateToMain = navigateToMain
                )
            }

            composable(
                route = Screen.MY_REPORTS_ROUTE,
                arguments = listOf(
                    navArgument(Screen.ARG_USER_ID) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { entry ->
                val userId = entry.arguments?.getString(Screen.ARG_USER_ID).orEmpty()

                MyReportsScreen(
                    userId = userId,
                    onNavigateBack = { navController.navigateUp() },
                    onReportClick = { reportId ->
                        if (reportId.isNotBlank()) {
                            navController.navigate(Screen.reportDetail(reportId))
                        }
                    },
                    onEditReport = { reportId ->
                        if (reportId.isNotBlank()) {
                            navController.navigate(Screen.editReport(reportId))
                        }
                    },
                    onCreateReport = { navController.navigate(Screen.CREATE_REPORT) }
                )
            }

            composable(
                route = Screen.EDIT_PROFILE,
                arguments = listOf(
                    navArgument(Screen.ARG_USER_ID) { type = NavType.StringType }
                )
            ) { entry ->
                val userId = entry.arguments?.getString(Screen.ARG_USER_ID).orEmpty()

                // Perfil debe recargar al volver, incluso si solo cambió la foto.
                val notifyProfileUpdated = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.let { handle ->
                            val current = handle.get<Int>(PROFILE_UPDATED_KEY) ?: 0
                            handle[PROFILE_UPDATED_KEY] = current + 1
                        }
                }

                EditProfileScreen(
                    userId = userId,
                    onNavigateBack = {
                        notifyProfileUpdated()
                        navController.navigateUp()
                    },
                    onSaved = {
                        notifyProfileUpdated()
                        navController.navigateUp()
                    }
                )
            }

            composable(Screen.CREATE_REPORT) {
                CreateReportScreen(
                    onReportCreated = { navController.navigateUp() },
                    onNavigateBack = { navController.navigateUp() }
                )
            }

            composable(
                route = Screen.REPORT_DETAIL,
                arguments = listOf(
                    navArgument(Screen.ARG_REPORT_ID) { type = NavType.StringType }
                ),
                deepLinks = listOf(
                    navDeepLink { uriPattern = Screen.REPORT_DEEP_LINK_PATTERN }
                )
            ) { entry ->
                val reportId = entry.arguments?.getString(Screen.ARG_REPORT_ID).orEmpty()
                val reportUpdated by entry.savedStateHandle
                    .getStateFlow(REPORT_UPDATED_KEY, 0)
                    .collectAsStateWithLifecycle()

                ReportDetailScreen(
                    onNavigateBack = { navController.navigateUp() },
                    onEditClick = {
                        if (reportId.isNotBlank()) {
                            navController.navigate(Screen.editReport(reportId))
                        }
                    },
                    onContactClick = { ownerId, ownerName, ownerAvatarUrl, reportId ->
                        if (ownerId.isNotBlank()) {
                            navController.navigate(
                                Screen.chat(
                                    ownerId = ownerId,
                                    ownerName = ownerName,
                                    ownerAvatarUrl = ownerAvatarUrl,
                                    reportId = reportId
                                )
                            )
                        }
                    },
                    refreshSignal = reportUpdated
                )
            }

            composable(
                route = Screen.CHAT,
                arguments = listOf(
                    navArgument(Screen.ARG_OWNER_ID) { type = NavType.StringType },
                    navArgument(Screen.ARG_OWNER_NAME) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument(Screen.ARG_OWNER_AVATAR) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument(Screen.ARG_REPORT_ID) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) {
                ChatScreen(
                    onNavigateBack = { navController.navigateUp() }
                )
            }

            composable(
                route = Screen.EDIT_REPORT,
                arguments = listOf(
                    navArgument(Screen.ARG_REPORT_ID) { type = NavType.StringType }
                )
            ) {
                EditReportScreen(
                    onNavigateBack = { navController.navigateUp() },
                    onReportUpdated = {
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.let { handle ->
                                val current = handle.get<Int>(REPORT_UPDATED_KEY) ?: 0
                                handle[REPORT_UPDATED_KEY] = current + 1
                            }
                        navController.navigateUp()
                    }
                )
            }

            composable(Screen.NOTIFICATION_SETTINGS) {
                NotificationSettingsScreen(
                    onNavigateBack = { navController.navigateUp() }
                )
            }

            composable(Screen.SETTINGS) {
                SettingsScreen(
                    onNavigateBack = { navController.navigateUp() },
                    onAccountDeleted = {
                        // La cuenta ya no existe: se limpia todo el backstack.
                        navController.navigate(Screen.LOGIN) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Screen.MODERATOR_DASHBOARD) {
                ModeratorScreen(
                    onReportClick = { reportId ->
                        if (reportId.isNotBlank()) {
                            navController.navigate(Screen.reportDetail(reportId))
                        }
                    },
                    onLogout = {
                        navController.navigate(Screen.LOGIN) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    }
}

/**
 * Contenido compartido por las cuatro pestañas principales. Cada ruta crea su
 * propia entrada en el backstack y su propio ViewModel, de modo que
 * `restoreState` conserva el estado de cada pestaña.
 */
@Composable
private fun HomeDestination(
    backStackEntry: NavBackStackEntry,
    tab: HomeTab,
    navController: NavHostController,
    onNavigateToMain: (String) -> Unit
) {
    val profileUpdated by backStackEntry.savedStateHandle
        .getStateFlow(PROFILE_UPDATED_KEY, 0)
        .collectAsStateWithLifecycle()

    HomeScreen(
        selectedTab = tab,
        onTabSelected = { selected -> onNavigateToMain(selected.toRoute()) },
        onNavigateToCreateReport = { navController.navigate(Screen.CREATE_REPORT) },
        onReportClick = { reportId ->
            if (reportId.isNotBlank()) {
                navController.navigate(Screen.reportDetail(reportId))
            }
        },
        onProfileClick = { onNavigateToMain(Screen.PROFILE) },
        onMyReportsClick = { userId ->
            navController.navigate(Screen.myReports(userId))
        },
        onEditProfileClick = { userId ->
            // "me" permite que el ViewModel resuelva la sesión si el perfil
            // todavía no terminó de cargar.
            navController.navigate(Screen.editProfile(userId.ifBlank { "me" }))
        },
        onNotificationsSettingsClick = {
            navController.navigate(Screen.NOTIFICATION_SETTINGS)
        },
        onSettingsClick = {
            navController.navigate(Screen.SETTINGS)
        },
        onLogout = {
            navController.navigate(Screen.LOGIN) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        },
        refreshSignal = profileUpdated
    )
}

/** Convierte una pestaña en su ruta de navegación. */
private fun HomeTab.toRoute(): String = when (this) {
    HomeTab.HOME -> Screen.HOME
    HomeTab.MAP -> Screen.MAP
    HomeTab.ALERTS -> Screen.ALERTS
    HomeTab.PROFILE -> Screen.PROFILE
}

/** Pantalla mínima mientras Supabase carga la sesión inicial. */
@Composable
private fun SplashContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandColors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Pets,
                contentDescription = null,
                tint = BrandColors.Primary,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            CircularProgressIndicator(color = BrandColors.Primary)
        }
    }
}
