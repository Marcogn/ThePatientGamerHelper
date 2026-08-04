package com.marcogn.thepatientgamerhelper.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.marcogn.thepatientgamerhelper.data.local.entity.ProConType
import com.marcogn.thepatientgamerhelper.data.local.entity.ReviewEntity
import com.marcogn.thepatientgamerhelper.data.local.entity.ReviewPlatformCrossRef
import com.marcogn.thepatientgamerhelper.data.local.entity.ReviewProConEntity
import com.marcogn.thepatientgamerhelper.domain.model.ReviewStatus
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class ReviewDaoTest {

    private lateinit var database: ThePatientGamerHelperDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ThePatientGamerHelperDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `review joined with platform and pro con via relations`() = runTest {
        val platformId = database.platformDao().getOrCreate("PC")
        database.reviewDao().upsertReview(sampleReview())
        database.reviewDao().insertPlatformCrossRefs(listOf(ReviewPlatformCrossRef("review-1", platformId)))
        database.reviewDao().insertProCon(
            listOf(
                ReviewProConEntity(reviewId = "review-1", type = ProConType.PRO, text = "Ottima colonna sonora", position = 0),
                ReviewProConEntity(reviewId = "review-1", type = ProConType.CONTRO, text = "Troppo lungo", position = 0),
            ),
        )

        val result = database.reviewDao().observeAllWithDetails().first()

        assertEquals(1, result.size)
        assertEquals("PC", result.first().platforms.single().name)
        assertEquals(2, result.first().proCon.size)
    }

    @Test
    fun `getOrCreate is idempotent for the same normalized name`() = runTest {
        val firstId = database.platformDao().getOrCreate("PlayStation 5")
        val secondId = database.platformDao().getOrCreate("  playstation 5  ")

        assertEquals(firstId, secondId)
    }

    @Test
    fun `deleting a review cascades cross refs and pro con rows`() = runTest {
        val platformId = database.platformDao().getOrCreate("PC")
        database.reviewDao().upsertReview(sampleReview())
        database.reviewDao().insertPlatformCrossRefs(listOf(ReviewPlatformCrossRef("review-1", platformId)))
        database.reviewDao().insertProCon(
            listOf(ReviewProConEntity(reviewId = "review-1", type = ProConType.PRO, text = "Bello", position = 0)),
        )

        database.reviewDao().deleteById("review-1")

        assertNull(database.reviewDao().getReviewEntity("review-1"))
        val remaining = database.reviewDao().observeAllWithDetails().first()
        assertEquals(0, remaining.size)
    }

    private fun sampleReview() = ReviewEntity(
        id = "review-1",
        title = "Test Game",
        rating = 8.5,
        startDate = LocalDate.of(2026, 1, 1),
        endDate = null,
        hoursPlayed = null,
        status = ReviewStatus.IN_CORSO,
        reviewText = "",
        coverImagePath = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
