package com.marcogn.thepatientgamerhelper.domain.export

import com.marcogn.thepatientgamerhelper.domain.model.ReviewStatus
import com.marcogn.thepatientgamerhelper.testutil.sampleReview
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewBackupMarkdownTest {

    @Test
    fun `roundtrips a full review through export then import`() {
        val original = sampleReview(
            id = "3f9a1c2e-6b7d-4e21-9c3a-1a2b3c4d5e6f",
            title = "Nebula Drift",
            platforms = listOf("Nintendo Switch"),
            genres = listOf("Metroidvania"),
            tags = listOf("indie", "backlog-hidden-gem"),
            rating = 8.5,
            startDate = LocalDate.of(2026, 5, 2),
            endDate = LocalDate.of(2026, 5, 19),
            hoursPlayed = 14.5,
            status = ReviewStatus.COMPLETATO,
            pros = listOf("Tight movement", "Great soundtrack"),
            cons = listOf("Confusing map legend"),
            reviewText = "Free-form review text goes here.",
            coverImagePath = "/data/covers/7d4e1f0a9b2c.jpg",
            developer = "Faraway Studio",
            publisher = "Faraway Studio",
            releaseYear = 2024,
            metadataSource = "thegamesdb",
            externalId = "84213",
            linkedBacklogItemId = "9c7e2b41-11aa-4de5-8f2c-77a0b6d9e123",
        )

        val parsed = parseReviewBackupMarkdown(original.toBackupMarkdown()).getOrThrow()

        assertEquals(original.id, parsed.id)
        assertEquals(original.title, parsed.title)
        assertEquals(original.platforms.map { it.name }, parsed.platformNames)
        assertEquals(original.genres.map { it.name }, parsed.genreNames)
        assertEquals(original.tags.map { it.name }, parsed.tagNames)
        assertEquals(original.rating, parsed.rating, 0.001)
        assertEquals(original.status, parsed.status)
        assertEquals(original.startDate, parsed.startDate)
        assertEquals(original.endDate, parsed.endDate)
        assertEquals(original.hoursPlayed, parsed.hoursPlayed)
        assertEquals("7d4e1f0a9b2c.jpg", parsed.coverImageFileName)
        assertEquals(original.developer, parsed.developer)
        assertEquals(original.publisher, parsed.publisher)
        assertEquals(original.releaseYear, parsed.releaseYear)
        assertEquals(original.metadataSource, parsed.metadataSource)
        assertEquals(original.externalId, parsed.externalId)
        assertEquals(original.linkedBacklogItemId, parsed.linkedBacklogItemId)
        assertEquals(original.pros, parsed.pros)
        assertEquals(original.cons, parsed.cons)
        assertEquals(original.reviewText, parsed.reviewText)
        assertEquals(original.createdAt, parsed.createdAt)
        assertEquals(original.updatedAt, parsed.updatedAt)
    }

    @Test
    fun `roundtrips a minimal review with no optional fields`() {
        val original = sampleReview(
            platforms = emptyList(),
            genres = emptyList(),
            tags = emptyList(),
            endDate = null,
            hoursPlayed = null,
            pros = emptyList(),
            cons = emptyList(),
            reviewText = "",
            coverImagePath = null,
        )

        val parsed = parseReviewBackupMarkdown(original.toBackupMarkdown()).getOrThrow()

        assertTrue(parsed.platformNames.isEmpty())
        assertTrue(parsed.genreNames.isEmpty())
        assertTrue(parsed.tagNames.isEmpty())
        assertNull(parsed.endDate)
        assertNull(parsed.hoursPlayed)
        assertNull(parsed.coverImageFileName)
        assertNull(parsed.developer)
        assertNull(parsed.linkedBacklogItemId)
        assertTrue(parsed.pros.isEmpty())
        assertTrue(parsed.cons.isEmpty())
        assertEquals("", parsed.reviewText)
    }

    @Test
    fun `fails when front matter is missing`() {
        val result = parseReviewBackupMarkdown("# Not a backup file\n\nJust some text.")
        assertTrue(result.isFailure)
    }

    @Test
    fun `fails when a required field is missing`() {
        val content = """
            ---
            id: abc-123
            title: "No status or dates"
            score: 7.0
            ---

            ## Review

            Body text.
        """.trimIndent()

        val result = parseReviewBackupMarkdown(content)
        assertTrue(result.isFailure)
    }

    @Test
    fun `fails on an unrecognized status word`() {
        val content = """
            ---
            id: abc-123
            title: "Bad status"
            score: 7.0
            status: playing
            startDate: 2026-01-01
            ---

            ## Review

            Body text.
        """.trimIndent()

        val result = parseReviewBackupMarkdown(content)
        assertTrue(result.isFailure)
    }

    @Test
    fun `toFormDraft never applies id or backup-only fields`() {
        val original = sampleReview(developer = "Some Dev")
        val parsed = parseReviewBackupMarkdown(original.toBackupMarkdown()).getOrThrow()

        val draft = parsed.toFormDraft(resolvedCoverImagePath = null)

        assertEquals(original.title, draft.title)
        assertEquals(original.rating, draft.rating, 0.001)
        assertNull(draft.coverImagePath)
    }
}
