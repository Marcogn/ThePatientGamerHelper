package com.marcogn.thepatientgamerhelper.data.howlongtobeat

import android.util.Log
import com.marcogn.thepatientgamerhelper.domain.howlongtobeat.HltbMatcher
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
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private const val LOG_TAG = "HowLongToBeatClient"
private const val BASE_URL = "https://howlongtobeat.com"
private const val SEARCH_PATH = "/api/bleed"
private const val INIT_PATH = "$SEARCH_PATH/init"
// A realistic desktop Chrome UA, not an app-identifying one: HowLongToBeat's frontend is behind
// bot-detection that's known to reject obviously non-browser User-Agent strings outright.
private const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
private const val CONNECT_TIMEOUT_MS = 8_000
private const val READ_TIMEOUT_MS = 12_000
private const val MAX_REDIRECTS = 5
private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

private val hltbJson = Json { ignoreUnknownKeys = true }

/**
 * Hand-rolled, best-effort client for HowLongToBeat's *unofficial* search endpoint (Fase 8) — same
 * `HttpURLConnection` pattern as `TheGamesDbApiClient`/`DriveApiClient`, no HTTP library added.
 *
 * **Ported from GameNative's `HltbService`**
 * (https://github.com/utkarshdalal/GameNative/blob/master/app/src/main/java/app/gamenative/utils/HltbService.kt),
 * which the user pointed at as a confirmed-working reference after our previous approach kept
 * failing in the field. The previous version of this client re-derived the current search
 * endpoint at runtime by scanning HowLongToBeat's homepage `<script>` bundles for a regex match —
 * that path itself turned out to be the recurring source of breakage (a looser regex once matched
 * the wrong `fetch()` call entirely, and Turbopack renaming every chunk to an opaque hash made the
 * scan slower and less reliable each time HowLongToBeat redeployed). GameNative instead hits a
 * **fixed, hardcoded endpoint** (`/api/bleed` + `/api/bleed/init`) with no bundle-scraping at all —
 * still unofficial and undocumented (HowLongToBeat is known to rotate this path across deploys,
 * same as before), but it removes an entire failure-prone extraction step, and it's the
 * concretely-confirmed-working shape as of this port. Differences from GameNative's version, which
 * targets a different app's dependency stack:
 *  - `HttpURLConnection` instead of OkHttp (no new dependency; also means no `Protocol.HTTP_1_1`
 *    knob — `HttpURLConnection` never speaks HTTP/2 to begin with, so GameNative's explicit
 *    HTTP/1.1 pin for this endpoint has nothing to opt out of here).
 *  - Manual 3xx-redirect following (see [request]) is kept from this client's previous version —
 *    `HttpURLConnection`'s default `followRedirects` doesn't reliably follow POST redirects,
 *    confirmed as a real failure mode on a real device once already (CLAUDE.md, Fase 8).
 *  - No `HltbCache`/DataStore persistence layer — GameNative persists a 12h-TTL result cache to
 *    survive process death; this client keeps only the in-process auth-token cache the previous
 *    version already had (`cachedAuth`), matching the "in-memory only, process lifetime" pattern
 *    `GameMetadataSearchCoordinator`'s TheGamesDB lookup caches already use. Add persistence
 *    separately if repeated cold-start lookups turn out to matter in practice.
 *  - Best-match selection (Levenshtein distance + an acceptable-match heuristic, not just
 *    "exact title match or first result") is ported faithfully, but as pure, unit-tested logic in
 *    [HltbMatcher] rather than inline private functions — same "pure domain logic, Android I/O
 *    stays in data/" split this codebase already uses for `domain/filter`/`domain/stats`.
 *  - Auth-rejected retry: on HTTP 401/403 from the search call, the cached auth is dropped and
 *    a single fresh `/init` + retry is attempted, same as GameNative — a stale in-process token is
 *    the one case worth retrying automatically instead of just failing this lookup.
 *  - `HowLongToBeatEstimate` has no "all playstyles" field (GameNative's `allStylesHours`) — the
 *    domain model only ever tracked main story/main+extra/completionist (Fase 8), so `comp_all` is
 *    read from the response like the rest but isn't surfaced; adding a fourth field would need a
 *    Room migration on `backlog_items` for no currently-requested feature.
 *
 * Every failure mode still funnels into either a thrown, descriptive exception (HTTP failures —
 * see [ensureSuccessful]) or a plain `null` (no results / no acceptable match), never a crash: the
 * caller ([com.marcogn.thepatientgamerhelper.data.thegamesdb.GameMetadataSearchCoordinator.searchHowLongToBeat])
 * turns both into a message shown in the backlog form's status line, never blocking "cerca online".
 * Check `adb logcat -s HowLongToBeatClient` first if estimates go missing again.
 */
@Singleton
class HowLongToBeatApiClient @Inject constructor() {

    private val mutex = Mutex()
    private var cachedAuth: HltbAuth? = null

    suspend fun search(title: String): HowLongToBeatEstimate? = withContext(Dispatchers.IO) {
        val auth = resolveAuth()
        when (val first = attemptSearch(title, auth)) {
            is SearchOutcome.Found -> first.estimate
            SearchOutcome.NoMatch -> null
            is SearchOutcome.Failed -> failSearch(first.httpCode, first.excerpt)
            is SearchOutcome.AuthRejected -> {
                Log.w(LOG_TAG, "Search rejected (HTTP ${first.httpCode}) for \"$title\", refreshing auth and retrying once")
                mutex.withLock { cachedAuth = null }
                val refreshedAuth = resolveAuth()
                when (val second = attemptSearch(title, refreshedAuth)) {
                    is SearchOutcome.Found -> second.estimate
                    SearchOutcome.NoMatch -> null
                    is SearchOutcome.Failed -> failSearch(second.httpCode, second.excerpt)
                    is SearchOutcome.AuthRejected -> failSearch(second.httpCode, "auth ancora rifiutata dopo refresh")
                }
            }
        }
    }

    private fun failSearch(httpCode: Int, excerpt: String): Nothing =
        error("HTTP $httpCode @ $BASE_URL$SEARCH_PATH${if (excerpt.isNotEmpty()) ": $excerpt" else ""}")

    /** Resolves (and caches for the process lifetime) the auth token/headers from `/init`. */
    private suspend fun resolveAuth(): HltbAuth = mutex.withLock {
        cachedAuth?.let { return@withLock it }
        val fetched = fetchAuth()
        cachedAuth = fetched
        fetched
    }

    private fun fetchAuth(): HltbAuth {
        val url = "$BASE_URL$INIT_PATH?t=${System.currentTimeMillis()}"
        val connection = request(url, method = "GET")
        val body = connection.readTextBody()
        connection.ensureSuccessful(body)
        return parseAuth(body) ?: error("Campi di autenticazione mancanti nella risposta di $INIT_PATH")
    }

    /** Mirrors GameNative's `parseAuth`: the token field is fixed, but the key/value field names vary. */
    private fun parseAuth(body: String): HltbAuth? = runCatching {
        val obj = hltbJson.parseToJsonElement(body).jsonObject
        val token = (obj["token"] as? JsonPrimitive)?.contentOrNull
        var key: String? = null
        var value: String? = null
        obj.forEach { (field, element) ->
            val text = (element as? JsonPrimitive)?.contentOrNull
            if (text.isNullOrEmpty()) return@forEach
            val normalizedField = field.lowercase()
            if (key == null && normalizedField.contains("key")) key = text
            if (value == null && normalizedField.contains("val")) value = text
        }
        if (token.isNullOrEmpty() || key == null || value == null) null else HltbAuth(token, key!!, value!!)
    }.getOrElse {
        Log.w(LOG_TAG, "Could not parse auth fields out of the init response", it)
        null
    }

    private fun attemptSearch(title: String, auth: HltbAuth): SearchOutcome {
        val body = buildSearchBody(title, auth)
        val headers = mapOf(
            "Content-Type" to "application/json",
            "x-auth-token" to auth.token,
            "x-hp-key" to auth.hpKey,
            "x-hp-val" to auth.hpVal,
        )
        val connection = request("$BASE_URL$SEARCH_PATH", method = "POST", body = body, headers = headers)
        val responseText = connection.readTextBody()
        val httpCode = connection.responseCode
        if (httpCode == 401 || httpCode == 403) return SearchOutcome.AuthRejected(httpCode)
        if (httpCode !in 200..299) return SearchOutcome.Failed(httpCode, responseText.trim().take(300))
        val estimate = parseBestMatch(responseText, title) ?: return SearchOutcome.NoMatch
        return SearchOutcome.Found(estimate)
    }

    /**
     * Same request shape as GameNative's `buildSearchRequest`: `modifier: "hide_dlc"` and
     * `sortCategory: "name"` (rather than this client's previous `"popular"`) are part of the
     * confirmed-working request, not incidental — kept as-is rather than guessed at. [auth]'s
     * key/value pair is injected as an extra top-level property, keyed by the field name itself,
     * exactly like the `x-hp-key`/`x-hp-val` headers — omitting this was the actual bug that made
     * the previous client's searches silently return nothing even with headers correctly set.
     */
    private fun buildSearchBody(title: String, auth: HltbAuth): String {
        val searchTerms = HltbMatcher.normalize(title).split(" ").filter { it.isNotBlank() }
        val json = buildJsonObject {
            put("searchType", "games")
            putJsonArray("searchTerms") { searchTerms.forEach { add(it) } }
            put("searchPage", 1)
            put("size", 20)
            putJsonObject("searchOptions") {
                putJsonObject("games") {
                    put("userId", 0)
                    put("platform", "")
                    put("sortCategory", "name")
                    put("rangeCategory", "main")
                    put("modifier", "hide_dlc")
                    putJsonObject("rangeTime") {
                        put("min", 0)
                        put("max", 0)
                    }
                    putJsonObject("rangeYear") {
                        put("min", "")
                        put("max", "")
                    }
                    putJsonObject("gameplay") {
                        put("perspective", "")
                        put("flow", "")
                        put("genre", "")
                        put("difficulty", "")
                    }
                }
                putJsonObject("users") {}
                putJsonObject("lists") {}
                put("filter", "")
                put("sort", 0)
                put("randomizer", 0)
            }
            put(auth.hpKey, auth.hpVal)
        }
        return json.toString()
    }

    private fun parseBestMatch(responseText: String, title: String): HowLongToBeatEstimate? {
        val root = hltbJson.parseToJsonElement(responseText).jsonObject
        val games = root["data"]?.jsonArray
        if (games.isNullOrEmpty()) {
            Log.w(LOG_TAG, "No \"data\" array (or it's empty) in the search response for \"$title\"")
            return null
        }

        val entries = games.map { hltbJson.decodeFromJsonElement<HltbGameDto>(it) }
        val best = HltbMatcher.findBestMatch(title, entries) { it.name }
        if (best == null) {
            Log.w(LOG_TAG, "No acceptable HLTB match for \"$title\" among ${entries.size} result(s)")
            return null
        }

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
     * historically has gaps with 308 (Permanent Redirect) specifically. Confirmed as an actual
     * failure mode on a real device with this client's previous version (see CLAUDE.md, Fase 8).
     * [body]/[headers] (and the request method) are replayed unchanged against the redirect
     * target, correct for 307/308 and the safest choice for 301/302/303 too since this client
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
                setRequestProperty("Referer", "$BASE_URL/")
                setRequestProperty("Origin", BASE_URL)
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

private data class HltbAuth(val token: String, val hpKey: String, val hpVal: String)

private sealed interface SearchOutcome {
    data class Found(val estimate: HowLongToBeatEstimate) : SearchOutcome
    data object NoMatch : SearchOutcome
    data class AuthRejected(val httpCode: Int) : SearchOutcome
    data class Failed(val httpCode: Int, val excerpt: String) : SearchOutcome
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

private fun HttpURLConnection.readTextBody(): String =
    (if (responseCode in 200..299) inputStream else errorStream)
        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""

private fun HttpURLConnection.ensureSuccessful(body: String) {
    if (responseCode !in 200..299) {
        val excerpt = body.trim().take(300)
        error("HTTP $responseCode @ $url${if (excerpt.isNotEmpty()) ": $excerpt" else ""}")
    }
}
