package com.app.buscandohuellas.ui.screens.chat

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.app.buscandohuellas.ui.components.MessageBubble
import com.app.buscandohuellas.ui.components.PendingImageBubble
import com.app.buscandohuellas.ui.screens.createReport.MapPickerScreen
import com.app.buscandohuellas.ui.theme.BrandColors
import java.io.File

/** Pantalla de chat con el otro participante de una conversación. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current

    // Condición explícita de negocio: dueño + match existente + sin confirmar.
    val isReportOwner = uiState.currentUserId.isNotBlank() &&
        uiState.currentUserId == uiState.reportOwnerId
    val pendingMatch = uiState.pendingMatch
    val showMatchBanner = isReportOwner &&
        pendingMatch != null &&
        pendingMatch.confirmedByOwner == false &&
        pendingMatch.rejectedByOwner == false &&
        !uiState.isLoadingMatch &&
        !uiState.isLoading

    LaunchedEffect(
        uiState.currentUserId,
        uiState.reportOwnerId,
        pendingMatch?.id,
        pendingMatch?.confirmedByOwner,
        pendingMatch?.rejectedByOwner,
        uiState.isLoadingMatch,
        showMatchBanner
    ) {
        Log.d(
            "BH_DEBUG",
            "ChatScreen BANNER: currentUserId=${uiState.currentUserId} " +
                "reportOwnerId=${uiState.reportOwnerId} isReportOwner=$isReportOwner " +
                "matchId=${pendingMatch?.id} confirmed=${pendingMatch?.confirmedByOwner} " +
                "rejected=${pendingMatch?.rejectedByOwner} loadingMatch=${uiState.isLoadingMatch} " +
                "show=$showMatchBanner"
        )
    }

    // --- ActivityResultLaunchers ---
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.sendImageFromUri(uri)
        }
    }

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && pendingCameraUri != null) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.sendImageFromUri(pendingCameraUri!!)
        }
        pendingCameraUri = null
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val photoFile = createChatImageFile(context)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            Toast.makeText(
                context,
                "Se necesita permiso de cámara. Puedes enviar la foto desde la galería.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.any { it }
        if (granted) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.sendCurrentLocation()
        } else {
            Toast.makeText(
                context,
                "Se necesita permiso de ubicación para compartir tu posición.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.initializeChat()
    }

    // El match se consulta en cuanto se conocen la sesión y la conversación.
    LaunchedEffect(uiState.conversation?.id, uiState.currentUserId) {
        viewModel.loadPendingMatch()
    }

    // reverseLayout coloca el primer item abajo; por eso la lista se invierte
    // y el scroll siempre apunta al índice 0.
    LaunchedEffect(uiState.messages.size, uiState.scrollEvent, uiState.pendingImageUri) {
        if (uiState.messages.isNotEmpty() || uiState.pendingImageUri != null) {
            runCatching { listState.animateScrollToItem(0) }
        }
    }

    LaunchedEffect(uiState.error) {
        val message = uiState.error ?: return@LaunchedEffect
        val action = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = "Reintentar",
            duration = SnackbarDuration.Short
        )
        if (action == SnackbarResult.ActionPerformed) {
            viewModel.retry()
        }
        viewModel.consumeError()
    }

    LaunchedEffect(uiState.matchError) {
        if (uiState.matchError == null) return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = uiState.matchError.orEmpty(),
            duration = SnackbarDuration.Short
        )
        viewModel.consumeMatchError()
    }

    Scaffold(
        topBar = {
            ChatTopBar(
                name = uiState.otherUserName,
                avatarUrl = uiState.otherUserAvatarUrl,
                online = !uiState.isLoading && uiState.error == null,
                onNavigateBack = onNavigateBack
            )
        },
        bottomBar = {
            ChatInputBar(
                value = uiState.input,
                enabled = !uiState.isLoading && uiState.conversation != null,
                isSending = uiState.isSending,
                canSend = uiState.canSend,
                attachmentsEnabled = !uiState.isBusy && uiState.conversation != null,
                onValueChange = viewModel::onInputChange,
                onSend = { viewModel.sendMessage(uiState.input) },
                onAttachClick = viewModel::toggleAttachmentMenu,
                onLocationClick = viewModel::toggleLocationOptions
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
            Column(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = showMatchBanner,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { -it })
                ) {
                    pendingMatch?.let { match ->
                        MatchConfirmationBanner(
                            isConfirming = uiState.isConfirmingMatch,
                            isRejecting = uiState.isRejectingMatch,
                            enabled = !uiState.isBusy,
                            onConfirm = { viewModel.confirmPetFound(match.id, match.lostReportId) },
                            onReject = { viewModel.rejectPetFound(match.id) }
                        )
                    }
                }

                AnimatedVisibility(
                    visible = uiState.canShowDeliveryButton,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { -it })
                ) {
                    DeliveryConfirmationButton(
                        isCompleting = uiState.isCompletingDelivery,
                        enabled = !uiState.isBusy,
                        onClick = viewModel::markPetHome
                    )
                }

                when {
                    uiState.isLoading -> Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = BrandColors.Primary)
                    }

                    uiState.isEmpty && uiState.pendingImageUri == null -> EmptyChatState(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )

                    else -> LazyColumn(
                        state = listState,
                        reverseLayout = true,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Burbuja temporal de imagen (va arriba por reverseLayout).
                        if (uiState.pendingImageUri != null) {
                            item(key = "pending-image") {
                                PendingImageBubble(
                                    imageUri = uiState.pendingImageUri,
                                    senderLabel = "${uiState.currentUserName} (Tú)"
                                )
                            }
                        }

                        items(items = uiState.messages.asReversed(), key = { it.id }) { message ->
                            MessageBubble(
                                message = message,
                                isMine = viewModel.isMine(message.senderId),
                                senderLabel = viewModel.displayName(
                                    name = message.senderName.orEmpty(),
                                    senderId = message.senderId
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    // --- BottomSheet de adjuntar archivos ---
    if (uiState.showAttachmentMenu) {
        AttachmentBottomSheet(
            onDismiss = viewModel::dismissAttachmentMenu,
            onCameraClick = {
                viewModel.dismissAttachmentMenu()
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    val photoFile = createChatImageFile(context)
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        photoFile
                    )
                    pendingCameraUri = uri
                    cameraLauncher.launch(uri)
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            onGalleryClick = {
                viewModel.dismissAttachmentMenu()
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        )
    }

    // --- Diálogo de opciones de ubicación ---
    if (uiState.showLocationOptions) {
        LocationOptionsDialog(
            onDismiss = viewModel::dismissLocationOptions,
            onCurrentLocation = {
                viewModel.dismissLocationOptions()
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.sendCurrentLocation()
                } else {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            },
            onPickOnMap = viewModel::showLocationPicker
        )
    }

    // --- Selector de ubicación en mapa (reutiliza MapPickerScreen) ---
    if (uiState.showLocationPicker) {
        MapPickerScreen(
            initialLatitude = uiState.pickerLatitude,
            initialLongitude = uiState.pickerLongitude,
            currentLatitude = uiState.pickerLatitude,
            currentLongitude = uiState.pickerLongitude,
            isLocating = uiState.isLocatingForPicker,
            locationError = if (uiState.error?.contains("ubicación") == true) uiState.error else null,
            onUseCurrentLocation = viewModel::updatePickerLocation,
            onConfirm = { lat, lon ->
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.sendSelectedLocation(lat, lon, "")
            },
            onCancel = viewModel::dismissLocationPicker
        )
    }
}

/**
 * Banner que ve únicamente el dueño del reporte perdido mientras exista un
 * match sin confirmar. Se oculta con animación en cuanto se toma una decisión.
 */
@Composable
private fun MatchConfirmationBanner(
    isConfirming: Boolean,
    isRejecting: Boolean,
    enabled: Boolean,
    onConfirm: () -> Unit,
    onReject: () -> Unit
) {
    val backgroundColor = if (BrandColors.IsDark) {
        BrandColors.WarningLight
    } else {
        Color(0xFFFEF3C7)
    }
    val titleColor = if (BrandColors.IsDark) BrandColors.TextPrimary else Color(0xFF92400E)
    val borderColor = if (BrandColors.IsDark) {
        BrandColors.WarningBorder
    } else {
        Color(0xFFFCD34D)
    }

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Pets,
                    contentDescription = null,
                    tint = titleColor,
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = if (BrandColors.IsDark) {
                                BrandColors.WarningBorder.copy(alpha = 0.25f)
                            } else {
                                Color(0xFFFFFBEB)
                            },
                            shape = CircleShape
                        )
                        .padding(6.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "¿Reconoces a tu mascota?",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = titleColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Alguien dice haberla encontrado. Revisa las fotos del chat.",
                        fontSize = 12.sp,
                        color = if (BrandColors.IsDark) BrandColors.TextSecondary else Color(0xFF92600E)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onConfirm,
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10B981),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFF10B981).copy(alpha = 0.5f),
                        disabledContentColor = Color.White.copy(alpha = 0.7f)
                    )
                ) {
                    if (isConfirming) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text("Sí, es mía", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                OutlinedButton(
                    onClick = onReject,
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (BrandColors.IsDark) BrandColors.Divider else Color(0xFF9CA3AF)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (BrandColors.IsDark) {
                            BrandColors.TextSecondary
                        } else {
                            Color(0xFF6B7280)
                        }
                    )
                ) {
                    if (isRejecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = BrandColors.TextSecondary
                        )
                    } else {
                        Text("No es mía", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * Acción secundaria que aparece solo para el dueño cuando el reporte ya está en
 * SIGHTED: la mascota fue identificada pero todavía no fue recogida.
 */
@Composable
private fun DeliveryConfirmationButton(
    isCompleting: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.height(36.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, BrandColors.Primary),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = BrandColors.Primary,
                disabledContentColor = BrandColors.TextSecondary
            )
        ) {
            if (isCompleting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = BrandColors.Primary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = "🏠 Ya está en casa",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    name: String,
    avatarUrl: String?,
    online: Boolean,
    onNavigateBack: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = BrandColors.TextPrimary
                )
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChatAvatar(name = name, avatarUrl = avatarUrl, size = 40.dp)

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = if (online) "En línea" else "Sin conexión",
                        fontSize = 12.sp,
                        color = BrandColors.TextSecondary,
                        maxLines = 1
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = BrandColors.Surface
        )
    )
}

@Composable
private fun EmptyChatState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(BrandColors.PrimaryLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = null,
                    tint = BrandColors.Primary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Aún no hay mensajes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = BrandColors.TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Escribe abajo o adjunta una foto o ubicación.",
                style = MaterialTheme.typography.bodyMedium,
                color = BrandColors.TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ChatInputBar(
    value: String,
    enabled: Boolean,
    isSending: Boolean,
    canSend: Boolean,
    attachmentsEnabled: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachClick: () -> Unit,
    onLocationClick: () -> Unit
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
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Botón de adjuntar (📎).
            IconButton(
                onClick = onAttachClick,
                enabled = attachmentsEnabled,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.AttachFile,
                    contentDescription = "Adjuntar archivo",
                    tint = BrandColors.Primary
                )
            }

            // Botón de ubicación (📍).
            IconButton(
                onClick = onLocationClick,
                enabled = attachmentsEnabled,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.MyLocation,
                    contentDescription = "Compartir ubicación",
                    tint = BrandColors.Primary
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                placeholder = {
                    Text(
                        text = "Escribe un mensaje...",
                        color = BrandColors.TextSecondary
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = BrandColors.TextPrimary
                ),
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = BrandColors.TextPrimary,
                    unfocusedTextColor = BrandColors.TextPrimary,
                    cursorColor = BrandColors.Primary,
                    focusedBorderColor = BrandColors.Primary,
                    unfocusedBorderColor = BrandColors.Divider,
                    focusedContainerColor = BrandColors.Surface,
                    unfocusedContainerColor = BrandColors.Surface,
                    focusedPlaceholderColor = BrandColors.TextSecondary,
                    unfocusedPlaceholderColor = BrandColors.TextSecondary
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            FilledIconButton(
                onClick = onSend,
                enabled = canSend,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = BrandColors.Primary,
                    contentColor = BrandColors.OnPrimary,
                    disabledContainerColor = BrandColors.PrimaryLight,
                    disabledContentColor = BrandColors.TextSecondary
                )
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = BrandColors.OnPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Enviar mensaje"
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachmentBottomSheet(
    onDismiss: () -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BrandColors.Surface
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = "Adjuntar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = BrandColors.TextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            HorizontalDivider(color = BrandColors.Divider)

            ListItem(
                headlineContent = { Text("Cámara", color = BrandColors.TextPrimary) },
                supportingContent = { Text("Tomar una foto ahora", color = BrandColors.TextSecondary) },
                leadingContent = {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = BrandColors.Primary)
                },
                modifier = Modifier.clickable { onCameraClick() }
            )
            ListItem(
                headlineContent = { Text("Galería", color = BrandColors.TextPrimary) },
                supportingContent = { Text("Elegir una foto guardada", color = BrandColors.TextSecondary) },
                leadingContent = {
                    Icon(Icons.Filled.Image, contentDescription = null, tint = BrandColors.Primary)
                },
                modifier = Modifier.clickable { onGalleryClick() }
            )
        }
    }
}

@Composable
private fun LocationOptionsDialog(
    onDismiss: () -> Unit,
    onCurrentLocation: () -> Unit,
    onPickOnMap: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compartir ubicación", color = BrandColors.TextPrimary) },
        text = {
            Column {
                TextButton(
                    onClick = onCurrentLocation,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.MyLocation, contentDescription = null, tint = BrandColors.Primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enviar mi ubicación actual", color = BrandColors.TextPrimary)
                }
                TextButton(
                    onClick = onPickOnMap,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Map, contentDescription = null, tint = BrandColors.Primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Seleccionar en mapa", color = BrandColors.TextPrimary)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = BrandColors.TextSecondary)
            }
        },
        containerColor = BrandColors.Surface
    )
}

@Composable
private fun ChatAvatar(name: String, avatarUrl: String?, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(BrandColors.PrimaryLight),
        contentAlignment = Alignment.Center
    ) {
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

private fun createChatImageFile(context: android.content.Context): File {
    val directory = File(context.cacheDir, "chat_images").apply { mkdirs() }
    return File.createTempFile("chat_${System.currentTimeMillis()}", ".jpg", directory)
}

private fun initialsOf(name: String): String =
    name.trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString(separator = "") { part -> part.first().uppercase() }
        .ifBlank { "?" }
