package com.app.buscandohuellas.ui.screens.found

import android.util.Log
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.app.buscandohuellas.ui.theme.BrandColors
import com.app.buscandohuellas.util.ReportMatchCandidate
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoundPetMatchScreen(
    onNavigateBack: () -> Unit,
    onNoneOfThese: () -> Unit,
    onOpenChat: (ownerId: String, ownerName: String, ownerAvatarUrl: String?, lostReportId: String) -> Unit,
    viewModel: FoundPetMatchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = BrandColors.Background,
        topBar = {
            TopAppBar(
                title = { Text("¿Es alguna de estas?") },
                navigationIcon = {
                    IconButton(onClick = {
                        Log.d("BH_DEBUG", "FoundPetMatch: TopBar close/volver al mapa clicado")
                        onNavigateBack()
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = "Volver al mapa")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandColors.Background
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = BrandColors.Primary
                    )
                }

                uiState.error != null -> {
                    MatchMessage(
                        icon = Icons.Filled.SearchOff,
                        title = "No se pudieron cargar coincidencias",
                        subtitle = uiState.error.orEmpty(),
                        actionLabel = "Reintentar",
                        onAction = viewModel::loadCandidates,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                uiState.currentCandidate == null -> {
                    MatchMessage(
                        icon = Icons.Filled.Pets,
                        title = if (uiState.isEmpty) "No encontramos mascotas perdidas cercanas" else "No hay más coincidencias",
                        subtitle = if (uiState.isEmpty)
                            "Tu reporte fue publicado. Si alguien busca una mascota similar, podrá contactarte."
                        else
                            "Has revisado todas las coincidencias cercanas.",
                        actionLabel = "Ninguna de estas",
                        onAction = {
                            Log.d("BH_DEBUG", "FoundPetMatch: 'Ninguna de estas' (estado vacío) clicado")
                            onNoneOfThese()
                        },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    val candidate = uiState.currentCandidate!!
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Encontramos mascotas perdidas cercanas. ¿Alguna coincide?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = BrandColors.TextSecondary
                        )

                        MatchCandidateCard(candidate = candidate)

                        Text(
                            text = "${uiState.currentIndex + 1} de ${uiState.candidates.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = BrandColors.TextSecondary,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = viewModel::onNotThisOne,
                                enabled = !uiState.isConfirming,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("No es esta")
                            }

                            Button(
                                onClick = { viewModel.onConfirmMatch(onOpenChat) },
                                enabled = !uiState.isConfirming,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BrandColors.Primary,
                                    contentColor = BrandColors.OnPrimary
                                )
                            ) {
                                if (uiState.isConfirming) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = BrandColors.OnPrimary
                                    )
                                } else {
                                    Icon(Icons.Filled.Check, contentDescription = null)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sí, es esta")
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                Log.d("BH_DEBUG", "FoundPetMatch: 'Ninguna de estas' clicado")
                                onNoneOfThese()
                            },
                            enabled = !uiState.isConfirming,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Ninguna de estas")
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchCandidateCard(candidate: ReportMatchCandidate) {
    val report = candidate.report
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BrandColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            val imageUrl = report.allImageUrls.firstOrNull()
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Foto de ${report.petName}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(BrandColors.PrimaryLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Pets,
                        contentDescription = null,
                        tint = BrandColors.Primary,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = report.petName.ifBlank { "Mascota sin nombre" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = BrandColors.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    SimilarityBadge(score = candidate.similarityScore)
                }

                if (report.description.isNotBlank()) {
                    Text(
                        text = report.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = BrandColors.TextSecondary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Place,
                        contentDescription = null,
                        tint = BrandColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = candidate.distanceKm?.let { formatDistance(it) } ?: "Distancia no disponible",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandColors.TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun SimilarityBadge(score: Int) {
    val color = when {
        score >= 60 -> BrandColors.Success
        score >= 30 -> BrandColors.Warning
        else -> BrandColors.TextSecondary
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = "$score% similitud",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@Composable
private fun MatchMessage(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = BrandColors.TextSecondary,
            modifier = Modifier.size(56.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = BrandColors.TextPrimary,
            textAlign = TextAlign.Center
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = BrandColors.TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onAction,
            colors = ButtonDefaults.buttonColors(
                containerColor = BrandColors.Primary,
                contentColor = BrandColors.OnPrimary
            )
        ) {
            Text(actionLabel)
        }
    }
}

private fun formatDistance(km: Double): String {
    return if (km < 1.0) {
        "${(km * 1000).toInt()} m de distancia"
    } else {
        String.format(Locale.getDefault(), "%.1f km de distancia", km)
    }
}
