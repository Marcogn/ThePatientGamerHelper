package com.marcogn.thepatientgamerhelper.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Longest edge a stored cover is downsampled to — comfortably more than any on-screen/PDF use needs. */
private const val COVER_MAX_DIMENSION = 900
private const val COVER_JPEG_QUALITY = 85
/** Above this, a file claiming to already be a compressed cover is treated as suspect and re-encoded anyway. */
private const val COVER_RECOMPRESS_THRESHOLD_BYTES = 400_000L

/**
 * Persists a picked cover image into the app's internal storage so it survives beyond the
 * transient permission granted by the photo picker, and exposes it as a plain absolute path.
 */
@Singleton
class ImageStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val coversDir: File
        get() = File(context.filesDir, "covers").apply { mkdirs() }

    /** Persists a photo-picker selection, downsized/compressed like every other cover source. */
    suspend fun persist(sourceUri: Uri): String = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
            ?: error("Impossibile leggere l'immagine selezionata")
        writeCompressedCover(bytes)
    }

    /**
     * Persists cover bytes downloaded from TheGamesDB. Their "original" box art can be several MB
     * at full resolution — nothing in this app displays a cover anywhere near that size, and saving
     * it as-is is what made a library's worth of covers balloon the on-device footprint and every
     * Drive backup. Downsampled and re-encoded the same way as a photo-picker pick.
     */
    suspend fun persistDownloadedCover(bytes: ByteArray): String = withContext(Dispatchers.IO) {
        writeCompressedCover(bytes)
    }

    private fun writeCompressedCover(bytes: ByteArray): String {
        val destination = File(coversDir, "${UUID.randomUUID()}.jpg")
        val downsampled = decodeDownsampled(bytes)
        destination.outputStream().use { output ->
            if (downsampled != null) {
                downsampled.compress(Bitmap.CompressFormat.JPEG, COVER_JPEG_QUALITY, output)
                downsampled.recycle()
            } else {
                // Not a decodable bitmap (or Robolectric's shadow decoder in tests) — keep the
                // original bytes rather than silently losing the image.
                output.write(bytes)
            }
        }
        return destination.absolutePath
    }

    /**
     * Recompresses any cover already on disk that predates the downsample/compress step above —
     * picked/downloaded before this app version, or written back by [writeBytes] while restoring
     * an old Drive backup taken before backups' covers were compressed either. Overwrites each
     * file **in place at its existing path**, so no `Review`/`BacklogItem.coverImagePath` needs
     * updating; only files that actually need it are rewritten (checked via [needsRecompression],
     * a cheap bounds-only decode), so this is safe to run unconditionally on every app startup —
     * see `ThePatientGamerHelperApplication.onCreate()`. Without this, the on-device/backup size
     * fix only ever applied going forward: a library whose covers were already downloaded before
     * updating stayed exactly as bloated as before, no matter how many *new* covers got compressed
     * correctly — a real device report after the original fix confirmed this gap.
     */
    suspend fun recompressOversizedCovers() = withContext(Dispatchers.IO) {
        coversDir.listFiles()?.forEach { file ->
            val bytes = file.readBytes()
            if (!needsRecompression(bytes, file.length())) return@forEach
            val downsampled = decodeDownsampled(bytes) ?: return@forEach
            file.outputStream().use { output ->
                downsampled.compress(Bitmap.CompressFormat.JPEG, COVER_JPEG_QUALITY, output)
            }
            downsampled.recycle()
        }
    }

    private fun needsRecompression(bytes: ByteArray, fileSizeBytes: Long): Boolean {
        if (fileSizeBytes > COVER_RECOMPRESS_THRESHOLD_BYTES) return true
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        return bounds.outWidth > COVER_MAX_DIMENSION || bounds.outHeight > COVER_MAX_DIMENSION
    }

    private fun decodeDownsampled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > COVER_MAX_DIMENSION || bounds.outHeight / sampleSize > COVER_MAX_DIMENSION) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    /**
     * Copies an existing cover to a new file so it has an independent lifecycle — used when a
     * backlog item's cover is carried over into a pre-populated review, so deleting one doesn't
     * pull the image out from under the other.
     */
    suspend fun duplicate(sourcePath: String): String = withContext(Dispatchers.IO) {
        val destination = File(coversDir, "${UUID.randomUUID()}.jpg")
        File(sourcePath).copyTo(destination, overwrite = true)
        destination.absolutePath
    }

    suspend fun delete(path: String?) = withContext(Dispatchers.IO) {
        if (path.isNullOrBlank()) return@withContext
        val file = File(path)
        if (file.exists() && file.parentFile == coversDir) {
            file.delete()
        }
    }

    /** All cover files currently on disk, for the backup archive to include. */
    suspend fun listAll(): List<File> = withContext(Dispatchers.IO) {
        coversDir.listFiles()?.toList() ?: emptyList()
    }

    /** Writes [bytes] under [fileName] as-is, for restoring a cover from a backup archive. */
    suspend fun writeBytes(fileName: String, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val destination = File(coversDir, fileName)
        destination.writeBytes(bytes)
        destination.absolutePath
    }

    /** Wipes every stored cover, used before a restore overwrites the library. */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        coversDir.listFiles()?.forEach { it.delete() }
    }
}
