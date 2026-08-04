package com.marcogn.gamereviewer.domain.backup

import com.marcogn.gamereviewer.testutil.sampleReview
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `backup file name is timestamped and unique per second`() {
        val name = suggestedBackupFileName(Instant.parse("2026-08-04T22:14:30Z"))

        assertEquals("gamereviewer-backup-20260804-221430.zip", name)
    }
}
