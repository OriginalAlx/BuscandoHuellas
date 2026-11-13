package com.app.buscandohuellas.domain.model

import androidx.compose.ui.graphics.Color

/**
 * Estados posibles de un reporte de mascota.
 *
 * [value] es el valor que se guarda en Supabase, mientras que [label] es el
 * texto legible en español. Los colores encapsulan la identidad visual de cada
 * estado en toda la app (badges, botones y marcadores del mapa).
 */
enum class ReportStatus(
    val value: String,
    val label: String,
    val textColor: Color,
    val backgroundColor: Color,
    val mapColor: Int
) {
    LOST(
        value = "LOST",
        label = "PERDIDA",
        textColor = Color(0xFFDC2626),
        backgroundColor = Color(0xFFFEE2E2),
        mapColor = 0xFFDC2626.toInt()
    ),
    SIGHTED(
        value = "SIGHTED",
        label = "AVISTADA",
        textColor = Color(0xFFEA580C),
        backgroundColor = Color(0xFFFFEDD5),
        mapColor = 0xFFEA580C.toInt()
    ),
    FOUND(
        value = "FOUND",
        label = "ENCONTRADA",
        textColor = Color(0xFF059669),
        backgroundColor = Color(0xFFD1FAE5),
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
