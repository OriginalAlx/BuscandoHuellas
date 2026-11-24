package com.app.buscandohuellas.data.repository

import android.util.Log
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException

internal object SocialErrorMapper {

    const val NO_CONNECTION = "No hay conexión a internet. Verifica tu red e intenta de nuevo."

    /**
     * Traduce errores de PostgREST a mensajes listos para mostrar en la UI.
     * Nunca se usa con `CancellationException`: esas deben propagarse.
     */
    fun map(error: Throwable, tag: String, fallback: String): Exception = when (error) {
        is HttpRequestException -> {
            Log.e(tag, "HttpRequestException", error)
            Exception(NO_CONNECTION)
        }

        is RestException -> {
            Log.e(tag, "RestException", error)
            Exception(restMessage(error.message.orEmpty(), fallback))
        }

        else -> {
            Log.e(tag, "Error inesperado", error)
            Exception(error.message?.takeIf { it.isNotBlank() } ?: fallback)
        }
    }

    private fun restMessage(message: String, fallback: String): String = when {
        message.contains("row-level security", ignoreCase = true) ||
            message.contains("permission denied", ignoreCase = true) ||
            message.contains("new row violates", ignoreCase = true) ->
            "No tienes permiso para realizar esta acción."

        message.contains("duplicate key", ignoreCase = true) ||
            message.contains("23505", ignoreCase = true) ||
            message.contains("unique constraint", ignoreCase = true) ->
            "Ya existe un registro con esos datos."

        message.contains("foreign key", ignoreCase = true) ||
            message.contains("23503", ignoreCase = true) ->
            "El reporte o la conversación ya no existe."

        message.contains("does not exist", ignoreCase = true) ||
            message.contains("schema cache", ignoreCase = true) ->
            "Falta configurar la tabla en Supabase. Inténtalo más tarde."

        else -> fallback
    }
}

/**
 * `true` cuando PostgREST no conoce una tabla o columna (normalmente porque el
 * script SQL no se ejecutó o porque hay que recargar la caché del esquema).
 */
internal fun isMissingSchemaColumnError(error: Throwable): Boolean {
    var current: Throwable? = error
    while (current != null) {
        val message = current.message.orEmpty()
        if (message.contains("does not exist", ignoreCase = true) ||
            message.contains("schema cache", ignoreCase = true) ||
            message.contains("column", ignoreCase = true) &&
            message.contains("could not find", ignoreCase = true)
        ) {
            return true
        }
        current = current.cause
    }
    return false
}

/** `true` cuando Supabase rechaza un valor para una columna generada. */
internal fun isGeneratedColumnError(error: Throwable): Boolean {
    var current: Throwable? = error
    while (current != null) {
        val message = current.message.orEmpty()
        if (message.contains("generated column", ignoreCase = true) ||
            message.contains("cannot insert a non-default value", ignoreCase = true)
        ) {
            return true
        }
        current = current.cause
    }
    return false
}

/** `true` cuando la conversación ya fue creada por otro proceso. */
internal fun isDuplicateKeyError(error: Throwable): Boolean {
    var current: Throwable? = error
    while (current != null) {
        val message = current.message.orEmpty()
        if (message.contains("duplicate key", ignoreCase = true) ||
            message.contains("23505", ignoreCase = true) ||
            message.contains("unique constraint", ignoreCase = true)
        ) {
            return true
        }
        current = current.cause
    }
    return false
}
