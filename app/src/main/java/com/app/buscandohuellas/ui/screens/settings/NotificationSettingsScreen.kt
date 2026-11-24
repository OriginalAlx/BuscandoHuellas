package com.app.buscandohuellas.ui.screens.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.buscandohuellas.data.NotificationRadius
import com.app.buscandohuellas.domain.model.ReportStatus
import com.app.buscandohuellas.ui.theme.BrandColors
import com.app.buscandohuellas.ui.theme.textColor
import com.app.buscandohuellas.ui.utils.RuntimePermissionRequestTracker

private val WarningYellow: Color
    @Composable
    @ReadOnlyComposable
    get() = BrandColors.WarningLight

private val WarningOrange: Color
    @Composable
    @ReadOnlyComposable
    get() = BrandColors.WarningBorder

private val WarningAmberColor: Color
    @Composable
    @ReadOnlyComposable
    get() = BrandColors.Warning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    /** Estado real del permiso, verificado con ContextCompat al entrar. */
    var hasNotificationPermission by remember {
        mutableStateOf(isNotificationPermissionGranted(context))
    }
    var showNotificationSettingsDialog by remember { mutableStateOf(false) }

    fun refreshPermissionState() {
        hasNotificationPermission = isNotificationPermissionGranted(context)
        viewModel.checkPermissions()
    }

    // Se conserva para el banner de ubicación; las notificaciones usan el
    // launcher individual que exige el toggle.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val locationGranted =
            result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                hasLocationPermission(context)
        val notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            result[Manifest.permission.POST_NOTIFICATIONS] == true ||
            isNotificationPermissionGranted(context)
        val wasGranted = hasNotificationPermission

        RuntimePermissionRequestTracker.markLocationRequested()
        if (result.containsKey(Manifest.permission.POST_NOTIFICATIONS)) {
            RuntimePermissionRequestTracker.markNotificationsRequested()
        }
        hasNotificationPermission = notificationsGranted
        viewModel.updatePermissions(locationGranted, notificationsGranted)
        if (notificationsGranted && !wasGranted) {
            viewModel.onNotificationPermissionGranted()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val isGranted = granted || isNotificationPermissionGranted(context)
        val wasGranted = hasNotificationPermission

        RuntimePermissionRequestTracker.markNotificationsRequested()
        hasNotificationPermission = isGranted
        viewModel.updatePermissions(hasLocationPermission(context), isGranted)

        if (isGranted) {
            if (!wasGranted) viewModel.onNotificationPermissionGranted()
        } else {
            viewModel.onNotificationPermissionDenied()
            val activity = context.findActivity()
            if (activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            ) {
                showNotificationSettingsDialog = true
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshPermissionState()
    }

    // Si el usuario activa el permiso en Ajustes y vuelve, la pantalla debe
    // reflejarlo sin necesidad de recrearse.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPermissionState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val message = state.message ?: state.errorMessage
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Notificaciones",
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
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = BrandColors.Primary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // El banner queda reservado a la ubicación: las notificaciones
                // se gestionan exclusivamente desde su tarjeta.
                if (!state.hasLocationPermission) {
                    PermissionBanner(
                        missingLocation = true,
                        missingNotifications = false,
                        onActivate = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    )
                }

                // La tarjeta depende únicamente de la preferencia guardada:
                // con notifications_enabled = true no se renderiza.
                AnimatedVisibility(
                    visible = !state.notificationsEnabled,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    NotificationDisabledCard(
                        isSaving = state.isSaving,
                        onActivate = {
                            if (hasNotificationPermission) {
                                // El permiso del sistema ya está concedido:
                                // solo se persiste la preferencia.
                                viewModel.setNotificationsEnabled(true)
                            } else {
                                notificationPermissionLauncher.launch(
                                    Manifest.permission.POST_NOTIFICATIONS
                                )
                            }
                        }
                    )
                }

                RadiusSection(
                    selected = state.radius,
                    onSelect = viewModel::setRadius
                )

                TypesSection(
                    notifyLost = state.notifyLost,
                    notifySighted = state.notifySighted,
                    notifyFound = state.notifyFound,
                    onLostChanged = viewModel::setNotifyLost,
                    onSightedChanged = viewModel::setNotifySighted,
                    onFoundChanged = viewModel::setNotifyFound
                )

                // TEMPORAL: botón de diagnóstico de notificaciones locales.
                Button(
                    onClick = viewModel::sendTestNotification,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WarningAmberColor,
                        contentColor = BrandColors.OnPrimary
                    )
                ) {
                    Text(
                        text = "🧪 Enviar notificación de prueba",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                Button(
                    onClick = viewModel::saveChanges,
                    enabled = !state.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandColors.Primary,
                        contentColor = BrandColors.OnPrimary,
                        disabledContainerColor = BrandColors.Primary.copy(alpha = 0.6f),
                        disabledContentColor = BrandColors.OnPrimary
                    )
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = BrandColors.OnPrimary
                        )
                    } else {
                        Text(
                            text = "Guardar cambios",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }

                Text(
                    text = "Se requieren permisos de ubicación y notificaciones para " +
                        "recibir avisos de mascotas cercanas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            }
        }
    }

    if (showNotificationSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationSettingsDialog = false },
            title = { Text("Permiso de notificaciones desactivado") },
            text = {
                Text(
                    "Marcaste «No volver a preguntar». Para recibir alertas, abre " +
                        "Ajustes → Aplicaciones → Buscando Huellas → Permisos y habilita Notificaciones."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNotificationSettingsDialog = false
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }
                ) {
                    Text("Abrir configuración")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationSettingsDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun PermissionBanner(
    missingLocation: Boolean,
    missingNotifications: Boolean,
    onActivate: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val message = when {
        missingLocation && missingNotifications ->
            "Activa la ubicación y las notificaciones para recibir avisos de mascotas cercanas."

        missingLocation ->
            "Activa la ubicación para recibir avisos de mascotas cercanas."

        else ->
            "Activa las notificaciones para recibir avisos de mascotas cercanas."
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(WarningYellow)
            .border(1.dp, WarningOrange, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = WarningAmberColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Faltan permisos",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = BrandColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandColors.TextSecondary
                )
            }
        }

        Button(
            onClick = onActivate,
            modifier = Modifier.align(Alignment.End),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = WarningAmberColor,
                contentColor = BrandColors.OnPrimary
            )
        ) {
            Text(text = "Activar", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun NotificationDisabledCard(
    isSaving: Boolean,
    onActivate: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BrandColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(BrandColors.PrimaryLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = BrandColors.Primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Notificaciones desactivadas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = BrandColors.TextPrimary
                )
                Text(
                    text = "Actívalas para recibir alertas de reportes cercanos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandColors.TextSecondary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onActivate,
                enabled = !isSaving,
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandColors.Primary,
                    contentColor = BrandColors.OnPrimary,
                    disabledContainerColor = BrandColors.DisabledBackground,
                    disabledContentColor = BrandColors.DisabledText
                )
            ) {
                Text(
                    text = "Activar",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun RadiusSection(
    selected: NotificationRadius,
    onSelect: (NotificationRadius) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel(text = "RANGO")
        Text(
            text = "¿A qué distancia te avisamos?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = BrandColors.TextPrimary
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RadiusCard(
                radius = NotificationRadius.ONE_KM,
                selected = selected == NotificationRadius.ONE_KM,
                onSelect = onSelect,
                modifier = Modifier.weight(1f)
            )
            RadiusCard(
                radius = NotificationRadius.THREE_KM,
                selected = selected == NotificationRadius.THREE_KM,
                onSelect = onSelect,
                modifier = Modifier.weight(1f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RadiusCard(
                radius = NotificationRadius.FIVE_KM,
                selected = selected == NotificationRadius.FIVE_KM,
                onSelect = onSelect,
                modifier = Modifier.weight(1f)
            )
            RadiusCard(
                radius = NotificationRadius.TEN_KM,
                selected = selected == NotificationRadius.TEN_KM,
                onSelect = onSelect,
                modifier = Modifier.weight(1f)
            )
        }

        AllDistancesCard(
            selected = selected == NotificationRadius.ALL,
            onClick = { onSelect(NotificationRadius.ALL) }
        )
    }
}

@Composable
private fun RadiusCard(
    radius: NotificationRadius,
    selected: Boolean,
    onSelect: (NotificationRadius) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    val accentColor = if (selected) BrandColors.Primary else BrandColors.TextPrimary

    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) BrandColors.PrimaryLight else BrandColors.Surface)
            .border(
                width = 1.dp,
                color = if (selected) BrandColors.Primary else BrandColors.Divider,
                shape = shape
            )
            .clickable { onSelect(radius) }
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = formatRadiusValue(radius),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "km",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) BrandColors.Primary else BrandColors.TextSecondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = radius.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) BrandColors.Primary else BrandColors.TextSecondary
            )
        }

        if (selected) {
            SelectedCheck(modifier = Modifier.align(Alignment.TopEnd))
        }
    }
}

@Composable
private fun AllDistancesCard(
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) BrandColors.PrimaryLight else BrandColors.Surface)
            .border(
                width = 1.dp,
                color = if (selected) BrandColors.Primary else BrandColors.Divider,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (selected) BrandColors.Primary else BrandColors.PrimaryLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Public,
                contentDescription = null,
                tint = if (selected) BrandColors.OnPrimary else BrandColors.Primary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = NotificationRadius.ALL.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = BrandColors.TextPrimary
            )
            Text(
                text = NotificationRadius.ALL.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) BrandColors.Primary else BrandColors.TextSecondary
            )
        }

        if (selected) {
            SelectedCheck()
        }
    }
}

@Composable
private fun TypesSection(
    notifyLost: Boolean,
    notifySighted: Boolean,
    notifyFound: Boolean,
    onLostChanged: (Boolean) -> Unit,
    onSightedChanged: (Boolean) -> Unit,
    onFoundChanged: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel(text = "TIPOS")
        Text(
            text = "¿Qué te queremos avisar?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = BrandColors.TextPrimary
        )

        TypeToggleCard(
            label = "Mascotas perdidas",
            dotColor = ReportStatus.LOST.textColor,
            checked = notifyLost,
            onCheckedChange = onLostChanged
        )
        TypeToggleCard(
            label = "Mascotas avistadas",
            dotColor = ReportStatus.SIGHTED.textColor,
            checked = notifySighted,
            onCheckedChange = onSightedChanged
        )
        TypeToggleCard(
            label = "Mascotas encontradas",
            dotColor = ReportStatus.FOUND.textColor,
            checked = notifyFound,
            onCheckedChange = onFoundChanged
        )
    }
}

@Composable
private fun TypeToggleCard(
    label: String,
    dotColor: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(BrandColors.Surface)
            .border(1.dp, BrandColors.Divider, shape)
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = BrandColors.TextPrimary
        )

        CheckboxIndicator(checked = checked)
    }
}

@Composable
private fun CheckboxIndicator(checked: Boolean) {
    val shape = RoundedCornerShape(6.dp)

    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(shape)
            .background(if (checked) BrandColors.Primary else Color.Transparent)
            .border(
                width = 1.5.dp,
                color = if (checked) BrandColors.Primary else BrandColors.Divider,
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = BrandColors.OnPrimary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun SelectedCheck(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(BrandColors.Primary),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = BrandColors.OnPrimary,
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = BrandColors.Primary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
}

private fun formatRadiusValue(radius: NotificationRadius): String {
    val value = radius.valueKm ?: return "∞"
    return if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

private fun isNotificationPermissionGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

/** Busca la Activity anclada al contexto de Compose. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
