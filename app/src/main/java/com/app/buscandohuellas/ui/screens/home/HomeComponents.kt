package com.app.buscandohuellas.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.buscandohuellas.ui.theme.BrandColors


enum class HomeTab(val label: String) {
    HOME("Inicio"),
    MAP("Mapa"),
    ALERTS("Alertas"),
    PROFILE("Perfil")
}


enum class AlertFilter(val label: String) {
    ALL("Todas"),
    UNREAD("Sin leer")
}


enum class PetReportStatus {
    LOST,
    FOUND
}


enum class AlertKind {
    INFO,
    FOUND
}


enum class ProfileMenuItem(
    val title: String,
    val icon: ImageVector,
    val isDestructive: Boolean = false
) {
    MY_REPORTS("Mis reportes", Icons.Filled.Pets),
    SETTINGS("Configuración", Icons.Filled.Settings),
    LOGOUT("Cerrar sesión", Icons.AutoMirrored.Filled.ExitToApp, isDestructive = true)
}


data class RecentReport(
    val id: String,
    val petName: String,
    val breed: String,
    val locationLine: String,
    val status: PetReportStatus
)


data class AlertItemUi(
    val id: String,
    val title: String,
    val message: String,
    val timeAgo: String,
    val kind: AlertKind,
    val isUnread: Boolean
)


data class UserProfileUi(
    val fullName: String = "",
    val email: String = "",
    val reportsCount: Int = 0,
    val contributionsCount: Int = 0,
    val reunionsCount: Int = 0
) {
    
    val initials: String
        get() {
            val source = fullName.trim().ifBlank { email.substringBefore('@') }
            if (source.isBlank()) return ""
            return source.split(' ')
                .filter { it.isNotBlank() }
                .take(2)
                .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                .joinToString("")
        }
}


data class HomeUiState(
    val selectedTab: HomeTab = HomeTab.HOME,
    val nearbyAlertsCount: Int = 0,
    val recentReports: List<RecentReport> = emptyList(),
    val alerts: List<AlertItemUi> = emptyList(),
    val alertsFilter: AlertFilter = AlertFilter.ALL,
    val profile: UserProfileUi = UserProfileUi()
) {
    
    val visibleAlerts: List<AlertItemUi>
        get() = if (alertsFilter == AlertFilter.UNREAD) alerts.filter { it.isUnread } else alerts

    
    val unreadAlertsCount: Int
        get() = alerts.count { it.isUnread }

    
    val firstName: String
        get() = profile.fullName.substringBefore(' ').ifBlank { "usuario" }
}


fun dummyHomeUiState(): HomeUiState = HomeUiState(
    selectedTab = HomeTab.HOME,
    nearbyAlertsCount = 2,
    recentReports = listOf(
        RecentReport("1", "Luna", "Beagle", "Parque del Sol · 350 m", PetReportStatus.LOST),
        RecentReport("2", "Nala", "Gatita", "Av. Libertad · 1,2 km", PetReportStatus.FOUND),
        RecentReport("3", "Milo", "Mestizo", "Barrio Norte · 1,8 km", PetReportStatus.LOST)
    ),
    alerts = listOf(
        AlertItemUi(
            id = "1",
            title = "¡Mascota avistada cerca!",
            message = "Un vecino vio a una mascota similar a Luna en Parque del Sol.",
            timeAgo = "Hace 5 min",
            kind = AlertKind.INFO,
            isUnread = true
        ),
        AlertItemUi(
            id = "2",
            title = "Nuevo reporte en tu zona",
            message = "Se encontró una gatita naranja a 1,2 km de tu ubicación.",
            timeAgo = "Hace 28 min",
            kind = AlertKind.FOUND,
            isUnread = true
        ),
        AlertItemUi(
            id = "3",
            title = "Tu reporte recibió una pista",
            message = "Martín comentó en el reporte de Luna.",
            timeAgo = "Hace 2 h",
            kind = AlertKind.INFO,
            isUnread = false
        ),
        AlertItemUi(
            id = "4",
            title = "Final feliz para Coco",
            message = "Coco volvió a casa gracias a la comunidad.",
            timeAgo = "Ayer",
            kind = AlertKind.FOUND,
            isUnread = false
        )
    ),
    profile = UserProfileUi(
        fullName = "Camila Rodríguez",
        email = "camila.rodriguez@email.com",
        reportsCount = 3,
        contributionsCount = 12,
        reunionsCount = 2
    )
)


@Composable
fun UserAvatar(
    initials: String,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    borderWidth: Dp = 0.dp,
    borderColor: Color = BrandColors.Surface
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(BrandColors.Primary)
            .border(width = borderWidth, color = borderColor, shape = CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value / 2.6f).sp
        )
        
    }
}


@Composable
fun NearbyAlertsBanner(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = BrandColors.PrimaryLight,
        contentColor = BrandColors.TextPrimary
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(BrandColors.Surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = BrandColors.Primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Tienes $count alertas cercanas",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = BrandColors.TextPrimary
                )
                Text(
                    text = "Hay nueva actividad en tu zona",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandColors.TextSecondary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clickable(onClick = onClick)
                    .clip(CircleShape)
                    .background(BrandColors.Surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Ver alertas",
                    tint = BrandColors.Primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}


@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (overline != null) {
            Text(
                text = overline,
                style = MaterialTheme.typography.labelMedium,
                color = BrandColors.TextSecondary,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = BrandColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            if (actionLabel != null && onActionClick != null) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = BrandColors.Primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onActionClick)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }
    }
}


@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BrandColors.Surface)
            .padding(horizontal = 20.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(BrandColors.PrimaryLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = BrandColors.Primary,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = BrandColors.TextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = BrandColors.TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}


@Composable
fun PetPhotoPlaceholder(size: Dp = 56.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(BrandColors.PrimaryLight),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Pets,
            contentDescription = null,
            tint = BrandColors.Primary,
            modifier = Modifier.size(size / 2.5f)
        )
        
    }
}


@Composable
fun ReportStatusChip(status: PetReportStatus, modifier: Modifier = Modifier) {
    val isLost = status == PetReportStatus.LOST
    val contentColor = if (isLost) BrandColors.Accent else BrandColors.Success
    val containerColor = if (isLost) {
        BrandColors.Accent.copy(alpha = 0.14f)
    } else {
        BrandColors.Success.copy(alpha = 0.14f)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(containerColor)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text = if (isLost) "PERDIDA" else "ENCONTRADA",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            color = contentColor
        )
    }
}


@Composable
fun PetReportCard(
    report: RecentReport,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BrandColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PetPhotoPlaceholder()

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = report.petName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = BrandColors.TextPrimary
                )
                Text(
                    text = report.breed,
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = BrandColors.TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = report.locationLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandColors.TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                ReportStatusChip(status = report.status)
                Spacer(modifier = Modifier.height(12.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Ver detalle",
                    tint = BrandColors.TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}


@Composable
fun AlertFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) BrandColors.Primary else BrandColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else BrandColors.TextSecondary
        )
    }
}


@Composable
fun AlertCard(alert: AlertItemUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val isFound = alert.kind == AlertKind.FOUND
    val icon = if (isFound) Icons.Filled.Pets else Icons.Filled.Notifications
    val iconTint = if (isFound) BrandColors.Success else BrandColors.Accent
    val iconBackground = if (isFound) BrandColors.SuccessLight else BrandColors.AccentLight

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BrandColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(iconBackground),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = alert.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = BrandColors.TextPrimary
                )
                Text(
                    text = alert.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandColors.TextSecondary
                )
                Text(
                    text = alert.timeAgo,
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandColors.TextSecondary
                )
            }

            if (alert.isUnread) {
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp, top = 6.dp)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(BrandColors.Info)
                )
            }
        }
    }
}


@Composable
fun ProfileStatItem(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = BrandColors.TextPrimary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = BrandColors.TextSecondary
        )
    }
}


@Composable
fun ProfileMenuRow(item: ProfileMenuItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val iconTint = if (item.isDestructive) BrandColors.Accent else BrandColors.Primary
    val iconBackground = if (item.isDestructive) {
        BrandColors.AccentLight
    } else {
        BrandColors.PrimaryLight
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = if (item.isDestructive) BrandColors.Accent else BrandColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = BrandColors.TextSecondary,
            modifier = Modifier.size(22.dp)
        )
    }
}


@Composable
fun MapReportFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = BrandColors.Accent,
        contentColor = Color.White,
        shape = RoundedCornerShape(16.dp),
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Pets,
            contentDescription = null,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Reportar",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
    }
}


@Composable
fun MapRecenterButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(BrandColors.Surface)
            .border(width = 1.dp, color = BrandColors.Divider, shape = CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.MyLocation,
            contentDescription = "Centrar mi ubicación",
            tint = BrandColors.Info,
            modifier = Modifier.size(22.dp)
        )
    }
}


@Composable
fun MapRadiusChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(BrandColors.Info.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = BrandColors.Info
        )
    }
}


@Composable
fun MapMarker(color: Color, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(BrandColors.Surface)
            .border(width = 2.dp, color = color, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Pets,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(size / 2)
        )
    }
}


@Composable
fun MapPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BrandColors.MapBackground)
    ) {
        
        
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(50))
                .background(BrandColors.Surface)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(BrandColors.Success)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Encontrados",
                style = MaterialTheme.typography.labelMedium,
                color = BrandColors.TextPrimary
            )
        }

        
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(300.dp)
                .clip(CircleShape)
                .background(BrandColors.Info.copy(alpha = 0.05f))
                .border(
                    width = 1.dp,
                    color = BrandColors.Info.copy(alpha = 0.35f),
                    shape = CircleShape
                )
        )

        
        MapMarker(
            color = BrandColors.Accent,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 120.dp)
        )
        MapMarker(
            color = BrandColors.Success,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 32.dp)
        )
        MapMarker(
            color = BrandColors.Accent,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 150.dp)
        )

        
        Text(
            text = "RÍO VERDE",
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 2.sp,
            color = BrandColors.Success.copy(alpha = 0.55f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp)
        )

        
        MapRadiusChip(
            text = "Radio de 2 km",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 76.dp)
        )
    }
}


data class BottomNavItem(val tab: HomeTab, val icon: ImageVector)


@Composable
fun AppBottomNavigationBar(
    selectedTab: HomeTab,
    unreadAlerts: Int,
    onTabSelected: (HomeTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        BottomNavItem(HomeTab.HOME, Icons.Filled.Home),
        BottomNavItem(HomeTab.MAP, Icons.Filled.Map),
        BottomNavItem(HomeTab.ALERTS, Icons.Filled.Notifications),
        BottomNavItem(HomeTab.PROFILE, Icons.Filled.Person)
    )

    NavigationBar(
        modifier = modifier,
        containerColor = BrandColors.Surface,
        tonalElevation = 0.dp
    ) {
        items.forEach { item ->
            val selected = item.tab == selectedTab

            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(item.tab) },
                icon = {
                    BadgedBox(
                        badge = {
                            if (item.tab == HomeTab.ALERTS && unreadAlerts > 0) {
                                Badge(
                                    containerColor = BrandColors.Accent,
                                    contentColor = Color.White
                                ) {
                                    Text(text = unreadAlerts.toString(), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.tab.label,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                label = {
                    Text(
                        text = item.tab.label,
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


@Composable
fun StatsDivider(modifier: Modifier = Modifier) {
    VerticalDivider(
        modifier = modifier.height(34.dp),
        color = BrandColors.Divider
    )
}
