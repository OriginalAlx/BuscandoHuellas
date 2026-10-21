package com.app.buscandohuellas.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Paleta de marca extraída del diseño de alta fidelidad (Figma) de BuscandoHuellas.
 *
 * Uso:
 *  - Primary: color principal (morado) para acciones, texto activo y enlaces.
 *  - Accent: rosa/rojo para estados "PERDIDA", CTA "Reportar" y acciones destructivas.
 *  - Success: verde para estados "ENCONTRADA".
 *  - Info: azul para indicadores no leídos y el mapa.
 *  - Los tonos *Light son los fondos suaves de los iconos/chips.
 *  - BackgroundTop/Background componen el degradado vertical del fondo.
 */
object BrandColors {

    /** Morado principal: enlaces, tabs activos, botones. */
    val Primary = Color(0xFF6C4BE0)

    /** Lavanda muy claro: cabecera, banners informativos y fondos de iconos. */
    val PrimaryLight = Color(0xFFEDE7FC)

    /** Lavanda intermedio: indicador de selección del BottomNavigation. */
    val PrimaryContainer = Color(0xFFE4DAFB)

    /** Rosa/rojo coral: alertas "PERDIDA", CTA principal, cerrar sesión. */
    val Accent = Color(0xFFFF6B6B)

    /** Tinte rosa suave para chips e iconos de alerta. */
    val AccentLight = Color(0xFFFFE7E7)

    /** Verde: estados "ENCONTRADA" y marcadores encontrados en el mapa. */
    val Success = Color(0xFF3EB489)

    /** Tinte verde suave. */
    val SuccessLight = Color(0xFFE1F6EC)

    /** Azul: indicadores no leídos y marcador "mi ubicación". */
    val Info = Color(0xFF3B82F6)

    /** Tinte azul suave. */
    val InfoLight = Color(0xFFE3EDFD)

    /** Color superior del degradado de fondo (cabecera lavanda). */
    val BackgroundTop = Color(0xFFEDEAF8)

    /** Color inferior del degradado de fondo. */
    val Background = Color(0xFFF7F6FC)

    /** Fondo de tarjetas y barras. */
    val Surface = Color(0xFFFFFFFF)

    /** Texto principal. */
    val TextPrimary = Color(0xFF1E1F2D)

    /** Texto secundario / metadatos. */
    val TextSecondary = Color(0xFF8B8AA3)

    /** Separadores y divisores. */
    val Divider = Color(0xFFECEAF3)

    /** Fondo del mapa (placeholder hasta integrar el SDK real). */
    val MapBackground = Color(0xFFECEEE2)
}
