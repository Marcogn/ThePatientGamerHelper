package com.marcogn.thepatientgamerhelper.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.marcogn.thepatientgamerhelper.data.image.ImageStorage
import com.marcogn.thepatientgamerhelper.data.local.ThePatientGamerHelperDatabase
import com.marcogn.thepatientgamerhelper.domain.model.BacklogComment
import com.marcogn.thepatientgamerhelper.domain.model.BacklogHistoryEntry
import com.marcogn.thepatientgamerhelper.domain.model.BacklogHistoryEventType
import com.marcogn.thepatientgamerhelper.domain.model.BacklogList
import com.marcogn.thepatientgamerhelper.domain.model.ReviewDraft
import com.marcogn.thepatientgamerhelper.testutil.sampleBacklogItem
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class BacklogRepositoryImplTest {

    private lateinit var database: ThePatientGamerHelperDatabase
    private lateinit var repository: BacklogRepositoryImpl
    private lateinit var reviewRepository: ReviewRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ThePatientGamerHelperDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val imageStorage = ImageStorage(context)
        repository = BacklogRepositoryImpl(
            database = database,
            backlogDao = database.backlogDao(),
            reviewDao = database.reviewDao(),
            platformDao = database.platformDao(),
            genreDao = database.genreDao(),
            tagDao = database.tagDao(),
            imageStorage = imageStorage,
        )
        reviewRepository = ReviewRepositoryImpl(
            database = database,
            reviewDao = database.reviewDao(),
            platformDao = database.platformDao(),
            genreDao = database.genreDao(),
            tagDao = database.tagDao(),
            imageStorage = imageStorage,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `replaceAll wipes the previous backlog and preserves list and item ids, systemKind and timestamps`() = runTest {
        repository.createList("Da rifare")

        val restoredList = BacklogList(id = 42L, name = "Da giocare", position = 0, createdAt = Instant.parse("2026-01-01T00:00:00Z"), systemKind = "COMPLETED_WITH_REVIEW")
        val restoredItem = sampleBacklogItem(id = "restored-item", listId = 42L, title = "Hades II", platforms = listOf("PC"))

        repository.replaceAll(listOf(restoredList), listOf(restoredItem))

        val lists = repository.observeLists().first()
        assertEquals(1, lists.size)
        assertEquals(42L, lists.single().id)
        assertEquals("COMPLETED_WITH_REVIEW", lists.single().systemKind)

        val items = repository.observeAllItems().first()
        assertEquals(1, items.size)
        val item = items.single()
        assertEquals("restored-item", item.id)
        assertEquals("Hades II", item.title)
        assertEquals(setOf("PC"), item.platforms.map { it.name }.toSet())
    }

    @Test
    fun `replaceAll preserves comment and history ids and content`() = runTest {
        val comment = BacklogComment(id = 7L, itemId = "item-1", text = "Ottimo inizio", timestamp = Instant.parse("2026-02-01T10:00:00Z"))
        val history = BacklogHistoryEntry(id = 3L, itemId = "item-1", type = BacklogHistoryEventType.CREATO, timestamp = Instant.parse("2026-02-01T09:00:00Z"), detail = "Hades II")
        val list = BacklogList(id = 1L, name = "Da giocare", position = 0, createdAt = Instant.EPOCH)
        val item = sampleBacklogItem(id = "item-1", listId = 1L).copy(comments = listOf(comment), history = listOf(history))

        repository.replaceAll(listOf(list), listOf(item))

        val restored = repository.observeItem("item-1").first()!!
        assertEquals(listOf(comment), restored.comments)
        assertEquals(listOf(history), restored.history)
    }

    @Test
    fun `replaceAll links a backlog item to a review restored earlier in the same operation`() = runTest {
        val reviewId = reviewRepository.save(id = null, draft = ReviewDraft.empty().copy(title = "Hades II"))
        val list = BacklogList(id = 1L, name = "Completate", position = 0, createdAt = Instant.EPOCH)
        val item = sampleBacklogItem(id = "item-1", listId = 1L).copy(reviewId = reviewId)

        repository.replaceAll(listOf(list), listOf(item))

        assertEquals(reviewId, repository.observeItem("item-1").first()!!.reviewId)
    }

    @Test
    fun `replaceAll with empty lists clears the backlog`() = runTest {
        repository.createList("Da giocare")

        repository.replaceAll(emptyList(), emptyList())

        assertTrue(repository.observeLists().first().isEmpty())
        assertTrue(repository.observeAllItems().first().isEmpty())
    }
}
