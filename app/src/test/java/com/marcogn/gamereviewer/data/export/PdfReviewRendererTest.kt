package com.marcogn.gamereviewer.data.export

import com.marcogn.gamereviewer.testutil.sampleReview
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class PdfReviewRendererTest {

    private val renderer = PdfReviewRenderer()

    @Test
    fun `renders at least one page for a short review`() {
        val document = renderer.render(listOf(sampleReview()))

        assertTrue(document.pages.size >= 1)
        document.close()
    }

    @Test
    fun `each review in a batch starts its own page range`() {
        val document = renderer.render(listOf(sampleReview(id = "1"), sampleReview(id = "2")))

        assertTrue(document.pages.size >= 2)
        document.close()
    }

    @Test
    fun `a long review body spills onto multiple pages`() {
        val longText = "Lorem ipsum dolor sit amet. ".repeat(400)
        val document = renderer.render(listOf(sampleReview(reviewText = longText)))

        assertTrue(document.pages.size > 1)
        document.close()
    }
}
