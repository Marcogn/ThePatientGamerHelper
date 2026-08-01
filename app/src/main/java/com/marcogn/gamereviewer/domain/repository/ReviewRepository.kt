package com.marcogn.gamereviewer.domain.repository

import com.marcogn.gamereviewer.domain.model.Review
import com.marcogn.gamereviewer.domain.model.ReviewDraft
import kotlinx.coroutines.flow.Flow

interface ReviewRepository {

    /** Reactive stream of every review, Room as single source of truth. */
    fun observeAll(): Flow<List<Review>>

    fun observeById(id: String): Flow<Review?>

    /**
     * Creates a new review when [id] is null, otherwise updates the existing one.
     * Platform/genre/tag names in [draft] are resolved to existing lookup rows or
     * created on the fly. Returns the review id.
     */
    suspend fun save(id: String?, draft: ReviewDraft): String

    suspend fun delete(id: String): Unit
}
