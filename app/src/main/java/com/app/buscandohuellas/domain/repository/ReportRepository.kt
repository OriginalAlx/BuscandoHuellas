package com.app.buscandohuellas.domain.repository

import com.app.buscandohuellas.domain.model.Report


interface ReportRepository {

    
    suspend fun createReport(report: Report): Result<Unit>

    
    suspend fun uploadReportImage(imageBytes: ByteArray, fileExtension: String): Result<String>

    
    suspend fun deleteReportImage(imageUrl: String): Result<Unit>

    suspend fun getAllReports(): Result<List<Report>>
}
