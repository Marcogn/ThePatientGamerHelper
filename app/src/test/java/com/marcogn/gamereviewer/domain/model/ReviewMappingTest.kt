package com.marcogn.gamereviewer.domain.model

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewMappingTest {

    @Test
    fun `toDraft carries over every editable field`() {
        val review = Review(
            id = "abc",
            title = "Outer Wilds",
            platforms = listOf(Platform(1, "PC")),
            genres = listOf(Genre(1, "Avventura")),
            tags = listOf(Tag(1, "esplorazione")),
            rating = 9.7,
            startDate = LocalDate.of(2026, 1, 1),
            endDate = LocalDate.of(2026, 1, 20),
            hoursPlayed = 18.5,
            status = ReviewStatus.COMPLETATO,
            pros = listOf("Level design"),
            cons = listOf("Curva di apprendimento"),
            reviewText = "Fantastico",
            coverImagePath = "/covers/abc.jpg",
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )

        val draft = review.toDraft()

        assertEquals("Outer Wilds", draft.title)
        assertEquals(listOf("PC"), draft.platformNames)
        assertEquals(listOf("Avventura"), draft.genreNames)
        assertEquals(listOf("esplorazione"), draft.tagNames)
        assertEquals(9.7, draft.rating, 0.0001)
        assertEquals(LocalDate.of(2026, 1, 1), draft.startDate)
        assertEquals(LocalDate.of(2026, 1, 20), draft.endDate)
        assertEquals(18.5, draft.hoursPlayed)
        assertEquals(ReviewStatus.COMPLETATO, draft.status)
        assertEquals(listOf("Level design"), draft.pros)
        assertEquals(listOf("Curva di apprendimento"), draft.cons)
        assertEquals("Fantastico", draft.reviewText)
        assertEquals("/covers/abc.jpg", draft.coverImagePath)
    }

    @Test
    fun `empty draft defaults to in-progress status with no ratings`() {
        val draft = ReviewDraft.empty(startDate = LocalDate.of(2026, 5, 1))

        assertEquals("", draft.title)
        assertEquals(ReviewStatus.IN_CORSO, draft.status)
        assertEquals(0.0, draft.rating, 0.0001)
        assertEquals(LocalDate.of(2026, 5, 1), draft.startDate)
        assertEquals(null, draft.endDate)
    }
}
