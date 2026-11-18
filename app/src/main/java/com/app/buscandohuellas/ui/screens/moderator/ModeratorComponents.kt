package com.app.buscandohuellas.ui.screens.moderator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.app.buscandohuellas.domain.model.Report
import com.app.buscandohuellas.domain.model.ReportStatus
import com.app.buscandohuellas.domain.model.ReportedPost
import com.app.buscandohuellas.ui.components.StatusBadge
import com.app.buscandohuellas.ui.theme.BrandColors
import com.app.buscandohuellas.ui.theme.ModeratorColors
import com.app.buscandohuellas.ui.theme.backgroundColor
import com.app.buscandohuellas.ui.theme.textColor
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

internal const val MULTIPLE_REPORTS_THRESHOLD = 3

/**
 * Formateador relativo en español para la pestaña de denuncias.
 * Si la fecha no se puede interpretar, devuelve el texto crudo.
 */
internal fun formatModeratorRelativeTime(
    value: String?,
    now: Instant = Instant.now()
): String {
    val instant = parseModeratorInstant(value) ?: return value?.trim().orEmpty()
    val elapsedMinutes = Duration.between(instant, now).toMinutes().coerceAtLeast(0)

    val hours = elapsedMinutes / 60
    val days = hours / 24

    return when {
        elapsedMinutes < 1 -> "hace un momento"
        elapsedMinutes == 1L -> "hace 1 min"
        elapsedMinutes < 60 -> "hace $elapsedMinutes min"
        hours == 1L -> "hace 1 hora"
        hours < 24 -> "hace $hours horas"
        days == 1L -> "hace 1 día"
        else -> "hace $days días"
    }
}

private fun parseModeratorInstant(value: String?): Instant? {
    if (value.isNullOrBlank()) return null
    val trimmed = value.trim()

    return try {
        Instant.parse(trimmed)
    } catch (error: DateTimeParseException) {
        try {
            OffsetDateTime.parse(trimmed).toInstant()
        } catch (offsetError: DateTimeParseException) {
            try {
                LocalDateTime.parse(trimmed).toInstant(ZoneOffset.UTC)
            } catch (localError: DateTimeParseException) {
                null
            }
        }
    }
}

internal fun moderationCountLabel(count: Int): String =
    if (count > 99) "99+" else count.toString()

@Composable
internal fun ModeratorStatCard(
    value: Int,
    label: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = ModeratorColors.Lavender
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = ModeratorColors.SecondaryText
            )
        }
    }
}

@Composable
internal fun ModeratorSectionHeader(
    reportedCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "COLA DE REVISIÓN",
                style = MaterialTheme.typography.labelSmall,
                color = ModeratorColors.SecondaryText,
                letterSpacing = 1.sp
            )
            Text(
                text = "Reportes recientes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (reportedCount > 0) {
            CountBadge(count = reportedCount)
        }
    }
}

@Composable
internal fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(ModeratorColors.Red),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = moderationCountLabel(count),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = BrandColors.OnPrimary
        )
    }
}

@Composable
internal fun ModeratorReportCard(
    report: Report,
    isActionLoading: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canOpen = report.id != null

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (canOpen) Modifier.clickable(onClick = onClick) else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ReportThumbnail(report = report)

            ReportInfoColumn(
                report = report,
                modifier = Modifier.weight(1f)
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CardIconButton(
                    icon = Icons.Filled.Edit,
                    contentDescription = "Editar reporte",
                    background = ModeratorColors.LavenderLight,
                    tint = ModeratorColors.Lavender,
                    enabled = !isActionLoading,
                    onClick = onEdit
                )
                CardIconButton(
                    icon = Icons.Filled.Delete,
                    contentDescription = "Eliminar reporte",
                    background = ModeratorColors.RedLight,
                    tint = ModeratorColors.Red,
                    enabled = !isActionLoading,
                    onClick = onDelete
                )
            }
        }
    }
}

@Composable
internal fun ReportedPostCard(
    post: ReportedPost,
    isActionLoading: Boolean,
    onView: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismissFlags: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                InfoBadge(
                    text = "Reportado",
                    icon = Icons.Filled.Flag,
                    background = ModeratorColors.Yellow,
                    contentColor = ModeratorColors.Orange
                )

                if (post.reporterCount >= MULTIPLE_REPORTS_THRESHOLD) {
                    InfoBadge(
                        text = "Múltiples reportes",
                        icon = Icons.Filled.Warning,
                        background = ModeratorColors.RedLight,
                        contentColor = ModeratorColors.RedText
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ReportThumbnail(report = post.report)
                ReportInfoColumn(
                    report = post.report,
                    modifier = Modifier.weight(1f)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ModeratorColors.GrayVeryLight)
                    .padding(12.dp)
            ) {
                Text(
                    text = "Motivo del reporte:",
                    style = MaterialTheme.typography.labelSmall,
                    color = ModeratorColors.SecondaryText
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = post.latestReason,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                val extraReports = post.reporterCount - 1
                if (extraReports > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (extraReports == 1) {
                            "y 1 denuncia más"
                        } else {
                            "y $extraReports denuncias más"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = ModeratorColors.SecondaryText
                    )
                }
            }

            val relativeTime = formatModeratorRelativeTime(post.latestReportedAt)
                .ifBlank { "recientemente" }
            Text(
                text = "Reportado $relativeTime por ${post.latestReporterName ?: "usuario"}",
                style = MaterialTheme.typography.labelSmall,
                color = ModeratorColors.SecondaryText,
                modifier = Modifier.padding(top = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CommunityActionButton(
                    label = "Ver",
                    icon = Icons.Filled.Visibility,
                    background = ModeratorColors.ChipBackground,
                    tint = ModeratorColors.SecondaryText,
                    enabled = !isActionLoading,
                    onClick = onView
                )
                CommunityActionButton(
                    label = "Editar",
                    icon = Icons.Filled.Edit,
                    background = ModeratorColors.LavenderLight,
                    tint = ModeratorColors.Lavender,
                    enabled = !isActionLoading,
                    onClick = onEdit
                )
                CommunityActionButton(
                    label = "Eliminar",
                    icon = Icons.Filled.Delete,
                    background = ModeratorColors.RedLight,
                    tint = ModeratorColors.Red,
                    enabled = !isActionLoading,
                    onClick = onDelete
                )
                CommunityActionButton(
                    label = "Descartar",
                    icon = Icons.Filled.Check,
                    background = ModeratorColors.GreenLight,
                    tint = ModeratorColors.Green,
                    enabled = !isActionLoading,
                    onClick = onDismissFlags
                )
            }
        }
    }
}

@Composable
private fun ReportThumbnail(report: Report, modifier: Modifier = Modifier) {
    val imageUrl = report.allImageUrls.firstOrNull()

    Box(
        modifier = modifier
            .size(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ModeratorColors.LavenderLight),
        contentAlignment = Alignment.Center
    ) {
        // Placeholder y fallback: queda visible mientras Coil carga o si falla.
        Icon(
            imageVector = Icons.Filled.Pets,
            contentDescription = null,
            tint = ModeratorColors.Lavender,
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
}

@Composable
private fun ReportInfoColumn(report: Report, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = report.petName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = report.description.ifBlank { "Sin descripción" },
            style = MaterialTheme.typography.bodySmall,
            color = ModeratorColors.SecondaryText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(6.dp))
        StatusBadge(status = report.status)
    }
}

@Composable
private fun InfoBadge(
    text: String,
    icon: ImageVector,
    background: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = contentColor
        )
    }
}

@Composable
private fun CardIconButton(
    icon: ImageVector,
    contentDescription: String,
    background: Color,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(background)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun RowScope.CommunityActionButton(
    label: String,
    icon: ImageVector,
    background: Color,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .weight(1f)
            .height(40.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModeratorEditSheet(
    report: Report,
    isActionLoading: Boolean,
    onDismiss: () -> Unit,
    onSave: (description: String, status: ReportStatus, reason: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var description by remember(report.id) { mutableStateOf(report.description) }
    var selectedStatus by remember(report.id) { mutableStateOf(report.status) }
    var reason by remember(report.id) { mutableStateOf("") }
    var reasonTouched by remember(report.id) { mutableStateOf(false) }

    val cleanDescription = description.trim()
    val cleanReason = reason.trim()
    val hasChanges = cleanDescription != report.description.trim() ||
        selectedStatus != report.status
    val canSave = cleanDescription.isNotBlank() &&
        cleanReason.isNotBlank() &&
        hasChanges &&
        !isActionLoading

    ModalBottomSheet(
        onDismissRequest = { if (!isActionLoading) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { WindowInsets.safeDrawing }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Editar reporte",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Actualiza la información visible para la comunidad.",
                style = MaterialTheme.typography.bodyMedium,
                color = ModeratorColors.SecondaryText
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isActionLoading,
                label = { Text("Descripción") },
                minLines = 3,
                maxLines = 6
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Estado",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReportStatus.entries.forEach { option ->
                    val selected = selectedStatus == option
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(option.backgroundColor)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) {
                                    option.textColor
                                } else {
                                    option.backgroundColor
                                },
                                shape = RoundedCornerShape(50)
                            )
                            .clickable(enabled = !isActionLoading) {
                                selectedStatus = option
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = option.textColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = reason,
                onValueChange = {
                    reason = it
                    reasonTouched = true
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isActionLoading,
                label = { Text("Motivo de la edición *") },
                placeholder = { Text("Explica brevemente el cambio...") },
                isError = reasonTouched && cleanReason.isBlank(),
                supportingText = {
                    if (reasonTouched && cleanReason.isBlank()) {
                        Text("El motivo de la edición es obligatorio.")
                    }
                },
                minLines = 2,
                maxLines = 4
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isActionLoading,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("Cancelar")
                }

                Button(
                    onClick = {
                        reasonTouched = true
                        if (canSave) {
                            onSave(cleanDescription, selectedStatus, cleanReason)
                        }
                    },
                    enabled = canSave,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ModeratorColors.Lavender,
                        contentColor = BrandColors.OnPrimary
                    )
                ) {
                    if (isActionLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Guardando...")
                    } else {
                        Text("Guardar cambios")
                    }
                }
            }
        }
    }
}

@Composable
internal fun DeleteReportDialog(
    report: Report,
    isActionLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isActionLoading) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = "¿Eliminar este reporte?",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(text = "Esta acción no se puede deshacer.")
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = report.petName,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isActionLoading,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                if (isActionLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text("Sí, eliminar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isActionLoading
            ) {
                Text("Cancelar")
            }
        }
    )
}
