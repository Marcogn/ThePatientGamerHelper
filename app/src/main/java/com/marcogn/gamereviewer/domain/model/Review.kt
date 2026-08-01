package com.marcogn.gamereviewer.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * A single game review. One review per game in the Phase 1 MVP (no replay entries).
 */
data class Review(
    val id: String,
    val title: String,
    val platforms: List<Platform>,
    val genres: List<Genre>,
    val tags: List<Tag>,
    val rating: Double,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val hoursPlayed: Double?,
    val status: ReviewStatus,
    val pros: List<String>,
    val cons: List<String>,
    val reviewText: String,
    val coverImagePath: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * Editable draft used by the create/edit form. Lookup values are plain names: the
 * repository resolves each name to an existing lookup row or creates a new one on save.
 */
data class ReviewDraft(
    val title: String,
    val platformNames: List<String>,
    val genreNames: List<String>,
    val tagNames: List<String>,
    val rating: Double,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val hoursPlayed: Double?,
    val status: ReviewStatus,
    val pros: List<String>,
    val cons: List<String>,
    val reviewText: String,
    val coverImagePath: String?,
) {
    companion object {
        fun empty(startDate: LocalDate = LocalDate.now()) = ReviewDraft(
            title = "",
            platformNames = emptyList(),
            genreNames = emptyList(),
            tagNames = emptyList(),
            rating = 0.0,
            startDate = startDate,
            endDate = null,
            hoursPlayed = null,
            status = ReviewStatus.IN_CORSO,
            pros = emptyList(),
            cons = emptyList(),
            reviewText = "",
            coverImagePath = null,
        )
    }
}

fun Review.toDraft(): ReviewDraft = ReviewDraft(
    title = title,
    platformNames = platforms.map { it.name },
    genreNames = genres.map { it.name },
    tagNames = tags.map { it.name },
    rating = rating,
    startDate = startDate,
    endDate = endDate,
    hoursPlayed = hoursPlayed,
    status = status,
    pros = pros,
    cons = cons,
    reviewText = reviewText,
    coverImagePath = coverImagePath,
)
