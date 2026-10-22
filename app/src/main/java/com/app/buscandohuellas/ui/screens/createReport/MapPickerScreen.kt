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


private val DEFAULT_LOCATION = LatLng(40.416775, -3.703790)

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

    
    val startTarget = LatLng(
        initialLatitude ?: currentLatitude ?: DEFAULT_LOCATION.latitude,
        initialLongitude ?: currentLongitude ?: DEFAULT_LOCATION.longitude
    )

    
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(startTarget, 14f)
    }

    
    val markerState = remember { MarkerState(startTarget) }

    
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
                    
                    markerState.position = latLng
                }
            )

            
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


@Composable
private fun GoogleMapView(
    cameraPositionState: com.google.maps.android.compose.CameraPositionState,
    markerState: MarkerState,
    onMapClick: (LatLng) -> Unit
) {
    
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


private fun formatCoordPrecise(value: Double): String =
    String.format(Locale.US, "%.6f", value)
