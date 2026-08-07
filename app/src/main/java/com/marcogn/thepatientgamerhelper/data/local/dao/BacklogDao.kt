package com.marcogn.thepatientgamerhelper.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.marcogn.thepatientgamerhelper.data.local.entity.BacklogCommentEntity
import com.marcogn.thepatientgamerhelper.data.local.entity.BacklogHistoryEntryEntity
import com.marcogn.thepatientgamerhelper.data.local.entity.BacklogItemEntity
import com.marcogn.thepatientgamerhelper.data.local.entity.BacklogItemGenreCrossRef
import com.marcogn.thepatientgamerhelper.data.local.entity.BacklogItemPlatformCrossRef
import com.marcogn.thepatientgamerhelper.data.local.entity.BacklogItemTagCrossRef
import com.marcogn.thepatientgamerhelper.data.local.entity.BacklogItemWithDetails
import com.marcogn.thepatientgamerhelper.data.local.entity.BacklogListEntity
import com.marcogn.thepatientgamerhelper.domain.model.BacklogItemStatus
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface BacklogDao {

    // --- Liste ---

    @Query("SELECT * FROM backlog_lists ORDER BY position")
    fun observeLists(): Flow<List<BacklogListEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM backlog_lists")
    suspend fun maxListPosition(): Int

    @Insert
    suspend fun insertList(list: BacklogListEntity): Long

    @Query("UPDATE backlog_lists SET name = :name WHERE id = :id")
    suspend fun renameList(id: Long, name: String)

    @Query("SELECT * FROM backlog_lists WHERE id = :id")
    suspend fun getList(id: Long): BacklogListEntity?

    @Query("SELECT * FROM backlog_lists WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getListByName(name: String): BacklogListEntity?

    @Query("DELETE FROM backlog_lists WHERE id = :id")
    suspend fun deleteList(id: Long)

    @Query("UPDATE backlog_lists SET position = :position WHERE id = :id")
    suspend fun updateListPosition(id: Long, position: Int)

    @Transaction
    suspend fun reorderLists(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> updateListPosition(id, index) }
    }

    // --- Item ---

    @Transaction
    @Query("SELECT * FROM backlog_items")
    fun observeAllItemsWithDetails(): Flow<List<BacklogItemWithDetails>>

    @Transaction
    @Query("SELECT * FROM backlog_items WHERE listId = :listId ORDER BY position")
    fun observeItemsForListWithDetails(listId: Long): Flow<List<BacklogItemWithDetails>>

    @Transaction
    @Query("SELECT * FROM backlog_items WHERE id = :id")
    fun observeItemWithDetails(id: String): Flow<BacklogItemWithDetails?>

    @Query("SELECT * FROM backlog_items WHERE id = :id")
    suspend fun getItemEntity(id: String): BacklogItemEntity?

    @Query("SELECT * FROM backlog_items WHERE listId = :listId")
    suspend fun getItemsForList(listId: Long): List<BacklogItemEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM backlog_items WHERE listId = :listId")
    suspend fun maxItemPosition(listId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: BacklogItemEntity)

    @Query("DELETE FROM backlog_items WHERE id = :id")
    suspend fun deleteItem(id: String)

    @Query("UPDATE backlog_items SET position = :position, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateItemPosition(id: String, position: Int, updatedAt: Instant)

    @Transaction
    suspend fun reorderItems(orderedIds: List<String>, updatedAt: Instant) {
        orderedIds.forEachIndexed { index, id -> updateItemPosition(id, index, updatedAt) }
    }

    @Query("UPDATE backlog_items SET listId = :listId, position = :position, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateItemListAndPosition(id: String, listId: Long, position: Int, updatedAt: Instant)

    @Query(
        "UPDATE backlog_items SET status = :status, startDate = :startDate, completedDate = :completedDate, " +
            "abandonNote = :abandonNote, updatedAt = :updatedAt WHERE id = :id",
    )
    suspend fun updateItemStatus(
        id: String,
        status: BacklogItemStatus,
        startDate: LocalDate?,
        completedDate: LocalDate?,
        abandonNote: String?,
        updatedAt: Instant,
    )

    @Query("UPDATE backlog_items SET reviewId = :reviewId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun linkReview(id: String, reviewId: String, updatedAt: Instant)

    @Query("DELETE FROM backlog_item_platform_cross_ref WHERE itemId = :itemId")
    suspend fun clearPlatformCrossRefs(itemId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlatformCrossRefs(refs: List<BacklogItemPlatformCrossRef>)

    @Query("DELETE FROM backlog_item_genre_cross_ref WHERE itemId = :itemId")
    suspend fun clearGenreCrossRefs(itemId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGenreCrossRefs(refs: List<BacklogItemGenreCrossRef>)

    @Query("DELETE FROM backlog_item_tag_cross_ref WHERE itemId = :itemId")
    suspend fun clearTagCrossRefs(itemId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTagCrossRefs(refs: List<BacklogItemTagCrossRef>)

    // --- Commenti e storico ---

    @Insert
    suspend fun insertComment(comment: BacklogCommentEntity)

    @Insert
    suspend fun insertHistoryEntry(entry: BacklogHistoryEntryEntity)
}
