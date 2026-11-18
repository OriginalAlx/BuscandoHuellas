package com.app.buscandohuellas.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.buscandohuellas.BuildConfig
import com.app.buscandohuellas.data.model.AppTheme
import com.app.buscandohuellas.ui.theme.BrandColors
import kotlinx.coroutines.launch

private const val SUPPORT_EMAIL = "alexperalta0321@gmail.com"

// Paleta de acentos. Los textos principales usan el colorScheme de Material 3
// para que la pantalla siga siendo legible en tema oscuro. Estos getters leen
// la paleta de marca activa, así que también cambian al alternar el tema.
private val Lavender: Color
    @Composable
    @ReadOnlyComposable
    get() = BrandColors.Primary

private val LavenderDark: Color
    @Composable
    @ReadOnlyComposable
    get() = BrandColors.Primary

private val LavenderSoft: Color
    @Composable
    @ReadOnlyComposable
    get() = BrandColors.PrimaryLight

private val DangerRed: Color
    @Composable
    @ReadOnlyComposable
    get() = BrandColors.Danger

private val DangerSoft: Color
    @Composable
    @ReadOnlyComposable
    get() = BrandColors.DangerLight

private val DangerBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = BrandColors.DangerBorder

private val DisabledBackground: Color
    @Composable
    @ReadOnlyComposable
    get() = BrandColors.DisabledBackground

private val DisabledText: Color
    @Composable
    @ReadOnlyComposable
    get() = BrandColors.DisabledText

/**
 * Pantalla de Configuración con las secciones Apariencia, Cuenta, Soporte y
 * Acerca de. El borrado de cuenta es modal y exige escribir "ELIMINAR".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onAccountDeleted: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteSheet by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        // El borrado es destructivo: ni el gesto ni el scrim deben cerrarlo.
        confirmValueChange = { value -> value != SheetValue.Hidden }
    )

    LaunchedEffect(Unit) {
        viewModel.loadSettings()
    }

    LaunchedEffect(uiState.isAccountDeleted) {
        if (!uiState.isAccountDeleted) return@LaunchedEffect
        showDeleteSheet = false
        viewModel.resetDeleteState()
        onAccountDeleted()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Configuración",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            AppearanceSection(
                selectedTheme = uiState.selectedTheme,
                onThemeSelected = viewModel::updateTheme
            )

            if (!uiState.error.isNullOrBlank() && !showDeleteSheet) {
                Text(
                    text = uiState.error.orEmpty(),
                    color = DangerRed,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            AccountSection(onDeleteClick = { showDeleteSheet = true })

            SupportSection(
                onClick = {
                    if (!sendSupportEmail(context)) {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                "No se encontró una app de correo instalada."
                            )
                        }
                    }
                }
            )

            AboutSection()
        }
    }

    if (showDeleteSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                // Modal: tocar fuera o pulsar atrás no lo cierra. Solo el botón
                // "Cancelar" descarta el diálogo.
            },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            DeleteAccountSheetContent(
                confirmationText = uiState.deleteConfirmationText,
                isDeleting = uiState.isDeletingAccount,
                canDelete = uiState.canDeleteAccount,
                error = uiState.error,
                onConfirmationChange = viewModel::updateDeleteConfirmation,
                onCancel = { showDeleteSheet = false },
                onConfirm = viewModel::deleteAccount
            )
        }
    }
}

@Composable
private fun AppearanceSection(
    selectedTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(text = "APARIENCIA", color = Lavender)
        Text(
            text = "Tema de la app",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Elige cómo quieres ver la app",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        AppTheme.entries.forEach { theme ->
            ThemeOptionRow(
                theme = theme,
                selected = selectedTheme == theme,
                onSelect = { onThemeSelected(theme) }
            )
        }
    }
}

@Composable
private fun ThemeOptionRow(
    theme: AppTheme,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) LavenderSoft else Color.Transparent)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = Lavender,
                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = theme.icon(),
            contentDescription = null,
            tint = if (selected) LavenderDark else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = theme.label,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) LavenderDark else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun AccountSection(onDeleteClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(text = "CUENTA", color = DangerRed)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DangerSoft)
                .border(1.dp, DangerBorder, RoundedCornerShape(12.dp))
                .clickable(onClick = onDeleteClick)
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = DangerRed,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Eliminar mi cuenta",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = DangerRed
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Esta acción es irreversible. Se borrarán todos tus reportes y datos.",
                    fontSize = 12.sp,
                    color = DangerRed
                )
            }
        }
    }
}

@Composable
private fun SupportSection(onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(text = "SOPORTE", color = Lavender)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
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
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(LavenderSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Email,
                        contentDescription = null,
                        tint = Lavender,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Contacto y soporte",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "¿Tienes problemas o sugerencias? Escríbenos",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun AboutSection() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(text = "ACERCA DE", color = Lavender)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.Pets,
                    contentDescription = "Logo de Buscando Huellas",
                    tint = Lavender,
                    modifier = Modifier.size(64.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Buscando Huellas",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Versión ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "© 2026 Alex Peralta & Eden Riveros",
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DeleteAccountSheetContent(
    confirmationText: String,
    isDeleting: Boolean,
    canDelete: Boolean,
    error: String?,
    onConfirmationChange: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.WarningAmber,
            contentDescription = null,
            tint = DangerRed,
            modifier = Modifier.size(48.dp)
        )

        Text(
            text = "¿Eliminar cuenta permanentemente?",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "Se eliminarán tu cuenta, todos tus reportes y datos asociados. " +
                "Esta acción no se puede deshacer.",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = confirmationText,
            onValueChange = onConfirmationChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = "Escribe ELIMINAR para confirmar",
                    color = DisabledText,
                    fontSize = 14.sp
                )
            },
            singleLine = true,
            enabled = !isDeleting,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                disabledContainerColor = MaterialTheme.colorScheme.surface,
                cursorColor = DangerRed,
                focusedBorderColor = DangerRed,
                unfocusedBorderColor = DisabledBackground,
                disabledBorderColor = DisabledBackground,
                focusedPlaceholderColor = DisabledText,
                unfocusedPlaceholderColor = DisabledText,
                disabledPlaceholderColor = DisabledText
            )
        )

        if (!error.isNullOrBlank()) {
            Text(
                text = error,
                color = DangerRed,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(
                onClick = onCancel,
                enabled = !isDeleting,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text(
                    text = "Cancelar",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }

            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                // Durante el borrado se mantiene habilitado para que el spinner
                // sea visible sobre el fondo rojo.
                enabled = isDeleting || canDelete,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DangerRed,
                    contentColor = BrandColors.OnPrimary,
                    disabledContainerColor = DisabledBackground,
                    disabledContentColor = DisabledText
                ),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = BrandColors.OnPrimary
                    )
                } else {
                    Text(
                        text = "Sí, eliminar mi cuenta",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
}

private fun AppTheme.icon(): ImageVector = when (this) {
    AppTheme.LIGHT -> Icons.Filled.WbSunny
    AppTheme.DARK -> Icons.Filled.DarkMode
    AppTheme.SYSTEM -> Icons.Filled.PhoneAndroid
}

/**
 * Abre el selector de apps de correo con un mensaje pre-llenado que incluye
 * datos de diagnóstico para facilitar el soporte.
 *
 * @return `false` si el dispositivo no tiene ninguna app que maneje `mailto:`.
 */
private fun sendSupportEmail(context: Context): Boolean {
    val version = "Versión ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"
    val device = "${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
    val body = """
        Hola equipo de Buscando Huellas,

        Necesito ayuda con:


        ---
        Información de diagnóstico:
        $version
        Dispositivo: $device
    """.trimIndent()

    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, "Soporte Buscando Huellas")
        putExtra(Intent.EXTRA_TEXT, body)
    }

    return try {
        context.startActivity(
            Intent.createChooser(intent, "Elige una app de correo")
        )
        true
    } catch (error: ActivityNotFoundException) {
        false
    }
}
