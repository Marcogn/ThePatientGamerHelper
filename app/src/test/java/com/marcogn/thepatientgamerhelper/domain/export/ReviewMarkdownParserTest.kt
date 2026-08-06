package com.marcogn.thepatientgamerhelper.domain.export

import com.marcogn.thepatientgamerhelper.domain.model.ReviewStatus
import com.marcogn.thepatientgamerhelper.testutil.sampleReview
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewMarkdownParserTest {

    @Test
    fun `roundtrips a full review through export then import`() {
        val original = sampleReview(
            title = "Hollow Knight",
            platforms = listOf("PC", "Switch"),
            genres = listOf("Metroidvania"),
            tags = listOf("difficile"),
            rating = 9.2,
            startDate = LocalDate.of(2026, 1, 1),
            endDate = LocalDate.of(2026, 1, 20),
            hoursPlayed = 42.5,
            status = ReviewStatus.COMPLETATO,
            pros = listOf("Level design", "Colonna sonora"),
            cons = listOf("Backtracking"),
            reviewText = "Un capolavoro.",
        )

        val draft = parseReviewMarkdown(original.toRedditMarkdown()).getOrThrow()

        assertEquals(original.title, draft.title)
        assertEquals(original.platforms.map { it.name }, draft.platformNames)
        assertEquals(original.genres.map { it.name }, draft.genreNames)
        assertEquals(original.tags.map { it.name }, draft.tagNames)
        assertEquals(original.rating, draft.rating, 0.001)
        assertEquals(original.startDate, draft.startDate)
        assertEquals(original.endDate, draft.endDate)
        assertEquals(original.hoursPlayed, draft.hoursPlayed)
        assertEquals(original.status, draft.status)
        assertEquals(original.pros, draft.pros)
        assertEquals(original.cons, draft.cons)
        assertEquals(original.reviewText, draft.reviewText)
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
        )

        val draft = parseReviewMarkdown(original.toRedditMarkdown()).getOrThrow()

        assertTrue(draft.platformNames.isEmpty())
        assertTrue(draft.genreNames.isEmpty())
        assertTrue(draft.tagNames.isEmpty())
        assertEquals(null, draft.endDate)
        assertEquals(null, draft.hoursPlayed)
        assertTrue(draft.pros.isEmpty())
        assertTrue(draft.cons.isEmpty())
        assertEquals("", draft.reviewText)
    }

    @Test
    fun `fails with a descriptive message when the title heading is missing`() {
        val result = parseReviewMarkdown("- **Voto:** 9.2/10\n")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("titolo"))
    }

    @Test
    fun `fails with a descriptive message when the rating is missing`() {
        val result = parseReviewMarkdown("# Titolo\n\n- **Stato:** In corso\n- **Iniziato il:** 01/01/2026\n")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Voto"))
    }

    @Test
    fun `fails with a descriptive message for an unrecognized status`() {
        val markdown = "# Titolo\n\n- **Voto:** 8/10\n- **Stato:** Boh\n- **Iniziato il:** 01/01/2026\n"

        val result = parseReviewMarkdown(markdown)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Stato"))
    }
}
