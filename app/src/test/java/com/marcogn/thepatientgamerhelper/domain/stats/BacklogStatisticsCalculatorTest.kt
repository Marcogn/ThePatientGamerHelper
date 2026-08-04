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
}
