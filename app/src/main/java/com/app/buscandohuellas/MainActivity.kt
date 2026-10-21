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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.app.buscandohuellas.ui.screens.createReport.CreateReportScreen
import com.app.buscandohuellas.ui.screens.home.HomeScreen
import com.app.buscandohuellas.ui.screens.login.LoginScreen
import com.app.buscandohuellas.ui.screens.register.RegisterScreen
import com.app.buscandohuellas.ui.theme.BuscandoHuellasTheme
import dagger.hilt.android.AndroidEntryPoint

// Código de Deep Links eliminado - confirmación de email desactivada en Supabase
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                    // TODO: navegar al detalle del reporte cuando exista la pantalla
                    // navController.navigate("report_detail/$reportId")
                },
                onProfileClick = {
                    // TODO: navegar a "editar perfil" cuando exista la pantalla
                    // navController.navigate("edit_profile")
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

        composable("moderator_dashboard") {
            PlaceholderScreen(
                titulo = "Panel de moderador",
                mensaje = "Sesión iniciada como moderador. Aquí va el panel de moderación."
            )
        }
    }
}

@Composable
private fun PlaceholderScreen(titulo: String, mensaje: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = titulo,
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = mensaje,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
