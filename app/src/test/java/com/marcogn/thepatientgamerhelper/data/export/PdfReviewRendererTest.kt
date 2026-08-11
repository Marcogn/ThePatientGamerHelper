package com.marcogn.thepatientgamerhelper.data.export

import com.marcogn.thepatientgamerhelper.testutil.sampleReview
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Only the content-building step is exercised here: PdfDocument's actual page/PDF-serialization
 * lifecycle depends on native code Robolectric doesn't provide (unlike SQLite, which it shadows
 * well), so it isn't meaningfully unit-testable — that part needs a real device/emulator or
 * manual verification.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class PdfReviewRendererTest {

    private val renderer = PdfReviewRenderer(NoOpPdfTemplateProvider())

    @Test
    fun `content includes title and metadata`() {
        val text = renderer.buildReviewText(sampleReview(title = "Hollow Knight", rating = 9.2)).toString()

        assertTrue(text.contains("Hollow Knight"))
        assertTrue(text.contains("Voto: 9.2/10"))
        assertTrue(text.contains("Stato: Completato"))
    }

    @Test
    fun `pro contro and body sections only appear when non-empty`() {
        val withContent = renderer.buildReviewText(
            sampleReview(pros = listOf("Ottimo"), cons = listOf("Lungo"), reviewText = "Corpo"),
        ).toString()
        val withoutContent = renderer.buildReviewText(
            sampleReview(pros = emptyList(), cons = emptyList(), reviewText = ""),
        ).toString()

        assertTrue(withContent.contains("Pro"))
        assertTrue(withContent.contains("• Ottimo"))
        assertTrue(withContent.contains("Contro"))
        assertTrue(withContent.contains("Corpo"))
        assertTrue(!withoutContent.contains("• "))
    }

    @Test
    fun `empty tag list is omitted`() {
        val text = renderer.buildReviewText(sampleReview(tags = emptyList())).toString()

        assertTrue(!text.contains("Tag:"))
    }
}
