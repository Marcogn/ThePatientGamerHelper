package com.marcogn.thepatientgamerhelper.domain.stats

import com.marcogn.thepatientgamerhelper.domain.model.ReviewStatus
import com.marcogn.thepatientgamerhelper.testutil.sampleReview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryStatisticsCalculatorTest {

    @Test
    fun `empty library yields EMPTY statistics`() {
        val stats = computeLibraryStatistics(emptyList())

        assertEquals(0, stats.totalReviews)
        assertNull(stats.averageRating)
        assertEquals(0.0, stats.totalHoursPlayed, 0.0)
        assertTrue(stats.platformDistribution.isEmpty())
        assertTrue(stats.genreDistribution.isEmpty())
        assertTrue(stats.statusBreakdown.isEmpty())
    }

    @Test
    fun `totals reviews average rating and sums hours played`() {
        val reviews = listOf(
            sampleReview(id = "1", rating = 8.0, hoursPlayed = 10.0),
            sampleReview(id = "2", rating = 6.0, hoursPlayed = 20.0),
            sampleReview(id = "3", rating = 4.0, hoursPlayed = null),
        )

        val stats = computeLibraryStatistics(reviews)

        assertEquals(3, stats.totalReviews)
        assertEquals(6.0, stats.averageRating!!, 0.0001)
        assertEquals(30.0, stats.totalHoursPlayed, 0.0001)
    }

    @Test
    fun `platform distribution counts one occurrence per review per platform`() {
        val reviews = listOf(
            sampleReview(id = "1", platforms = listOf("PC", "Switch")),
            sampleReview(id = "2", platforms = listOf("PC")),
            sampleReview(id = "3", platforms = listOf("PS5")),
        )

        val distribution = computeLibraryStatistics(reviews).platformDistribution

        assertEquals(
            listOf("PC" to 2, "PS5" to 1, "Switch" to 1),
            distribution.map { it.label to it.count },
        )
    }

    @Test
    fun `status breakdown counts and percentages sum to the total`() {
        val reviews = listOf(
            sampleReview(id = "1", status = ReviewStatus.COMPLETATO),
            sampleReview(id = "2", status = ReviewStatus.COMPLETATO),
            sampleReview(id = "3", status = ReviewStatus.ABBANDONATO),
            sampleReview(id = "4", status = ReviewStatus.IN_CORSO),
        )

        val breakdown = computeLibraryStatistics(reviews).statusBreakdown

        val byStatus = breakdown.associateBy { it.status }
        assertEquals(2, byStatus.getValue(ReviewStatus.COMPLETATO).count)
        assertEquals(50.0, byStatus.getValue(ReviewStatus.COMPLETATO).percentage, 0.0001)
        assertEquals(1, byStatus.getValue(ReviewStatus.ABBANDONATO).count)
        assertEquals(25.0, byStatus.getValue(ReviewStatus.ABBANDONATO).percentage, 0.0001)
        assertEquals(1, byStatus.getValue(ReviewStatus.IN_CORSO).count)
        assertEquals(25.0, byStatus.getValue(ReviewStatus.IN_CORSO).percentage, 0.0001)
        assertEquals(100.0, breakdown.sumOf { it.percentage }, 0.0001)
    }
}
