package com.app.buscandohuellas.ui.screens.reportDetail

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.app.buscandohuellas.domain.model.Report
import com.app.buscandohuellas.ui.theme.BrandColors
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.util.Locale

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 5f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReportDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sightingInput by viewModel.sightingInput.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var fullScreenStartPage by remember { mutableStateOf<Int?>(null) }

    val successState = uiState as? DetailUiState.Success

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            viewModel.getCurrentLocation(context)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Detalle",
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
                actions = {
                    IconButton(
                        onClick = {
                            successState?.report?.let { shareReport(context, it) }
                        },
                        enabled = successState != null
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Compartir reporte",
                            tint = BrandColors.TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandColors.Surface
                )
            )
        },
        bottomBar = {
            if (successState != null) {
                CommentInput(
                    value = sightingInput,
                    onValueChange = viewModel::onSightingInputChange,
                    onSend = viewModel::addSighting
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = BrandColors.Surface
    ) { paddingValues ->
        when (val state = uiState) {
            DetailUiState.Loading -> LoadingState(
                modifier = Modifier.padding(paddingValues)
            )

            is DetailUiState.Error -> ErrorState(
                message = state.message,
                onRetry = viewModel::retry,
                modifier = Modifier.padding(paddingValues)
            )

            is DetailUiState.Success -> DetailContent(
                state = state,
                contentPadding = paddingValues,
                onExpandPhoto = { page -> fullScreenStartPage = page },
                onContactClick = {
                    showMessage("El canal de contacto con el dueño estará disponible pronto.")
                },
                onEditClick = {
                    showMessage("Solo el dueño del reporte puede editarlo.")
                },
                onReportClick = {
                    showMessage("Gracias, revisaremos este reporte.")
                },
                onProfileClick = {
                    showMessage("El perfil del reportante estará disponible pronto.")
                }
            )
        }
    }

    val startPage = fullScreenStartPage
    if (successState != null && startPage != null) {
        val images = successState.report.allImageUrls
        if (images.isNotEmpty()) {
            FullScreenGallery(
                images = images,
                initialPage = startPage,
                petName = successState.report.petName,
                onDismiss = { fullScreenStartPage = null }
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
            text = "No pudimos abrir el reporte",
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
private fun DetailContent(
    state: DetailUiState.Success,
    contentPadding: PaddingValues,
    onExpandPhoto: (Int) -> Unit,
    onContactClick: () -> Unit,
    onEditClick: () -> Unit,
    onReportClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    val report = state.report
    val images = report.allImageUrls

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding()
            )
    ) {
        val galleryHeight = (maxHeight * 0.4f).coerceIn(220.dp, 400.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            PhotoGallery(
                images = images,
                petName = report.petName,
                onExpandPhoto = onExpandPhoto,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(galleryHeight)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(18.dp))

                PetHeader(state = state)

                Spacer(modifier = Modifier.height(16.dp))

                ReporterCard(
                    name = state.userName,
                    avatarUrl = state.userAvatarUrl,
                    onProfileClick = onProfileClick
                )

                Text(
                    text = report.description.ifBlank { "Sin descripción." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = BrandColors.TextSecondary,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                LocationCard(
                    formattedDistance = state.formattedDistance,
                    latitude = report.latitude,
                    longitude = report.longitude
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onContactClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandColors.Primary,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Contactar dueño",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                SecondaryActions(
                    isOwner = state.isOwner,
                    onEditClick = onEditClick,
                    onReportClick = onReportClick
                )

                Spacer(modifier = Modifier.height(24.dp))

                SightingsSection(sightings = state.sightings)

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun PetHeader(state: DetailUiState.Success) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = state.report.petName.ifBlank { "Sin nombre" },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = BrandColors.TextPrimary
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatusPill(status = state.report.status)

            if (state.relativeTime.isNotBlank()) {
                Text(
                    text = state.relativeTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandColors.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val style = statusStyle(status)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(style.container)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = style.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            color = style.content
        )
    }
}

@Composable
private fun ReporterCard(
    name: String,
    avatarUrl: String?,
    onProfileClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BrandColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(name = name, avatarUrl = avatarUrl, size = 40.dp)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = BrandColors.TextPrimary
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Ver perfil",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = BrandColors.Primary,
                    modifier = Modifier.clickable(onClick = onProfileClick)
                )
            }
        }
    }
}

@Composable
private fun LocationCard(
    formattedDistance: String,
    latitude: Double?,
    longitude: Double?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BrandColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "ÚLTIMA UBICACIÓN",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
                color = BrandColors.TextSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(BrandColors.PrimaryLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = BrandColors.Primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = formattedDistance,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = BrandColors.TextPrimary
                )
            }

            if (latitude != null && longitude != null) {
                Spacer(modifier = Modifier.height(14.dp))

                ReportMiniMap(
                    latitude = latitude,
                    longitude = longitude,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }
        }
    }
}

@Composable
private fun SecondaryActions(
    isOwner: Boolean,
    onEditClick: () -> Unit,
    onReportClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (isOwner) {
            ActionButton(
                icon = Icons.Filled.Edit,
                label = "Editar",
                onClick = onEditClick,
                modifier = Modifier.weight(1f)
            )
        }

        ActionButton(
            icon = Icons.Filled.Flag,
            label = "Reportar",
            onClick = onReportClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BrandColors.Surface),
        border = BorderStroke(1.dp, BrandColors.Divider),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = BrandColors.TextPrimary,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = BrandColors.TextPrimary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SightingsSection(sightings: List<SightingUi>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Avistamientos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = BrandColors.TextPrimary
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(BrandColors.PrimaryLight)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = sightings.size.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = BrandColors.Primary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (sightings.isEmpty()) {
            Text(
                text = "Aún no hay avistamientos. Comparte si la viste.",
                style = MaterialTheme.typography.bodyMedium,
                color = BrandColors.TextSecondary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            sightings.forEachIndexed { index, sighting ->
                CommentItem(sighting = sighting)

                if (index != sightings.lastIndex) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = BrandColors.Divider
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentItem(sighting: SightingUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        UserAvatar(
            name = sighting.authorName,
            avatarUrl = sighting.authorAvatarUrl,
            size = 32.dp
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = sighting.authorName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = BrandColors.TextPrimary
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = sighting.message,
                style = MaterialTheme.typography.bodyMedium,
                color = BrandColors.TextSecondary
            )
        }
    }
}

@Composable
private fun CommentInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Surface(
        color = BrandColors.Surface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = "Comparte si la viste...",
                        color = BrandColors.TextSecondary
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = BrandColors.TextPrimary
                ),
                shape = RoundedCornerShape(50),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = BrandColors.TextPrimary,
                    unfocusedTextColor = BrandColors.TextPrimary,
                    cursorColor = BrandColors.Primary,
                    focusedBorderColor = BrandColors.Primary,
                    unfocusedBorderColor = BrandColors.TextSecondary,
                    focusedContainerColor = BrandColors.Surface,
                    unfocusedContainerColor = BrandColors.Surface,
                    focusedPlaceholderColor = BrandColors.TextSecondary,
                    unfocusedPlaceholderColor = BrandColors.TextSecondary
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            FilledIconButton(
                onClick = onSend,
                enabled = value.isNotBlank(),
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = BrandColors.Primary,
                    contentColor = Color.White,
                    disabledContainerColor = BrandColors.PrimaryLight,
                    disabledContentColor = BrandColors.TextSecondary
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Enviar avistamiento"
                )
            }
        }
    }
}

@Composable
private fun UserAvatar(
    name: String,
    avatarUrl: String?,
    size: Dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(BrandColors.PrimaryLight),
        contentAlignment = Alignment.Center
    ) {
        // Las iniciales quedan siempre detrás de la foto: si la imagen falla o
        // aún está cargando, el usuario ve un placeholder legible.
        Text(
            text = initialsOf(name),
            color = BrandColors.Primary,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.4f).sp
        )

        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(avatarUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun PhotoGallery(
    images: List<String>,
    petName: String,
    onExpandPhoto: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(BrandColors.PrimaryLight)
    ) {
        if (images.isEmpty()) {
            EmptyPhotoPlaceholder(modifier = Modifier.fillMaxSize())
        } else {
            val pagerState = rememberPagerState(pageCount = { images.size })

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(images[page])
                        .crossfade(true)
                        .build(),
                    contentDescription = "Foto ${page + 1} de $petName",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(88.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f))
                        )
                    )
            )

            if (images.size > 1) {
                GalleryDots(
                    count = images.size,
                    selectedIndex = pagerState.currentPage,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                )
            }

            SmallFloatingActionButton(
                onClick = { onExpandPhoto(pagerState.currentPage) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(14.dp),
                containerColor = BrandColors.Surface.copy(alpha = 0.94f),
                contentColor = BrandColors.Primary
            ) {
                Icon(
                    imageVector = Icons.Filled.ZoomOutMap,
                    contentDescription = "Ver foto en pantalla completa",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyPhotoPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(BrandColors.PrimaryLight),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(BrandColors.Surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.ImageNotSupported,
                    contentDescription = null,
                    tint = BrandColors.Primary,
                    modifier = Modifier.size(34.dp)
                )
            }
            Text(
                text = "Sin fotos disponibles",
                style = MaterialTheme.typography.labelLarge,
                color = BrandColors.TextSecondary
            )
        }
    }
}

@Composable
private fun GalleryDots(
    count: Int,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.White.copy(alpha = 0.45f)
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .height(7.dp)
                    .width(if (selected) 18.dp else 7.dp)
                    .clip(CircleShape)
                    .background(if (selected) activeColor else inactiveColor)
            )
        }
    }
}

@Composable
private fun FullScreenGallery(
    images: List<String>,
    initialPage: Int,
    petName: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val pagerState = rememberPagerState(
            initialPage = initialPage.coerceIn(0, images.lastIndex),
            pageCount = { images.size }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.96f))
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                ZoomableImage(
                    imageUrl = images[page],
                    contentDescription = "Foto ${page + 1} de $petName",
                    modifier = Modifier.fillMaxSize()
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .background(Color.White.copy(alpha = 0.14f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Cerrar",
                    tint = Color.White
                )
            }

            if (images.size > 1) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${images.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 52.dp)
                )

                GalleryDots(
                    count = images.size,
                    selectedIndex = pagerState.currentPage,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp)
                )
            }
        }
    }
}

@Composable
private fun ZoomableImage(
    imageUrl: String,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(MIN_ZOOM) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                detectZoomAndPan(
                    isZoomed = { scale > MIN_ZOOM },
                    onTransform = { zoomChange, panChange ->
                        scale = (scale * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)

                        offset = if (scale <= MIN_ZOOM) {
                            Offset.Zero
                        } else {
                            val maxX = (containerSize.width * (scale - 1f)) / 2f
                            val maxY = (containerSize.height * (scale - 1f)) / 2f
                            Offset(
                                x = (offset.x + panChange.x).coerceIn(-maxX, maxX),
                                y = (offset.y + panChange.y).coerceIn(-maxY, maxY)
                            )
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )
    }
}

private suspend fun PointerInputScope.detectZoomAndPan(
    isZoomed: () -> Boolean,
    onTransform: (zoomChange: Float, panChange: Offset) -> Unit
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)

        do {
            val event = awaitPointerEvent()
            val pointerCount = event.changes.count { it.pressed }

            if (pointerCount > 1 || isZoomed()) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()

                if (zoomChange != 1f || panChange != Offset.Zero) {
                    onTransform(zoomChange, panChange)
                }

                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}

@Composable
private fun ReportMiniMap(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier
) {
    var mapView by remember { mutableStateOf<MapView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            mapView?.onDetach()
            mapView = null
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            Configuration.getInstance().userAgentValue = ctx.packageName
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(false)
                controller.setZoom(16.0)
                controller.setCenter(GeoPoint(latitude, longitude))
                overlays.add(
                    Marker(this).apply {
                        position = GeoPoint(latitude, longitude)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        icon = redMarkerDrawable()
                    }
                )
                onResume()
                mapView = this
            }
        },
        update = { map ->
            map.controller.setCenter(GeoPoint(latitude, longitude))
        }
    )
}

private fun redMarkerDrawable(): Drawable {
    val size = 72
    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)

    val fillPaint = Paint().apply {
        color = android.graphics.Color.rgb(220, 38, 38)
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    val borderPaint = Paint().apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    val radius = size / 2f - 4f
    canvas.drawCircle(size / 2f, size / 2f, radius, fillPaint)
    canvas.drawCircle(size / 2f, size / 2f, radius, borderPaint)

    return bitmap.toDrawable(Resources.getSystem())
}

private data class StatusStyle(
    val label: String,
    val container: Color,
    val content: Color
)

private fun statusStyle(status: String): StatusStyle = when (status.uppercase()) {
    "FOUND" -> StatusStyle("ENCONTRADA", BrandColors.SuccessLight, BrandColors.Success)
    "SIGHTED" -> StatusStyle("AVISTADA", BrandColors.InfoLight, BrandColors.Info)
    else -> StatusStyle("PERDIDA", BrandColors.AccentLight, BrandColors.Accent)
}

private fun initialsOf(name: String): String {
    val initials = name
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")

    return initials.ifBlank { "?" }
}

private fun shareReport(context: Context, report: Report) {
    val message = buildString {
        append("Ayúdanos a encontrar a ${report.petName.ifBlank { "esta mascota" }}.")
        if (report.description.isNotBlank()) {
            append("\n\n")
            append(report.description)
        }
        report.allImageUrls.firstOrNull()?.let { imageUrl ->
            append("\n\n")
            append(imageUrl)
        }
        if (report.latitude != null && report.longitude != null) {
            append("\n\n")
            append(
                String.format(
                    Locale.US,
                    "Ubicación: %.5f, %.5f",
                    report.latitude,
                    report.longitude
                )
            )
        }
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Reporte de ${report.petName}")
        putExtra(Intent.EXTRA_TEXT, message)
    }

    context.startActivity(Intent.createChooser(intent, "Compartir reporte"))
}
