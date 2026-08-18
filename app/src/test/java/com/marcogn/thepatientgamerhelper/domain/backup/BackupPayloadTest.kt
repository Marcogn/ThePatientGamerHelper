package com.marcogn.thepatientgamerhelper.domain.backup

import com.marcogn.thepatientgamerhelper.domain.model.BacklogComment
import com.marcogn.thepatientgamerhelper.domain.model.BacklogHistoryEntry
import com.marcogn.thepatientgamerhelper.domain.model.BacklogHistoryEventType
import com.marcogn.thepatientgamerhelper.domain.model.BacklogList
import com.marcogn.thepatientgamerhelper.testutil.sampleBacklogItem
import com.marcogn.thepatientgamerhelper.testutil.sampleReview
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPayloadTest {

    @Test
    fun `review roundtrips through the backup dto`() {
        val review = sampleReview(coverImagePath = "/data/data/app/files/covers/abc-123.jpg")

        val dto = review.toBackupDto()
        assertEquals("abc-123.jpg", dto.coverImageFileName)

        val restored = dto.toDomain(resolvedCoverImagePath = "/new/path/abc-123.jpg")

        assertEquals(review.id, restored.id)
        assertEquals(review.title, restored.title)
        assertEquals(review.platforms.map { it.name }, restored.platforms.map { it.name })
        assertEquals(review.genres.map { it.name }, restored.genres.map { it.name })
        assertEquals(review.tags.map { it.name }, restored.tags.map { it.name })
        assertEquals(review.rating, restored.rating, 0.0)
        assertEquals(review.startDate, restored.startDate)
        assertEquals(review.endDate, restored.endDate)
        assertEquals(review.hoursPlayed, restored.hoursPlayed)
        assertEquals(review.status, restored.status)
        assertEquals(review.pros, restored.pros)
        assertEquals(review.cons, restored.cons)
        assertEquals(review.reviewText, restored.reviewText)
        assertEquals("/new/path/abc-123.jpg", restored.coverImagePath)
        assertEquals(review.createdAt, restored.createdAt)
        assertEquals(review.updatedAt, restored.updatedAt)
    }

    @Test
    fun `null cover produces a null file name and survives the roundtrip`() {
        val dto = sampleReview(coverImagePath = null).toBackupDto()

        assertNull(dto.coverImageFileName)
        assertNull(dto.toDomain(resolvedCoverImagePath = null).coverImagePath)
    }

    @Test
    fun `payload survives a json roundtrip`() {
        val payload = listOf(sampleReview(id = "a"), sampleReview(id = "b", coverImagePath = "/x/cover.png"))
            .toBackupPayload(createdAt = Instant.parse("2026-08-04T12:00:00Z"))

        val restored = payload.toJson().toBackupPayload()

        assertEquals(payload, restored)
    }

    @Test
    fun `backlog list and item roundtrip through the backup dto, including comments and history ids`() {
        val comment = BacklogComment(id = 5L, itemId = "item-1", text = "Bellissimo", timestamp = Instant.parse("2026-03-01T10:00:00Z"))
        val history = BacklogHistoryEntry(id = 2L, itemId = "item-1", type = BacklogHistoryEventType.CREATO, timestamp = Instant.parse("2026-03-01T09:00:00Z"), detail = "Hades II")
        val list = BacklogList(id = 9L, name = "Da giocare", position = 0, createdAt = Instant.parse("2026-01-01T00:00:00Z"), systemKind = "COMPLETED_WITH_REVIEW")
        val item = sampleBacklogItem(id = "item-1", listId = 9L, title = "Hades II")
            .copy(coverImagePath = "/data/data/app/files/covers/xyz.jpg", reviewId = "review-1", comments = listOf(comment), history = listOf(history))

        val payload = buildBackupPayload(reviews = emptyList(), backlogLists = listOf(list), backlogItems = listOf(item))

        val listDto = payload.backlogLists.single()
        assertEquals(9L, listDto.id)
        assertEquals("COMPLETED_WITH_REVIEW", listDto.systemKind)
        val itemDto = listDto.items.single()
        assertEquals("xyz.jpg", itemDto.coverImageFileName)
        assertEquals("review-1", itemDto.reviewId)

        val restoredList = listDto.toDomain()
        val restoredItem = itemDto.toDomain(resolvedCoverImagePath = "/new/path/xyz.jpg")

        assertEquals(list, restoredList)
        assertEquals(item.id, restoredItem.id)
        assertEquals(item.title, restoredItem.title)
        assertEquals(item.reviewId, restoredItem.reviewId)
        assertEquals("/new/path/xyz.jpg", restoredItem.coverImagePath)
        assertEquals(listOf(comment), restoredItem.comments)
        assertEquals(listOf(history), restoredItem.history)
    }

    @Test
    fun `a backup taken before the backlog field existed still decodes, with an empty backlog`() {
        val payloadWithoutBacklogKey = """{"schemaVersion":1,"createdAt":"2026-01-01T00:00:00Z","reviews":[]}"""

        val decoded = payloadWithoutBacklogKey.toBackupPayload()

        assertTrue(decoded.backlogLists.isEmpty())
    }

    @Test
    fun `backup file name is timestamped and unique per second`() {
        val name = suggestedBackupFileName(Instant.parse("2026-08-04T22:14:30Z"))

        assertEquals("the-patient-gamer-helper-backup-20260804-221430.zip", name)
    }
}
