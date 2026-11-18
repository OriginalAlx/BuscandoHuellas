package com.app.buscandohuellas.ui.screens.moderator

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.buscandohuellas.ui.theme.BrandColors
import com.app.buscandohuellas.ui.theme.ModeratorColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeratorScreen(
    onReportClick: (String) -> Unit,
    onLogout: () -> Unit,
    viewModel: ModeratorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage, uiState.userMessage) {
        val message = uiState.errorMessage ?: uiState.userMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearMessages()
    }

    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) onLogout()
    }

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Moderación",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.loadData(isRefresh = true) },
                            enabled = !uiState.isLoading &&
                                !uiState.isRefreshing &&
                                !uiState.isActionLoading
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Actualizar",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = viewModel::logout) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Cerrar sesión",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                if (uiState.isActionLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = ModeratorColors.Lavender,
                        trackColor = ModeratorColors.LavenderLight
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.isLoading) {
                LoadingState()
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    ModeratorTabs(
                        selectedTab = uiState.selectedTab,
                        reportedCount = uiState.reportedPosts.size,
                        onTabSelected = viewModel::selectTab
                    )

                    Box(modifier = Modifier.weight(1f)) {
                        when (uiState.selectedTab) {
                            ModeratorTab.ALL_REPORTS -> {
                                val reportsError = uiState.reportsLoadError
                                if (reportsError != null && uiState.allReports.isEmpty()) {
                                    LoadErrorState(
                                        message = reportsError,
                                        onRetry = { viewModel.loadData() }
                                    )
                                } else {
                                    AllReportsTabContent(
                                        uiState = uiState,
                                        onFilterSelected = viewModel::selectFilter,
                                        onReportClick = onReportClick,
                                        onEdit = viewModel::requestEdit,
                                        onDelete = viewModel::requestDelete
                                    )
                                }
                            }

                            ModeratorTab.COMMUNITY_REPORTS -> {
                                val reportedPostsError = uiState.reportedPostsLoadError
                                if (
                                    reportedPostsError != null &&
                                    uiState.reportedPosts.isEmpty()
                                ) {
                                    LoadErrorState(
                                        message = reportedPostsError,
                                        onRetry = { viewModel.loadData() }
                                    )
                                } else {
                                    CommunityReportsTabContent(
                                        uiState = uiState,
                                        onReportClick = onReportClick,
                                        onEdit = viewModel::requestEdit,
                                        onDelete = viewModel::requestDelete,
                                        onDismissFlags = viewModel::dismissFlags
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    uiState.reportToEdit?.let { report ->
        ModeratorEditSheet(
            report = report,
            isActionLoading = uiState.isActionLoading,
            onDismiss = viewModel::dismissEdit,
            onSave = viewModel::saveEdit
        )
    }

    uiState.reportToDelete?.let { report ->
        DeleteReportDialog(
            report = report,
            isActionLoading = uiState.isActionLoading,
            onDismiss = viewModel::dismissDelete,
            onConfirm = viewModel::confirmDelete
        )
    }
}

@Composable
private fun ModeratorTabs(
    selectedTab: ModeratorTab,
    reportedCount: Int,
    onTabSelected: (ModeratorTab) -> Unit
) {
    SecondaryTabRow(
        selectedTabIndex = selectedTab.ordinal,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = ModeratorColors.Lavender,
        indicator = {
            TabRowDefaults.SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(selectedTab.ordinal),
                height = 2.dp,
                color = ModeratorColors.Lavender
            )
        },
        divider = { HorizontalDivider() }
    ) {
        ModeratorTab.entries.forEach { tab ->
            Tab(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                selectedContentColor = ModeratorColors.Lavender,
                unselectedContentColor = ModeratorColors.SecondaryText,
                text = {
                    TabTitle(
                        tab = tab,
                        reportedCount = reportedCount,
                        selected = selectedTab == tab
                    )
                }
            )
        }
    }
}

@Composable
private fun TabTitle(
    tab: ModeratorTab,
    reportedCount: Int,
    selected: Boolean
) {
    val titleStyle = MaterialTheme.typography.labelMedium.copy(
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        textAlign = TextAlign.Center
    )

    if (tab == ModeratorTab.COMMUNITY_REPORTS && reportedCount > 0) {
        BadgedBox(
            badge = {
                Badge(
                    containerColor = ModeratorColors.Red,
                    contentColor = BrandColors.OnPrimary
                ) {
                    Text(
                        text = moderationCountLabel(reportedCount),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        ) {
            Text(
                text = tab.title,
                style = titleStyle,
                maxLines = 2
            )
        }
    } else {
        Text(
            text = tab.title,
            style = titleStyle,
            maxLines = 2
        )
    }
}
