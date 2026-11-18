package com.app.buscandohuellas.data.model

/**
 * Tema visual elegido por el usuario.
 *
 * [SYSTEM] sigue la configuración de Android; los otros dos fuerzan el modo
 * claro u oscuro independientemente del sistema.
 */
enum class AppTheme(
    val label: String,
    val description: String
) {
    LIGHT(
        label = "Claro",
        description = "Siempre con fondo claro"
    ),
    DARK(
        label = "Oscuro",
        description = "Ideal para poca luz"
    ),
    SYSTEM(
        label = "Automático (del sistema)",
        description = "Sigue el tema del dispositivo"
    );

    companion object {

        /** Convierte el valor guardado en DataStore; si no existe, usa [SYSTEM]. */
        fun fromStorage(value: String?): AppTheme =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}
