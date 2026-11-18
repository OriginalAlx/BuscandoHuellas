package com.app.buscandohuellas.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Paleta exclusiva del panel de moderación.
 *
 * Todos los colores del diseño viven aquí para evitar hexadecimales sueltos en
 * los composables. Al ser getters composables leen la paleta de marca activa,
 * por lo que el panel también respeta el modo oscuro.
 */
object ModeratorColors {

    val Lavender: Color
        @Composable
        @ReadOnlyComposable
        get() = BrandColors.Primary

    val SecondaryText: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onSurfaceVariant

    val ChipBackground: Color
        @Composable
        @ReadOnlyComposable
        get() = if (BrandColors.IsDark) Color(0xFF2A2A36) else Color(0xFFF3F4F6)

    val ChipText: Color
        @Composable
        @ReadOnlyComposable
        get() = if (BrandColors.IsDark) Color(0xFFD7D4E3) else Color(0xFF374151)

    val LavenderLight: Color
        @Composable
        @ReadOnlyComposable
        get() = BrandColors.PrimaryLight

    val RedLight: Color
        @Composable
        @ReadOnlyComposable
        get() = BrandColors.DangerLight

    val Red: Color
        @Composable
        @ReadOnlyComposable
        get() = if (BrandColors.IsDark) Color(0xFFF87171) else Color(0xFFEF4444)

    val RedText: Color
        @Composable
        @ReadOnlyComposable
        get() = if (BrandColors.IsDark) Color(0xFFFCA5A5) else Color(0xFFDC2626)

    val Yellow: Color
        @Composable
        @ReadOnlyComposable
        get() = BrandColors.WarningLight

    val Orange: Color
        @Composable
        @ReadOnlyComposable
        get() = BrandColors.Warning

    val GrayVeryLight: Color
        @Composable
        @ReadOnlyComposable
        get() = if (BrandColors.IsDark) Color(0xFF22222C) else Color(0xFFF9FAFB)

    val GreenLight: Color
        @Composable
        @ReadOnlyComposable
        get() = BrandColors.SuccessLight

    val Green: Color
        @Composable
        @ReadOnlyComposable
        get() = BrandColors.Success
}
