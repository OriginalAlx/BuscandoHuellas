package com.app.buscandohuellas.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Tokens de color de la marca con variantes clara y oscura.
 *
 * La paleta se provee desde [BuscandoHuellasTheme] mediante
 * [LocalBrandPalette], de modo que cualquier composable que use [BrandColors]
 * reacciona automáticamente al tema elegido por el usuario.
 */
@Immutable
data class BrandPalette(
    val isDark: Boolean,
    val primary: Color,
    val onPrimary: Color,
    val primaryLight: Color,
    val primaryContainer: Color,
    val accent: Color,
    val accentLight: Color,
    val success: Color,
    val successLight: Color,
    val info: Color,
    val infoLight: Color,
    val danger: Color,
    val dangerLight: Color,
    val dangerBorder: Color,
    val warning: Color,
    val warningLight: Color,
    val warningBorder: Color,
    val disabledBackground: Color,
    val disabledText: Color,
    val backgroundTop: Color,
    val background: Color,
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
    val mapBackground: Color
)

/** Paleta clara original de la marca (lavanda sobre fondos blancos). */
internal val LightBrandPalette = BrandPalette(
    isDark = false,
    primary = Color(0xFF6C4BE0),
    onPrimary = Color(0xFFFFFFFF),
    primaryLight = Color(0xFFEDE7FC),
    primaryContainer = Color(0xFFE4DAFB),
    accent = Color(0xFFFF6B6B),
    accentLight = Color(0xFFFFE7E7),
    success = Color(0xFF3EB489),
    successLight = Color(0xFFE1F6EC),
    info = Color(0xFF3B82F6),
    infoLight = Color(0xFFE3EDFD),
    danger = Color(0xFFDC2626),
    dangerLight = Color(0xFFFEE2E2),
    dangerBorder = Color(0xFFFCA5A5),
    warning = Color(0xFFF59E0B),
    warningLight = Color(0xFFFFF8E1),
    warningBorder = Color(0xFFFFB74D),
    disabledBackground = Color(0xFFD1D5DB),
    disabledText = Color(0xFF9CA3AF),
    backgroundTop = Color(0xFFEDEAF8),
    background = Color(0xFFF7F6FC),
    surface = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF1E1F2D),
    textSecondary = Color(0xFF8B8AA3),
    divider = Color(0xFFECEAF3),
    mapBackground = Color(0xFFECEEE2)
)

/**
 * Paleta oscura: mismos acentos lavanda aclarados para mantener contraste
 * sobre superficies profundas, y contenedores tintados en lugar de blancos.
 */
internal val DarkBrandPalette = BrandPalette(
    isDark = true,
    primary = Color(0xFFB9AFFF),
    onPrimary = Color(0xFF1B1333),
    primaryLight = Color(0xFF2A2340),
    primaryContainer = Color(0xFF3B2F66),
    accent = Color(0xFFFF8A8A),
    accentLight = Color(0xFF4A2222),
    success = Color(0xFF4ADE80),
    successLight = Color(0xFF123B2C),
    info = Color(0xFF60A5FA),
    infoLight = Color(0xFF1E2A44),
    danger = Color(0xFFF87171),
    dangerLight = Color(0xFF4A1D1D),
    dangerBorder = Color(0xFF7F1D1D),
    warning = Color(0xFFFBBF24),
    warningLight = Color(0xFF4A3A12),
    warningBorder = Color(0xFFB45309),
    disabledBackground = Color(0xFF3A3A46),
    disabledText = Color(0xFF8B8AA3),
    backgroundTop = Color(0xFF17151F),
    background = Color(0xFF121218),
    surface = Color(0xFF1B1B24),
    textPrimary = Color(0xFFF3F1FA),
    textSecondary = Color(0xFFA9A6BD),
    divider = Color(0xFF2E2E3A),
    mapBackground = Color(0xFF23251F)
)

/**
 * Paleta activa según el tema resuelto (claro, oscuro o automático).
 * [BuscandoHuellasTheme] es el único punto que la provee.
 */
val LocalBrandPalette = staticCompositionLocalOf { LightBrandPalette }

/**
 * Acceso a los colores de la marca sensibles al tema.
 *
 * Se mantienen los nombres históricos (`Primary`, `Surface`, etc.) para que
 * los composables existentes se adapten al modo oscuro sin cambiar cada
 * referencia. Al ser getters composables, deben usarse dentro de un
 * composable; para contextos externos (notificaciones FCM) existe
 * [NotificationPrimary], que es un color estático.
 */
object BrandColors {

    val IsDark: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalBrandPalette.current.isDark

    val Primary: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalBrandPalette.current.primary

    val OnPrimary: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalBrandPalette.current.onPrimary

    val PrimaryLight: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalBrandPalette.current.primaryLight

    val PrimaryContainer: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalBrandPalette.current.primaryContainer

    val Accent: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalBrandPalette.current.accent

    val AccentLight: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalBrandPalette.current.accentLight

    val Success: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalBrandPalette.current.success

    val SuccessLight: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalBrandPalette.current.successLight

    val Info: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalBrandPalette.current.info

    val InfoLight: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalBrandPalette.current.infoLight

    val Danger: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalBrandPalette.current.danger

    val DangerLight: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalBrandPalette.current.dangerLight

    val DangerBorder: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalBrandPalette.current.dangerBorder

    val Warning: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalBrandPalette.current.warning

    val WarningLight: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalBrandPalette.current.warningLight

    val WarningBorder: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalBrandPalette.current.warningBorder

    val DisabledBackground: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalBrandPalette.current.disabledBackground

    val DisabledText: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalBrandPalette.current.disabledText

    val BackgroundTop: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalBrandPalette.current.backgroundTop

    val Background: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalBrandPalette.current.background

    val Surface: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalBrandPalette.current.surface

    val TextPrimary: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalBrandPalette.current.textPrimary

    val TextSecondary: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalBrandPalette.current.textSecondary

    val Divider: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalBrandPalette.current.divider

    val MapBackground: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalBrandPalette.current.mapBackground

    /**
     * Color estático de marca para contextos sin composición, como el color
     * de acento de las notificaciones FCM.
     */
    val NotificationPrimary: Color = Color(0xFF6C4BE0)
}
