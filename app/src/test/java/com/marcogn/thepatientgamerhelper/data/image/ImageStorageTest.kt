package com.marcogn.thepatientgamerhelper.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class ImageStorageTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val imageStorage = ImageStorage(context)

    @Test
    fun `persistDownloadedCover downsamples an oversized image to the target dimension`() = runTest {
        val path = imageStorage.persistDownloadedCover(solidColorJpegBytes(width = 2000, height = 3000, quality = 100))

        val (width, height) = decodeDimensions(File(path).readBytes())
        assertTrue("width $width should be downsampled", width <= 900)
        assertTrue("height $height should be downsampled", height <= 900)
    }

    @Test
    fun `recompressOversizedCovers shrinks a pre-existing oversized cover in place, preserving its path`() = runTest {
        // Simulates a cover downloaded/picked before this app version compressed anything, or
        // restored from an old Drive backup taken before backups' covers were compressed either.
        val path = imageStorage.writeBytes("legacy-cover.jpg", solidColorJpegBytes(width = 2000, height = 3000, quality = 100))

        imageStorage.recompressOversizedCovers()

        val resultFile = File(path)
        assertTrue("the file must still exist at its original path", resultFile.exists())
        val (width, height) = decodeDimensions(resultFile.readBytes())
        assertTrue("width $width should be downsampled", width <= 900)
        assertTrue("height $height should be downsampled", height <= 900)
    }

    @Test
    fun `recompressOversizedCovers leaves an already-compliant cover untouched`() = runTest {
        val path = imageStorage.persistDownloadedCover(solidColorJpegBytes(width = 400, height = 600, quality = 85))
        val beforeBytes = File(path).readBytes()

        imageStorage.recompressOversizedCovers()

        assertArrayEquals("an already small/compliant cover shouldn't be rewritten", beforeBytes, File(path).readBytes())
    }

    private fun solidColorJpegBytes(width: Int, height: Int, quality: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    private fun decodeDimensions(bytes: ByteArray): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        return options.outWidth to options.outHeight
    }
}
