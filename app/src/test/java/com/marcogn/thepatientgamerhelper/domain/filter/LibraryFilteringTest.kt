package com.marcogn.thepatientgamerhelper.domain.filter

import com.marcogn.thepatientgamerhelper.domain.model.Genre
import com.marcogn.thepatientgamerhelper.domain.model.Platform
import com.marcogn.thepatientgamerhelper.domain.model.Review
import com.marcogn.thepatientgamerhelper.domain.model.ReviewStatus
import com.marcogn.thepatientgamerhelper.domain.model.Tag
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun review(
    id: String = "id",
    title: String = "Titolo",
    platforms: List<String> = emptyList(),
    genres: List<String> = emptyList(),
    tags: List<String> = emptyList(),
    rating: Double = 5.0,
    startDate: LocalDate = LocalDate.of(2026, 1, 1),
    endDate: LocalDate? = LocalDate.of(2026, 1, 10),
    hoursPlayed: Double? = 10.0,
    status: ReviewStatus = ReviewStatus.COMPLETATO,
    pros: List<String> = emptyList(),
    cons: List<String> = emptyList(),
    reviewText: String = "",
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
    coverImagePath = null,
    developer = null,
    publisher = null,
    releaseYear = null,
    metadataSource = null,
    externalId = null,
    linkedBacklogItemId = null,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
)

class LibraryFilteringTest {

    @Test
    fun `search query matches title case-insensitively`() {
        val reviews = listOf(review(id = "1", title = "Hollow Knight"), review(id = "2", title = "Hades"))

        val result = applyLibraryFilters(reviews, LibraryFilters(searchQuery = "hollow"))

        assertEquals(listOf("1"), result.map { it.id })
    }

    @Test
    fun `search query matches review text and pro con`() {
        val reviews = listOf(
            review(id = "1", reviewText = "Un capolavoro assoluto"),
            review(id = "2", pros = listOf("Colonna sonora fantastica")),
            review(id = "3", cons = listOf("Troppo lungo")),
            review(id = "4"),
        )

        assertEquals(listOf("1"), applyLibraryFilters(reviews, LibraryFilters(searchQuery = "capolavoro")).map { it.id })
        assertEquals(listOf("2"), applyLibraryFilters(reviews, LibraryFilters(searchQuery = "sonora")).map { it.id })
        assertEquals(listOf("3"), applyLibraryFilters(reviews, LibraryFilters(searchQuery = "lungo")).map { it.id })
    }

    @Test
    fun `platform filter matches any selected platform (OR)`() {
        val reviews = listOf(
            review(id = "1", platforms = listOf("PC")),
            review(id = "2", platforms = listOf("Switch")),
            review(id = "3", platforms = listOf("PS5")),
        )

        val result = applyLibraryFilters(reviews, LibraryFilters(platforms = setOf("PC", "Switch")))

        assertEquals(setOf("1", "2"), result.map { it.id }.toSet())
    }

    @Test
    fun `multiple filter dimensions are ANDed together`() {
        val reviews = listOf(
            review(id = "1", platforms = listOf("PC"), status = ReviewStatus.COMPLETATO),
            review(id = "2", platforms = listOf("PC"), status = ReviewStatus.ABBANDONATO),
            review(id = "3", platforms = listOf("Switch"), status = ReviewStatus.COMPLETATO),
        )

        val result = applyLibraryFilters(
            reviews,
            LibraryFilters(platforms = setOf("PC"), statuses = setOf(ReviewStatus.COMPLETATO)),
        )

        assertEquals(listOf("1"), result.map { it.id })
    }

    @Test
    fun `rating range filter is inclusive`() {
        val reviews = listOf(
            review(id = "1", rating = 5.0),
            review(id = "2", rating = 7.5),
            review(id = "3", rating = 9.9),
        )

        val result = applyLibraryFilters(reviews, LibraryFilters(minRating = 5.0, maxRating = 7.5))

        assertEquals(setOf("1", "2"), result.map { it.id }.toSet())
    }

    @Test
    fun `date range filter uses end date when present, otherwise start date`() {
        val reviews = listOf(
            review(id = "1", startDate = LocalDate.of(2026, 1, 1), endDate = LocalDate.of(2026, 1, 31)),
            review(id = "2", startDate = LocalDate.of(2026, 2, 1), endDate = null),
            review(id = "3", startDate = LocalDate.of(2026, 3, 1), endDate = LocalDate.of(2026, 3, 15)),
        )

        val result = applyLibraryFilters(
            reviews,
            LibraryFilters(dateFrom = LocalDate.of(2026, 1, 15), dateTo = LocalDate.of(2026, 2, 15)),
        )

        assertEquals(setOf("1", "2"), result.map { it.id }.toSet())
    }

    @Test
    fun `sortReviews orders by rating ascending and descending`() {
        val reviews = listOf(review(id = "low", rating = 2.0), review(id = "high", rating = 9.0))

        val asc = sortReviews(reviews, SortOption(SortField.RATING, SortDirection.ASC))
        val desc = sortReviews(reviews, SortOption(SortField.RATING, SortDirection.DESC))

        assertEquals(listOf("low", "high"), asc.map { it.id })
        assertEquals(listOf("high", "low"), desc.map { it.id })
    }

    @Test
    fun `sortReviews orders by title case-insensitively`() {
        val reviews = listOf(review(id = "1", title = "zelda"), review(id = "2", title = "Aloy"))

        val asc = sortReviews(reviews, SortOption(SortField.TITLE, SortDirection.ASC))

        assertEquals(listOf("2", "1"), asc.map { it.id })
    }

    @Test
    fun `sortReviews handles null hours played by treating them as lowest`() {
        val reviews = listOf(
            review(id = "no-hours", hoursPlayed = null),
            review(id = "with-hours", hoursPlayed = 5.0),
        )

        val asc = sortReviews(reviews, SortOption(SortField.HOURS_PLAYED, SortDirection.ASC))

        assertEquals(listOf("no-hours", "with-hours"), asc.map { it.id })
    }

    @Test
    fun `isActive reflects whether any filter dimension is set`() {
        assertTrue(!LibraryFilters().isActive)
        assertTrue(LibraryFilters(searchQuery = "test").isActive)
        assertTrue(LibraryFilters(platforms = setOf("PC")).isActive)
        assertTrue(LibraryFilters(minRating = 1.0).isActive)
    }
}
