package com.app.buscandohuellas.ui.screens.createReport

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapPickerScreen(
    initialLatitude: Double?,
    initialLongitude: Double?,
    currentLatitude: Double?,
    currentLongitude: Double?,
    isLocating: Boolean,
    onUseCurrentLocation: () -> Unit,
    onConfirm: (latitude: Double, longitude: Double) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val locationManager = remember {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    fun isGpsEnabled(): Boolean = runCatching {
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }.getOrDefault(false)

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

    fun initialPoint(): GeoPoint? = when {
        initialLatitude != null && initialLongitude != null ->
            GeoPoint(initialLatitude, initialLongitude)

        currentLatitude != null && currentLongitude != null ->
            GeoPoint(currentLatitude, currentLongitude)

        else -> null
    }

    var gpsEnabled by remember { mutableStateOf(isGpsEnabled()) }
    var locationPermissionGranted by remember { mutableStateOf(hasLocationPermission()) }
    var showGpsDialog by remember { mutableStateOf(false) }
    var mapInstance by remember { mutableStateOf<MapView?>(null) }
    var myLocationOverlay by remember { mutableStateOf<MyLocationNewOverlay?>(null) }
    var centeredOnUser by remember { mutableStateOf(false) }
    var selectedPoint by remember { mutableStateOf(initialPoint()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            hasLocationPermission()

        locationPermissionGranted = granted

        if (!granted) {
            Toast.makeText(
                context,
                "El permiso de ubicación es necesario para centrar el mapa en tu posición.",
                Toast.LENGTH_LONG
            ).show()
            return@rememberLauncherForActivityResult
        }

        val gpsNowEnabled = isGpsEnabled()
        gpsEnabled = gpsNowEnabled

        if (gpsNowEnabled) {
            onUseCurrentLocation()
        } else {
            showGpsDialog = true
        }
    }

    fun requestLocationPermissions() {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    fun openLocationSettings() {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure {
                Toast.makeText(
                    context,
                    "No se pudo abrir la configuración de ubicación.",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    fun requestLocationAccess() {
        val gpsNowEnabled = isGpsEnabled()
        gpsEnabled = gpsNowEnabled

        when {
            !gpsNowEnabled -> showGpsDialog = true
            !hasLocationPermission() -> requestLocationPermissions()
            else -> {
                locationPermissionGranted = true
                onUseCurrentLocation()
            }
        }
    }

    fun focusOnUserLocation() {
        if (!isGpsEnabled()) {
            gpsEnabled = false
            showGpsDialog = true
            return
        }
        if (!hasLocationPermission()) {
            requestLocationPermissions()
            return
        }

        val userPoint = myLocationOverlay?.myLocation
        if (userPoint != null) {
            mapInstance?.controller?.animateTo(userPoint)
            mapInstance?.controller?.setZoom(16.0)
        } else {
            onUseCurrentLocation()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val gpsNowEnabled = isGpsEnabled()
                gpsEnabled = gpsNowEnabled
                locationPermissionGranted = hasLocationPermission()

                when {
                    !gpsNowEnabled -> showGpsDialog = true
                    locationPermissionGranted -> onUseCurrentLocation()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        val gpsNowEnabled = isGpsEnabled()
        gpsEnabled = gpsNowEnabled

        when {
            !gpsNowEnabled -> showGpsDialog = true
            !hasLocationPermission() -> requestLocationPermissions()
            else -> onUseCurrentLocation()
        }
    }

    LaunchedEffect(gpsEnabled, locationPermissionGranted) {
        val map = mapInstance ?: return@LaunchedEffect
        if (gpsEnabled && locationPermissionGranted) {
            if (myLocationOverlay == null) {
                val overlay = MyLocationNewOverlay(map)
                overlay.enableMyLocation()
                overlay.runOnFirstFix {
                    overlay.myLocation?.let { point ->
                        map.controller.setCenter(point)
                        map.controller.setZoom(16.0)
                    }
                }
                myLocationOverlay = overlay
                map.overlays.add(overlay)
            }
            if (!centeredOnUser) {
                onUseCurrentLocation()
                centeredOnUser = true
            }
        }
    }

    LaunchedEffect(currentLatitude, currentLongitude) {
        if (currentLatitude != null && currentLongitude != null) {
            val point = GeoPoint(currentLatitude, currentLongitude)
            selectedPoint = point
            mapInstance?.controller?.animateTo(point)
        }
    }

    if (showGpsDialog) {
        AlertDialog(
            onDismissRequest = { showGpsDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.LocationOff,
                    contentDescription = null
                )
            },
            title = { Text("Activa el GPS") },
            text = {
                Text(
                    "La ubicación del dispositivo está desactivada. Actívala para centrar el " +
                        "mapa en tu posición actual."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showGpsDialog = false
                        openLocationSettings()
                    }
                ) {
                    Text("Activar GPS")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGpsDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Seleccionar ubicación") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Cerrar mapa")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        setBuiltInZoomControls(true)

                        val startPoint = selectedPoint
                        if (startPoint != null) {
                            controller.setZoom(16.0)
                            controller.setCenter(startPoint)
                        } else {
                            controller.setZoom(2.0)
                        }

                        val marker = Marker(this).apply {
                            position = startPoint ?: GeoPoint(0.0, 0.0)
                            isEnabled = startPoint != null
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            isDraggable = true
                            setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                                override fun onMarkerDrag(p0: Marker?) {}

                                override fun onMarkerDragEnd(p0: Marker?) {
                                    p0?.let { selectedPoint = it.position }
                                }

                                override fun onMarkerDragStart(p0: Marker?) {}
                            })
                        }
                        overlays.add(marker)

                        val mapEventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                                p?.let {
                                    selectedPoint = it
                                    marker.position = it
                                    marker.isEnabled = true
                                }
                                return true
                            }

                            override fun longPressHelper(p: GeoPoint?): Boolean = false
                        })
                        overlays.add(mapEventsOverlay)

                        mapInstance = this
                    }
                },
                update = { mapView ->
                    if (gpsEnabled && locationPermissionGranted && myLocationOverlay == null) {
                        val overlay = MyLocationNewOverlay(mapView)
                        overlay.enableMyLocation()
                        overlay.runOnFirstFix {
                            overlay.myLocation?.let { point ->
                                mapView.controller.setCenter(point)
                                mapView.controller.setZoom(16.0)
                            }
                        }
                        myLocationOverlay = overlay
                        mapView.overlays.add(overlay)
                    }

                    val currentMarker = mapView.overlays.find { it is Marker } as? Marker
                    val point = selectedPoint
                    if (currentMarker != null && point != null) {
                        currentMarker.position = point
                        currentMarker.isEnabled = true
                    }
                    mapView.invalidate()
                }
            )

            if (!locationPermissionGranted) {
                LocationPermissionOverlay(
                    onRequestPermission = { requestLocationAccess() },
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center)
                )
            }

            FloatingActionButton(
                onClick = {
                    when {
                        !isGpsEnabled() -> {
                            gpsEnabled = false
                            showGpsDialog = true
                        }

                        !hasLocationPermission() -> requestLocationPermissions()
                        else -> focusOnUserLocation()
                    }
                },
                containerColor = Color(0xFF2196F3),
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                if (isLocating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.MyLocation,
                        contentDescription = "Centrar en mi ubicación"
                    )
                }
            }

            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Place,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ubicación seleccionada",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }

                    Text(
                        text = selectedPoint?.let {
                            "Ubicación: ${formatCoordPrecise(it.latitude)}, " +
                                formatCoordPrecise(it.longitude)
                        } ?: "Aún no hay una ubicación seleccionada. Usa el botón de ubicación o toca el mapa.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancelar")
                        }
                        Button(
                            onClick = {
                                selectedPoint?.let {
                                    onConfirm(it.latitude, it.longitude)
                                }
                            },
                            enabled = selectedPoint != null,
                            modifier = Modifier
                                .weight(2f)
                                .height(48.dp)
                        ) {
                            Text("Confirmar ubicación")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationPermissionOverlay(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(Color(0xCC10131E)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = "Necesitamos tu ubicación",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Activar la capa de ubicación nos permite centrar el mapa en tu posición " +
                    "y sugerirle al usuario el punto más cercano de la mascota reportada.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFCBD5E1),
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.GpsFixed,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Solicitar permiso")
            }
        }
    }
}

private fun formatCoordPrecise(value: Double): String =
    String.format(Locale.US, "%.6f", value)
