package com.marcogn.thepatientgamerhelper.data.howlongtobeat

import com.marcogn.thepatientgamerhelper.domain.model.HowLongToBeatEstimate
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

private const val BASE_URL = "https://howlongtobeat.com"
private const val FALLBACK_SEARCH_PATH = "/api/s/"
private const val USER_AGENT = "Mozilla/5.0 (Android) ThePatientGamerHelper/1.0"
private const val CONNECT_TIMEOUT_MS = 8_000
private const val READ_TIMEOUT_MS = 12_000

private val hltbJson = Json { ignoreUnknownKeys = true }

/**
 * Hand-rolled, best-effort client for HowLongToBeat's *unofficial* search endpoint (Fase 8) — same
 * `HttpURLConnection` pattern as `TheGamesDbApiClient`/`DriveApiClient`, no HTTP library added.
 *
 * Unlike TheGamesDB, HowLongToBeat has **no public API at all** (verified before implementing,
 * same "check first" rule CLAUDE.md already applied to the TheGamesDB apikey policy change): every
 * known integration (howlongtobeatpy, ckatzorke/howlongtobeat, etc.) works by re-deriving the
 * current search endpoint from HowLongToBeat's own frontend JS bundle at runtime, because the path
 * changes across their deploys. This client does the same: fetch the homepage, find the Next.js
 * `_app-*.js` bundle, regex the POST endpoint out of it, then call it.
 *
 * **This is inherently fragile** — a reverse-engineered technique against an undocumented,
 * unversioned target, not a stable contract. It could not be exercised against the real
 * `howlongtobeat.com` from this sandbox (no network access, see CLAUDE.md's sandbox limitation
 * note), so treat it as unverified until checked on a real device. Every failure mode (HTML/JS
 * shape changed, endpoint blocked, response schema changed) is swallowed by the caller
 * ([com.marcogn.thepatientgamerhelper.data.thegamesdb.GameMetadataSearchCoordinator.searchHowLongToBeat])
 * and simply yields no estimate — this integration must never block or crash the "cerca online"
 * flow it rides on.
 */
@Singleton
class HowLongToBeatApiClient @Inject constructor() {

    private val mutex = Mutex()
    private var cachedSearchPath: String? = null

    suspend fun search(title: String): HowLongToBeatEstimate? = withContext(Dispatchers.IO) {
        val path = resolveSearchPath()
        val body = """{"searchType":"games","searchTerms":${title.trim().split(Regex("\\s+")).toJsonStringArray()},""" +
            """"searchPage":1,"size":20,"searchOptions":{"games":{"userId":0,"platform":"","sortCategory":"popular",""" +
            """"rangeCategory":"main","modifier":""},"users":{"sortCategory":"postcount"},"filter":"","sort":0,""" +
            """"randomizer":0}}"""

        val connection = openConnection("$BASE_URL$path", method = "POST")
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Referer", "$BASE_URL/")
        connection.setRequestProperty("Origin", BASE_URL)
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

        val responseText = connection.readTextBody()
        connection.ensureSuccessful(responseText)
        parseBestMatch(responseText, title)
    }

    /** Resolves (and caches for the process lifetime) the current search endpoint, falling back to the historically stable default path if anything about the extraction fails. */
    private suspend fun resolveSearchPath(): String = mutex.withLock {
        cachedSearchPath?.let { return@withLock it }
        val resolved = runCatching { extractSearchPathFromFrontend() }.getOrNull() ?: FALLBACK_SEARCH_PATH
        cachedSearchPath = resolved
        resolved
    }

    private fun extractSearchPathFromFrontend(): String? {
        val homepage = openConnection(BASE_URL, method = "GET").readTextBody()
        val scriptSrc = Regex("""/_next/static/[^"'\s]*?_app-[a-zA-Z0-9]+\.js""").find(homepage)?.value ?: return null
        val script = openConnection("$BASE_URL$scriptSrc", method = "GET").readTextBody()
        val endpoint = Regex("""fetch\([^)]*?["'](/api/[a-zA-Z0-9_/]+)["']""").find(script)?.groupValues?.get(1)
        return endpoint
    }

    private fun parseBestMatch(responseText: String, title: String): HowLongToBeatEstimate? {
        val root = hltbJson.parseToJsonElement(responseText).jsonObject
        val games = root["data"]?.jsonArray ?: return null
        if (games.isEmpty()) return null

        val entries = games.map { hltbJson.decodeFromJsonElement<HltbGameDto>(it) }
        val best = entries.firstOrNull { it.name.equals(title, ignoreCase = true) } ?: entries.first()

        val estimate = HowLongToBeatEstimate(
            mainStoryHours = best.mainStorySeconds.toHoursOrNull(),
            mainExtraHours = best.mainExtraSeconds.toHoursOrNull(),
            completionistHours = best.completionistSeconds.toHoursOrNull(),
        )
        return estimate.takeUnless { it.isEmpty }
    }

    private fun openConnection(url: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json, text/html;q=0.8, */*;q=0.5")
            setRequestProperty("User-Agent", USER_AGENT)
        }
}

@Serializable
private data class HltbGameDto(
    @SerialName("game_name") val name: String = "",
    @SerialName("comp_main") val mainStorySeconds: Long? = null,
    @SerialName("comp_plus") val mainExtraSeconds: Long? = null,
    @SerialName("comp_100") val completionistSeconds: Long? = null,
)

/** HowLongToBeat represents "not enough submissions" as 0, same as "unknown" — both map to null here. */
private fun Long?.toHoursOrNull(): Double? =
    this?.takeIf { it > 0 }?.let { seconds -> Math.round(seconds / 3600.0 * 10) / 10.0 }

private fun List<String>.toJsonStringArray(): String =
    joinToString(prefix = "[", postfix = "]") { term -> "\"" + term.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" }

private fun HttpURLConnection.readTextBody(): String =
    (if (responseCode in 200..299) inputStream else errorStream)
        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""

private fun HttpURLConnection.ensureSuccessful(body: String) {
    if (responseCode !in 200..299) {
        val excerpt = body.trim().take(300)
        error("HTTP $responseCode${if (excerpt.isNotEmpty()) ": $excerpt" else ""}")
    }
}
