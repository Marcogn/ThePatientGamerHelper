package com.marcogn.thepatientgamerhelper.data.repository

import com.marcogn.thepatientgamerhelper.data.local.entity.GenreEntity
import com.marcogn.thepatientgamerhelper.data.local.entity.PlatformEntity
import com.marcogn.thepatientgamerhelper.data.local.entity.ProConType
import com.marcogn.thepatientgamerhelper.data.local.entity.ReviewWithDetails
import com.marcogn.thepatientgamerhelper.data.local.entity.TagEntity
import com.marcogn.thepatientgamerhelper.domain.model.Genre
import com.marcogn.thepatientgamerhelper.domain.model.Platform
import com.marcogn.thepatientgamerhelper.domain.model.Review
import com.marcogn.thepatientgamerhelper.domain.model.Tag

fun PlatformEntity.toDomain() = Platform(id, name)
fun GenreEntity.toDomain() = Genre(id, name)
fun TagEntity.toDomain() = Tag(id, name)

fun ReviewWithDetails.toDomain(): Review {
    val pros = proCon.filter { it.type == ProConType.PRO }.sortedBy { it.position }.map { it.text }
    val cons = proCon.filter { it.type == ProConType.CONTRO }.sortedBy { it.position }.map { it.text }
    return Review(
        id = review.id,
        title = review.title,
        platforms = platforms.map { it.toDomain() }.sortedBy { it.name },
        genres = genres.map { it.toDomain() }.sortedBy { it.name },
        tags = tags.map { it.toDomain() }.sortedBy { it.name },
        rating = review.rating,
        startDate = review.startDate,
        endDate = review.endDate,
        hoursPlayed = review.hoursPlayed,
        status = review.status,
        pros = pros,
        cons = cons,
        reviewText = review.reviewText,
        coverImagePath = review.coverImagePath,
        createdAt = review.createdAt,
        updatedAt = review.updatedAt,
    )
}
