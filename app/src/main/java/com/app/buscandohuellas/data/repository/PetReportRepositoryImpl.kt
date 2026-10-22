package com.app.buscandohuellas.data.repository

import com.app.buscandohuellas.domain.model.PetReport
import com.app.buscandohuellas.domain.model.ReportType
import com.app.buscandohuellas.domain.repository.PetReportRepository

class PetReportRepositoryImpl : PetReportRepository {

    override fun getReports(): List<PetReport> = listOf(
        PetReport(
            id = 1,
            petName = "Luna",
            type = ReportType.LOST,
            description = "Perra mestiza color canela, se perdió cerca del parque central."
        ),
        PetReport(
            id = 2,
            petName = "Rocky",
            type = ReportType.FOUND,
            description = "Golden retriever encontrado en la avenida principal."
        )
    )
}
