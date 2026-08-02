package com.marcogn.gamereviewer.testutil

import com.marcogn.gamereviewer.domain.model.Genre
import com.marcogn.gamereviewer.domain.model.Platform
import com.marcogn.gamereviewer.domain.model.Review
import com.marcogn.gamereviewer.domain.model.ReviewStatus
import com.marcogn.gamereviewer.domain.model.Tag
import java.time.Instant
import java.time.LocalDate

fun sampleReview(
    id: String = "id-1",
    title: String = "Hollow Knight",
    platforms: List<String> = listOf("PC"),
    genres: List<String> = listOf("Metroidvania"),
    tags: List<String> = listOf("difficile"),
    rating: Double = 9.2,
    startDate: LocalDate = LocalDate.of(2026, 1, 1),
    endDate: LocalDate? = LocalDate.of(2026, 1, 20),
    hoursPlayed: Double? = 42.5,
    status: ReviewStatus = ReviewStatus.COMPLETATO,
    pros: List<String> = listOf("Level design"),
    cons: List<String> = listOf("Backtracking"),
    reviewText: String = "Un capolavoro.",
    coverImagePath: String? = null,
    createdAt: Instant = Instant.parse("2026-01-20T10:00:00Z"),
    updatedAt: Instant = Instant.parse("2026-01-20T10:00:00Z"),
): Review = Review(
    id = id,
    title = title,
    platforms = platforms.mapIndexed { index, name -> Platform(index.toLong(), name) },
    genres = genres.mapIndexed { index, name -> Genre(index.toLong(), name) },
    tags = tags.mapIndexed { index, name -> Tag(index.toLong(), name) },
    rating = rating,
    startDate = startDate,
    endDate = endDate,
    hoursPlayed = hoursPlayed,
    status = status,
    pros = pros,
    cons = cons,
    reviewText = reviewText,
    coverImagePath = coverImagePath,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
