package com.marcogn.gamereviewer.di

import com.marcogn.gamereviewer.data.repository.BacklogRepositoryImpl
import com.marcogn.gamereviewer.data.repository.LookupRepositoryImpl
import com.marcogn.gamereviewer.data.repository.ReviewRepositoryImpl
import com.marcogn.gamereviewer.domain.repository.BacklogRepository
import com.marcogn.gamereviewer.domain.repository.LookupRepository
import com.marcogn.gamereviewer.domain.repository.ReviewRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindReviewRepository(impl: ReviewRepositoryImpl): ReviewRepository

    @Binds
    @Singleton
    abstract fun bindLookupRepository(impl: LookupRepositoryImpl): LookupRepository

    @Binds
    @Singleton
    abstract fun bindBacklogRepository(impl: BacklogRepositoryImpl): BacklogRepository
}
