package com.marcogn.thepatientgamerhelper.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ProConType { PRO, CONTRO }

@Entity(
    tableName = "review_pro_con",
    indices = [Index("reviewId")],
    foreignKeys = [
        ForeignKey(
            entity = ReviewEntity::class,
            parentColumns = ["id"],
            childColumns = ["reviewId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ReviewProConEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reviewId: String,
    val type: ProConType,
    val text: String,
    val position: Int,
)
