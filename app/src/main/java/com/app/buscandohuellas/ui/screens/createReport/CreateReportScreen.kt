package com.app.buscandohuellas.ui.screens.createReport

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateReportScreen(
    onReportCreated: () -> Unit,
    onNavigateBack: () -> Unit = onReportCreated,
    viewModel: CreateReportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val petName by viewModel.petName.collectAsStateWithLifecycle()
    val description by viewModel.description.collectAsStateWithLifecycle()
    val latitude by viewModel.latitude.collectAsStateWithLifecycle()
    val longitude by viewModel.longitude.collectAsStateWithLifecycle()
    val isLocationLoading by viewModel.isLocationLoading.collectAsStateWithLifecycle()
    val selectedImageUris by viewModel.selectedImageUris.collectAsStateWithLifecycle()
    val isUploadingImage by viewModel.isUploadingImage.collectAsStateWithLifecycle()
    val isMapPickerOpen by viewModel.isMapPickerOpen.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.getCurrentLocation()
        } else {
            viewModel.onLocationPermissionDenied()
        }
    }

    
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addPhotos(uris)
        }
    }

    
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            viewModel.onImageCaptured(bitmap)
        }
    }

    
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraLauncher.launch(null)
        } else {
            scope.launch { snackbarHostState.showSnackbar("Se necesita permiso de cámara para tomar una foto.") }
        }
    }

    
    LaunchedEffect(uiState) {
        val success = uiState as? CreateReportUiState.Success
        if (success != null) {
            success.warning?.let { message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
            onReportCreated()
        }
    }

    
    val errorMessage = (uiState as? CreateReportUiState.Error)?.message
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
        }
    }

    LaunchedEffect(isMapPickerOpen) {
        if (isMapPickerOpen && latitude == null && longitude == null) {
            val locationGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (locationGranted) {
                viewModel.getCurrentLocation()
            } else {
                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nuevo Reporte") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = petName,
                onValueChange = viewModel::onPetNameChange,
                label = { Text("Nombre de la mascota *") },
                singleLine = true,
                enabled = uiState !is CreateReportUiState.Loading,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("Descripción (raza, color, último lugar visto...)") },
                minLines = 3,
                maxLines = 6,
                enabled = uiState !is CreateReportUiState.Loading,
                modifier = Modifier.fillMaxWidth()
            )

            LocationSection(
                isLocationLoading = isLocationLoading,
                latitude = latitude,
                longitude = longitude,
                onRequestLocation = {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                    if (granted) {
                        viewModel.getCurrentLocation()
                    } else {
                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                },
                onSelectOnMap = { viewModel.openMapPicker() }
            )

            
            PhotoSection(
                photos = selectedImageUris,
                maxPhotos = CreateReportViewModel.MAX_PHOTOS,
                isUploading = isUploadingImage,
                enabled = uiState !is CreateReportUiState.Loading,
                onTakePhoto = {
                    val cameraGranted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    if (cameraGranted) {
                        cameraLauncher.launch(null)
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                onPickFromGallery = {
                    galleryLauncher.launch("image/*")
                },
                onRemovePhoto = { uri -> viewModel.removePhoto(uri) }
            )

            Button(
                onClick = viewModel::createReport,
                enabled = petName.isNotBlank() &&
                    latitude != null &&
                    longitude != null &&
                    !isUploadingImage &&
                    uiState !is CreateReportUiState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (uiState is CreateReportUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Publicando...")
                } else {
                    Text("Publicar Reporte")
                }
            }
        }
    }

    
    if (isMapPickerOpen) {
        MapPickerScreen(
            initialLatitude = latitude,
            initialLongitude = longitude,
            currentLatitude = latitude,
            currentLongitude = longitude,
            isLocating = isLocationLoading,
            onUseCurrentLocation = {
                
                val locationGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (locationGranted) {
                    viewModel.getCurrentLocation()
                } else {
                    permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            },
            onConfirm = { selectedLat, selectedLon ->
                viewModel.onMapLocationSelected(selectedLat, selectedLon)
            },
            onCancel = { viewModel.closeMapPicker() }
        )
    }
}

@Composable
private fun LocationSection(
    isLocationLoading: Boolean,
    latitude: Double?,
    longitude: Double?,
    onRequestLocation: () -> Unit,
    onSelectOnMap: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Ubicación",
                style = MaterialTheme.typography.titleSmall
            )

            if (isLocationLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Obteniendo ubicación...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                
                Button(
                    onClick = onRequestLocation,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.MyLocation,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Obtener mi ubicación actual")
                }
            }

            
            OutlinedButton(
                onClick = onSelectOnMap,
                enabled = !isLocationLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.Map,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Seleccionar en mapa")
            }

            
            if (latitude != null && longitude != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Ubicación obtenida",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Ubicación: ${formatCoordinate(latitude)}, " +
                            formatCoordinate(longitude),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoSection(
    photos: List<android.net.Uri>,
    maxPhotos: Int,
    isUploading: Boolean,
    enabled: Boolean,
    onTakePhoto: () -> Unit,
    onPickFromGallery: () -> Unit,
    onRemovePhoto: (android.net.Uri) -> Unit
) {
    val canAddMore = photos.size < maxPhotos

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Fotos de la mascota (opcional)",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "${photos.size}/$maxPhotos fotos",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (photos.isEmpty()) {
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .dashedBorder(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            cornerRadius = 12.dp
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhotoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                        Button(
                            onClick = onPickFromGallery,
                            enabled = enabled && !isUploading
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Agregar fotos")
                        }
                        OutlinedButton(
                            onClick = onTakePhoto,
                            enabled = enabled && !isUploading
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Tomar foto")
                        }
                    }
                }
            }
        } else {
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(photos, key = { it.toString() }) { uri ->
                    PhotoThumbnail(
                        uri = uri,
                        enabled = enabled && !isUploading,
                        onRemove = { onRemovePhoto(uri) }
                    )
                }

                if (canAddMore) {
                    item {
                        AddPhotoTile(
                            enabled = enabled && !isUploading,
                            onClick = onPickFromGallery
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (canAddMore) {
                    OutlinedButton(
                        onClick = onTakePhoto,
                        enabled = enabled && !isUploading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Tomar foto")
                    }
                }
                if (photos.size >= maxPhotos) {
                    Text(
                        text = "Máximo $maxPhotos fotos alcanzado",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        if (isUploading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                text = "Subiendo fotos...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PhotoThumbnail(
    uri: android.net.Uri,
    enabled: Boolean,
    onRemove: () -> Unit
) {
    Box(modifier = Modifier.size(104.dp)) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(uri)
                .crossfade(true)
                .build(),
            contentDescription = "Foto seleccionada",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
        )

        if (enabled) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(28.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Eliminar foto",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun AddPhotoTile(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(104.dp)
            .clip(RoundedCornerShape(12.dp))
            .dashedBorder(
                color = MaterialTheme.colorScheme.outlineVariant,
                cornerRadius = 12.dp
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Agregar",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun formatCoordinate(value: Double): String = "%.4f".format(Locale.US, value)


private fun Modifier.dashedBorder(
    color: Color,
    strokeWidth: Dp = 1.dp,
    cornerRadius: Dp = 12.dp,
    dashLength: Dp = 8.dp,
    gapLength: Dp = 8.dp
): Modifier = drawBehind {
    val stroke = Stroke(
        width = strokeWidth.toPx(),
        pathEffect = PathEffect.dashPathEffect(
            intervals = floatArrayOf(dashLength.toPx(), gapLength.toPx()),
            phase = 0f
        )
    )
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(cornerRadius.toPx()),
        style = stroke
    )
}
