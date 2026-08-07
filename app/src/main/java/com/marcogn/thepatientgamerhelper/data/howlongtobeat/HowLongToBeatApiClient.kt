package com.marcogn.thepatientgamerhelper.data.howlongtobeat

import android.util.Log
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

private const val LOG_TAG = "HowLongToBeatClient"
private const val BASE_URL = "https://howlongtobeat.com"
private const val FALLBACK_SEARCH_PATH = "/api/s/"
// A realistic desktop Chrome UA, not an app-identifying one: HowLongToBeat's frontend is behind
// bot-detection that's known to reject obviously non-browser User-Agent strings outright.
private const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
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
 * current search endpoint from HowLongToBeat's own frontend JS bundle at runtime, because both the
 * path and the anti-scraping requirements change across their deploys. As of the most recent
 * technique documented by actively-maintained wrappers (ScrappyCocco/HowLongToBeat-PythonAPI), a
 * bare POST to the search endpoint is no longer enough — the response also needs a handful of
 * headers (`x-auth-token`/`x-hp-key`/`x-hp-val`) obtained from a `GET <searchPath>/init` call made
 * right before the search. This client implements that full flow:
 *  1. GET the homepage, find the Next.js `_app-*.js` bundle reference.
 *  2. GET that bundle, regex out the `/api/...` path used by the frontend's own search `fetch()`.
 *  3. GET `<path>init` and pull the token/key/val fields out of whatever JSON it returns.
 *  4. POST the actual search to `<path>` with those headers attached.
 *
 * **This is inherently fragile** — a reverse-engineered technique against an undocumented,
 * unversioned target, not a stable contract. It could not be exercised against the real
 * `howlongtobeat.com` from this sandbox (no network access, see CLAUDE.md's sandbox limitation
 * note), so treat it as unverified until checked on a real device. Every failure mode (HTML/JS
 * shape changed, endpoint blocked, response schema changed, anti-bot challenge) is swallowed by the
 * caller
 * ([com.marcogn.thepatientgamerhelper.data.thegamesdb.GameMetadataSearchCoordinator.searchHowLongToBeat])
 * and simply yields no estimate — this integration must never block or crash the "cerca online"
 * flow it rides on. Every step logs a warning with `LOG_TAG` on failure, since a fully silent
 * failure gave no way to diagnose why estimates never showed up (same lesson as the TheGamesDB
 * "generic message swallowed the real error" fix in Fase 7) — check `adb logcat -s HowLongToBeatClient`
 * if estimates still don't appear after this fix.
 */
@Singleton
class HowLongToBeatApiClient @Inject constructor() {

    private val mutex = Mutex()
    private var cachedAuth: HltbAuth? = null

    suspend fun search(title: String): HowLongToBeatEstimate? = withContext(Dispatchers.IO) {
        val auth = resolveAuth()
        val body = buildSearchBody(title)

        val connection = openConnection("$BASE_URL${auth.searchPath}", method = "POST")
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Referer", "$BASE_URL/")
        connection.setRequestProperty("Origin", BASE_URL)
        auth.token?.let { connection.setRequestProperty("x-auth-token", it) }
        auth.hpKey?.let { connection.setRequestProperty("x-hp-key", it) }
        auth.hpVal?.let { connection.setRequestProperty("x-hp-val", it) }
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

        val responseText = connection.readTextBody()
        connection.ensureSuccessful(responseText)
        parseBestMatch(responseText, title)
    }

    /** Resolves (and caches for the process lifetime) the current search endpoint + auth headers, falling back to the historically stable default path with no headers if anything about the extraction fails. */
    private suspend fun resolveAuth(): HltbAuth = mutex.withLock {
        cachedAuth?.let { return@withLock it }
        val resolved = runCatching { fetchAuth() }.getOrElse { throwable ->
            Log.w(LOG_TAG, "Endpoint/auth extraction failed, falling back to $FALLBACK_SEARCH_PATH with no auth headers", throwable)
            HltbAuth(searchPath = FALLBACK_SEARCH_PATH, token = null, hpKey = null, hpVal = null)
        }
        cachedAuth = resolved
        resolved
    }

    private fun fetchAuth(): HltbAuth {
        val homepage = openConnection(BASE_URL, method = "GET").readTextBody()
        val scriptSrc = Regex("""/_next/static/[^"'\s]*?_app-[a-zA-Z0-9]+\.js""").find(homepage)?.value
        if (scriptSrc == null) {
            Log.w(LOG_TAG, "Could not find the _app-*.js bundle reference in the homepage HTML")
            return HltbAuth(searchPath = FALLBACK_SEARCH_PATH, token = null, hpKey = null, hpVal = null)
        }

        val script = openConnection("$BASE_URL$scriptSrc", method = "GET").readTextBody()
        val rawPath = Regex("""fetch\([^)]*?["'](/api/[a-zA-Z0-9_/]+)["']""").find(script)?.groupValues?.get(1)
        val searchPath = (rawPath ?: FALLBACK_SEARCH_PATH).let { if (it.endsWith("/")) it else "$it/" }
        if (rawPath == null) Log.w(LOG_TAG, "Could not extract the search endpoint from the bundle, using fallback $FALLBACK_SEARCH_PATH")

        val initBody = runCatching { openConnection("$BASE_URL${searchPath}init", method = "GET").readTextBody() }
            .onFailure { Log.w(LOG_TAG, "GET ${searchPath}init failed", it) }
            .getOrNull()
        val auth = initBody?.let(::extractAuthFields) ?: AuthFields(null, null, null)

        return HltbAuth(searchPath = searchPath, token = auth.token, hpKey = auth.hpKey, hpVal = auth.hpVal)
    }

    private fun extractAuthFields(json: String): AuthFields = runCatching {
        val obj = hltbJson.parseToJsonElement(json).jsonObject
        var token: String? = null
        var hpKey: String? = null
        var hpVal: String? = null
        obj.forEach { (key, value) ->
            val text = (value as? JsonPrimitive)?.contentOrNull ?: return@forEach
            when {
                key.contains("token", ignoreCase = true) -> token = text
                key.contains("key", ignoreCase = true) -> hpKey = text
                key.contains("val", ignoreCase = true) -> hpVal = text
            }
        }
        AuthFields(token, hpKey, hpVal)
    }.getOrElse {
        Log.w(LOG_TAG, "Could not parse auth fields out of the init response", it)
        AuthFields(null, null, null)
    }

    private fun buildSearchBody(title: String): String =
        """{"searchType":"games","searchTerms":${title.trim().split(Regex("\\s+")).toJsonStringArray()},""" +
            """"searchPage":1,"size":20,"searchOptions":{"games":{"userId":0,"platform":"","sortCategory":"popular",""" +
            """"rangeCategory":"main","modifier":""},"users":{"sortCategory":"postcount"},"filter":"","sort":0,""" +
            """"randomizer":0}}"""

    private fun parseBestMatch(responseText: String, title: String): HowLongToBeatEstimate? {
        val root = hltbJson.parseToJsonElement(responseText).jsonObject
        val games = root["data"]?.jsonArray
        if (games.isNullOrEmpty()) {
            Log.w(LOG_TAG, "No \"data\" array (or it's empty) in the search response for \"$title\"")
            return null
        }

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
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            setRequestProperty("User-Agent", USER_AGENT)
        }
}

private data class HltbAuth(val searchPath: String, val token: String?, val hpKey: String?, val hpVal: String?)
private data class AuthFields(val token: String?, val hpKey: String?, val hpVal: String?)

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
