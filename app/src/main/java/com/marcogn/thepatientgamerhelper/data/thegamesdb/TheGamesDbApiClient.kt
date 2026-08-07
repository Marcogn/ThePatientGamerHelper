package com.marcogn.thepatientgamerhelper.data.thegamesdb

import com.marcogn.thepatientgamerhelper.domain.model.GameMetadataSearchResult
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

private const val BASE_URL = "https://api.thegamesdb.net/v1"
private const val USER_AGENT = "ThePatientGamerHelper/1.0 (Android; +https://github.com/Marcogn/GameReviewer)"
private const val CONNECT_TIMEOUT_MS = 10_000
private const val READ_TIMEOUT_MS = 15_000

private val theGamesDbJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

/**
 * Hand-rolled TheGamesDB REST v1 client, same `HttpURLConnection` + kotlinx.serialization
 * pattern as `DriveApiClient` (Fase 4) — no Retrofit/Ktor dependency added: this covers four GET
 * endpoints (search + three id→name lookups), not enough to justify a full HTTP client stack,
 * consistent with CLAUDE.md's dependency-minimalism.
 *
 * Every endpoint requires an `apikey` query param: TheGamesDB dropped anonymous/public access in
 * a 2026-02-17 policy change, verified against their forum/changelog while implementing this —
 * see `TheGamesDbPreferences` for where the (user-supplied, runtime-editable) key comes from.
 */
@Singleton
class TheGamesDbApiClient @Inject constructor() {

    private val cacheMutex = Mutex()
    private var platformsCache: Map<Long, String>? = null
    private var genresCache: Map<Long, String>? = null
    private var developersCache: Map<Long, String>? = null

    /** Searches by title, optionally narrowed to the platform whose name best matches [platformHint]. */
    suspend fun search(apiKey: String, title: String, platformHint: String?): List<GameMetadataSearchResult> =
        withContext(Dispatchers.IO) {
            val platformNamesById = platforms(apiKey)
            val genreNamesById = genres(apiKey)
            val developerNamesById = developers(apiKey)
            val platformId = platformHint?.takeIf { it.isNotBlank() }?.let { hint -> bestPlatformMatch(platformNamesById, hint) }

            val query = buildString {
                append(BASE_URL).append("/Games/ByGameName")
                append("?apikey=").append(apiKey.urlEncode())
                append("&name=").append(title.urlEncode())
                // "platform" is always present on the base game object (not a requestable field);
                // every independent source for the "fields" param lists it without "platform".
                append("&fields=").append("overview,genres,developers,release_date".urlEncode())
                append("&include=").append("boxart".urlEncode())
                // TheGamesDB uses PHP/Laravel-style indexed array query params for filters.
                if (platformId != null) append("&filter%5Bplatform%5D%5B0%5D=").append(platformId)
            }

            val connection = openConnection(query)
            val body = connection.readTextBody()
            connection.ensureSuccessful(body)

            val root = theGamesDbJson.parseToJsonElement(body).jsonObject
            val games = root["data"]?.jsonObject?.get("games")
                ?.let { theGamesDbJson.decodeFromJsonElement<List<GameDto>>(it) }
                .orEmpty()

            val boxart = root["include"]?.jsonObject?.get("boxart")?.jsonObject
            val boxartBaseUrl = boxart?.get("base_url")?.jsonObject?.get("original")?.jsonPrimitive?.contentOrNull
            val boxartData = boxart?.get("data")?.jsonObject

            games.map { game ->
                game.toDomain(
                    platformNamesById = platformNamesById,
                    genreNamesById = genreNamesById,
                    developerNamesById = developerNamesById,
                    boxartBaseUrl = boxartBaseUrl,
                    boxartData = boxartData,
                )
            }
        }

    suspend fun downloadCoverBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", USER_AGENT)
        }
        check(connection.responseCode in 200..299) { "Download copertina non riuscito (HTTP ${connection.responseCode})" }
        connection.inputStream.use { it.readBytes() }
    }

    private suspend fun platforms(apiKey: String): Map<Long, String> = cacheMutex.withLock {
        platformsCache ?: fetchLookup(apiKey, "Platforms", "platforms").also { platformsCache = it }
    }

    private suspend fun genres(apiKey: String): Map<Long, String> = cacheMutex.withLock {
        genresCache ?: fetchLookup(apiKey, "Genres", "genres").also { genresCache = it }
    }

    private suspend fun developers(apiKey: String): Map<Long, String> = cacheMutex.withLock {
        developersCache ?: fetchLookup(apiKey, "Developers", "developers").also { developersCache = it }
    }

    /** Fetches an id→name lookup table (Platforms/Genres/Developers share the same `{data: {<key>: {id, name}}}` shape). */
    private fun fetchLookup(apiKey: String, path: String, dataKey: String): Map<Long, String> {
        val connection = openConnection("$BASE_URL/$path?apikey=${apiKey.urlEncode()}")
        val body = connection.readTextBody()
        connection.ensureSuccessful(body)

        val entries = theGamesDbJson.parseToJsonElement(body).jsonObject["data"]?.jsonObject?.get(dataKey)?.jsonObject
            ?: return emptyMap()
        return entries.entries.mapNotNull { (_, value) ->
            val obj = value.jsonObject
            val id = obj["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            id to name
        }.toMap()
    }

    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
}

/**
 * TheGamesDB returns `null` (not a missing key) for `genres`/`developers` on games that have
 * none catalogued — a default value only covers a missing key, not an explicit JSON `null`, so
 * these must be nullable or `Json.decodeFromString` throws `SerializationException` on every such
 * game (this was breaking every search that happened to include one, platform filter or not).
 */
@Serializable
private data class GameDto(
    val id: Long,
    @SerialName("game_title") val title: String,
    val platform: Long? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val genres: List<Long>? = null,
    val developers: List<Long>? = null,
)

private fun GameDto.toDomain(
    platformNamesById: Map<Long, String>,
    genreNamesById: Map<Long, String>,
    developerNamesById: Map<Long, String>,
    boxartBaseUrl: String?,
    boxartData: JsonObject?,
): GameMetadataSearchResult {
    val images = boxartData?.get(id.toString())?.jsonArray
    val filename = images?.firstOrNull { it.jsonObject["side"]?.jsonPrimitive?.contentOrNull == "front" }
        ?.jsonObject?.get("filename")?.jsonPrimitive?.contentOrNull
        ?: images?.firstOrNull()?.jsonObject?.get("filename")?.jsonPrimitive?.contentOrNull

    return GameMetadataSearchResult(
        externalId = id,
        title = title,
        platformName = platform?.let { platformNamesById[it] },
        releaseYear = releaseDate?.take(4)?.toIntOrNull(),
        genreNames = genres.orEmpty().mapNotNull { genreId -> genreNamesById[genreId] },
        developerName = developers.orEmpty().firstOrNull()?.let { developerNamesById[it] },
        coverImageUrl = if (boxartBaseUrl != null && filename != null) boxartBaseUrl + filename else null,
    )
}

private fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8")

private fun HttpURLConnection.readTextBody(): String =
    (if (responseCode in 200..299) inputStream else errorStream)
        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""

private fun HttpURLConnection.ensureSuccessful(body: String) {
    if (responseCode !in 200..299) {
        val excerpt = body.trim().take(300)
        error("HTTP $responseCode @ $url${if (excerpt.isNotEmpty()) ": $excerpt" else ""}")
    }
}

private fun bestPlatformMatch(platforms: Map<Long, String>, hint: String): Long? =
    platforms.entries.firstOrNull { it.value.equals(hint, ignoreCase = true) }?.key
        ?: platforms.entries.firstOrNull { it.value.contains(hint, ignoreCase = true) || hint.contains(it.value, ignoreCase = true) }?.key
