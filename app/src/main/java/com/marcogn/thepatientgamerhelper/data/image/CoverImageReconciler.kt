package com.marcogn.thepatientgamerhelper.data.image

import com.marcogn.thepatientgamerhelper.domain.repository.BacklogRepository
import com.marcogn.thepatientgamerhelper.domain.repository.ReviewRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Deletes cover files under `covers/` that no review or backlog item references any more. The
 * form screens intentionally don't delete a replaced/abandoned cover the moment it stops being
 * used (see `ReviewFormViewModel`/`BacklogItemFormViewModel`) — doing that immediately used to
 * delete a still-referenced file out from under a review whenever the user picked a replacement
 * cover and then cancelled instead of saving. This sweep is the actual cleanup: run at app
 * startup, cheap enough for a single-user library of this size.
 */
@Singleton
class CoverImageReconciler @Inject constructor(
    private val reviewRepository: ReviewRepository,
    private val backlogRepository: BacklogRepository,
    private val imageStorage: ImageStorage,
) {
    suspend fun pruneOrphanedCovers() {
        val referenced = buildSet {
            reviewRepository.observeAll().first().forEach { review -> review.coverImagePath?.let(::add) }
            backlogRepository.observeAllItems().first().forEach { item -> item.coverImagePath?.let(::add) }
        }
        imageStorage.listAll()
            .filter { file -> file.absolutePath !in referenced }
            .forEach { file -> imageStorage.delete(file.absolutePath) }
    }
}
