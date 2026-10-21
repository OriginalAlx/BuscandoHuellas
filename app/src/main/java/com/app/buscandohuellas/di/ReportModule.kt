package com.app.buscandohuellas.di

import com.app.buscandohuellas.data.repository.ReportRepositoryImpl
import com.app.buscandohuellas.domain.repository.ReportRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Vincula la interfaz de dominio [ReportRepository] con su implementación.
 * Se usa @Binds porque [ReportRepositoryImpl] ya se construye con @Inject
 * constructor (Hilt le inyecta el SupabaseClient).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReportModule {

    @Binds
    @Singleton
    abstract fun bindReportRepository(impl: ReportRepositoryImpl): ReportRepository
}
