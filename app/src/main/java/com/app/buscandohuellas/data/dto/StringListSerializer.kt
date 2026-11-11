package com.app.buscandohuellas.data.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * Acepta tanto un arreglo nativo de PostgREST (`["url1", "url2"]`) como un
 * string que contenga ese arreglo (`"[\"url1\", \"url2\"]"`) o una sola URL.
 * PostgREST devuelve `text[]` como arreglo, pero este fallback evita romper la
 * decodificación si la columna se expone como texto JSON.
 */
object StringListSerializer : KSerializer<List<String>?> {

    private val delegate = ListSerializer(String.serializer())

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): List<String>? {
        val jsonDecoder = decoder as? JsonDecoder ?: return null
        return parse(jsonDecoder.decodeJsonElement())
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: List<String>?) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            encoder.encodeSerializableValue(delegate, value)
        }
    }

    private fun parse(element: JsonElement?): List<String>? = when (element) {
        null, JsonNull -> null
        is JsonArray -> element
            .mapNotNull { it.asUrlOrNull() }
            .takeIf { it.isNotEmpty() }
        is JsonPrimitive -> parsePrimitive(element)
        else -> null
    }

    private fun parsePrimitive(element: JsonPrimitive): List<String>? {
        if (!element.isString) return null

        val raw = element.content.trim()
        if (raw.isEmpty()) return null

        if (raw.startsWith("[")) {
            val parsed = runCatching { Json.parseToJsonElement(raw) }.getOrNull()
            if (parsed != null) return parse(parsed)
        }

        return listOf(raw).takeIf { it.isNotEmpty() }
    }

    private fun JsonElement.asUrlOrNull(): String? {
        val value = (this as? JsonPrimitive)?.content?.trim().orEmpty()
        return value.takeIf { it.isNotBlank() }
    }
}
