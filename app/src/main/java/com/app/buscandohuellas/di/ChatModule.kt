package com.app.buscandohuellas.di

import com.app.buscandohuellas.data.repository.ChatRepositoryImpl
import com.app.buscandohuellas.domain.repository.ChatRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Mantiene una única instancia del repositorio de chat, que ahora persiste
 * conversaciones y mensajes en Supabase.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ChatModule {

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository
}
