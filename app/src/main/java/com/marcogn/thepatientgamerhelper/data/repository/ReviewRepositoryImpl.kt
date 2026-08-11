package com.marcogn.thepatientgamerhelper.data.repository

import androidx.room.withTransaction
import com.marcogn.thepatientgamerhelper.data.image.ImageStorage
import com.marcogn.thepatientgamerhelper.data.local.ThePatientGamerHelperDatabase
import com.marcogn.thepatientgamerhelper.data.local.dao.GenreDao
import com.marcogn.thepatientgamerhelper.data.local.dao.PlatformDao
import com.marcogn.thepatientgamerhelper.data.local.dao.ReviewDao
import com.marcogn.thepatientgamerhelper.data.local.dao.TagDao
import com.marcogn.thepatientgamerhelper.data.local.entity.ProConType
import com.marcogn.thepatientgamerhelper.data.local.entity.ReviewEntity
import com.marcogn.thepatientgamerhelper.data.local.entity.ReviewGenreCrossRef
import com.marcogn.thepatientgamerhelper.data.local.entity.ReviewPlatformCrossRef
import com.marcogn.thepatientgamerhelper.data.local.entity.ReviewProConEntity
import com.marcogn.thepatientgamerhelper.data.local.entity.ReviewTagCrossRef
import com.marcogn.thepatientgamerhelper.domain.model.Review
import com.marcogn.thepatientgamerhelper.domain.model.ReviewDraft
import com.marcogn.thepatientgamerhelper.domain.repository.ReviewRepository
import java.time.Instant
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ReviewRepositoryImpl @Inject constructor(
    private val database: ThePatientGamerHelperDatabase,
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
        val existing = id?.let { reviewDao.getReviewEntity(it) }

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
                // Never edited by the form — carry over whatever the review already had (null for a
                // brand new one) instead of wiping it on every save. See Review.kt's doc comment.
                developer = existing?.developer,
                publisher = existing?.publisher,
                releaseYear = existing?.releaseYear,
                metadataSource = existing?.metadataSource,
                externalId = existing?.externalId,
                linkedBacklogItemId = existing?.linkedBacklogItemId,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
        reviewDao.clearPlatformCrossRefs(reviewId)
        reviewDao.clearGenreCrossRefs(reviewId)
        reviewDao.clearTagCrossRefs(reviewId)
        reviewDao.clearProCon(reviewId)
        writeRelations(reviewId, draft.platformNames, draft.genreNames, draft.tagNames, draft.pros, draft.cons)

        reviewId
    }

    override suspend fun delete(id: String) {
        val entity = reviewDao.getReviewEntity(id)
        reviewDao.deleteById(id)
        imageStorage.delete(entity?.coverImagePath)
    }

    override suspend fun replaceAll(reviews: List<Review>) = database.withTransaction {
        reviewDao.deleteAll()
        platformDao.deleteAll()
        genreDao.deleteAll()
        tagDao.deleteAll()

        reviews.forEach { review -> upsertReviewEntity(review) }
    }

    /**
     * Multi-review import (v2 §2.4): upserts each review by id, preserving id/createdAt/updatedAt
     * from the parsed file — same "preserve, don't regenerate" restore semantics as [replaceAll],
     * but additive (existing reviews not present in [reviews] are left untouched) instead of a full
     * wipe. Caller (`ReviewImporter`) has already validated every review in the batch atomically
     * before this runs, so every entry here is written unconditionally.
     */
    override suspend fun upsertImported(reviews: List<Review>) = database.withTransaction {
        reviews.forEach { review -> upsertReviewEntity(review) }
    }

    private suspend fun upsertReviewEntity(review: Review) {
        reviewDao.upsertReview(
            ReviewEntity(
                id = review.id,
                title = review.title,
                rating = review.rating,
                startDate = review.startDate,
                endDate = review.endDate,
                hoursPlayed = review.hoursPlayed,
                status = review.status,
                reviewText = review.reviewText,
                coverImagePath = review.coverImagePath,
                developer = review.developer,
                publisher = review.publisher,
                releaseYear = review.releaseYear,
                metadataSource = review.metadataSource,
                externalId = review.externalId,
                linkedBacklogItemId = review.linkedBacklogItemId,
                createdAt = review.createdAt,
                updatedAt = review.updatedAt,
            ),
        )
        reviewDao.clearPlatformCrossRefs(review.id)
        reviewDao.clearGenreCrossRefs(review.id)
        reviewDao.clearTagCrossRefs(review.id)
        reviewDao.clearProCon(review.id)
        writeRelations(
            reviewId = review.id,
            platformNames = review.platforms.map { it.name },
            genreNames = review.genres.map { it.name },
            tagNames = review.tags.map { it.name },
            pros = review.pros,
            cons = review.cons,
        )
    }

    /** Resolves lookup names to ids and writes cross-refs + pro/con rows for a review that already exists. */
    private suspend fun writeRelations(
        reviewId: String,
        platformNames: List<String>,
        genreNames: List<String>,
        tagNames: List<String>,
        pros: List<String>,
        cons: List<String>,
    ) {
        val platformIds = distinctNames(platformNames).map { platformDao.getOrCreate(it) }
        if (platformIds.isNotEmpty()) {
            reviewDao.insertPlatformCrossRefs(platformIds.map { ReviewPlatformCrossRef(reviewId, it) })
        }

        val genreIds = distinctNames(genreNames).map { genreDao.getOrCreate(it) }
        if (genreIds.isNotEmpty()) {
            reviewDao.insertGenreCrossRefs(genreIds.map { ReviewGenreCrossRef(reviewId, it) })
        }

        val tagIds = distinctNames(tagNames).map { tagDao.getOrCreate(it) }
        if (tagIds.isNotEmpty()) {
            reviewDao.insertTagCrossRefs(tagIds.map { ReviewTagCrossRef(reviewId, it) })
        }

        val proConEntities = pros.filter { it.isNotBlank() }
            .mapIndexed { index, text -> ReviewProConEntity(reviewId = reviewId, type = ProConType.PRO, text = text.trim(), position = index) } +
            cons.filter { it.isNotBlank() }
                .mapIndexed { index, text -> ReviewProConEntity(reviewId = reviewId, type = ProConType.CONTRO, text = text.trim(), position = index) }
        if (proConEntities.isNotEmpty()) {
            reviewDao.insertProCon(proConEntities)
        }
    }

    private fun distinctNames(names: List<String>): List<String> =
        names.map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(Locale.ROOT) }
}
