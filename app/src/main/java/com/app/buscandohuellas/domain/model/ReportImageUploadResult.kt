package com.app.buscandohuellas.domain.model

data class ReportImageUploadResult(
    val urls: List<String>,
    val failedCount: Int
) {
    val hasFailures: Boolean
        get() = failedCount > 0
}
