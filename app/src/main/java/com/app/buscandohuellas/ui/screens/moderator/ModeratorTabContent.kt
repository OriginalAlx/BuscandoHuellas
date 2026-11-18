package com.app.buscandohuellas.ui.screens.moderator

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.app.buscandohuellas.domain.model.Report
import com.app.buscandohuellas.ui.theme.BrandColors
import com.app.buscandohuellas.ui.theme.ModeratorColors

@Composable
internal fun AllReportsTabContent(
    uiState: ModeratorUiState,
    onFilterSelected: (ReportFilterOption) -> Unit,
    onReportClick: (String) -> Unit,
    onEdit: (Report) -> Unit,
    onDelete: (Report) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ModeratorStatCard(
                    value = uiState.stats.active,
                    label = "Activos",
                    modifier = Modifier.weight(1f)
                )
                ModeratorStatCard(
                    value = uiState.stats.reported,
                    label = "Denunciados",
                    modifier = Modifier.weight(1f)
                )
                ModeratorStatCard(
                    value = uiState.stats.resolved,
                    label = "Resueltos",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            ReportFilterChips(
                selectedFilter = uiState.selectedFilter,
                onFilterSelected = onFilterSelected
            )
        }

        item {
            ModeratorSectionHeader(
                reportedCount = uiState.reportedPosts.size,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (uiState.filteredReports.isEmpty()) {
            item {
                EmptyTabState(
                    message = "No hay reportes que coincidan",
                    icon = Icons.Filled.Pets
                )
            }
        } else {
            items(
                items = uiState.filteredReports,
                key = { report -> report.id.orEmpty() }
            ) { report ->
                ModeratorReportCard(
                    report = report,
                    isActionLoading = uiState.isActionLoading,
                    onEdit = { onEdit(report) },
                    onDelete = { onDelete(report) },
                    onClick = {
                        report.id?.let(onReportClick)
                    }
                )
            }
        }
    }
}

@Composable
private fun ReportFilterChips(
    selectedFilter: ReportFilterOption,
    onFilterSelected: (ReportFilterOption) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = ReportFilterOption.entries,
            key = { option -> option.name }
        ) { option ->
            val selected = option == selectedFilter

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (selected) {
                            ModeratorColors.Lavender
                        } else {
                            ModeratorColors.ChipBackground
                        }
                    )
                    .clickable { onFilterSelected(option) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (selected) BrandColors.OnPrimary else ModeratorColors.ChipText
                )
            }
        }
    }
}

@Composable
internal fun CommunityReportsTabContent(
    uiState: ModeratorUiState,
    onReportClick: (String) -> Unit,
    onEdit: (Report) -> Unit,
    onDelete: (Report) -> Unit,
    onDismissFlags: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CommunityInfoHeader()
        }

        if (uiState.reportedPosts.isEmpty()) {
            item {
                EmptyTabState(
                    message = "No hay denuncias pendientes",
                    icon = Icons.Filled.Flag
                )
            }
        } else {
            items(
                items = uiState.reportedPosts,
                key = { post -> post.report.id.orEmpty() }
            ) { post ->
                ReportedPostCard(
                    post = post,
                    isActionLoading = uiState.isActionLoading,
                    onView = {
                        post.report.id?.let(onReportClick)
                    },
                    onEdit = { onEdit(post.report) },
                    onDelete = { onDelete(post.report) },
                    onDismissFlags = {
                        post.report.id?.let(onDismissFlags)
                    }
                )
            }
        }
    }
}

@Composable
private fun CommunityInfoHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(ModeratorColors.RedLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Flag,
                contentDescription = null,
                tint = ModeratorColors.Red,
                modifier = Modifier.size(24.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Publicaciones denunciadas por la comunidad",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Revisa y toma acción sobre los reportes recibidos",
                style = MaterialTheme.typography.bodySmall,
                color = ModeratorColors.SecondaryText
            )
        }
    }
}

@Composable
private fun EmptyTabState(
    message: String,
    icon: ImageVector
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(ModeratorColors.LavenderLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ModeratorColors.Lavender,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = ModeratorColors.SecondaryText,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
internal fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = ModeratorColors.Lavender)
    }
}

@Composable
internal fun LoadErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(ModeratorColors.RedLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = ModeratorColors.Red,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No pudimos cargar el panel",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = ModeratorColors.SecondaryText,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = ModeratorColors.Lavender,
                contentColor = BrandColors.OnPrimary
            )
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
