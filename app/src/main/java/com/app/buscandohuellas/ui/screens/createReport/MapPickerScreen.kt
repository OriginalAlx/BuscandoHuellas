package com.app.buscandohuellas.ui.screens.createReport

import android.content.pm.PackageManager
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
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import java.util.Locale

/**
 * ============================================================================
 * MAP PICKER SCREEN (pantalla completa para seleccionar ubicación en el mapa)
 * ============================================================================
 *
 * - Mapa interactivo centrado en la ubicación actual del usuario (si existe
 *   permiso); la ubicación de reserva solo se usa si no se consigue la del
 *   dispositivo.
 * - El usuario puede hacer clic en cualquier punto del mapa o arrastrar el
 *   marcador para reposicionarlo (drag & drop).
 * - Botón "Usar mi ubicación actual" dentro del mapa: reutiliza
 *   FusedLocationProviderClient a través del callback onUseCurrentLocation.
 * - "Confirmar ubicación" devuelve las coordenadas (lat, lon) al ViewModel.
 * - "Cancelar" cierra sin cambios.
 *
 * Estado: la cámara sobrevive a rotaciones (CameraPositionState guarda su
 * posición) y los datos confirmados viven en CreateReportViewModel, que
 * también sobrevive a cambios de configuración.
 * ============================================================================
 */

/** Ubicación de reserva usada únicamente si no se obtiene la del dispositivo. */
private val DEFAULT_LOCATION = LatLng(40.416775, -3.703790)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapPickerScreen(
    /** Ubicación ya confirmada previamente (si existe), para inicializar el marcador. */
    initialLatitude: Double?,
    initialLongitude: Double?,
    /** Última ubicación conocida del usuario (obtenida mientras el picker está abierto). */
    currentLatitude: Double?,
    currentLongitude: Double?,
    /** true mientras se solicita la ubicación actual al FusedLocationProviderClient. */
    isLocating: Boolean,
    /** Solicita la ubicación actual (requiere permiso ya concedido o el flujo de la UI). */
    onUseCurrentLocation: () -> Unit,
    /** Confirma la ubicación seleccionada y la devuelve al CreateReportViewModel. */
    onConfirm: (latitude: Double, longitude: Double) -> Unit,
    /** Cierra el selector sin cambios. */
    onCancel: () -> Unit
) {
    val context = LocalContext.current

    // Verifica si la API Key de Google Maps está configurada en el manifest.
    val hasMapApiKey = remember {
        try {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            val key = appInfo.metaData?.getString("com.google.android.geo.API_KEY")
            !key.isNullOrBlank()
        } catch (error: Exception) {
            false
        }
    }

    // Punto inicial del mapa: confirmación previa > ubicación actual > reserva.
    val startTarget = LatLng(
        initialLatitude ?: currentLatitude ?: DEFAULT_LOCATION.latitude,
        initialLongitude ?: currentLongitude ?: DEFAULT_LOCATION.longitude
    )

    // Estado de la cámara: recuerda su posición ante rotaciones de pantalla.
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(startTarget, 14f)
    }

    // Marcador seleccionable por clic o drag & drop.
    val markerState = remember { MarkerState(startTarget) }

    // Re-centra la cámara y el marcador cuando se obtiene la ubicación actual.
    LaunchedEffect(currentLatitude, currentLongitude) {
        if (currentLatitude != null && currentLongitude != null) {
            val current = LatLng(currentLatitude, currentLongitude)
            markerState.position = current
            cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(current, 15f))
        }
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
        if (!hasMapApiKey) {
            // Sin API Key: mensaje explicativo en lugar de un mapa en blanco.
            NoApiKeyContent(modifier = Modifier.padding(paddingValues))
            return@Scaffold
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            GoogleMapView(
                cameraPositionState = cameraPositionState,
                markerState = markerState,
                onMapClick = { latLng ->
                    // Clic en cualquier punto del mapa: mueve el marcador ahí.
                    markerState.position = latLng
                }
            )

            // Botón flotante: "Usar mi ubicación actual" dentro del mapa.
            FloatingActionButton(
                onClick = onUseCurrentLocation,
                modifier = Modifier
                    .align(Alignment.TopEnd)
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
                        contentDescription = "Usar mi ubicación actual"
                    )
                }
            }

            // Tarjeta inferior con las coordenadas seleccionadas y botones finales.
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
                        text = "Ubicación: ${formatCoordPrecise(markerState.position.latitude)}, " +
                            formatCoordPrecise(markerState.position.longitude),
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
                                onConfirm(
                                    markerState.position.latitude,
                                    markerState.position.longitude
                                )
                            },
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

/** Mapa interactivo envuelto en AndroidView-compatible composable de maps-compose. */
@Composable
private fun GoogleMapView(
    cameraPositionState: com.google.maps.android.compose.CameraPositionState,
    markerState: MarkerState,
    onMapClick: (LatLng) -> Unit
) {
    // GoogleMap de maps-compose: clic para reposicionar + marcador arrastrable.
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        onMapClick = onMapClick
    ) {
        Marker(
            state = markerState,
            draggable = true,
            title = "Ubicación del reporte",
            snippet = "Arrastra o toca el mapa para ajustar"
        )
    }
}

/** Contenido mostrado cuando falta la API Key de Google Maps. */
@Composable
private fun NoApiKeyContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Place,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Text("Falta la API Key de Google Maps", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Añade en local.properties:\nMAPS_API_KEY=TU_API_KEY\n\n" +
                "Créala en https://console.cloud.google.com con la SDK \"Maps SDK for Android\" activada.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Formatea una coordenada con 6 decimales y puntos consistentes. */
private fun formatCoordPrecise(value: Double): String =
    String.format(Locale.US, "%.6f", value)
