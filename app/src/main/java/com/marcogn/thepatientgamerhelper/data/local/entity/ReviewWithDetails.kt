package com.marcogn.thepatientgamerhelper.data.local.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/** A review joined with its many-to-many lookups and its pro/con child rows. */
data class ReviewWithDetails(
    @Embedded val review: ReviewEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ReviewPlatformCrossRef::class,
            parentColumn = "reviewId",
            entityColumn = "platformId",
        ),
    )
    val platforms: List<PlatformEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ReviewGenreCrossRef::class,
            parentColumn = "reviewId",
            entityColumn = "genreId",
        ),
    )
    val genres: List<GenreEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ReviewTagCrossRef::class,
            parentColumn = "reviewId",
            entityColumn = "tagId",
        ),
    )
    val tags: List<TagEntity>,

    @Relation(parentColumn = "id", entityColumn = "reviewId")
    val proCon: List<ReviewProConEntity>,
)
