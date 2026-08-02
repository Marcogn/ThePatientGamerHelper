package com.marcogn.gamereviewer.domain.export

import com.marcogn.gamereviewer.domain.model.Review
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ReviewExportDto(
    val id: String,
    val titolo: String,
    val piattaforme: List<String>,
    val generi: List<String>,
    val tag: List<String>,
    val voto: Double,
    val dataInizio: String,
    val dataFine: String?,
    val oreGioco: Double?,
    val stato: String,
    val pro: List<String>,
    val contro: List<String>,
    val testoRecensione: String,
    val copertina: String?,
    val creatoIl: String,
    val modificatoIl: String,
)

@Serializable
data class LibraryExportDto(
    val esportatoIl: String,
    val numeroRecensioni: Int,
    val recensioni: List<ReviewExportDto>,
)

fun Review.toExportDto(): ReviewExportDto = ReviewExportDto(
    id = id,
    titolo = title,
    piattaforme = platforms.map { it.name },
    generi = genres.map { it.name },
    tag = tags.map { it.name },
    voto = rating,
    dataInizio = startDate.toString(),
    dataFine = endDate?.toString(),
    oreGioco = hoursPlayed,
    stato = status.name,
    pro = pros,
    contro = cons,
    testoRecensione = reviewText,
    copertina = coverImagePath,
    creatoIl = createdAt.toString(),
    modificatoIl = updatedAt.toString(),
)

fun List<Review>.toLibraryExportDto(exportedAt: Instant = Instant.now()): LibraryExportDto =
    LibraryExportDto(
        esportatoIl = exportedAt.toString(),
        numeroRecensioni = size,
        recensioni = map { it.toExportDto() },
    )

private val exportJson = Json {
    prettyPrint = true
    encodeDefaults = true
}

fun List<Review>.toJsonExport(): String = exportJson.encodeToString(toLibraryExportDto())
