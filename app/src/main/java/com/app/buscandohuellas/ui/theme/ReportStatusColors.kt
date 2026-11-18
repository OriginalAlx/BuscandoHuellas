package com.app.buscandohuellas.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.app.buscandohuellas.domain.model.ReportStatus

/**
 * Color de texto del estado según la paleta activa.
 *
 * El enum de dominio guarda ambas variantes y estos getters eligen la que
 * corresponde al tema resuelto por [BuscandoHuellasTheme].
 */
val ReportStatus.textColor: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalBrandPalette.current.isDark) darkTextColor else lightTextColor

/** Color de fondo (chip/badge) del estado según la paleta activa. */
val ReportStatus.backgroundColor: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalBrandPalette.current.isDark) darkBackgroundColor else lightBackgroundColor
