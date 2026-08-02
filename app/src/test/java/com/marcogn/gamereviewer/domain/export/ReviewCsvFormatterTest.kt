package com.marcogn.gamereviewer.domain.export

import com.marcogn.gamereviewer.domain.model.Review
import com.marcogn.gamereviewer.testutil.sampleReview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewCsvFormatterTest {

    @Test
    fun `header row lists every column`() {
        val csv = emptyList<Review>().toCsvExport()

        assertEquals(
            "id,titolo,piattaforme,generi,tag,voto,dataInizio,dataFine,oreGioco,stato,pro,contro,testoRecensione,copertina,creatoIl,modificatoIl\r\n",
            csv,
        )
    }

    @Test
    fun `multi-value fields are joined with semicolon`() {
        val review = sampleReview(platforms = listOf("PC", "Switch"), tags = listOf("a", "b"))

        val csv = listOf(review).toCsvExport()

        assertTrue(csv.contains("PC; Switch"))
        assertTrue(csv.contains("a; b"))
    }

    @Test
    fun `fields containing commas or quotes are escaped per RFC 4180`() {
        val review = sampleReview(title = "Baldur's Gate, Act 3", reviewText = "Testo con \"citazione\"")

        val csv = listOf(review).toCsvExport()

        assertTrue(csv.contains("\"Baldur's Gate, Act 3\""))
        assertTrue(csv.contains("\"Testo con \"\"citazione\"\"\""))
    }

    @Test
    fun `null end date and hours played become empty cells`() {
        val review = sampleReview(endDate = null, hoursPlayed = null)

        val csv = listOf(review).toCsvExport()
        val dataRow = csv.lines()[1]

        assertEquals(16, dataRow.split(",").size)
    }
}
