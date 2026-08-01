package com.marcogn.gamereviewer.data.repository

import androidx.room.withTransaction
import com.marcogn.gamereviewer.data.image.ImageStorage
import com.marcogn.gamereviewer.data.local.GameReviewerDatabase
import com.marcogn.gamereviewer.data.local.dao.GenreDao
import com.marcogn.gamereviewer.data.local.dao.PlatformDao
import com.marcogn.gamereviewer.data.local.dao.ReviewDao
import com.marcogn.gamereviewer.data.local.dao.TagDao
import com.marcogn.gamereviewer.data.local.entity.ProConType
import com.marcogn.gamereviewer.data.local.entity.ReviewEntity
import com.marcogn.gamereviewer.data.local.entity.ReviewGenreCrossRef
import com.marcogn.gamereviewer.data.local.entity.ReviewPlatformCrossRef
import com.marcogn.gamereviewer.data.local.entity.ReviewProConEntity
import com.marcogn.gamereviewer.data.local.entity.ReviewTagCrossRef
import com.marcogn.gamereviewer.domain.model.Review
import com.marcogn.gamereviewer.domain.model.ReviewDraft
import com.marcogn.gamereviewer.domain.repository.ReviewRepository
import java.time.Instant
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ReviewRepositoryImpl @Inject constructor(
    private val database: GameReviewerDatabase,
    private val reviewDao: ReviewDao,
    private val platformDao: PlatformDao,
    private val genreDao: GenreDao,
    private val tagDao: TagDao,
    private val imageStorage: ImageStorage,
) : ReviewRepository {

    override fun observeAll(): Flow<List<Review>> =
        reviewDao.observeAllWithDetails().map { rows -> rows.map { it.toDomain() } }

    override fun observeById(id: String): Flow<Review?> =
        reviewDao.observeByIdWithDetails(id).map { it?.toDomain() }

    override suspend fun save(id: String?, draft: ReviewDraft): String = database.withTransaction {
        val now = Instant.now()
        val reviewId = id ?: UUID.randomUUID().toString()
        val createdAt = id?.let { reviewDao.getReviewEntity(it)?.createdAt } ?: now

        reviewDao.upsertReview(
            ReviewEntity(
                id = reviewId,
                title = draft.title.trim(),
                rating = draft.rating,
                startDate = draft.startDate,
                endDate = draft.endDate,
                hoursPlayed = draft.hoursPlayed,
                status = draft.status,
                reviewText = draft.reviewText,
                coverImagePath = draft.coverImagePath,
                createdAt = createdAt,
                updatedAt = now,
            ),
        )

        val platformIds = distinctNames(draft.platformNames).map { platformDao.getOrCreate(it) }
        reviewDao.clearPlatformCrossRefs(reviewId)
        if (platformIds.isNotEmpty()) {
            reviewDao.insertPlatformCrossRefs(platformIds.map { ReviewPlatformCrossRef(reviewId, it) })
        }

        val genreIds = distinctNames(draft.genreNames).map { genreDao.getOrCreate(it) }
        reviewDao.clearGenreCrossRefs(reviewId)
        if (genreIds.isNotEmpty()) {
            reviewDao.insertGenreCrossRefs(genreIds.map { ReviewGenreCrossRef(reviewId, it) })
        }

        val tagIds = distinctNames(draft.tagNames).map { tagDao.getOrCreate(it) }
        reviewDao.clearTagCrossRefs(reviewId)
        if (tagIds.isNotEmpty()) {
            reviewDao.insertTagCrossRefs(tagIds.map { ReviewTagCrossRef(reviewId, it) })
        }

        reviewDao.clearProCon(reviewId)
        val proConEntities = draft.pros.filter { it.isNotBlank() }
            .mapIndexed { index, text -> ReviewProConEntity(reviewId = reviewId, type = ProConType.PRO, text = text.trim(), position = index) } +
            draft.cons.filter { it.isNotBlank() }
                .mapIndexed { index, text -> ReviewProConEntity(reviewId = reviewId, type = ProConType.CONTRO, text = text.trim(), position = index) }
        if (proConEntities.isNotEmpty()) {
            reviewDao.insertProCon(proConEntities)
        }

        reviewId
    }

    override suspend fun delete(id: String) {
        val entity = reviewDao.getReviewEntity(id)
        reviewDao.deleteById(id)
        imageStorage.delete(entity?.coverImagePath)
    }

    private fun distinctNames(names: List<String>): List<String> =
        names.map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(Locale.ROOT) }
}
