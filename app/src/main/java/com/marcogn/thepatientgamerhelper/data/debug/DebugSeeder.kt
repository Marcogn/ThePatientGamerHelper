package com.marcogn.thepatientgamerhelper.data.debug

import com.marcogn.thepatientgamerhelper.domain.model.ReviewDraft
import com.marcogn.thepatientgamerhelper.domain.model.ReviewStatus
import com.marcogn.thepatientgamerhelper.domain.repository.ReviewRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Populates the database with a few sample reviews for local development/preview only.
 * Invoked from [com.marcogn.thepatientgamerhelper.ThePatientGamerHelperApplication] and gated behind
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
            tagNames = listOf("difficult", "atmospheric"),
            rating = 9.2,
            startDate = LocalDate.of(2025, 11, 3),
            endDate = LocalDate.of(2025, 12, 20),
            hoursPlayed = 42.5,
            status = ReviewStatus.COMPLETATO,
            pros = listOf("Exceptional level design", "Memorable soundtrack", "Satisfying combat"),
            cons = listOf("Some bosses have excessive backtracking"),
            reviewText = "One of the best metroidvanias I've ever played. The art direction is incredible and " +
                "exploration never gets old despite the long runtime.",
            coverImagePath = null,
        ),
        ReviewDraft(
            title = "Hades",
            platformNames = listOf("PC"),
            genreNames = listOf("Roguelike", "Action"),
            tagNames = listOf("narrative", "replayable"),
            rating = 8.8,
            startDate = LocalDate.of(2026, 1, 5),
            endDate = LocalDate.of(2026, 2, 1),
            hoursPlayed = 30.0,
            status = ReviewStatus.COMPLETATO,
            pros = listOf("Excellent character writing", "Very compelling gameplay loop"),
            cons = listOf("Repetitive after many runs"),
            reviewText = "A roguelike that makes repetition part of the narrative itself. Recommended.",
            coverImagePath = null,
        ),
        ReviewDraft(
            title = "Baldur's Gate 3",
            platformNames = listOf("PC"),
            genreNames = listOf("RPG"),
            tagNames = listOf("backlog", "long"),
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
            genreNames = listOf("RPG", "Action"),
            tagNames = listOf("open world"),
            rating = 4.5,
            startDate = LocalDate.of(2025, 3, 1),
            endDate = LocalDate.of(2025, 3, 10),
            hoursPlayed = 8.0,
            status = ReviewStatus.ABBANDONATO,
            pros = listOf("Well-crafted setting"),
            cons = listOf("Too many launch bugs on my setup", "Didn't get into the main story"),
            reviewText = "Dropped after a few chapters, might give it another try after a few more patches.",
            coverImagePath = null,
        ),
    )
}
