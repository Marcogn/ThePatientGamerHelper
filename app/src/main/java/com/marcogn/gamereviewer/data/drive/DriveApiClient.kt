package com.marcogn.gamereviewer.data.drive

import com.marcogn.gamereviewer.domain.model.BackupFile
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val FILES_URL = "https://www.googleapis.com/drive/v3/files"
private const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"

/** appDataFolder: a private per-app space, invisible in the Drive UI and not shareable. */
private const val APP_DATA_FOLDER = "appDataFolder"
private const val LIST_FIELDS = "files(id,name,createdTime,size)"
private const val UPLOAD_FIELDS = "id,name,createdTime,size"

private val driveJson = Json { ignoreUnknownKeys = true }

/**
 * Minimal hand-rolled Drive REST API v3 client (appDataFolder scope only) built on
 * `HttpURLConnection` — no Google API Java client dependency (it pulls Guava and a sizeable
 * transitive graph for what's, here, three endpoints). See CLAUDE.md Fase 4 notes for the
 * tradeoff. Every call requires an OAuth access token with the `drive.appdata` scope, obtained
 * via [com.marcogn.gamereviewer.data.drive.DriveAuthManager].
 */
@Singleton
class DriveApiClient @Inject constructor() {

    suspend fun uploadBackup(accessToken: String, fileName: String, content: ByteArray): BackupFile =
        withContext(Dispatchers.IO) {
            val boundary = "gamereviewer-${UUID.randomUUID()}"
            val metadataJson = driveJson.encodeToString(DriveFileMetadataDto(name = fileName, parents = listOf(APP_DATA_FOLDER)))
            val body = ByteArrayOutputStream().apply {
                write("--$boundary\r\n".toByteArray(Charsets.UTF_8))
                write("Content-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray(Charsets.UTF_8))
                write(metadataJson.toByteArray(Charsets.UTF_8))
                write("\r\n--$boundary\r\n".toByteArray(Charsets.UTF_8))
                write("Content-Type: application/zip\r\n\r\n".toByteArray(Charsets.UTF_8))
                write(content)
                write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))
            }.toByteArray()

            val connection = openConnection(
                url = "$UPLOAD_URL?uploadType=multipart&fields=${UPLOAD_FIELDS.urlEncode()}",
                method = "POST",
                accessToken = accessToken,
            )
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            connection.outputStream.use { it.write(body) }

            val responseBody = connection.readTextBody()
            connection.ensureSuccessful(responseBody)
            driveJson.decodeFromString<DriveFileDto>(responseBody).toDomain()
        }

    suspend fun listBackups(accessToken: String): List<BackupFile> = withContext(Dispatchers.IO) {
        val query = "spaces=$APP_DATA_FOLDER&fields=${LIST_FIELDS.urlEncode()}" +
            "&orderBy=${"createdTime desc".urlEncode()}&pageSize=100"
        val connection = openConnection(url = "$FILES_URL?$query", method = "GET", accessToken = accessToken)

        val responseBody = connection.readTextBody()
        connection.ensureSuccessful(responseBody)
        driveJson.decodeFromString<DriveFileListDto>(responseBody).files.map { it.toDomain() }
    }

    suspend fun downloadBackup(accessToken: String, fileId: String): ByteArray = withContext(Dispatchers.IO) {
        val connection = openConnection(url = "$FILES_URL/$fileId?alt=media", method = "GET", accessToken = accessToken)

        if (connection.responseCode !in 200..299) {
            connection.ensureSuccessful(connection.readTextBody())
        }
        connection.inputStream.use { it.readBytes() }
    }

    private fun openConnection(url: String, method: String, accessToken: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", "Bearer $accessToken")
        }
}

private fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8")

private fun HttpURLConnection.readTextBody(): String =
    (if (responseCode in 200..299) inputStream else errorStream)
        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""

private fun HttpURLConnection.ensureSuccessful(body: String) {
    if (responseCode !in 200..299) {
        error("Richiesta Drive non riuscita (HTTP $responseCode): $body")
    }
}
