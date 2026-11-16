package com.app.buscandohuellas.domain.repository

import android.net.Uri
import com.app.buscandohuellas.domain.model.Report
import com.app.buscandohuellas.domain.model.ReportImageUploadResult
import com.app.buscandohuellas.domain.model.ReportReason
import com.app.buscandohuellas.domain.model.ReportStatus
import com.app.buscandohuellas.domain.model.ReportedPost

interface ReportRepository {

    suspend fun createReport(report: Report): Result<Unit>

    suspend fun uploadReportImages(uris: List<Uri>): Result<ReportImageUploadResult>

    suspend fun deleteReportImage(imageUrl: String): Result<Unit>

    suspend fun getAllReports(): Result<List<Report>>

    suspend fun getReportById(reportId: String): Result<Report>

    suspend fun updateReport(reportId: String, report: Report): Result<Unit>

    suspend fun updateReportStatus(reportId: String, status: ReportStatus): Result<Unit>

    suspend fun submitReport(
        reportId: String,
        reporterId: String,
        reason: ReportReason,
        customReasonText: String?
    ): Result<Unit>

    suspend fun deleteReport(reportId: String): Result<Unit>

    /**
     * Actualiza descripción y estado en un único UPDATE.
     *
     * [reason] es opcional y solo se usa para la bitácora de moderación.
     */
    suspend fun updateReportAsModerator(
        reportId: String,
        description: String,
        status: ReportStatus,
        reason: String? = null
    ): Result<Unit>

    /** Denuncias PENDING agrupadas por reporte, la más reciente primero. */
    suspend fun getReportedPosts(): Result<List<ReportedPost>>

    /** Marca como DISMISSED todas las denuncias PENDING del reporte. */
    suspend fun dismissReportFlags(reportId: String): Result<Unit>

    /** Marca como RESOLVED todas las denuncias PENDING del reporte. */
    suspend fun resolveReportFlags(reportId: String): Result<Unit>

    /** Registra EDIT, DELETE o DISMISS en public.moderation_logs. */
    suspend fun logModerationAction(
        reportId: String,
        action: String,
        reason: String?
    ): Result<Unit>
}
