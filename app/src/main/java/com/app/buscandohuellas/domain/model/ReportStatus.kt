package com.app.buscandohuellas.domain.model

import androidx.compose.ui.graphics.Color

/**
 * Estados posibles de un reporte de mascota.
 *
 * [value] es el valor que se guarda en Supabase, mientras que [label] es el
 * texto legible en español. Cada estado define colores para tema claro y
 * oscuro; los getters composables [com.app.buscandohuellas.ui.theme.textColor]
 * y [com.app.buscandohuellas.ui.theme.backgroundColor] eligen la variante
 * activa. [mapColor] se mantiene estático porque los marcadores se dibujan
 * sobre el mapa claro de OpenStreetMap.
 */
enum class ReportStatus(
    val value: String,
    val label: String,
    val lightTextColor: Color,
    val lightBackgroundColor: Color,
    val darkTextColor: Color,
    val darkBackgroundColor: Color,
    val mapColor: Int
) {
    LOST(
        value = "LOST",
        label = "PERDIDA",
        lightTextColor = Color(0xFFDC2626),
        lightBackgroundColor = Color(0xFFFEE2E2),
        darkTextColor = Color(0xFFF87171),
        darkBackgroundColor = Color(0xFF4A1D1D),
        mapColor = 0xFFDC2626.toInt()
    ),
    SIGHTED(
        value = "SIGHTED",
        label = "AVISTADA",
        lightTextColor = Color(0xFFEA580C),
        lightBackgroundColor = Color(0xFFFFEDD5),
        darkTextColor = Color(0xFFFB923C),
        darkBackgroundColor = Color(0xFF4A2A12),
        mapColor = 0xFFEA580C.toInt()
    ),
    FOUND(
        value = "FOUND",
        label = "ENCONTRADA",
        lightTextColor = Color(0xFF059669),
        lightBackgroundColor = Color(0xFFD1FAE5),
        darkTextColor = Color(0xFF34D399),
        darkBackgroundColor = Color(0xFF123B2C),
        mapColor = 0xFF059669.toInt()
    );

    /**
     * Siguiente estado en el ciclo PERDIDA → AVISTADA → ENCONTRADA → PERDIDA.
     */
    fun next(): ReportStatus = when (this) {
        LOST -> SIGHTED
        SIGHTED -> FOUND
        FOUND -> LOST
    }

    companion object {
        /**
         * Convierte el string de Supabase en un estado de dominio. Cualquier
         * valor desconocido o vacío cae en [LOST] para no romper la UI.
         */
        fun fromString(value: String?): ReportStatus {
            val normalized = value?.trim().orEmpty()
            return entries.firstOrNull { it.value.equals(normalized, ignoreCase = true) } ?: LOST
        }
    }
}
