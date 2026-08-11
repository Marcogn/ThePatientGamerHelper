package com.marcogn.thepatientgamerhelper.data.export

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Spanned
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import com.marcogn.thepatientgamerhelper.domain.model.Review
import com.marcogn.thepatientgamerhelper.domain.model.label
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val PAGE_WIDTH = 595 // A4 at 72dpi, points
private const val PAGE_HEIGHT = 842
private const val MARGIN = 48
private const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2
private const val CONTENT_HEIGHT = PAGE_HEIGHT - MARGIN * 2

private val pdfDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/**
 * Renders one or more reviews into a paginated [PdfDocument] using the native PDF API (no
 * PDFBox/iText dependency, matching the spec's licensing guidance). Each review's text is laid
 * out once via [StaticLayout] and then sliced across as many pages as it needs by translating +
 * clipping the canvas — the standard technique for spreading a single StaticLayout over
 * multiple PdfDocument pages. Every review starts on a fresh page.
 *
 * Checks [templateProvider] before rendering each review (v2 §2.6): with no template configured
 * (the only state today, see [PdfTemplateProvider]) every review falls back to the plain layout
 * below, unchanged.
 */
@Singleton
class PdfReviewRenderer @Inject constructor(
    private val templateProvider: PdfTemplateProvider,
) {

    fun render(reviews: List<Review>): PdfDocument {
        val template = templateProvider.currentTemplate()
        val document = PdfDocument()
        var nextPageNumber = 1
        reviews.forEach { review -> nextPageNumber = renderReview(document, review, nextPageNumber, template) }
        return document
    }

    private fun renderReview(document: PdfDocument, review: Review, startPageNumber: Int, template: PdfTemplate?): Int {
        if (template != null) {
            // No template implementation exists yet — currentTemplate() always returns null today,
            // so this branch is unreachable in practice until a real PdfTemplate is built. Left here
            // as the seam's actual extension point rather than only a comment, so wiring in a real
            // template later means filling this in, not restructuring the caller.
        }
        val textPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 12f
            color = Color.BLACK
        }
        val content = buildReviewText(review)
        val layout = StaticLayout.Builder
            .obtain(content, 0, content.length, textPaint, CONTENT_WIDTH)
            .setLineSpacing(4f, 1f)
            .build()

        var yOffset = 0
        var pageNumber = startPageNumber
        while (yOffset < layout.height) {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            canvas.save()
            canvas.translate(MARGIN.toFloat(), MARGIN.toFloat() - yOffset)
            canvas.clipRect(0, yOffset, CONTENT_WIDTH, yOffset + CONTENT_HEIGHT)
            layout.draw(canvas)
            canvas.restore()
            document.finishPage(page)
            yOffset += CONTENT_HEIGHT
            pageNumber++
        }
        return pageNumber
    }

    /** Visible to tests: the PDF-writing half (PdfDocument's native page lifecycle) isn't
     * meaningfully testable under Robolectric, but this content-building step is. */
    internal fun buildReviewText(review: Review): CharSequence {
        val builder = SpannableStringBuilder()

        appendStyled(builder, review.title, sizeMultiplier = 1.5f)
        builder.append("\n\n")

        appendMetadataLine(builder, "Voto", "${"%.1f".format(Locale.ROOT, review.rating)}/10")
        appendMetadataLine(builder, "Stato", review.status.label())
        if (review.platforms.isNotEmpty()) {
            appendMetadataLine(builder, "Piattaforme", review.platforms.joinToString(", ") { it.name })
        }
        if (review.genres.isNotEmpty()) {
            appendMetadataLine(builder, "Generi", review.genres.joinToString(", ") { it.name })
        }
        if (review.tags.isNotEmpty()) {
            appendMetadataLine(builder, "Tag", review.tags.joinToString(", ") { it.name })
        }
        appendMetadataLine(builder, "Iniziato il", review.startDate.format(pdfDateFormatter))
        appendMetadataLine(builder, "Terminato il", review.endDate?.format(pdfDateFormatter) ?: "—")
        review.hoursPlayed?.let {
            appendMetadataLine(builder, "Ore di gioco", "%.1f".format(Locale.ROOT, it))
        }

        if (review.pros.isNotEmpty()) {
            builder.append("\n")
            appendStyled(builder, "Pro", sizeMultiplier = 1.2f)
            builder.append("\n")
            review.pros.forEach { builder.append("• $it\n") }
        }
        if (review.cons.isNotEmpty()) {
            builder.append("\n")
            appendStyled(builder, "Contro", sizeMultiplier = 1.2f)
            builder.append("\n")
            review.cons.forEach { builder.append("• $it\n") }
        }
        if (review.reviewText.isNotBlank()) {
            builder.append("\n")
            appendStyled(builder, "Recensione", sizeMultiplier = 1.2f)
            builder.append("\n")
            builder.append(review.reviewText.trim())
            builder.append("\n")
        }

        return builder
    }

    private fun appendStyled(builder: SpannableStringBuilder, text: String, sizeMultiplier: Float) {
        val start = builder.length
        builder.append(text)
        val end = builder.length
        builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.setSpan(RelativeSizeSpan(sizeMultiplier), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun appendMetadataLine(builder: SpannableStringBuilder, label: String, value: String) {
        val start = builder.length
        builder.append("$label: ")
        val end = builder.length
        builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.append("$value\n")
    }
}
