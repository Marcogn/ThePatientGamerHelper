package com.marcogn.thepatientgamerhelper.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.marcogn.thepatientgamerhelper.data.image.ImageStorage
import com.marcogn.thepatientgamerhelper.data.local.ThePatientGamerHelperDatabase
import com.marcogn.thepatientgamerhelper.domain.model.ReviewDraft
import com.marcogn.thepatientgamerhelper.testutil.sampleReview
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
class ReviewRepositoryImplTest {

    private lateinit var database: ThePatientGamerHelperDatabase
    private lateinit var repository: ReviewRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ThePatientGamerHelperDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ReviewRepositoryImpl(
            database = database,
            reviewDao = database.reviewDao(),
            platformDao = database.platformDao(),
            genreDao = database.genreDao(),
            tagDao = database.tagDao(),
            imageStorage = ImageStorage(context),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `replaceAll wipes the previous library and preserves ids and timestamps`() = runTest {
        repository.save(id = null, draft = ReviewDraft.empty().copy(title = "Old game", platformNames = listOf("PC")))

        val restored = sampleReview(id = "restored-1", platforms = listOf("Switch", "PC"))
        repository.replaceAll(listOf(restored))

        val all = repository.observeAll().first()
        assertEquals(1, all.size)
        val review = all.single()
        assertEquals("restored-1", review.id)
        assertEquals(restored.title, review.title)
        assertEquals(restored.createdAt, review.createdAt)
        assertEquals(restored.updatedAt, review.updatedAt)
        assertEquals(setOf("Switch", "PC"), review.platforms.map { it.name }.toSet())
    }

    @Test
    fun `replaceAll clears stale lookup rows so autocomplete does not accumulate orphans`() = runTest {
        repository.save(id = null, draft = ReviewDraft.empty().copy(title = "Old game", platformNames = listOf("Wii U")))

        repository.replaceAll(listOf(sampleReview(id = "restored-1", platforms = listOf("PC"))))

        val remainingPlatforms = database.platformDao().observeAll().first()
        assertEquals(listOf("PC"), remainingPlatforms.map { it.name })
        assertTrue(remainingPlatforms.none { it.name == "Wii U" })
    }

    @Test
    fun `replaceAll with an empty list clears the library`() = runTest {
        repository.save(id = null, draft = ReviewDraft.empty().copy(title = "Old game"))

        repository.replaceAll(emptyList())

        assertTrue(repository.observeAll().first().isEmpty())
    }
}
