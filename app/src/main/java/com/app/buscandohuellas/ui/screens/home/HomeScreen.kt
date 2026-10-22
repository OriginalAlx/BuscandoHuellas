package com.app.buscandohuellas.ui.screens.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.buscandohuellas.domain.model.Report
import com.app.buscandohuellas.ui.theme.BrandColors
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices


@Composable
fun HomeScreen(
    onNavigateToCreateReport: () -> Unit,
    onReportClick: (String) -> Unit = {},
    onProfileClick: () -> Unit = {},
    onLogout: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val reportsForMap by viewModel.reportsForMap.collectAsStateWithLifecycle()
    val isLoadingMap by viewModel.isLoadingMap.collectAsStateWithLifecycle()
    val mapError by viewModel.mapError.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            HomeTopBar(
                selectedTab = uiState.selectedTab,
                unreadAlerts = uiState.unreadAlertsCount,
                initials = uiState.profile.initials,
                onNotificationsClick = { viewModel.onTabSelected(HomeTab.ALERTS) },
                onProfileClick = onProfileClick
            )
        },
        bottomBar = {
            AppBottomNavigationBar(
                selectedTab = uiState.selectedTab,
                unreadAlerts = uiState.unreadAlertsCount,
                onTabSelected = viewModel::onTabSelected
            )
        },
        floatingActionButton = {
            
            if (uiState.selectedTab == HomeTab.MAP) {
                MapReportFab(onClick = onNavigateToCreateReport)
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(BrandColors.BackgroundTop, BrandColors.Background)
                    )
                )
                .padding(paddingValues)
        ) {
            when (uiState.selectedTab) {
                HomeTab.HOME -> HomeContent(
                    state = uiState,
                    onReportClick = onReportClick,
                    onAlertsBannerClick = {
                        viewModel.onTabSelected(HomeTab.ALERTS)
                    },
                    onSeeAllReportsClick = {  }
                )

                HomeTab.MAP -> MapContent(
                    reports = reportsForMap,
                    isLoading = isLoadingMap,
                    errorMessage = mapError,
                    onMarkerClick = onReportClick,
                    onRetry = viewModel::loadReports
                )

                HomeTab.ALERTS -> AlertsContent(
                    state = uiState,
                    onFilterSelected = viewModel::onAlertsFilterSelected,
                    onMarkAllAsRead = viewModel::markAlertsAsRead,
                    onAlertClick = viewModel::onAlertClicked
                )

                HomeTab.PROFILE -> ProfileContent(
                    profile = uiState.profile,
                    onEditProfileClick = onProfileClick,
                    onMenuItemClick = { menuItem ->
                        if (menuItem == ProfileMenuItem.LOGOUT) onLogout()
                    }
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    selectedTab: HomeTab,
    unreadAlerts: Int,
    initials: String,
    onNotificationsClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = "Buscando Huellas",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = BrandColors.TextPrimary
            )
        },
        actions = {
            if (selectedTab == HomeTab.MAP) {
                IconButton(onClick = {  }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Filtros",
                        tint = BrandColors.TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                    
                }
            }

            IconButton(onClick = onNotificationsClick) {
                BadgedBox(
                    badge = {
                        if (unreadAlerts > 0) {
                            Badge(containerColor = BrandColors.Accent) {
                                Text(text = unreadAlerts.toString())
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = "Notificaciones",
                        tint = BrandColors.TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            UserAvatar(
                initials = initials,
                size = 36.dp,
                onClick = onProfileClick
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
    )
}


@Composable
private fun HomeContent(
    state: HomeUiState,
    onReportClick: (String) -> Unit,
    onAlertsBannerClick: () -> Unit,
    onSeeAllReportsClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "BUENOS DÍAS",
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 1.sp,
            color = BrandColors.TextSecondary
        )
        Text(
            text = "Hola, ${state.firstName}",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = BrandColors.TextPrimary
        )

        if (state.nearbyAlertsCount > 0) {
            NearbyAlertsBanner(count = state.nearbyAlertsCount, onClick = onAlertsBannerClick)
        }

        SectionHeader(
            overline = "COMUNIDAD",
            title = "Reportes recientes",
            actionLabel = "Ver todos",
            onActionClick = onSeeAllReportsClick
        )

        if (state.recentReports.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Pets,
                title = "Aún no hay reportes",
                message = "Cuando la comunidad publique mascotas perdidas o encontradas cerca de ti, aparecerán aquí."
            )
        } else {
            state.recentReports.forEach { report ->
                PetReportCard(
                    report = report,
                    onClick = { onReportClick(report.id) }
                )
            }
        }
    }
}


@Composable
private fun MapContent(
    reports: List<Report>,
    isLoading: Boolean,
    errorMessage: String?,
    onMarkerClick: (String) -> Unit,
    onRetry: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var currentLocation by remember { mutableStateOf<LatLng?>(null) }
    var hasCenteredOnUser by remember { mutableStateOf(false) }

    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    @SuppressLint("MissingPermission")
    fun fetchUserLocation() {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val currentLocationRequest = com.google.android.gms.location.CurrentLocationRequest.Builder()
            .setPriority(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY)
            .build()

        fusedLocationClient.getCurrentLocation(currentLocationRequest, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    currentLocation = LatLng(location.latitude, location.longitude)
                }
            }
            .addOnFailureListener { _ ->
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { cachedLocation ->
                        if (cachedLocation != null) {
                            currentLocation = LatLng(cachedLocation.latitude, cachedLocation.longitude)
                        }
                    }
            }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            fetchUserLocation()
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(DEFAULT_MAP_CENTER, 12f)
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fetchUserLocation()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    LaunchedEffect(currentLocation) {
        val userLocation = currentLocation ?: return@LaunchedEffect
        if (!hasCenteredOnUser) {
            cameraPositionState.position =
                CameraPosition.fromLatLngZoom(userLocation, 13f)
            hasCenteredOnUser = true
        }
    }

    fun currentCenter(): LatLng {
        val located = reports.firstOrNull { it.latitude != null && it.longitude != null }
        return located?.let { LatLng(it.latitude!!, it.longitude!!) } ?: DEFAULT_MAP_CENTER
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState
        ) {
            reports.forEach { report ->
                val lat = report.latitude
                val lon = report.longitude
                if (lat != null && lon != null) {
                    val icon = remember(report.status) { getMarkerIcon(report.status) }
                    Marker(
                        state = MarkerState(position = LatLng(lat, lon)),
                        title = report.petName,
                        snippet = report.status,
                        icon = icon,
                        onInfoWindowClick = { onMarkerClick(report.id.orEmpty()) }
                    )
                }
            }
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = BrandColors.Accent,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
            )
        }

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(44.dp)
            )
        }

        if (!isLoading && errorMessage == null &&
            reports.none { it.latitude != null && it.longitude != null }
        ) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = BrandColors.Surface)
            ) {
                Text(
                    text = "No hay reportes activos en esta zona",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BrandColors.TextPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }

        MapRecenterButton(
            onClick = {
                val target = currentLocation ?: currentCenter()
                cameraPositionState.position = CameraPosition.fromLatLngZoom(target, 13f)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp)
        )
    }
}


@Composable
private fun AlertsContent(
    state: HomeUiState,
    onFilterSelected: (AlertFilter) -> Unit,
    onMarkAllAsRead: () -> Unit,
    onAlertClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "ACTIVIDAD",
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 1.sp,
            color = BrandColors.TextSecondary
        )
        Text(
            text = "Alertas",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = BrandColors.TextPrimary
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            AlertFilterChip(
                label = "Todas",
                selected = state.alertsFilter == AlertFilter.ALL,
                onClick = { onFilterSelected(AlertFilter.ALL) }
            )
            Spacer(modifier = Modifier.width(10.dp))
            AlertFilterChip(
                label = "Sin leer",
                selected = state.alertsFilter == AlertFilter.UNREAD,
                onClick = { onFilterSelected(AlertFilter.UNREAD) }
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Marcar leídas",
                style = MaterialTheme.typography.labelLarge,
                color = BrandColors.Primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onMarkAllAsRead)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }

        state.visibleAlerts.forEach { alert ->
            AlertCard(
                alert = alert,
                onClick = { onAlertClick(alert.id) }
            )
        }

        if (state.visibleAlerts.isEmpty()) {
            Text(
                text = "No tienes alertas por aquí.",
                style = MaterialTheme.typography.bodyMedium,
                color = BrandColors.TextSecondary
            )
        }
    }
}


@Composable
private fun ProfileContent(
    profile: UserProfileUi,
    onEditProfileClick: () -> Unit,
    onMenuItemClick: (ProfileMenuItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        ProfileHeader(profile = profile, onEditProfileClick = onEditProfileClick)

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BrandColors.Surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ProfileMenuItem.entries.forEach { menuItem ->
                        ProfileMenuRow(
                            item = menuItem,
                            onClick = { onMenuItemClick(menuItem) }
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun ProfileHeader(profile: UserProfileUi, onEditProfileClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(
                    bottomStart = 32.dp,
                    bottomEnd = 32.dp
                )
            )
            .background(BrandColors.PrimaryLight)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(BrandColors.Surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Pets,
                    contentDescription = null,
                    tint = BrandColors.Accent,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        UserAvatar(
            initials = profile.initials,
            size = 96.dp,
            borderWidth = 4.dp,
            borderColor = BrandColors.Surface
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = profile.fullName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = BrandColors.TextPrimary
        )
        Text(
            text = profile.email,
            style = MaterialTheme.typography.bodyMedium,
            color = BrandColors.TextSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onEditProfileClick,
            shape = RoundedCornerShape(50)
        ) {
            Text(
                text = "Editar perfil",
                color = BrandColors.Primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}


@androidx.compose.ui.tooling.preview.Preview(showBackground = true, heightDp = 900)
@Composable
private fun HomeContentPreview() {
    MaterialTheme {
        HomeContent(
            state = dummyHomeUiState(),
            onReportClick = {},
            onAlertsBannerClick = {},
            onSeeAllReportsClick = {}
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, heightDp = 900)
@Composable
private fun AlertsContentPreview() {
    MaterialTheme {
        AlertsContent(
            state = dummyHomeUiState(),
            onFilterSelected = {},
            onMarkAllAsRead = {},
            onAlertClick = {}
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, heightDp = 900)
@Composable
private fun ProfileContentPreview() {
    MaterialTheme {
        ProfileContent(
            profile = dummyHomeUiState().profile,
            onEditProfileClick = {},
            onMenuItemClick = {}
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, heightDp = 900)
@Composable
private fun MapContentPreview() {
    MaterialTheme {
        MapContent(
            reports = listOf(
                Report(
                    userId = "1",
                    petName = "Luna",
                    description = "Beagle perdida",
                    status = "LOST",
                    latitude = 40.4200,
                    longitude = -3.6900
                ),
                Report(
                    userId = "2",
                    petName = "Nala",
                    description = "Gatita encontrada",
                    status = "FOUND",
                    latitude = 40.4250,
                    longitude = -3.7050
                )
            ),
            isLoading = false,
            errorMessage = null,
            onMarkerClick = {},
            onRetry = {}
        )
    }
}

private val DEFAULT_MAP_CENTER = LatLng(0.0, 0.0)

private fun getMarkerIcon(status: String): BitmapDescriptor = when (status.uppercase()) {
    "FOUND" -> BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
    "SIGHTED" -> BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
    else -> BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
}
