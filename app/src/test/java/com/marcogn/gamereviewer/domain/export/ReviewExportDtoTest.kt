package com.marcogn.gamereviewer.domain.export

import com.marcogn.gamereviewer.testutil.sampleReview
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewExportDtoTest {

    @Test
    fun `toExportDto maps every field, dates as ISO-8601`() {
        val review = sampleReview()

        val dto = review.toExportDto()

        assertEquals(review.id, dto.id)
        assertEquals(review.title, dto.titolo)
        assertEquals(listOf("PC"), dto.piattaforme)
        assertEquals("2026-01-01", dto.dataInizio)
        assertEquals("2026-01-20", dto.dataFine)
        assertEquals("2026-01-20T10:00:00Z", dto.creatoIl)
        assertEquals(review.status.name, dto.stato)
    }

    @Test
    fun `null end date and hours played survive the round trip`() {
        val review = sampleReview(endDate = null, hoursPlayed = null)

        val dto = review.toExportDto()

        assertEquals(null, dto.dataFine)
        assertEquals(null, dto.oreGioco)
    }

    @Test
    fun `library export wraps reviews with a count and is valid JSON`() {
        val reviews = listOf(sampleReview(id = "1"), sampleReview(id = "2"))

        val json = reviews.toJsonExport()
        val decoded = Json.decodeFromString(LibraryExportDto.serializer(), json)

        assertEquals(2, decoded.numeroRecensioni)
        assertEquals(2, decoded.recensioni.size)
        assertTrue(json.contains("\"esportatoIl\""))
    }
}
