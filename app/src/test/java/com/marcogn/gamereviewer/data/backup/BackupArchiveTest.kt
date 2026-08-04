package com.marcogn.gamereviewer.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.marcogn.gamereviewer.data.image.ImageStorage
import com.marcogn.gamereviewer.domain.backup.toBackupPayload
import com.marcogn.gamereviewer.testutil.sampleReview
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
}
