package com.app.buscandohuellas.di

import com.app.buscandohuellas.data.repository.AlertRepositoryImpl
import com.app.buscandohuellas.domain.repository.AlertRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Expone una única instancia del repositorio de alertas. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AlertModule {

    @Binds
    @Singleton
    abstract fun bindAlertRepository(impl: AlertRepositoryImpl): AlertRepository
}
