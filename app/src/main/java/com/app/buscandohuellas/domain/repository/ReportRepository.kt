package com.app.buscandohuellas.domain.repository

import android.net.Uri
import com.app.buscandohuellas.domain.model.Report
import com.app.buscandohuellas.domain.model.ReportImageUploadResult
import com.app.buscandohuellas.domain.model.ReportStatus

interface ReportRepository {

    suspend fun createReport(report: Report): Result<Unit>

    suspend fun uploadReportImages(uris: List<Uri>): Result<ReportImageUploadResult>

    suspend fun deleteReportImage(imageUrl: String): Result<Unit>

    suspend fun getAllReports(): Result<List<Report>>

    suspend fun getReportById(reportId: String): Result<Report>

    suspend fun updateReport(reportId: String, report: Report): Result<Unit>

    suspend fun updateReportStatus(reportId: String, status: ReportStatus): Result<Unit>
}
