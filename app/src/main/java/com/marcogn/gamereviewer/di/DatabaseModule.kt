package com.marcogn.gamereviewer.di

import android.content.Context
import androidx.room.Room
import com.marcogn.gamereviewer.data.local.GameReviewerDatabase
import com.marcogn.gamereviewer.data.local.dao.GenreDao
import com.marcogn.gamereviewer.data.local.dao.PlatformDao
import com.marcogn.gamereviewer.data.local.dao.ReviewDao
import com.marcogn.gamereviewer.data.local.dao.TagDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GameReviewerDatabase =
        Room.databaseBuilder(
            context,
            GameReviewerDatabase::class.java,
            GameReviewerDatabase.DATABASE_NAME,
        ).build()

    @Provides
    fun provideReviewDao(database: GameReviewerDatabase): ReviewDao = database.reviewDao()

    @Provides
    fun providePlatformDao(database: GameReviewerDatabase): PlatformDao = database.platformDao()

    @Provides
    fun provideGenreDao(database: GameReviewerDatabase): GenreDao = database.genreDao()

    @Provides
    fun provideTagDao(database: GameReviewerDatabase): TagDao = database.tagDao()
}
