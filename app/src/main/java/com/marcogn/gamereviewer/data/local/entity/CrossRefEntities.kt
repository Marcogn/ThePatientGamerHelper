package com.marcogn.gamereviewer.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "review_platform_cross_ref",
    primaryKeys = ["reviewId", "platformId"],
    indices = [Index("platformId")],
    foreignKeys = [
        ForeignKey(
            entity = ReviewEntity::class,
            parentColumns = ["id"],
            childColumns = ["reviewId"],
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
data class ReviewPlatformCrossRef(val reviewId: String, val platformId: Long)

@Entity(
    tableName = "review_genre_cross_ref",
    primaryKeys = ["reviewId", "genreId"],
    indices = [Index("genreId")],
    foreignKeys = [
        ForeignKey(
            entity = ReviewEntity::class,
            parentColumns = ["id"],
            childColumns = ["reviewId"],
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
data class ReviewGenreCrossRef(val reviewId: String, val genreId: Long)

@Entity(
    tableName = "review_tag_cross_ref",
    primaryKeys = ["reviewId", "tagId"],
    indices = [Index("tagId")],
    foreignKeys = [
        ForeignKey(
            entity = ReviewEntity::class,
            parentColumns = ["id"],
            childColumns = ["reviewId"],
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
data class ReviewTagCrossRef(val reviewId: String, val tagId: Long)
