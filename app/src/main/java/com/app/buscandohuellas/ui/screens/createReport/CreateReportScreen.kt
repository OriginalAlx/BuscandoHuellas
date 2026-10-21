package com.app.buscandohuellas.ui.screens.createReport

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
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
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * ============================================================================
 * CÓMO INTEGRAR ESTA PANTALLA (Objetivo 2 + geolocalización del Objetivo 3)
 * ============================================================================
 *
 * 1) En el NavHost de MainActivity.kt agrega el destino:
 *
 *      import com.app.buscandohuellas.ui.screens.createReport.CreateReportScreen
 *      ...
 *      composable("create_report") {
 *          CreateReportScreen(
 *              onReportCreated = { navController.popBackStack() },
 *              onNavigateBack = { navController.popBackStack() }
 *          )
 *      }
 *
 * 2) En la pantalla Home añade un FloatingActionButton que navegue:
 *
 *      Scaffold(
 *          floatingActionButton = {
 *              FloatingActionButton(onClick = { navController.navigate("create_report") }) {
 *                  Icon(Icons.Filled.Add, contentDescription = "Nuevo reporte")
 *              }
 *          }
 *      ) { padding -> /* contenido Home */ }
 *
 * 3) Permisos: ya declarados en AndroidManifest.xml
 *      <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
 *      <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
 *      <uses-permission android:name="android.permission.CAMERA" />
 *      <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
 *      <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
 *
 * 4) Supabase Storage: crear bucket "pet-reports" (público) en Storage del proyecto.
 * ============================================================================
 */
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
    val selectedImageUri by viewModel.selectedImageUri.collectAsStateWithLifecycle()
    val isUploadingImage by viewModel.isUploadingImage.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Permiso de ubicación: se pide antes de consultar el FusedLocationProviderClient.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.getCurrentLocation()
        } else {
            viewModel.onLocationPermissionDenied()
        }
    }

    // Galería: ActivityResultContracts.GetContent() (SAF, no requiere permisos en API >= 19)
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.onImageSelected(uri)
        }
    }

    // Cámara: ActivityResultContracts.TakePicturePreview() (thumbnail)
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            viewModel.onImageCaptured(bitmap)
        }
    }

    // Permiso de cámara
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraLauncher.launch(null)
        } else {
            scope.launch { snackbarHostState.showSnackbar("Se necesita permiso de cámara para tomar una foto.") }
        }
    }

    // Al publicar con éxito se navega de vuelta.
    LaunchedEffect(uiState) {
        if (uiState is CreateReportUiState.Success) {
            onReportCreated()
        }
    }

    // Errores amigables vía Snackbar.
    val errorMessage = (uiState as? CreateReportUiState.Error)?.message
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
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
                }
            )

            // Sección de foto
            PhotoSection(
                imageUri = selectedImageUri,
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
                    viewModel.selectImageFromGallery()
                    galleryLauncher.launch("image/*")
                },
                onRemovePhoto = { viewModel.clearImage() }
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
}

@Composable
private fun LocationSection(
    isLocationLoading: Boolean,
    latitude: Double?,
    longitude: Double?,
    onRequestLocation: () -> Unit
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

            when {
                isLocationLoading -> Row(
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

                latitude != null && longitude != null -> Row(
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

                else -> Button(
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
        }
    }
}

@Composable
private fun PhotoSection(
    imageUri: android.net.Uri?,
    isUploading: Boolean,
    enabled: Boolean,
    onTakePhoto: () -> Unit,
    onPickFromGallery: () -> Unit,
    onRemovePhoto: () -> Unit
) {
    var menuExpanded = remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Foto de la mascota (opcional)",
            style = MaterialTheme.typography.titleSmall
        )

        if (imageUri == null) {
            // Sin imagen: borde punteado con opción de añadir foto
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
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "Añadir foto",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box {
                            OutlinedButton(
                                onClick = { menuExpanded.value = true },
                                enabled = enabled && !isUploading
                            ) {
                                Text("Tomar foto o seleccionar")
                            }
                            DropdownMenu(
expanded = menuExpanded.value,
                                onDismissRequest = { menuExpanded.value = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Tomar foto") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.CameraAlt, contentDescription = null)
                                    },
                                    onClick = {
                                        menuExpanded.value = false
                                        onTakePhoto()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Seleccionar de galería") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                                    },
                                    onClick = {
                                        menuExpanded.value = false
                                        onPickFromGallery()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Con imagen seleccionada
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Foto de la mascota",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Overlay con botón eliminar
                if (enabled) {
                    IconButton(
                        onClick = onRemovePhoto,
                        enabled = !isUploading,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Eliminar foto",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }

                // Indicador de carga si se está subiendo
                if (isUploading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(color = Color.White)
                            Text(
                                text = "Subiendo imagen...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Barra de progreso durante la subida
        if (isUploading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun formatCoordinate(value: Double): String = "%.4f".format(Locale.US, value)

/**
 * Borde punteado (Compose no lo trae de serie): se dibuja un contorno redondeado
 * con un PathEffect de guiones.
 */
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
