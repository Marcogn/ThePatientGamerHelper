package com.marcogn.thepatientgamerhelper.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.marcogn.thepatientgamerhelper.domain.model.BacklogHistoryEventType
import com.marcogn.thepatientgamerhelper.domain.model.BacklogItemStatus
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "backlog_lists")
data class BacklogListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int,
    val createdAt: Instant,
)

@Entity(
    tableName = "backlog_items",
    indices = [Index("listId"), Index("reviewId")],
    foreignKeys = [
        ForeignKey(
            entity = BacklogListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ReviewEntity::class,
            parentColumns = ["id"],
            childColumns = ["reviewId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class BacklogItemEntity(
    @PrimaryKey val id: String,
    val listId: Long,
    val title: String,
    val coverImagePath: String?,
    val status: BacklogItemStatus,
    val position: Int,
    val addedAt: Instant,
    val startDate: LocalDate?,
    val completedDate: LocalDate?,
    val reviewId: String?,
    val abandonNote: String?,
    val releaseYear: Int?,
    val developer: String?,
    val hltbMainStoryHours: Double?,
    val hltbMainExtraHours: Double?,
    val hltbCompletionistHours: Double?,
    @ColumnInfo(name = "updatedAt") val updatedAt: Instant,
)

@Entity(
    tableName = "backlog_comments",
    indices = [Index("itemId")],
    foreignKeys = [
        ForeignKey(
            entity = BacklogItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BacklogCommentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: String,
    val text: String,
    val timestamp: Instant,
)

@Entity(
    tableName = "backlog_history_entries",
    indices = [Index("itemId")],
    foreignKeys = [
        ForeignKey(
            entity = BacklogItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BacklogHistoryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: String,
    val eventType: BacklogHistoryEventType,
    val timestamp: Instant,
    val detail: String?,
)

@Entity(
    tableName = "backlog_item_platform_cross_ref",
    primaryKeys = ["itemId", "platformId"],
    indices = [Index("platformId")],
    foreignKeys = [
        ForeignKey(
            entity = BacklogItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PlatformEntity::class,
            parentColumns = ["id"],
            childColumns = ["platformId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BacklogItemPlatformCrossRef(val itemId: String, val platformId: Long)

@Entity(
    tableName = "backlog_item_genre_cross_ref",
    primaryKeys = ["itemId", "genreId"],
    indices = [Index("genreId")],
    foreignKeys = [
        ForeignKey(
            entity = BacklogItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = GenreEntity::class,
            parentColumns = ["id"],
            childColumns = ["genreId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BacklogItemGenreCrossRef(val itemId: String, val genreId: Long)

@Entity(
    tableName = "backlog_item_tag_cross_ref",
    primaryKeys = ["itemId", "tagId"],
    indices = [Index("tagId")],
    foreignKeys = [
        ForeignKey(
            entity = BacklogItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BacklogItemTagCrossRef(val itemId: String, val tagId: Long)
