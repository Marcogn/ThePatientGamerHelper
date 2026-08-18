package com.marcogn.thepatientgamerhelper.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.marcogn.thepatientgamerhelper.data.image.ImageStorage
import com.marcogn.thepatientgamerhelper.domain.backup.buildBackupPayload
import com.marcogn.thepatientgamerhelper.domain.backup.toBackupPayload
import com.marcogn.thepatientgamerhelper.domain.model.BacklogList
import com.marcogn.thepatientgamerhelper.testutil.sampleBacklogItem
import com.marcogn.thepatientgamerhelper.testutil.sampleReview
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class BackupArchiveTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val imageStorage = ImageStorage(context)
    private val builder = BackupArchiveBuilder(imageStorage)
    private val reader = BackupArchiveReader()

    @Test
    fun `archive round trips the library and its cover images`() = runTest {
        val coverBytes = byteArrayOf(1, 2, 3, 4, 5)
        val coverPath = imageStorage.writeBytes("cover-1.jpg", coverBytes)
        val reviews = listOf(sampleReview(id = "r1", coverImagePath = coverPath))
        val payload = reviews.toBackupPayload()

        val archiveBytes = builder.build(payload)
        val content = reader.read(archiveBytes)

        assertEquals(payload, content.payload)
        assertTrue(content.images.containsKey("cover-1.jpg"))
        assertArrayEquals(coverBytes, content.images.getValue("cover-1.jpg"))
    }

    @Test
    fun `archive with no covers still round trips`() = runTest {
        val payload = listOf(sampleReview(id = "r1", coverImagePath = null)).toBackupPayload()

        val content = reader.read(builder.build(payload))

        assertEquals(payload, content.payload)
        assertTrue(content.images.isEmpty())
    }

    @Test
    fun `archive excludes cover files not referenced by any review or backlog item in the payload`() = runTest {
        val coverPath = imageStorage.writeBytes("cover-1.jpg", byteArrayOf(1, 2, 3))
        // Simulates a file left over from an abandoned form (see CoverImageReconciler): present on
        // disk, but not referenced by any review or backlog item going into this backup.
        imageStorage.writeBytes("orphan.jpg", byteArrayOf(9, 9, 9))
        val payload = listOf(sampleReview(id = "r1", coverImagePath = coverPath)).toBackupPayload()

        val content = reader.read(builder.build(payload))

        assertEquals(setOf("cover-1.jpg"), content.images.keys)
    }

    @Test
    fun `archive includes backlog item covers alongside review covers`() = runTest {
        val reviewCoverPath = imageStorage.writeBytes("review-cover.jpg", byteArrayOf(1, 2, 3))
        val backlogCoverPath = imageStorage.writeBytes("backlog-cover.jpg", byteArrayOf(4, 5, 6))
        val list = BacklogList(id = 1L, name = "Da giocare", position = 0, createdAt = Instant.EPOCH)
        val item = sampleBacklogItem(id = "item-1", listId = 1L).copy(coverImagePath = backlogCoverPath)
        val payload = buildBackupPayload(
            reviews = listOf(sampleReview(id = "r1", coverImagePath = reviewCoverPath)),
            backlogLists = listOf(list),
            backlogItems = listOf(item),
        )

        val content = reader.read(builder.build(payload))

        assertEquals(payload, content.payload)
        assertEquals(setOf("review-cover.jpg", "backlog-cover.jpg"), content.images.keys)
    }
}
