package com.marcogn.gamereviewer.domain.filter

import com.marcogn.gamereviewer.domain.model.BacklogItemStatus
import com.marcogn.gamereviewer.testutil.sampleBacklogItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BacklogFilteringTest {

    @Test
    fun `search query matches title case-insensitively`() {
        val items = listOf(sampleBacklogItem(id = "1", title = "Hollow Knight"), sampleBacklogItem(id = "2", title = "Hades"))

        val result = applyBacklogFilters(items, BacklogFilters(searchQuery = "hollow"))

        assertEquals(listOf("1"), result.map { it.id })
    }

    @Test
    fun `search query matches tags`() {
        val items = listOf(
            sampleBacklogItem(id = "1", tags = listOf("da rigiocare")),
            sampleBacklogItem(id = "2", tags = emptyList()),
        )

        val result = applyBacklogFilters(items, BacklogFilters(searchQuery = "rigiocare"))

        assertEquals(listOf("1"), result.map { it.id })
    }

    @Test
    fun `list filter restricts to the selected lists`() {
        val items = listOf(
            sampleBacklogItem(id = "1", listId = 1L),
            sampleBacklogItem(id = "2", listId = 2L),
            sampleBacklogItem(id = "3", listId = 3L),
        )

        val result = applyBacklogFilters(items, BacklogFilters(listIds = setOf(1L, 2L)))

        assertEquals(setOf("1", "2"), result.map { it.id }.toSet())
    }

    @Test
    fun `status and platform filters are ANDed together`() {
        val items = listOf(
            sampleBacklogItem(id = "1", platforms = listOf("PC"), status = BacklogItemStatus.IN_CORSO),
            sampleBacklogItem(id = "2", platforms = listOf("PC"), status = BacklogItemStatus.DA_INIZIARE),
            sampleBacklogItem(id = "3", platforms = listOf("Switch"), status = BacklogItemStatus.IN_CORSO),
        )

        val result = applyBacklogFilters(
            items,
            BacklogFilters(platforms = setOf("PC"), statuses = setOf(BacklogItemStatus.IN_CORSO)),
        )

        assertEquals(listOf("1"), result.map { it.id })
    }

    @Test
    fun `genre filter matches any selected genre (OR)`() {
        val items = listOf(
            sampleBacklogItem(id = "1", genres = listOf("RPG")),
            sampleBacklogItem(id = "2", genres = listOf("Platform")),
            sampleBacklogItem(id = "3", genres = listOf("Puzzle")),
        )

        val result = applyBacklogFilters(items, BacklogFilters(genres = setOf("RPG", "Platform")))

        assertEquals(setOf("1", "2"), result.map { it.id }.toSet())
    }

    @Test
    fun `sortBacklogItemsByRecency orders newest first`() {
        val items = listOf(
            sampleBacklogItem(id = "old", addedAt = java.time.Instant.parse("2026-01-01T00:00:00Z")),
            sampleBacklogItem(id = "new", addedAt = java.time.Instant.parse("2026-02-01T00:00:00Z")),
        )

        val result = sortBacklogItemsByRecency(items)

        assertEquals(listOf("new", "old"), result.map { it.id })
    }

    @Test
    fun `isActive reflects whether any filter dimension is set`() {
        assertTrue(!BacklogFilters().isActive)
        assertTrue(BacklogFilters(searchQuery = "test").isActive)
        assertTrue(BacklogFilters(listIds = setOf(1L)).isActive)
        assertTrue(BacklogFilters(statuses = setOf(BacklogItemStatus.ABBANDONATO)).isActive)
    }
}
