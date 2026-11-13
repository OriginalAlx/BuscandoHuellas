package com.app.buscandohuellas.ui.screens.editReport

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.app.buscandohuellas.domain.model.ReportStatus
import com.app.buscandohuellas.ui.theme.BrandColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditReportScreen(
    onNavigateBack: () -> Unit,
    onReportUpdated: () -> Unit,
    viewModel: EditReportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addPhotos(uris)
        }
    }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is EditReportUiState.Success -> {
                Toast.makeText(
                    context,
                    state.warning ?: "Cambios guardados correctamente.",
                    Toast.LENGTH_LONG
                ).show()
                onReportUpdated()
            }

            is EditReportUiState.Editing -> {
                state.message?.let { message ->
                    snackbarHostState.showSnackbar(message)
                    viewModel.consumeMessage()
                }
            }

            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Editar reporte",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandColors.TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = BrandColors.TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandColors.Surface
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = BrandColors.Background
    ) { paddingValues ->
        when (val state = uiState) {
            EditReportUiState.Loading -> LoadingState(
                modifier = Modifier.padding(paddingValues)
            )

            is EditReportUiState.Error -> ErrorState(
                message = state.message,
                onRetry = viewModel::retry,
                modifier = Modifier.padding(paddingValues)
            )

            is EditReportUiState.Editing -> EditReportContent(
                form = state.form,
                isSaving = false,
                contentPadding = paddingValues,
                onPetNameChange = viewModel::updatePetName,
                onDescriptionChange = viewModel::updateDescription,
                onStatusSelected = viewModel::updateStatus,
                onRemoveExistingPhoto = viewModel::removePhoto,
                onRemoveNewPhoto = viewModel::removeNewPhoto,
                onAddPhotos = { galleryLauncher.launch("image/*") },
                onSave = viewModel::saveChanges,
                onCancel = onNavigateBack
            )

            is EditReportUiState.Saving -> EditReportContent(
                form = state.form,
                isSaving = true,
                contentPadding = paddingValues,
                onPetNameChange = viewModel::updatePetName,
                onDescriptionChange = viewModel::updateDescription,
                onStatusSelected = viewModel::updateStatus,
                onRemoveExistingPhoto = viewModel::removePhoto,
                onRemoveNewPhoto = viewModel::removeNewPhoto,
                onAddPhotos = { galleryLauncher.launch("image/*") },
                onSave = viewModel::saveChanges,
                onCancel = onNavigateBack
            )

            is EditReportUiState.Success -> LoadingState(
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = BrandColors.Primary)
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(BrandColors.AccentLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Pets,
                contentDescription = null,
                tint = BrandColors.Accent,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No pudimos cargar el reporte",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = BrandColors.TextPrimary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = BrandColors.TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = BrandColors.Primary)
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Reintentar")
        }
    }
}

@Composable
private fun EditReportContent(
    form: EditReportForm,
    isSaving: Boolean,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    onPetNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onStatusSelected: (ReportStatus) -> Unit,
    onRemoveExistingPhoto: (String) -> Unit,
    onRemoveNewPhoto: (Uri) -> Unit,
    onAddPhotos: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = form.petName,
            onValueChange = onPetNameChange,
            label = { Text("Nombre de la mascota *") },
            singleLine = true,
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = BrandColors.TextPrimary),
            colors = editTextFieldColors()
        )

        OutlinedTextField(
            value = form.description,
            onValueChange = onDescriptionChange,
            label = { Text("Descripción *") },
            placeholder = { Text("Raza, color, último lugar visto...") },
            minLines = 4,
            maxLines = 8,
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = BrandColors.TextPrimary),
            colors = editTextFieldColors()
        )

        StatusSelector(
            selectedStatus = form.status,
            enabled = !isSaving,
            onStatusSelected = onStatusSelected
        )

        PhotoSection(
            form = form,
            enabled = !isSaving,
            onRemoveExistingPhoto = onRemoveExistingPhoto,
            onRemoveNewPhoto = onRemoveNewPhoto,
            onAddPhotos = onAddPhotos
        )

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = onSave,
            enabled = !isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = BrandColors.Primary,
                contentColor = Color.White,
                disabledContainerColor = BrandColors.PrimaryLight,
                disabledContentColor = BrandColors.TextSecondary
            )
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Guardando...",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Guardar cambios",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        OutlinedButton(
            onClick = onCancel,
            enabled = !isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, BrandColors.Divider)
        ) {
            Text(
                text = "Cancelar",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = BrandColors.TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun editTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = BrandColors.TextPrimary,
    unfocusedTextColor = BrandColors.TextPrimary,
    cursorColor = BrandColors.Primary,
    focusedBorderColor = BrandColors.Primary,
    unfocusedBorderColor = BrandColors.Divider,
    focusedLabelColor = BrandColors.Primary,
    unfocusedLabelColor = BrandColors.TextSecondary,
    focusedContainerColor = BrandColors.Surface,
    unfocusedContainerColor = BrandColors.Surface,
    focusedPlaceholderColor = BrandColors.TextSecondary,
    unfocusedPlaceholderColor = BrandColors.TextSecondary
)

@Composable
private fun StatusSelector(
    selectedStatus: ReportStatus,
    enabled: Boolean,
    onStatusSelected: (ReportStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Estado del reporte",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = BrandColors.TextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportStatus.entries.forEach { status ->
                val selected = status == selectedStatus
                val shape = RoundedCornerShape(12.dp)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .clip(shape)
                        .background(status.backgroundColor)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) {
                                status.textColor
                            } else {
                                status.textColor.copy(alpha = 0.25f)
                            },
                            shape = shape
                        )
                        .clickable(enabled = enabled) { onStatusSelected(status) }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Estado seleccionado",
                            tint = status.textColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Text(
                        text = status.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = status.textColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhotoSection(
    form: EditReportForm,
    enabled: Boolean,
    onRemoveExistingPhoto: (String) -> Unit,
    onRemoveNewPhoto: (Uri) -> Unit,
    onAddPhotos: () -> Unit
) {
    val canAddMore = form.totalPhotoCount < EditReportViewModel.MAX_PHOTOS

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Fotos de la mascota",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = BrandColors.TextPrimary
            )
            Text(
                text = "${form.totalPhotoCount}/${EditReportViewModel.MAX_PHOTOS} fotos",
                style = MaterialTheme.typography.labelMedium,
                color = BrandColors.TextSecondary
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 3
        ) {
            form.imageUrls.forEach { url ->
                key(url) {
                    PhotoTile(
                        model = url,
                        contentDescription = "Foto actual de la mascota",
                        enabled = enabled,
                        onRemove = { onRemoveExistingPhoto(url) }
                    )
                }
            }

            form.newPhotoUris.forEach { uri ->
                key(uri) {
                    PhotoTile(
                        model = uri,
                        contentDescription = "Foto nueva de la mascota",
                        enabled = enabled,
                        onRemove = { onRemoveNewPhoto(uri) }
                    )
                }
            }

            if (canAddMore) {
                AddPhotoTile(enabled = enabled, onClick = onAddPhotos)
            }
        }

        if (form.totalPhotoCount == 0) {
            Text(
                text = "Puedes agregar hasta ${EditReportViewModel.MAX_PHOTOS} fotos.",
                style = MaterialTheme.typography.bodySmall,
                color = BrandColors.TextSecondary
            )
        }
    }
}

@Composable
private fun PhotoTile(
    model: Any,
    contentDescription: String,
    enabled: Boolean,
    onRemove: () -> Unit
) {
    Box(modifier = Modifier.size(104.dp)) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(model)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
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
                        color = BrandColors.Surface.copy(alpha = 0.9f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Eliminar foto",
                    tint = BrandColors.Accent,
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
            .background(BrandColors.Surface)
            .dashedBorder(
                color = BrandColors.Primary.copy(alpha = 0.5f),
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
                tint = BrandColors.Primary
            )
            Text(
                text = "Agregar",
                style = MaterialTheme.typography.labelSmall,
                color = BrandColors.Primary
            )
        }
    }
}

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
