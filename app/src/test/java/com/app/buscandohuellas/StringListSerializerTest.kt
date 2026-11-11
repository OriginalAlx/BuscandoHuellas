package com.app.buscandohuellas

import com.app.buscandohuellas.data.dto.StringListSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StringListSerializerTest {

    @Serializable
    private data class Holder(
        @Serializable(with = StringListSerializer::class)
        val image_urls: List<String>? = null
    )

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodesNativeArray() {
        val holder = json.decodeFromString<Holder>("""{"image_urls":["a.jpg","b.jpg"]}""")
        assertEquals(listOf("a.jpg", "b.jpg"), holder.image_urls)
    }

    @Test
    fun decodesJsonStringArray() {
        val holder = json.decodeFromString<Holder>(
            """{"image_urls":"[\"a.jpg\",\"b.jpg\"]"}"""
        )
        assertEquals(listOf("a.jpg", "b.jpg"), holder.image_urls)
    }

    @Test
    fun decodesSingleUrlString() {
        val holder = json.decodeFromString<Holder>("""{"image_urls":"a.jpg"}""")
        assertEquals(listOf("a.jpg"), holder.image_urls)
    }

    @Test
    fun decodesNullAndMissing() {
        assertNull(json.decodeFromString<Holder>("""{"image_urls":null}""").image_urls)
        assertNull(json.decodeFromString<Holder>("{}").image_urls)
    }
}
