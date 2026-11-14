package com.app.buscandohuellas.domain.model

/**
 * Motivos por los que un usuario puede reportar una publicación.
 *
 * [value] es el valor que se guarda en Supabase y [displayName] el texto que se
 * muestra en la UI.
 */
enum class ReportReason(
    val value: String,
    val displayName: String
) {
    SPAM_DUPLICATE(
        value = "SPAM_DUPLICATE",
        displayName = "Es spam o está duplicado"
    ),
    FAKE_SCAM(
        value = "FAKE_SCAM",
        displayName = "Es falso o parece una estafa"
    ),
    INCORRECT_INFO(
        value = "INCORRECT_INFO",
        displayName = "La información es incorrecta"
    ),
    INAPPROPRIATE(
        value = "INAPPROPRIATE",
        displayName = "Contenido inapropiado u ofensivo"
    ),
    PRIVATE_INFO(
        value = "PRIVATE_INFO",
        displayName = "Contiene información privada"
    ),
    OTHER(
        value = "OTHER",
        displayName = "Otro motivo"
    )
}
