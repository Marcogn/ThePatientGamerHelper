package com.marcogn.thepatientgamerhelper.di

import com.marcogn.thepatientgamerhelper.data.export.NoOpPdfTemplateProvider
import com.marcogn.thepatientgamerhelper.data.export.PdfTemplateProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PdfModule {

    @Binds
    @Singleton
    abstract fun bindPdfTemplateProvider(impl: NoOpPdfTemplateProvider): PdfTemplateProvider
}
