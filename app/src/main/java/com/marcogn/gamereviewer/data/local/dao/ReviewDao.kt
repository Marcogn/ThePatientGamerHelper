package com.marcogn.gamereviewer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.marcogn.gamereviewer.data.local.entity.ReviewEntity
import com.marcogn.gamereviewer.data.local.entity.ReviewGenreCrossRef
import com.marcogn.gamereviewer.data.local.entity.ReviewPlatformCrossRef
import com.marcogn.gamereviewer.data.local.entity.ReviewProConEntity
import com.marcogn.gamereviewer.data.local.entity.ReviewTagCrossRef
import com.marcogn.gamereviewer.data.local.entity.ReviewWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewDao {

    @Transaction
    @Query("SELECT * FROM reviews")
    fun observeAllWithDetails(): Flow<List<ReviewWithDetails>>

    @Transaction
    @Query("SELECT * FROM reviews WHERE id = :id")
    fun observeByIdWithDetails(id: String): Flow<ReviewWithDetails?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReview(review: ReviewEntity)

    @Query("SELECT * FROM reviews WHERE id = :id")
    suspend fun getReviewEntity(id: String): ReviewEntity?

    @Query("DELETE FROM reviews WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM reviews")
    suspend fun deleteAll()

    @Query("DELETE FROM review_platform_cross_ref WHERE reviewId = :reviewId")
    suspend fun clearPlatformCrossRefs(reviewId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlatformCrossRefs(refs: List<ReviewPlatformCrossRef>)

    @Query("DELETE FROM review_genre_cross_ref WHERE reviewId = :reviewId")
    suspend fun clearGenreCrossRefs(reviewId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGenreCrossRefs(refs: List<ReviewGenreCrossRef>)

    @Query("DELETE FROM review_tag_cross_ref WHERE reviewId = :reviewId")
    suspend fun clearTagCrossRefs(reviewId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTagCrossRefs(refs: List<ReviewTagCrossRef>)

    @Query("DELETE FROM review_pro_con WHERE reviewId = :reviewId")
    suspend fun clearProCon(reviewId: String)

    @Insert
    suspend fun insertProCon(items: List<ReviewProConEntity>)
}
