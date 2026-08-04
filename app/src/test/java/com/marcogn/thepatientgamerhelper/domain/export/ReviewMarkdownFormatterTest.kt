package com.marcogn.thepatientgamerhelper.domain.export

import com.marcogn.thepatientgamerhelper.testutil.sampleReview
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewMarkdownFormatterTest {

    @Test
    fun `title becomes a level-1 heading`() {
        val markdown = sampleReview(title = "Hollow Knight").toRedditMarkdown()

        assertTrue(markdown.lines().first() == "# Hollow Knight")
    }

    @Test
    fun `metadata is a bullet list, not trailing-space hard breaks`() {
        val markdown = sampleReview(rating = 9.2, platforms = listOf("PC", "Switch")).toRedditMarkdown()

        assertTrue(markdown.contains("- **Voto:** 9.2/10"))
        assertTrue(markdown.contains("- **Piattaforme:** PC, Switch"))
        assertFalse(markdown.contains("  \n"))
    }

    @Test
    fun `empty tag list is omitted entirely rather than left blank`() {
        val markdown = sampleReview(tags = emptyList()).toRedditMarkdown()

        assertFalse(markdown.contains("**Tag:**"))
    }

    @Test
    fun `missing end date renders as an em dash`() {
        val markdown = sampleReview(endDate = null).toRedditMarkdown()

        assertTrue(markdown.contains("- **Terminato il:** —"))
    }

    @Test
    fun `pro contro and body sections only appear when non-empty`() {
        val withContent = sampleReview(pros = listOf("Ottimo"), cons = listOf("Lungo"), reviewText = "Corpo").toRedditMarkdown()
        val withoutContent = sampleReview(pros = emptyList(), cons = emptyList(), reviewText = "").toRedditMarkdown()

        assertTrue(withContent.contains("## Pro"))
        assertTrue(withContent.contains("- Ottimo"))
        assertTrue(withContent.contains("## Contro"))
        assertTrue(withContent.contains("Corpo"))
        assertFalse(withoutContent.contains("## Pro"))
        assertFalse(withoutContent.contains("## Contro"))
    }
}
