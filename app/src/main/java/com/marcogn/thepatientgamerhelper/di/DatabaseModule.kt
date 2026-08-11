package com.marcogn.thepatientgamerhelper.di

import android.content.Context
import androidx.room.Room
import com.marcogn.thepatientgamerhelper.data.local.ThePatientGamerHelperDatabase
import com.marcogn.thepatientgamerhelper.data.local.MIGRATION_1_2
import com.marcogn.thepatientgamerhelper.data.local.MIGRATION_2_3
import com.marcogn.thepatientgamerhelper.data.local.MIGRATION_3_4
import com.marcogn.thepatientgamerhelper.data.local.MIGRATION_4_5
import com.marcogn.thepatientgamerhelper.data.local.dao.BacklogDao
import com.marcogn.thepatientgamerhelper.data.local.dao.GenreDao
import com.marcogn.thepatientgamerhelper.data.local.dao.PlatformDao
import com.marcogn.thepatientgamerhelper.data.local.dao.ReviewDao
import com.marcogn.thepatientgamerhelper.data.local.dao.TagDao
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
    fun provideDatabase(@ApplicationContext context: Context): ThePatientGamerHelperDatabase =
        Room.databaseBuilder(
            context,
            ThePatientGamerHelperDatabase::class.java,
            ThePatientGamerHelperDatabase.DATABASE_NAME,
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build()

    @Provides
    fun provideReviewDao(database: ThePatientGamerHelperDatabase): ReviewDao = database.reviewDao()

    @Provides
    fun providePlatformDao(database: ThePatientGamerHelperDatabase): PlatformDao = database.platformDao()

    @Provides
    fun provideGenreDao(database: ThePatientGamerHelperDatabase): GenreDao = database.genreDao()

    @Provides
    fun provideTagDao(database: ThePatientGamerHelperDatabase): TagDao = database.tagDao()

    @Provides
    fun provideBacklogDao(database: ThePatientGamerHelperDatabase): BacklogDao = database.backlogDao()
}
