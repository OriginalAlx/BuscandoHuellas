package com.app.buscandohuellas.ui.screens.myReports

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.app.buscandohuellas.domain.model.Report
import com.app.buscandohuellas.ui.components.StatusBadge
import com.app.buscandohuellas.ui.theme.BrandColors
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Pantalla con los reportes creados por el usuario autenticado.
 *
 * @param userId usuario recibido por navegación. Si llega vacío, el
 * [MyReportsViewModel] resuelve el usuario desde la sesión de Supabase.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyReportsScreen(
    userId: String = "",
    onNavigateBack: () -> Unit,
    onReportClick: (String) -> Unit,
    onEditReport: (String) -> Unit,
    onCreateReport: () -> Unit,
    viewModel: MyReportsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var reportToDelete by remember { mutableStateOf<Report?>(null) }

    // Se recarga al entrar a la pantalla y cada vez que se vuelve de editar o
    // crear un reporte (la composición se reanuda al volver al back stack).
    LaunchedEffect(userId) {
        viewModel.loadReports(userId)
    }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Mis reportes",
                        fontWeight = FontWeight.Bold,
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
                    containerColor = BrandColors.Background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BrandColors.Background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> LoadingState()

                uiState.error != null -> ErrorState(
                    message = uiState.error.orEmpty(),
                    onRetry = viewModel::retry
                )

                uiState.isEmpty -> EmptyReportsState(onCreateReport = onCreateReport)

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = uiState.reports,
                        key = { report -> report.id ?: report.hashCode() }
                    ) { report ->
                        MyReportCard(
                            report = report,
                            isDeleting = uiState.deletingReportId == report.id,
                            onClick = {
                                report.id?.takeIf { it.isNotBlank() }?.let(onReportClick)
                            },
                            onEditClick = {
                                report.id?.takeIf { it.isNotBlank() }?.let(onEditReport)
                            },
                            onDeleteClick = { reportToDelete = report }
                        )
                    }
                }
            }
        }
    }

    reportToDelete?.let { report ->
        DeleteReportBottomSheet(
            report = report,
            onConfirm = {
                reportToDelete = null
                report.id?.takeIf { it.isNotBlank() }?.let(viewModel::deleteReport)
            },
            onDismiss = { reportToDelete = null }
        )
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = BrandColors.Primary)
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = BrandColors.Accent,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No pudimos cargar tus reportes",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = BrandColors.TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
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
            colors = ButtonDefaults.buttonColors(
                containerColor = BrandColors.Primary,
                contentColor = BrandColors.OnPrimary
            )
        ) {
            Text(text = "Reintentar", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyReportsState(onCreateReport: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(BrandColors.PrimaryLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Pets,
                contentDescription = null,
                tint = BrandColors.Primary,
                modifier = Modifier.size(44.dp)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Aún no has creado reportes",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = BrandColors.TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Cuando publiques una mascota perdida o encontrada, aparecerá aquí.",
            style = MaterialTheme.typography.bodyMedium,
            color = BrandColors.TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onCreateReport,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = BrandColors.Primary,
                contentColor = BrandColors.OnPrimary
            ),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Pets,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Crear mi primer reporte", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MyReportCard(
    report: Report,
    isDeleting: Boolean,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val imageUrl = report.allImageUrls.firstOrNull()

    Card(
        modifier = Modifier
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
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BrandColors.PrimaryLight),
                contentAlignment = Alignment.Center
            ) {
                // Placeholder visible mientras Coil carga o si la URL falla.
                Icon(
                    imageVector = Icons.Filled.Pets,
                    contentDescription = null,
                    tint = BrandColors.Primary,
                    modifier = Modifier.size(28.dp)
                )

                if (!imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "Foto de ${report.petName}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = report.petName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    StatusBadge(status = report.status)
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = report.description.ifBlank { "Sin descripción" },
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                formatReportDate(report.createdAt)?.let { date ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CalendarMonth,
                            contentDescription = null,
                            tint = BrandColors.TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = date,
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandColors.TextSecondary
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onEditClick,
                    enabled = !isDeleting
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Editar reporte",
                        tint = BrandColors.Primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onDeleteClick,
                    enabled = !isDeleting
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = BrandColors.Accent
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Eliminar reporte",
                            tint = BrandColors.Accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteReportBottomSheet(
    report: Report,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BrandColors.Surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "¿Eliminar este reporte?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = BrandColors.TextPrimary
            )
            Text(
                text = "Se eliminará \"${report.petName}\" y sus fotos. " +
                    "Esta acción no se puede deshacer.",
                style = MaterialTheme.typography.bodyMedium,
                color = BrandColors.TextSecondary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(text = "Cancelar", color = BrandColors.TextPrimary)
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandColors.Accent,
                        contentColor = BrandColors.OnPrimary
                    )
                ) {
                    Text(text = "Eliminar", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private val reportDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es"))

/**
 * Formatea `created_at` (ISO-8601 de Supabase) como "d MMM yyyy".
 * Devuelve null si el valor está vacío o no se puede interpretar.
 */
private fun formatReportDate(createdAt: String?): String? {
    if (createdAt.isNullOrBlank()) return null

    val value = createdAt.trim()
    return try {
        OffsetDateTime.parse(value).toLocalDate().format(reportDateFormatter)
    } catch (error: DateTimeParseException) {
        try {
            LocalDateTime.parse(value).toLocalDate().format(reportDateFormatter)
        } catch (fallback: DateTimeParseException) {
            try {
                LocalDate.parse(value).format(reportDateFormatter)
            } catch (finalError: DateTimeParseException) {
                null
            }
        }
    }
}
