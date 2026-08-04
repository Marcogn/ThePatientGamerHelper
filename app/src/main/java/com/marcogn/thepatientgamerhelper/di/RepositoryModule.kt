package com.marcogn.thepatientgamerhelper.di

import com.marcogn.thepatientgamerhelper.data.repository.BacklogRepositoryImpl
import com.marcogn.thepatientgamerhelper.data.repository.LookupRepositoryImpl
import com.marcogn.thepatientgamerhelper.data.repository.ReviewRepositoryImpl
import com.marcogn.thepatientgamerhelper.domain.repository.BacklogRepository
import com.marcogn.thepatientgamerhelper.domain.repository.LookupRepository
import com.marcogn.thepatientgamerhelper.domain.repository.ReviewRepository
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
