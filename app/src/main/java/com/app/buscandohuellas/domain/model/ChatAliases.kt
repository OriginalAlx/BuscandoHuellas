package com.app.buscandohuellas.domain.model

/**
 * Alias legibles para los tipos del chat.
 *
 * La UI y el repositorio pueden hablar de `Message` y `Conversation` sin
 * duplicar modelos: ambos apuntan a las clases de dominio ya existentes.
 */
typealias Message = ChatMessage
typealias Conversation = ChatConversation
