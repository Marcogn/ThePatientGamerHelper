package com.marcogn.gamereviewer.data.local.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/** A backlog item joined with its many-to-many lookups, comments and history. */
data class BacklogItemWithDetails(
    @Embedded val item: BacklogItemEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = BacklogItemPlatformCrossRef::class,
            parentColumn = "itemId",
            entityColumn = "platformId",
        ),
    )
    val platforms: List<PlatformEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = BacklogItemGenreCrossRef::class,
            parentColumn = "itemId",
            entityColumn = "genreId",
        ),
    )
    val genres: List<GenreEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = BacklogItemTagCrossRef::class,
            parentColumn = "itemId",
            entityColumn = "tagId",
        ),
    )
    val tags: List<TagEntity>,

    @Relation(parentColumn = "id", entityColumn = "itemId")
    val comments: List<BacklogCommentEntity>,

    @Relation(parentColumn = "id", entityColumn = "itemId")
    val history: List<BacklogHistoryEntryEntity>,
)
