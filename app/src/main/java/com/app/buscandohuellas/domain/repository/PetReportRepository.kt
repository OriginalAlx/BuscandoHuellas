package com.app.buscandohuellas.domain.repository

import com.app.buscandohuellas.domain.model.PetReport

interface PetReportRepository {
    fun getReports(): List<PetReport>
}
