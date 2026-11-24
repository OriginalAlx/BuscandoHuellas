package com.app.buscandohuellas.di

import com.app.buscandohuellas.data.repository.CommentRepositoryImpl
import com.app.buscandohuellas.domain.repository.CommentRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CommentModule {

    @Binds
    @Singleton
    abstract fun bindCommentRepository(impl: CommentRepositoryImpl): CommentRepository
}
