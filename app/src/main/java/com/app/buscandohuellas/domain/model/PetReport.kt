package com.app.buscandohuellas.domain.model

enum class ReportType(val statusLabel: String) {
    LOST("Perdido"),
    FOUND("Encontrado")
}

data class PetReport(
    val id: Int,
    val petName: String,
    val type: ReportType,
    val description: String
)
