package com.marcogn.thepatientgamerhelper.data.export

import com.marcogn.thepatientgamerhelper.domain.export.toBackupMarkdown
import com.marcogn.thepatientgamerhelper.domain.export.toFileSlug
import com.marcogn.thepatientgamerhelper.domain.model.Review
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val REVIEWS_PREFIX = "reviews/"
private const val IMAGES_PREFIX = "images/"

/**
 * Multi-review export archive (reviews/backlog import-export spec v2 §2.3): one front-matter `.md`
 * file per review (see `domain/export/ReviewBackupMarkdown.kt`) under `reviews/`, plus every
 * referenced cover under `images/` — same zip shape as `data/export/BacklogExportArchive.kt`. The
 * `images/` entry prefix is only ever written when [coverPathsByFileName] is non-empty, so a batch
 * with no covers at all produces a zip with no image folder in it (v2 §2.3's "only if at least one
 * exported review has a cover image" — satisfied by construction, a zip doesn't materialize empty
 * directory entries unless explicitly told to).
 */
@Singleton
class ReviewZipArchiveBuilder @Inject constructor() {
    suspend fun build(reviews: List<Review>, coverPathsByFileName: Map<String, String>): ByteArray =
        withContext(Dispatchers.IO) {
            val buffer = ByteArrayOutputStream()
            ZipOutputStream(buffer).use { zip ->
                val usedNames = mutableSetOf<String>()
                reviews.forEach { review ->
                    val fileName = uniqueFileName(review.title.toFileSlug(), usedNames)
                    zip.putNextEntry(ZipEntry("$REVIEWS_PREFIX$fileName.md"))
                    zip.write(review.toBackupMarkdown().toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }

                coverPathsByFileName.forEach { (fileName, absolutePath) ->
                    val file = File(absolutePath)
                    if (!file.exists()) return@forEach
                    zip.putNextEntry(ZipEntry("$IMAGES_PREFIX$fileName"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            buffer.toByteArray()
        }

    private fun uniqueFileName(base: String, usedNames: MutableSet<String>): String {
        var candidate = base
        var suffix = 2
        while (!usedNames.add(candidate)) {
            candidate = "$base-$suffix"
            suffix++
        }
        return candidate
    }
}

/** [reviewMarkdownFiles] keeps the zip entry name (e.g. `reviews/foo.md`) as the key, so validation failures can be reported per file name (v2 §2.4). */
data class ReviewZipArchiveContent(val reviewMarkdownFiles: Map<String, String>, val images: Map<String, ByteArray>)

@Singleton
class ReviewZipArchiveReader @Inject constructor() {
    suspend fun read(bytes: ByteArray): ReviewZipArchiveContent = withContext(Dispatchers.IO) {
        val markdownFiles = mutableMapOf<String, String>()
        val images = mutableMapOf<String, ByteArray>()

        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val content = zip.readBytes()
                when {
                    entry.name.endsWith(".md") -> markdownFiles[entry.name] = content.toString(Charsets.UTF_8)
                    entry.name.startsWith(IMAGES_PREFIX) -> images[entry.name.removePrefix(IMAGES_PREFIX)] = content
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        ReviewZipArchiveContent(reviewMarkdownFiles = markdownFiles, images = images)
    }
}
