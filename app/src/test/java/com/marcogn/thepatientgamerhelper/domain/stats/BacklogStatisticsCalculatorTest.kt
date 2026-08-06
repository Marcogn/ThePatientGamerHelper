package com.marcogn.thepatientgamerhelper.domain.stats

import com.marcogn.thepatientgamerhelper.domain.model.BacklogItemStatus
import com.marcogn.thepatientgamerhelper.testutil.sampleBacklogItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BacklogStatisticsCalculatorTest {

    @Test
    fun `empty backlog yields EMPTY statistics`() {
        val stats = computeBacklogStatistics(emptyList())

        assertEquals(0, stats.totalItems)
        assertTrue(stats.countByStatus.isEmpty())
        assertTrue(stats.countByList.isEmpty())
    }

    @Test
    fun `counts items per status and per list`() {
        val items = listOf(
            sampleBacklogItem(id = "1", listId = 1L, status = BacklogItemStatus.DA_INIZIARE),
            sampleBacklogItem(id = "2", listId = 1L, status = BacklogItemStatus.IN_CORSO),
            sampleBacklogItem(id = "3", listId = 2L, status = BacklogItemStatus.DA_INIZIARE),
        )

        val stats = computeBacklogStatistics(items)

        assertEquals(3, stats.totalItems)
        assertEquals(2, stats.countByStatus[BacklogItemStatus.DA_INIZIARE])
        assertEquals(1, stats.countByStatus[BacklogItemStatus.IN_CORSO])
        assertEquals(0, stats.countByStatus[BacklogItemStatus.COMPLETATO])
        assertEquals(2, stats.countByList[1L])
        assertEquals(1, stats.countByList[2L])
    }

    @Test
    fun `time estimate is EMPTY when no item has HowLongToBeat data`() {
        val items = listOf(sampleBacklogItem(id = "1"), sampleBacklogItem(id = "2"))

        val stats = computeBacklogStatistics(items)

        assertEquals(0, stats.timeEstimate.itemsWithEstimate)
        assertEquals(0.0, stats.timeEstimate.totalMainStoryHours, 0.0)
    }

    @Test
    fun `time estimate sums hours only across items that have at least one HowLongToBeat field`() {
        val items = listOf(
            sampleBacklogItem(id = "1", hltbMainStoryHours = 12.0, hltbMainExtraHours = 18.0, hltbCompletionistHours = 35.0),
            sampleBacklogItem(id = "2", hltbMainStoryHours = 8.5),
            sampleBacklogItem(id = "3"),
        )

        val stats = computeBacklogStatistics(items)

        assertEquals(2, stats.timeEstimate.itemsWithEstimate)
        assertEquals(20.5, stats.timeEstimate.totalMainStoryHours, 0.001)
        assertEquals(18.0, stats.timeEstimate.totalMainExtraHours, 0.001)
        assertEquals(35.0, stats.timeEstimate.totalCompletionistHours, 0.001)
    }
}
