package com.marcogn.thepatientgamerhelper.domain.export

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportFileNamingTest {

    @Test
    fun `toFileSlug lowercases and replaces non-alphanumeric runs with a dash`() {
        assertEquals("baldur-s-gate-3", "Baldur's Gate 3".toFileSlug())
        assertEquals("hollow-knight", "  Hollow   Knight  ".toFileSlug())
    }

    @Test
    fun `toFileSlug falls back to a default for blank input`() {
        assertEquals("recensione", "   ".toFileSlug())
    }

    @Test
    fun `suggestedReviewFileName appends the format extension`() {
        assertEquals("hollow-knight.md", suggestedReviewFileName("Hollow Knight", ExportFormat.MARKDOWN))
        assertEquals("hollow-knight.pdf", suggestedReviewFileName("Hollow Knight", ExportFormat.PDF))
    }

    @Test
    fun `suggestedLibraryFileName embeds the given date`() {
        val name = suggestedLibraryFileName(ExportFormat.JSON, today = LocalDate.of(2026, 8, 1))

        assertEquals("recensioni-videogiochi-2026-08-01.json", name)
    }
}
