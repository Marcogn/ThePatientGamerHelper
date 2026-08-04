package com.marcogn.thepatientgamerhelper.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.marcogn.thepatientgamerhelper.domain.model.ReviewStatus
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "reviews")
data class ReviewEntity(
    @PrimaryKey val id: String,
    val title: String,
    val rating: Double,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val hoursPlayed: Double?,
    val status: ReviewStatus,
    val reviewText: String,
    val coverImagePath: String?,
    val createdAt: Instant,
    @ColumnInfo(name = "updatedAt") val updatedAt: Instant,
)
