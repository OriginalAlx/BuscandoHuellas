package com.app.buscandohuellas.di

import com.app.buscandohuellas.SupabaseClient as SupabaseAppClient
import com.app.buscandohuellas.data.repository.AuthRepositoryImpl
import com.app.buscandohuellas.domain.repository.AuthRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient = SupabaseAppClient.client

    @Provides
    @Singleton
    fun provideAuthRepository(client: SupabaseClient): AuthRepository = AuthRepositoryImpl(client)
}
