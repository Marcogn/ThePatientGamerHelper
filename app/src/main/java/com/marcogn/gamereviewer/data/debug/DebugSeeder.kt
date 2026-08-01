package com.marcogn.gamereviewer.data.debug

import com.marcogn.gamereviewer.domain.model.ReviewDraft
import com.marcogn.gamereviewer.domain.model.ReviewStatus
import com.marcogn.gamereviewer.domain.repository.ReviewRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Populates the database with a few sample reviews for local development/preview only.
 * Invoked from [com.marcogn.gamereviewer.GameReviewerApplication] and gated behind
 * `BuildConfig.SEED_DEBUG_DATA`, which is only `true` in debug builds — release builds never
 * call this, so no mock data ever reaches a real install.
 */
@Singleton
class DebugSeeder @Inject constructor(
    private val reviewRepository: ReviewRepository,
) {
    suspend fun seedIfEmpty() {
        val alreadyHasData = reviewRepository.observeAll().first().isNotEmpty()
        if (alreadyHasData) return
        sampleDrafts().forEach { draft -> reviewRepository.save(id = null, draft = draft) }
    }

    private fun sampleDrafts(): List<ReviewDraft> = listOf(
        ReviewDraft(
            title = "Hollow Knight",
            platformNames = listOf("PC", "Nintendo Switch"),
            genreNames = listOf("Metroidvania", "Indie"),
            tagNames = listOf("difficile", "atmosferico"),
            rating = 9.2,
            startDate = LocalDate.of(2025, 11, 3),
            endDate = LocalDate.of(2025, 12, 20),
            hoursPlayed = 42.5,
            status = ReviewStatus.COMPLETATO,
            pros = listOf("Level design eccezionale", "Colonna sonora memorabile", "Combattimenti soddisfacenti"),
            cons = listOf("Alcuni boss backtracking eccessivo"),
            reviewText = "Uno dei migliori metroidvania mai giocati. La cura artistica è incredibile e " +
                "l'esplorazione non annoia mai nonostante la lunga durata.",
            coverImagePath = null,
        ),
        ReviewDraft(
            title = "Hades",
            platformNames = listOf("PC"),
            genreNames = listOf("Roguelike", "Azione"),
            tagNames = listOf("narrativa", "rigiocabile"),
            rating = 8.8,
            startDate = LocalDate.of(2026, 1, 5),
            endDate = LocalDate.of(2026, 2, 1),
            hoursPlayed = 30.0,
            status = ReviewStatus.COMPLETATO,
            pros = listOf("Scrittura dei personaggi ottima", "Gameplay loop molto avvincente"),
            cons = listOf("Ripetitivo dopo molte run"),
            reviewText = "Un roguelike che rende la ripetizione parte della narrazione stessa. Consigliato.",
            coverImagePath = null,
        ),
        ReviewDraft(
            title = "Baldur's Gate 3",
            platformNames = listOf("PC"),
            genreNames = listOf("GDR"),
            tagNames = listOf("backlog", "lungo"),
            rating = 0.0,
            startDate = LocalDate.of(2026, 6, 1),
            endDate = null,
            hoursPlayed = 15.0,
            status = ReviewStatus.IN_CORSO,
            pros = emptyList(),
            cons = emptyList(),
            reviewText = "",
            coverImagePath = null,
        ),
        ReviewDraft(
            title = "Cyberpunk 2077",
            platformNames = listOf("PC"),
            genreNames = listOf("GDR", "Azione"),
            tagNames = listOf("open world"),
            rating = 4.5,
            startDate = LocalDate.of(2025, 3, 1),
            endDate = LocalDate.of(2025, 3, 10),
            hoursPlayed = 8.0,
            status = ReviewStatus.ABBANDONATO,
            pros = listOf("Ambientazione curata"),
            cons = listOf("Troppi bug al lancio sulla mia configurazione", "Non mi ha coinvolto la trama principale"),
            reviewText = "Abbandonato dopo pochi capitoli, magari ci riprovo dopo qualche altra patch.",
            coverImagePath = null,
        ),
    )
}
