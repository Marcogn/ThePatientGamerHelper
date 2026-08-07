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
private const val MAX_REDIRECTS = 5
private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
// Ported from ScrappyCocco/HowLongToBeat-PythonAPI's HTMLRequests.py (actively maintained, checked
// as recently as mid-2026): requiring `method: "POST"` in the same fetch(...) call is the part a
// looser regex (this client's previous version) is missing — see the call site for why that matters.
private val SEARCH_ENDPOINT_REGEX =
    Regex("""fetch\s*\(\s*["'](/api/[a-zA-Z0-9_/]+)[^"']*["']\s*,\s*\{[^}]*method:\s*["']POST["'][^}]*\}""")

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
        val headers = buildMap {
            put("Content-Type", "application/json")
            put("Referer", "$BASE_URL/")
            put("Origin", BASE_URL)
            auth.token?.let { put("x-auth-token", it) }
            auth.hpKey?.let { put("x-hp-key", it) }
            auth.hpVal?.let { put("x-hp-val", it) }
        }

        val connection = request("$BASE_URL${auth.searchPath}", method = "POST", body = body, headers = headers)
        val responseText = connection.readTextBody()
        try {
            connection.ensureSuccessful(responseText)
        } catch (e: IllegalStateException) {
            // Which of the two paths produced the failing URL matters: "fallback" means the
            // historically-stable default itself is now stale (needs fresh research, not a blind
            // retry); "bundle" means extraction found *a* path but it's wrong — possibly because the
            // regex grabbed an unrelated /api/... fetch() call out of the bundle, not the search one.
            throw IllegalStateException("${e.message} [origine percorso: ${auth.source}]", e)
        }
        parseBestMatch(responseText, title)
    }

    /** Resolves (and caches for the process lifetime) the current search endpoint + auth headers, falling back to the historically stable default path with no headers if anything about the extraction fails. */
    private suspend fun resolveAuth(): HltbAuth = mutex.withLock {
        cachedAuth?.let { return@withLock it }
        val resolved = runCatching { fetchAuth() }.getOrElse { throwable ->
            Log.w(LOG_TAG, "Endpoint/auth extraction failed, falling back to $FALLBACK_SEARCH_PATH with no auth headers", throwable)
            HltbAuth(searchPath = FALLBACK_SEARCH_PATH, token = null, hpKey = null, hpVal = null, source = "fallback (homepage/bundle fetch fallita)")
        }
        cachedAuth = resolved
        resolved
    }

    private fun fetchAuth(): HltbAuth {
        val homepage = request(BASE_URL, method = "GET").readTextBody()
        val scriptSrc = Regex("""/_next/static/[^"'\s]*?_app-[a-zA-Z0-9]+\.js""").find(homepage)?.value
        if (scriptSrc == null) {
            Log.w(LOG_TAG, "Could not find the _app-*.js bundle reference in the homepage HTML")
            return HltbAuth(searchPath = FALLBACK_SEARCH_PATH, token = null, hpKey = null, hpVal = null, source = "fallback (bundle _app-*.js non trovato)")
        }

        val script = request("$BASE_URL$scriptSrc", method = "GET").readTextBody()
        // Requiring `method: "POST"` inside the same fetch(...) call is load-bearing, not cosmetic:
        // without it this regex can (and, on a real device, did — see CLAUDE.md, Fase 8) latch onto
        // an unrelated GET fetch() elsewhere in the bundle, silently resolving to a nonexistent path
        // and 404ing the search on every title. Ported from the actively-maintained
        // ScrappyCocco/HowLongToBeat-PythonAPI (HTMLRequests.py), whose broader regex this is a
        // direct translation of — not a fresh guess.
        val rawPath = SEARCH_ENDPOINT_REGEX.find(script)?.groupValues?.get(1)
        val searchPath = (rawPath ?: FALLBACK_SEARCH_PATH).let { if (it.endsWith("/")) it else "$it/" }
        val source = if (rawPath == null) {
            Log.w(LOG_TAG, "Could not extract the search endpoint from the bundle, using fallback $FALLBACK_SEARCH_PATH")
            "fallback (nessun /api/... trovato nel bundle)"
        } else {
            "bundle ($searchPath)"
        }

        val initBody = runCatching { request("$BASE_URL${searchPath}init", method = "GET").readTextBody() }
            .onFailure { Log.w(LOG_TAG, "GET ${searchPath}init failed", it) }
            .getOrNull()
        val auth = initBody?.let(::extractAuthFields) ?: AuthFields(null, null, null)

        return HltbAuth(searchPath = searchPath, token = auth.token, hpKey = auth.hpKey, hpVal = auth.hpVal, source = source)
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

    /**
     * Opens a connection, manually following 3xx redirects (up to [MAX_REDIRECTS]) instead of
     * relying on `HttpURLConnection`'s built-in `followRedirects` — that default only reliably
     * follows GET redirects; it does **not** consistently follow redirects on POST requests, and
     * historically has gaps with 308 (Permanent Redirect) specifically. Confirmed as the actual
     * failure mode on a real device (every search failing with a bare "HTTP 308", no body) after
     * the previous fix still left HowLongToBeat silent — see CLAUDE.md, Fase 8. [body]/[headers]
     * (and the request method) are replayed unchanged against the redirect target, which is the
     * correct behavior for 307/308 and the safest choice for 301/302/303 too, since this client
     * always expects a JSON response either way.
     */
    private fun request(
        url: String,
        method: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): HttpURLConnection {
        var currentUrl = url
        repeat(MAX_REDIRECTS + 1) {
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json, text/html;q=0.8, */*;q=0.5")
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                setRequestProperty("User-Agent", USER_AGENT)
                headers.forEach { (key, value) -> setRequestProperty(key, value) }
                if (body != null) doOutput = true
            }
            if (body != null) {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }

            val location = if (connection.responseCode in REDIRECT_CODES) connection.getHeaderField("Location") else null
            if (location == null) return connection
            Log.w(LOG_TAG, "$method $currentUrl redirected (HTTP ${connection.responseCode}) to $location")
            currentUrl = URL(URL(currentUrl), location).toString()
        }
        error("Troppi redirect (>$MAX_REDIRECTS) per $url, ultimo: $currentUrl")
    }
}

private data class HltbAuth(val searchPath: String, val token: String?, val hpKey: String?, val hpVal: String?, val source: String)
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
        error("HTTP $responseCode @ $url${if (excerpt.isNotEmpty()) ": $excerpt" else ""}")
    }
}
