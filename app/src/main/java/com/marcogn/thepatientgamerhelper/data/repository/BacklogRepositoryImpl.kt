package com.marcogn.thepatientgamerhelper.data.repository

import androidx.room.withTransaction
import com.marcogn.thepatientgamerhelper.data.image.ImageStorage
import com.marcogn.thepatientgamerhelper.data.local.ThePatientGamerHelperDatabase
import com.marcogn.thepatientgamerhelper.data.local.dao.BacklogDao
import com.marcogn.thepatientgamerhelper.data.local.dao.GenreDao
import com.marcogn.thepatientgamerhelper.data.local.dao.PlatformDao
import com.marcogn.thepatientgamerhelper.data.local.dao.ReviewDao
import com.marcogn.thepatientgamerhelper.data.local.dao.TagDao
import com.marcogn.thepatientgamerhelper.data.local.entity.BacklogCommentEntity
import com.marcogn.thepatientgamerhelper.data.local.entity.BacklogHistoryEntryEntity
import com.marcogn.thepatientgamerhelper.data.local.entity.BacklogItemEntity
import com.marcogn.thepatientgamerhelper.data.local.entity.BacklogItemGenreCrossRef
import com.marcogn.thepatientgamerhelper.data.local.entity.BacklogItemPlatformCrossRef
import com.marcogn.thepatientgamerhelper.data.local.entity.BacklogItemTagCrossRef
import com.marcogn.thepatientgamerhelper.data.local.entity.BacklogListEntity
import com.marcogn.thepatientgamerhelper.domain.model.BacklogHistoryEventType
import com.marcogn.thepatientgamerhelper.domain.model.BacklogItem
import com.marcogn.thepatientgamerhelper.domain.model.BacklogItemDraft
import com.marcogn.thepatientgamerhelper.domain.model.BacklogItemStatus
import com.marcogn.thepatientgamerhelper.domain.model.BacklogList
import com.marcogn.thepatientgamerhelper.domain.model.BacklogListKind
import com.marcogn.thepatientgamerhelper.domain.model.ImportedBacklogList
import com.marcogn.thepatientgamerhelper.domain.repository.BacklogRepository
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BacklogRepositoryImpl @Inject constructor(
    private val database: ThePatientGamerHelperDatabase,
    private val backlogDao: BacklogDao,
    private val reviewDao: ReviewDao,
    private val platformDao: PlatformDao,
    private val genreDao: GenreDao,
    private val tagDao: TagDao,
    private val imageStorage: ImageStorage,
) : BacklogRepository {

    override fun observeLists(): Flow<List<BacklogList>> =
        backlogDao.observeLists().map { lists -> lists.map { it.toDomain() } }

    override fun observeItemsForList(listId: Long): Flow<List<BacklogItem>> =
        backlogDao.observeItemsForListWithDetails(listId).map { rows -> rows.map { it.toDomain() } }

    override fun observeAllItems(): Flow<List<BacklogItem>> =
        backlogDao.observeAllItemsWithDetails().map { rows -> rows.map { it.toDomain() } }

    override fun observeItem(itemId: String): Flow<BacklogItem?> =
        backlogDao.observeItemWithDetails(itemId).map { it?.toDomain() }

    override suspend fun createList(name: String): Long = database.withTransaction {
        val position = backlogDao.maxListPosition() + 1
        backlogDao.insertList(BacklogListEntity(name = name.trim(), position = position, createdAt = Instant.now()))
    }

    override suspend fun renameList(listId: Long, name: String) {
        backlogDao.renameList(listId, name.trim())
    }

    override suspend fun deleteList(listId: Long) = database.withTransaction {
        val covers = backlogDao.getItemsForList(listId).mapNotNull { it.coverImagePath }
        backlogDao.deleteList(listId)
        covers.forEach { imageStorage.delete(it) }
    }

    override suspend fun reorderLists(orderedIds: List<Long>) {
        backlogDao.reorderLists(orderedIds)
    }

    override suspend fun getOrCreateSystemList(kind: BacklogListKind, displayName: String): Long = database.withTransaction {
        backlogDao.getListBySystemKind(kind.name)?.let { return@withTransaction it.id }
        val position = backlogDao.maxListPosition() + 1
        backlogDao.insertList(
            BacklogListEntity(name = displayName.trim(), position = position, createdAt = Instant.now(), systemKind = kind.name),
        )
    }

    override suspend fun saveItem(id: String?, listId: Long, draft: BacklogItemDraft): String = database.withTransaction {
        val now = Instant.now()
        val itemId = id ?: UUID.randomUUID().toString()
        val existing = id?.let { backlogDao.getItemEntity(it) }

        backlogDao.upsertItem(
            BacklogItemEntity(
                id = itemId,
                listId = existing?.listId ?: listId,
                title = draft.title.trim(),
                coverImagePath = draft.coverImagePath,
                status = existing?.status ?: BacklogItemStatus.DA_INIZIARE,
                position = existing?.position ?: (backlogDao.maxItemPosition(listId) + 1),
                addedAt = existing?.addedAt ?: now,
                startDate = existing?.startDate,
                completedDate = existing?.completedDate,
                reviewId = existing?.reviewId,
                abandonNote = existing?.abandonNote,
                releaseYear = draft.releaseYear,
                developer = draft.developer,
                hltbMainStoryHours = draft.hltbMainStoryHours,
                hltbMainExtraHours = draft.hltbMainExtraHours,
                hltbCompletionistHours = draft.hltbCompletionistHours,
                updatedAt = now,
            ),
        )
        backlogDao.clearPlatformCrossRefs(itemId)
        backlogDao.clearGenreCrossRefs(itemId)
        backlogDao.clearTagCrossRefs(itemId)
        writeRelations(itemId, draft.platformNames, draft.genreNames, draft.tagNames)

        if (existing == null) {
            backlogDao.insertHistoryEntry(
                BacklogHistoryEntryEntity(
                    itemId = itemId,
                    eventType = BacklogHistoryEventType.CREATO,
                    timestamp = now,
                    detail = draft.title.trim(),
                ),
            )
        }

        itemId
    }

    override suspend fun deleteItem(itemId: String) {
        val entity = backlogDao.getItemEntity(itemId)
        backlogDao.deleteItem(itemId)
        imageStorage.delete(entity?.coverImagePath)
    }

    override suspend fun moveItem(itemId: String, targetListId: Long) = database.withTransaction {
        val entity = backlogDao.getItemEntity(itemId) ?: return@withTransaction
        if (entity.listId == targetListId) return@withTransaction

        val now = Instant.now()
        val newPosition = backlogDao.maxItemPosition(targetListId) + 1
        backlogDao.updateItemListAndPosition(itemId, targetListId, newPosition, now)
        backlogDao.insertHistoryEntry(
            BacklogHistoryEntryEntity(
                itemId = itemId,
                eventType = BacklogHistoryEventType.CAMBIO_LISTA,
                timestamp = now,
                detail = backlogDao.getList(targetListId)?.name,
            ),
        )
    }

    override suspend fun reorderItems(listId: Long, orderedItemIds: List<String>) {
        backlogDao.reorderItems(orderedItemIds, Instant.now())
    }

    override suspend fun updateStatus(itemId: String, status: BacklogItemStatus, abandonNote: String?) =
        database.withTransaction {
            val entity = backlogDao.getItemEntity(itemId) ?: return@withTransaction
            val statusChanged = entity.status != status
            val note = if (status == BacklogItemStatus.ABBANDONATO) abandonNote else null
            if (!statusChanged && note == entity.abandonNote) return@withTransaction

            val now = Instant.now()
            val startDate = if (status == BacklogItemStatus.IN_CORSO && entity.startDate == null) {
                LocalDate.now()
            } else {
                entity.startDate
            }
            val completedDate = if (status == BacklogItemStatus.COMPLETATO && entity.completedDate == null) {
                LocalDate.now()
            } else {
                entity.completedDate
            }

            backlogDao.updateItemStatus(itemId, status, startDate, completedDate, note, now)
            if (statusChanged) {
                backlogDao.insertHistoryEntry(
                    BacklogHistoryEntryEntity(
                        itemId = itemId,
                        eventType = BacklogHistoryEventType.CAMBIO_STATO,
                        timestamp = now,
                        detail = status.name,
                    ),
                )
            }
        }

    override suspend fun linkReview(itemId: String, reviewId: String) = database.withTransaction {
        val now = Instant.now()
        backlogDao.linkReview(itemId, reviewId, now)
        backlogDao.insertHistoryEntry(
            BacklogHistoryEntryEntity(
                itemId = itemId,
                eventType = BacklogHistoryEventType.RECENSIONE_COLLEGATA,
                timestamp = now,
                detail = null,
            ),
        )
    }

    override suspend fun addComment(itemId: String, text: String) = database.withTransaction {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@withTransaction

        val now = Instant.now()
        backlogDao.insertComment(BacklogCommentEntity(itemId = itemId, text = trimmed, timestamp = now))
        backlogDao.insertHistoryEntry(
            BacklogHistoryEntryEntity(itemId = itemId, eventType = BacklogHistoryEventType.COMMENTO, timestamp = now, detail = null),
        )
    }

    /**
     * Imports whole lists from a backlog export (Fase 8): always additive, never a replace — every
     * imported list becomes a brand new list and every item gets a fresh id/position, comments and
     * history are re-inserted verbatim (no synthetic CREATO entry, the original one from the export
     * is already in [ImportedBacklogList.items]'s history). [ImportedBacklogItem.reviewId] is
     * best-effort, same principle as covers (v2 §4): linked back onto the item only if a review with
     * that id already exists on *this* device, left null otherwise — it usually belongs to whichever
     * library exported the file and won't exist here. No new history entry is synthesized for the
     * link either way: if the source device had one, it's already in the re-inserted history above.
     */
    override suspend fun importLists(lists: List<ImportedBacklogList>) = database.withTransaction {
        lists.forEach { importedList ->
            val listPosition = backlogDao.maxListPosition() + 1
            val listId = backlogDao.insertList(
                BacklogListEntity(name = importedList.name.trim(), position = listPosition, createdAt = Instant.now()),
            )
            importedList.items.forEachIndexed { index, importedItem ->
                val itemId = UUID.randomUUID().toString()
                val linkedReviewId = importedItem.reviewId?.takeIf { reviewDao.getReviewEntity(it) != null }
                backlogDao.upsertItem(
                    BacklogItemEntity(
                        id = itemId,
                        listId = listId,
                        title = importedItem.title.trim(),
                        coverImagePath = importedItem.coverImagePath,
                        status = importedItem.status,
                        position = index,
                        addedAt = importedItem.addedAt,
                        startDate = importedItem.startDate,
                        completedDate = importedItem.completedDate,
                        reviewId = linkedReviewId,
                        abandonNote = importedItem.abandonNote,
                        releaseYear = importedItem.releaseYear,
                        developer = importedItem.developer,
                        hltbMainStoryHours = importedItem.hltbMainStoryHours,
                        hltbMainExtraHours = importedItem.hltbMainExtraHours,
                        hltbCompletionistHours = importedItem.hltbCompletionistHours,
                        updatedAt = importedItem.addedAt,
                    ),
                )
                writeRelations(itemId, importedItem.platformNames, importedItem.genreNames, importedItem.tagNames)
                importedItem.comments.forEach { comment ->
                    backlogDao.insertComment(BacklogCommentEntity(itemId = itemId, text = comment.text, timestamp = comment.timestamp))
                }
                importedItem.history.forEach { entry ->
                    backlogDao.insertHistoryEntry(
                        BacklogHistoryEntryEntity(itemId = itemId, eventType = entry.type, timestamp = entry.timestamp, detail = entry.detail),
                    )
                }
            }
        }
    }

    /** Resolves lookup names to ids and writes cross-refs for an item that already exists. */
    private suspend fun writeRelations(itemId: String, platformNames: List<String>, genreNames: List<String>, tagNames: List<String>) {
        val platformIds = distinctNames(platformNames).map { platformDao.getOrCreate(it) }
        if (platformIds.isNotEmpty()) {
            backlogDao.insertPlatformCrossRefs(platformIds.map { BacklogItemPlatformCrossRef(itemId, it) })
        }

        val genreIds = distinctNames(genreNames).map { genreDao.getOrCreate(it) }
        if (genreIds.isNotEmpty()) {
            backlogDao.insertGenreCrossRefs(genreIds.map { BacklogItemGenreCrossRef(itemId, it) })
        }

        val tagIds = distinctNames(tagNames).map { tagDao.getOrCreate(it) }
        if (tagIds.isNotEmpty()) {
            backlogDao.insertTagCrossRefs(tagIds.map { BacklogItemTagCrossRef(itemId, it) })
        }
    }

    private fun distinctNames(names: List<String>): List<String> =
        names.map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(Locale.ROOT) }
}
