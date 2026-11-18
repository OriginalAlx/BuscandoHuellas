package com.app.buscandohuellas.navigation

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.app.buscandohuellas.ui.theme.BrandColors

private data class BottomBarItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

/**
 * Barra de navegación inferior compartida por las cuatro rutas principales.
 *
 * La selección se deriva de [currentRoute] (obtenido con
 * `currentBackStackEntryAsState` en [AppNavHost]), de modo que la barra y el
 * backstack siempre están sincronizados. En las subpantallas no se compone.
 */
@Composable
fun AppBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        BottomBarItem(Screen.HOME, "Inicio", Icons.Filled.Home),
        BottomBarItem(Screen.MAP, "Mapa", Icons.Filled.Map),
        BottomBarItem(Screen.ALERTS, "Alertas", Icons.Filled.Notifications),
        BottomBarItem(Screen.PROFILE, "Perfil", Icons.Filled.Person)
    )

    NavigationBar(
        modifier = modifier,
        containerColor = BrandColors.Surface,
        tonalElevation = 0.dp
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route

            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item.route) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = BrandColors.Primary,
                    selectedTextColor = BrandColors.Primary,
                    indicatorColor = BrandColors.PrimaryLight,
                    unselectedIconColor = BrandColors.TextSecondary,
                    unselectedTextColor = BrandColors.TextSecondary
                )
            )
        }
    }
}
