package com.marcogn.gamereviewer.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.marcogn.gamereviewer.data.local.dao.GenreDao
import com.marcogn.gamereviewer.data.local.dao.PlatformDao
import com.marcogn.gamereviewer.data.local.dao.ReviewDao
import com.marcogn.gamereviewer.data.local.dao.TagDao
import com.marcogn.gamereviewer.data.local.entity.GenreEntity
import com.marcogn.gamereviewer.data.local.entity.PlatformEntity
import com.marcogn.gamereviewer.data.local.entity.ReviewEntity
import com.marcogn.gamereviewer.data.local.entity.ReviewGenreCrossRef
import com.marcogn.gamereviewer.data.local.entity.ReviewPlatformCrossRef
import com.marcogn.gamereviewer.data.local.entity.ReviewProConEntity
import com.marcogn.gamereviewer.data.local.entity.ReviewTagCrossRef
import com.marcogn.gamereviewer.data.local.entity.TagEntity

@Database(
    entities = [
        ReviewEntity::class,
        PlatformEntity::class,
        GenreEntity::class,
        TagEntity::class,
        ReviewPlatformCrossRef::class,
        ReviewGenreCrossRef::class,
        ReviewTagCrossRef::class,
        ReviewProConEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class GameReviewerDatabase : RoomDatabase() {
    abstract fun reviewDao(): ReviewDao
    abstract fun platformDao(): PlatformDao
    abstract fun genreDao(): GenreDao
    abstract fun tagDao(): TagDao

    companion object {
        const val DATABASE_NAME = "game_reviewer.db"
    }
}
