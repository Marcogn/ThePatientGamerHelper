package com.marcogn.thepatientgamerhelper.domain.repository

import com.marcogn.thepatientgamerhelper.domain.model.BacklogItem
import com.marcogn.thepatientgamerhelper.domain.model.BacklogItemDraft
import com.marcogn.thepatientgamerhelper.domain.model.BacklogItemStatus
import com.marcogn.thepatientgamerhelper.domain.model.BacklogList
import com.marcogn.thepatientgamerhelper.domain.model.ImportedBacklogList
import kotlinx.coroutines.flow.Flow

interface BacklogRepository {

    fun observeLists(): Flow<List<BacklogList>>
    fun observeItemsForList(listId: Long): Flow<List<BacklogItem>>
    fun observeAllItems(): Flow<List<BacklogItem>>
    fun observeItem(itemId: String): Flow<BacklogItem?>

    suspend fun createList(name: String): Long
    suspend fun renameList(listId: Long, name: String)
    suspend fun deleteList(listId: Long)
    suspend fun reorderLists(orderedIds: List<Long>)

    /** Creates a new item when [id] is null, otherwise updates title/platforms/genres/tags/cover only. */
    suspend fun saveItem(id: String?, listId: Long, draft: BacklogItemDraft): String

    suspend fun deleteItem(itemId: String)

    /** Moves [itemId] to the end of [targetListId], recording a CAMBIO_LISTA history entry. */
    suspend fun moveItem(itemId: String, targetListId: Long)

    /** Persists a manual drag-to-reorder within a single list. */
    suspend fun reorderItems(listId: Long, orderedItemIds: List<String>)

    /**
     * Dedicated status-change path (the "selettore" in the spec, separate from [saveItem]):
     * auto-fills startDate/completedDate the first time the item enters IN_CORSO/COMPLETATO,
     * and records a CAMBIO_STATO history entry.
     */
    suspend fun updateStatus(itemId: String, status: BacklogItemStatus, abandonNote: String? = null)

    suspend fun linkReview(itemId: String, reviewId: String)

    suspend fun addComment(itemId: String, text: String)

    /** Additive import from a backlog export (Fase 8) — see `BacklogRepositoryImpl` for the exact semantics. */
    suspend fun importLists(lists: List<ImportedBacklogList>)
}
