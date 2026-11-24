package com.app.buscandohuellas.ui.screens.alerts

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.buscandohuellas.data.model.Alert
import com.app.buscandohuellas.ui.theme.BrandColors

/**
 * Pantalla principal de alertas persistentes.
 *
 * Es una ruta del BottomBar, por lo que no muestra botón de retroceso. La
 * campana, los chips de filtro y el botón "Marcar leídas" siguen la paleta
 * lavanda de la app y se adaptan al modo oscuro mediante [BrandColors].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    onReportClick: (String) -> Unit,
    viewModel: AlertsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.loadAlerts()
    }

    // Limpieza de alertas antiguas al entrar (una vez por sesión).
    LaunchedEffect(viewModel) {
        viewModel.cleanOldAlertsIfNeeded()
    }

    LaunchedEffect(viewModel) {
        viewModel.navigationEvent.collect { reportId ->
            if (reportId.isNotBlank()) onReportClick(reportId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Alertas",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = BrandColors.TextPrimary
                    )
                },
                actions = {
                    TextButton(
                        onClick = viewModel::markAllAsRead,
                        enabled = state.unreadCount > 0 && !state.isUpdating
                    ) {
                        Text(
                            text = if (state.isUpdating) {
                                "Marcando..."
                            } else {
                                "Marcar leídas"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (state.unreadCount > 0) {
                                BrandColors.Primary
                            } else {
                                BrandColors.DisabledText
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
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
            Column(modifier = Modifier.fillMaxSize()) {
                AlertFilters(
                    selected = state.filter,
                    onFilterSelected = viewModel::setFilter
                )

                Box(modifier = Modifier.weight(1f)) {
                    when {
                        state.isLoading -> LoadingState()

                        state.error != null -> ErrorState(
                            message = state.error.orEmpty(),
                            onRetry = viewModel::retry
                        )

                        state.visibleAlerts.isEmpty() -> EmptyState(
                            filtered = state.filter == AlertFilter.UNREAD &&
                                state.alerts.isNotEmpty()
                        )

                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 20.dp,
                                end = 20.dp,
                                top = 4.dp,
                                bottom = 24.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = state.visibleAlerts,
                                key = { alert -> alert.id }
                            ) { alert ->
                                AlertCard(
                                    alert = alert,
                                    onClick = {
                                        // El ViewModel espera al UPDATE en Supabase
                                        // antes de navegar al detalle del reporte.
                                        viewModel.markAsRead(alert.id, alert.reportId)
                                    },
                                    onDelete = { viewModel.deleteAlert(alert.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertFilters(
    selected: AlertFilter,
    onFilterSelected: (AlertFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AlertFilter.entries.forEach { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onFilterSelected(filter) },
                label = {
                    Text(
                        text = filter.label,
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = BrandColors.Surface,
                    labelColor = BrandColors.TextSecondary,
                    selectedContainerColor = BrandColors.Primary,
                    selectedLabelColor = BrandColors.OnPrimary
                )
            )
        }
    }
}

@Composable
private fun AlertCard(
    alert: Alert,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val unread = !alert.isRead

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (unread) {
                BrandColors.PrimaryLight.copy(alpha = 0.6f)
            } else {
                BrandColors.Surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(BrandColors.PrimaryLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = BrandColors.Primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = alert.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = alert.message,
                    fontSize = 14.sp,
                    color = BrandColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatAlertDate(alert.createdAt),
                    fontSize = 12.sp,
                    color = BrandColors.TextSecondary
                )
            }

            if (unread) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp, start = 6.dp)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(BrandColors.Primary)
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Eliminar alerta",
                    tint = BrandColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
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
private fun EmptyState(filtered: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(BrandColors.PrimaryLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = null,
                tint = BrandColors.Primary,
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = if (filtered) {
                "No tienes alertas sin leer"
            } else {
                "No tienes alertas por aquí"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = BrandColors.TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (filtered) {
                "Cuando llegue una nueva alerta aparecerá en esta lista."
            } else {
                "Recibirás alertas cuando haya reportes cerca de ti."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = BrandColors.TextSecondary,
            textAlign = TextAlign.Center
        )
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
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = BrandColors.TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BrandColors.Primary,
                contentColor = BrandColors.OnPrimary
            )
        ) {
            Text(text = "Reintentar", fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatAlertDate(createdAt: Long): String =
    DateUtils.getRelativeTimeSpanString(
        createdAt,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()
