package com.marcogn.thepatientgamerhelper.data.export

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.marcogn.thepatientgamerhelper.domain.export.ExportLabels
import com.marcogn.thepatientgamerhelper.domain.model.ReviewStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class ExportLabelsProviderTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    @Config(sdk = [26], qualifiers = "en")
    fun `english resources give english labels`() {
        val labels = context.toExportLabels()

        assertEquals("Completed", labels.statusLabel(ReviewStatus.COMPLETATO))
        assertEquals("Score", labels.pdfScore)
        assertEquals("game-reviews", labels.libraryFilePrefix)
        assertEquals("title", labels.csvColumns[1])
        assertEquals(ExportLabels.CSV_COLUMN_COUNT, labels.csvColumns.size)
    }

    @Test
    @Config(sdk = [26], qualifiers = "it")
    fun `italian resources give the italian defaults`() {
        val labels = context.toExportLabels()

        assertEquals(ExportLabels.ITALIAN, labels)
    }

    @Test
    @Config(sdk = [26], qualifiers = "en")
    fun `no label is blank`() {
        val labels = context.toExportLabels()

        assertTrue(labels.csvColumns.all { it.isNotBlank() })
        assertTrue(
            listOf(
                labels.statusInProgress, labels.statusCompleted, labels.statusAbandoned, labels.pdfScore, labels.pdfStatus,
                labels.pdfPlatforms, labels.pdfGenres, labels.pdfTags, labels.pdfStarted, labels.pdfFinished,
                labels.pdfHoursPlayed, labels.pdfPros, labels.pdfCons, labels.pdfReview,
                labels.fallbackReviewFileName, labels.libraryFilePrefix, labels.backlogFilePrefix,
            ).all { it.isNotBlank() },
        )
    }
}
