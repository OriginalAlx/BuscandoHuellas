package com.app.buscandohuellas.domain.repository

import com.app.buscandohuellas.domain.model.Report

/**
 * Contrato de la capa de dominio para gestionar reportes de mascotas.
 * Objetivo 2: creación de reportes.
 */
interface ReportRepository {

    /**
     * Crea un nuevo reporte de mascota perdida asociado al usuario autenticado.
     *
     * @param report datos del reporte a persistir. El [Report.userId] que llegue
     *        se sobrescribe en la capa data con el id de la sesión real.
     * @return [Result.success] si se guardó correctamente;
     *         [Result.failure] con un mensaje amigable si no hay sesión o falla la red.
     */
    suspend fun createReport(report: Report): Result<Unit>

    /**
     * Sube una imagen al bucket "pet-reports" de Supabase Storage.
     * @param imageBytes bytes de la imagen
     * @param fileExtension extensión del archivo (jpg, png, etc.)
     * @return [Result.success] con la URL pública; [Result.failure] si falla la subida.
     */
    suspend fun uploadReportImage(imageBytes: ByteArray, fileExtension: String): Result<String>

    /**
     * Elimina una imagen subida a Supabase Storage. Operación best-effort.
     * @param imageUrl URL pública de la imagen
     * @return [Result.success] aunque falle (para no bloquear la UI)
     */
    suspend fun deleteReportImage(imageUrl: String): Result<Unit>
}
