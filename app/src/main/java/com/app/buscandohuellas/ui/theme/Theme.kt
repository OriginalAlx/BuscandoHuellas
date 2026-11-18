package com.app.buscandohuellas.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Lavender = Color(0xFF8B7CFF)

/**
 * Esquema claro con la paleta lavanda de la marca. Se usa cuando el usuario
 * elige "Claro" o cuando el sistema está en modo claro con "Automático".
 *
 * Los colores estructurales salen de [LightBrandPalette] para que
 * MaterialTheme y los tokens de [BrandColors] nunca se desincronicen.
 */
private val LightColorScheme = lightColorScheme(
    primary = LightBrandPalette.primary,
    onPrimary = LightBrandPalette.onPrimary,
    primaryContainer = LightBrandPalette.primaryContainer,
    onPrimaryContainer = Color(0xFF2E1B66),
    secondary = Lavender,
    onSecondary = Color.White,
    background = LightBrandPalette.background,
    onBackground = LightBrandPalette.textPrimary,
    surface = LightBrandPalette.surface,
    onSurface = LightBrandPalette.textPrimary,
    surfaceVariant = Color(0xFFF3F1FA),
    onSurfaceVariant = LightBrandPalette.textSecondary,
    outline = Color(0xFFD1D5DB),
    error = LightBrandPalette.danger,
    onError = Color.White,
    errorContainer = LightBrandPalette.dangerLight,
    onErrorContainer = LightBrandPalette.danger
)

/**
 * Esquema oscuro con lavanda como primario. Los grises azulados mantienen el
 * contraste suficiente para texto y controles.
 */
private val DarkColorScheme = darkColorScheme(
    primary = DarkBrandPalette.primary,
    onPrimary = DarkBrandPalette.onPrimary,
    primaryContainer = DarkBrandPalette.primaryContainer,
    onPrimaryContainer = Color(0xFFE9E3FF),
    secondary = DarkBrandPalette.primary,
    onSecondary = DarkBrandPalette.onPrimary,
    background = DarkBrandPalette.background,
    onBackground = DarkBrandPalette.textPrimary,
    surface = DarkBrandPalette.surface,
    onSurface = DarkBrandPalette.textPrimary,
    surfaceVariant = Color(0xFF2A2A36),
    onSurfaceVariant = DarkBrandPalette.textSecondary,
    outline = Color(0xFF4B4B5A),
    error = DarkBrandPalette.danger,
    onError = Color(0xFF3B0A0A),
    errorContainer = DarkBrandPalette.dangerLight,
    onErrorContainer = Color(0xFFFECACA)
)

@Composable
fun BuscandoHuellasTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Por defecto se respeta la identidad lavanda de la marca. El color
    // dinámico de Android 12+ queda como opción explícita.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val brandPalette = if (darkTheme) DarkBrandPalette else LightBrandPalette

    // La paleta de marca se provee junto con MaterialTheme para que los
    // composables que aún usan BrandColors reaccionen al mismo cambio de tema.
    CompositionLocalProvider(LocalBrandPalette provides brandPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
