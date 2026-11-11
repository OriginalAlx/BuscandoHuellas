package com.app.buscandohuellas.ui.screens.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withRotation
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.buscandohuellas.domain.model.Report
import com.app.buscandohuellas.ui.theme.BrandColors
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun HomeScreen(
    onNavigateToCreateReport: () -> Unit,
    onReportClick: (String) -> Unit = {},
    onProfileClick: () -> Unit = {},
    onLogout: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val reports by viewModel.reports.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val mapError by viewModel.mapError.collectAsStateWithLifecycle()
    val shouldShowMap by viewModel.shouldShowMap.collectAsStateWithLifecycle()
    val userLocation by viewModel.userLocation.collectAsStateWithLifecycle()
    val hasLocationPermission by viewModel.hasLocationPermission.collectAsStateWithLifecycle()
    val showPermissionDeniedDialog by viewModel.showPermissionDeniedDialog.collectAsStateWithLifecycle()

    val context = LocalContext.current

    fun hasLocationPermissionNow(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

    LaunchedEffect(Unit) {
        val granted = hasLocationPermissionNow()
        viewModel.syncLocationPermission(granted)
        if (granted) {
            viewModel.getCurrentLocation(context)
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onLocationPermissionResult(granted, context)
    }

    val requestLocationPermission: () -> Unit = {
        if (hasLocationPermissionNow()) {
            viewModel.onLocationPermissionResult(true, context)
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

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
        containerColor = ComposeColor.Transparent
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
                    userLocation = userLocation,
                    isRefreshing = isRefreshing,
                    onRefresh = viewModel::refreshReports,
                    onReportClick = onReportClick,
                    onAlertsBannerClick = { viewModel.onTabSelected(HomeTab.ALERTS) },
                    onSeeAllReportsClick = { viewModel.onTabSelected(HomeTab.MAP) }
                )

                HomeTab.MAP -> MapContent(
                    reports = reports,
                    isLoading = isLoading,
                    errorMessage = mapError,
                    shouldShowMap = shouldShowMap,
                    hasLocationPermission = hasLocationPermission,
                    userLocation = userLocation,
                    onLocationPermissionRequest = requestLocationPermission,
                    onReportClick = onReportClick
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

    if (showPermissionDeniedDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissPermissionDialog,
            title = { Text("Permiso de ubicación requerido") },
            text = {
                Text(
                    "Para mostrar el mapa y los reportes cercanos necesitamos acceso a tu " +
                            "ubicación. Habilita el permiso en la configuración de la aplicación."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissPermissionDialog()
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }
                ) {
                    Text("Abrir configuración")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissPermissionDialog) {
                    Text("Cancelar")
                }
            }
        )
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
                IconButton(onClick = { }) {
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
        colors = TopAppBarDefaults.topAppBarColors(containerColor = ComposeColor.Transparent)
    )
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    userLocation: GeoPoint?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onReportClick: (String) -> Unit,
    onAlertsBannerClick: () -> Unit,
    onSeeAllReportsClick: () -> Unit
) {
    var reportsPage by remember { mutableStateOf(0) }
    val pageCount = maxOf(
        1,
        (state.recentReports.size + REPORTS_PER_PAGE - 1) / REPORTS_PER_PAGE
    )
    val currentPage = reportsPage.coerceIn(0, pageCount - 1)
    val pullRefreshState = rememberPullToRefreshState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullToRefresh(
                state = pullRefreshState,
                isRefreshing = isRefreshing,
                onRefresh = onRefresh
            )
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
                val visibleReports = state.recentReports
                    .drop(currentPage * REPORTS_PER_PAGE)
                    .take(REPORTS_PER_PAGE)

                visibleReports.forEach { report ->
                    PetReportCard(
                        report = report,
                        userLocation = userLocation,
                        onClick = { onReportClick(report.id) }
                    )
                }

                if (pageCount > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { reportsPage = currentPage - 1 },
                            enabled = currentPage > 0
                        ) {
                            Text("Anterior")
                        }
                        Text(
                            text = "Página ${currentPage + 1} de $pageCount",
                            style = MaterialTheme.typography.labelMedium,
                            color = BrandColors.TextSecondary
                        )
                        OutlinedButton(
                            onClick = { reportsPage = currentPage + 1 },
                            enabled = currentPage < pageCount - 1
                        ) {
                            Text("Siguiente")
                        }
                    }
                }
            }
        }

        PullToRefreshDefaults.Indicator(
            state = pullRefreshState,
            isRefreshing = isRefreshing,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun MapContent(
    reports: List<Report>,
    isLoading: Boolean,
    errorMessage: String?,
    shouldShowMap: Boolean,
    hasLocationPermission: Boolean,
    userLocation: GeoPoint?,
    onLocationPermissionRequest: () -> Unit,
    onReportClick: (String) -> Unit
) {
    var mapInstance by remember { mutableStateOf<MapView?>(null) }
    var selectedReport by remember { mutableStateOf<Report?>(null) }
    // Conserva el último reporte elegido para que el panel tenga contenido
    // durante la animación de salida.
    var panelReport by remember { mutableStateOf<Report?>(null) }

    // Los iconos solo dependen del estado y la densidad: se reutilizan entre
    // marcadores para no crear un bitmap por cada recomposición.
    val pawIcons = remember { mutableMapOf<String, Drawable>() }
    fun pawIcon(status: String): Drawable =
        pawIcons.getOrPut(status.uppercase()) { getPawPrintDrawable(status) }

    LaunchedEffect(userLocation) {
        val location = userLocation ?: return@LaunchedEffect
        mapInstance?.controller?.apply {
            setZoom(16.0)
            setCenter(location)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!shouldShowMap) {
            MapActivationPlaceholder(modifier = Modifier.fillMaxSize())
        } else {
            AndroidView(
                factory = { ctx ->
                    Configuration.getInstance().userAgentValue = ctx.packageName
                    val map = MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        setBuiltInZoomControls(true)
                        controller.setZoom(12.0)
                    }
                    if (hasLocationPermission) {
                        map.overlays.add(
                            MyLocationNewOverlay(map).apply {
                                enableMyLocation()
                            }
                        )
                    }
                    mapInstance = map
                    map
                },
                modifier = Modifier.fillMaxSize(),
                update = { mapView ->
                    mapView.overlays.removeAll { it is Marker && it.id == REPORT_MARKER_ID }

                    reports.forEach { report ->
                        if (report.latitude != null && report.longitude != null) {
                            val marker = Marker(mapView).apply {
                                id = REPORT_MARKER_ID
                                position = GeoPoint(report.latitude, report.longitude)
                                title = report.petName
                                snippet = "${report.status}\n${report.description}"
                                icon = pawIcon(report.status)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                setOnMarkerClickListener { _, _ ->
                                    panelReport = report
                                    selectedReport = report
                                    true
                                }
                            }
                            mapView.overlays.add(marker)
                        }
                    }

                    mapView.invalidate()
                }
            )
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = BrandColors.Accent,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
            )
        }

        if (shouldShowMap && isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(44.dp)
            )
        }

        if (shouldShowMap && !isLoading && errorMessage == null &&
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
            onClick = onLocationPermissionRequest,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp)
        )

        if (!shouldShowMap && errorMessage == null) {
            Text(
                text = "Toca el botón de ubicación para activar el mapa",
                style = MaterialTheme.typography.labelSmall,
                color = BrandColors.TextSecondary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 88.dp)
            )
        }

        AnimatedVisibility(
            visible = selectedReport != null,
            enter = slideInVertically(initialOffsetY = { height -> height }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { height -> height }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp)
                .padding(bottom = 96.dp)
        ) {
            panelReport?.let { report ->
                ReportMarkerPanel(
                    report = report,
                    onClose = { selectedReport = null },
                    onViewDetail = {
                        selectedReport = null
                        onReportClick(report.id.orEmpty())
                    }
                )
            }
        }
    }
}

private const val REPORT_MARKER_ID = "buscando_huellas_report_marker"
private const val REPORTS_PER_PAGE = 5

@Composable
private fun MapActivationPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(BrandColors.MapBackground)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Map,
                contentDescription = null,
                tint = BrandColors.Primary,
                modifier = Modifier.size(72.dp)
            )
            Text(
                text = "Tu ubicación está apagada",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = BrandColors.TextPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Actívala para ver los reportes de mascotas perdidas y encontradas cerca de ti en el mapa.",
                style = MaterialTheme.typography.bodyMedium,
                color = BrandColors.TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Dibuja una huella de mascota vectorial con Canvas/Path: una almohadilla
 * principal y cuatro dedos en arco, con borde blanco de 2dp para que resalte
 * sobre el mapa.
 */
private fun getPawPrintDrawable(status: String): Drawable {
    val pawColor = when (status.uppercase()) {
        "LOST" -> "#DC2626".toColorInt()
        "FOUND" -> "#059669".toColorInt()
        "SIGHTED" -> "#2563EB".toColorInt()
        else -> "#6B7280".toColorInt()
    }

    val displayMetrics = Resources.getSystem().displayMetrics
    val density = displayMetrics.density
    val sizeDp = 60f
    val sizePx = (sizeDp * density).toInt().coerceAtLeast(1)

    val bitmap = createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
        // La densidad hace que el drawable mida 60dp reales en pantalla.
        this.density = displayMetrics.densityDpi
    }
    val canvas = Canvas(bitmap)
    canvas.scale(density, density)

    val fillPaint = Paint().apply {
        color = pawColor
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    val borderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f // 2dp de borde a cada lado de la silueta
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    fun drawToe(cx: Float, cy: Float, rx: Float, ry: Float, rotation: Float) {
        val left = cx - rx
        val top = cy - ry
        val right = cx + rx
        val bottom = cy + ry
        canvas.withRotation(rotation, cx, cy) {
            drawOval(left, top, right, bottom, borderPaint)
            drawOval(left, top, right, bottom, fillPaint)
        }
    }

    // Cuatro dedos distribuidos en arco sobre la almohadilla.
    drawToe(cx = 13.5f, cy = 22.5f, rx = 5f, ry = 7f, rotation = -28f)
    drawToe(cx = 23.5f, cy = 15f, rx = 5.5f, ry = 7.5f, rotation = -9f)
    drawToe(cx = 36.5f, cy = 15f, rx = 5.5f, ry = 7.5f, rotation = 9f)
    drawToe(cx = 46.5f, cy = 22.5f, rx = 5f, ry = 7f, rotation = 28f)

    // Almohadilla principal, más ancha arriba y redondeada abajo.
    val pad = Path().apply {
        moveTo(30f, 29f)
        cubicTo(39f, 29f, 46f, 35f, 46f, 42f)
        cubicTo(46f, 50f, 39f, 55f, 30f, 55f)
        cubicTo(21f, 55f, 14f, 50f, 14f, 42f)
        cubicTo(14f, 35f, 21f, 29f, 30f, 29f)
        close()
    }
    canvas.drawPath(pad, borderPaint)
    canvas.drawPath(pad, fillPaint)

    return bitmap.toDrawable(Resources.getSystem())
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
            userLocation = GeoPoint(-25.7535, -57.2533),
            isRefreshing = false,
            onRefresh = {},
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
            shouldShowMap = false,
            hasLocationPermission = false,
            userLocation = null,
            onLocationPermissionRequest = {},
            onReportClick = {}
        )
    }
}
