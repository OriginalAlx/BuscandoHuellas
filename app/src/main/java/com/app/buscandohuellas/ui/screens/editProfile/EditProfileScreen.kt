package com.app.buscandohuellas.ui.screens.editProfile

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.app.buscandohuellas.ui.theme.BrandColors
import kotlinx.coroutines.withTimeoutOrNull

// Paleta de la pantalla. Los getters leen la paleta de marca activa para que
// los textos, bordes y contenedores cambien con el modo oscuro.
private val TextPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = BrandColors.TextPrimary

private val TextSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = BrandColors.TextSecondary

private val PlaceholderGray: Color
    @Composable
    @ReadOnlyComposable
    get() = BrandColors.TextSecondary

private val BorderNormal: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.outline

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

private val DisabledBackground: Color
    @Composable
    @ReadOnlyComposable
    get() = BrandColors.DisabledBackground

private val DisabledText: Color
    @Composable
    @ReadOnlyComposable
    get() = BrandColors.DisabledText

private val ErrorRed: Color
    @Composable
    @ReadOnlyComposable
    get() = BrandColors.Danger

private val SuccessGreen: Color
    @Composable
    @ReadOnlyComposable
    get() = BrandColors.Success

private const val SUCCESS_MESSAGE = "Perfil actualizado correctamente"

/**
 * Pantalla para editar el nombre y la foto del perfil.
 *
 * El avatar se sube en cuanto el usuario lo elige; el botón de guardar solo
 * persiste el nombre en la tabla `profiles`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    userId: String,
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EditProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.selectImage(uri)
        }
    }

    LaunchedEffect(userId) {
        viewModel.loadProfile(userId)
    }

    LaunchedEffect(uiState.isSuccess) {
        if (!uiState.isSuccess) return@LaunchedEffect
        // Se muestra brevemente el snackbar y luego se vuelve al perfil.
        withTimeoutOrNull(1_200L) {
            snackbarHostState.showSnackbar(SUCCESS_MESSAGE)
        }
        viewModel.consumeSuccess()
        onSaved()
    }

    // El retroceso del sistema también avisa a Perfil para que refresque.
    BackHandler(onBack = onNavigateBack)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Editar perfil",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandColors.Surface
                )
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = SuccessGreen,
                    contentColor = BrandColors.OnPrimary
                )
            }
        },
        containerColor = BrandColors.Surface
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Lavender)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ProfileAvatar(
                    avatarUrl = uiState.avatarPreviewUri ?: uiState.avatarUrl,
                    initials = uiState.initials,
                    isUploading = uiState.isUploadingImage
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = { imagePicker.launch("image/*") },
                    enabled = !uiState.isUploadingImage && !uiState.isSaving,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (!uiState.isUploadingImage && !uiState.isSaving) {
                            Lavender
                        } else {
                            BorderNormal
                        }
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = LavenderDark,
                        disabledContentColor = DisabledText
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoCamera,
                        contentDescription = null,
                        tint = if (!uiState.isUploadingImage && !uiState.isSaving) {
                            LavenderDark
                        } else {
                            DisabledText
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "Cambiar foto",
                        color = if (!uiState.isUploadingImage && !uiState.isSaving) {
                            LavenderDark
                        } else {
                            DisabledText
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Los errores de subida se muestran aquí mismo, en rojo.
                if (!uiState.error.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.error.orEmpty(),
                        color = ErrorRed,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Nombre completo",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                NameTextField(
                    value = uiState.fullName,
                    onValueChange = viewModel::updateFullName,
                    isError = !uiState.isNameValid,
                    enabled = !uiState.isSaving
                )

                if (!uiState.isNameValid) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "El nombre no puede estar vacío",
                        color = ErrorRed,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = viewModel::saveChanges,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    // Se mantiene habilitado durante el guardado para que el
                    // spinner sea visible sobre el fondo lavanda.
                    enabled = uiState.isSaving || uiState.canSave,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Lavender,
                        contentColor = BrandColors.OnPrimary,
                        disabledContainerColor = DisabledBackground,
                        disabledContentColor = DisabledText
                    ),
                    contentPadding = PaddingValues(horizontal = 24.dp)
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = BrandColors.OnPrimary
                        )
                    } else {
                        Text(
                            text = "Guardar cambios",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Campo de nombre con borde exterior dibujado manualmente para poder mostrar
 * 2dp lavanda al enfocar y 1dp gris/rojo en el resto de estados.
 */
@Composable
private fun NameTextField(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    enabled: Boolean
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val borderColor = when {
        isError -> ErrorRed
        isFocused -> Lavender
        else -> BorderNormal
    }
    val borderWidth = if (isFocused && !isError) 2.dp else 1.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = borderWidth,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(color = TextPrimary, fontSize = 16.sp),
            placeholder = {
                Text(
                    text = "Ingresa tu nombre completo",
                    color = PlaceholderGray,
                    fontSize = 16.sp
                )
            },
            singleLine = true,
            enabled = enabled,
            isError = isError,
            interactionSource = interactionSource,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                disabledTextColor = TextPrimary,
                focusedContainerColor = BrandColors.Surface,
                unfocusedContainerColor = BrandColors.Surface,
                disabledContainerColor = BrandColors.Surface,
                cursorColor = Lavender,
                // El borde real lo dibuja el Box contenedor.
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
                errorBorderColor = Color.Transparent,
                focusedPlaceholderColor = PlaceholderGray,
                unfocusedPlaceholderColor = PlaceholderGray,
                disabledPlaceholderColor = PlaceholderGray
            )
        )
    }
}

@Composable
private fun ProfileAvatar(
    avatarUrl: String?,
    initials: String,
    isUploading: Boolean
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .size(120.dp)
            .clip(CircleShape)
            .background(LavenderSoft)
            .border(width = 2.dp, color = Lavender, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (avatarUrl.isNullOrBlank()) {
            Text(
                text = initials,
                color = Lavender,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(avatarUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Foto de perfil",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isUploading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp,
                    color = Color.White
                )
            }
        }
    }
}
