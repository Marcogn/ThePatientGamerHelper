package com.marcogn.thepatientgamerhelper.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.marcogn.thepatientgamerhelper.data.local.dao.BacklogDao
import com.marcogn.thepatientgamerhelper.data.local.dao.GenreDao
import com.marcogn.thepatientgamerhelper.data.local.dao.PlatformDao
import com.marcogn.thepatientgamerhelper.data.local.dao.ReviewDao
import com.marcogn.thepatientgamerhelper.data.local.dao.TagDao
import com.marcogn.thepatientgamerhelper.data.local.entity.BacklogCommentEntity
import com.marcogn.thepatientgamerhelper.data.local.entity.BacklogHistoryEntryEntity
import com.marcogn.thepatientgamerhelper.data.local.entity.BacklogItemEntity
import com.marcogn.thepatientgamerhelper.data.local.entity.BacklogItemGenreCrossRef
import com.marcogn.thepatientgamerhelper.data.local.entity.BacklogItemPlatformCrossRef
import com.marcogn.thepatientgamerhelper.data.local.entity.BacklogItemTagCrossRef
import com.marcogn.thepatientgamerhelper.data.local.entity.BacklogListEntity
import com.marcogn.thepatientgamerhelper.data.local.entity.GenreEntity
import com.marcogn.thepatientgamerhelper.data.local.entity.PlatformEntity
import com.marcogn.thepatientgamerhelper.data.local.entity.ReviewEntity
import com.marcogn.thepatientgamerhelper.data.local.entity.ReviewGenreCrossRef
import com.marcogn.thepatientgamerhelper.data.local.entity.ReviewPlatformCrossRef
import com.marcogn.thepatientgamerhelper.data.local.entity.ReviewProConEntity
import com.marcogn.thepatientgamerhelper.data.local.entity.ReviewTagCrossRef
import com.marcogn.thepatientgamerhelper.data.local.entity.TagEntity

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
        BacklogListEntity::class,
        BacklogItemEntity::class,
        BacklogCommentEntity::class,
        BacklogHistoryEntryEntity::class,
        BacklogItemPlatformCrossRef::class,
        BacklogItemGenreCrossRef::class,
        BacklogItemTagCrossRef::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ThePatientGamerHelperDatabase : RoomDatabase() {
    abstract fun reviewDao(): ReviewDao
    abstract fun platformDao(): PlatformDao
    abstract fun genreDao(): GenreDao
    abstract fun tagDao(): TagDao
    abstract fun backlogDao(): BacklogDao

    companion object {
        const val DATABASE_NAME = "the_patient_gamer_helper.db"
    }
}
