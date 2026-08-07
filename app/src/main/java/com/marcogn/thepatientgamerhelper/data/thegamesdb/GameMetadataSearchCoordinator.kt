package com.marcogn.thepatientgamerhelper.data.thegamesdb

import android.content.Context
import android.util.Log
import com.marcogn.thepatientgamerhelper.R
import com.marcogn.thepatientgamerhelper.data.howlongtobeat.HowLongToBeatApiClient
import com.marcogn.thepatientgamerhelper.data.image.ImageStorage
import com.marcogn.thepatientgamerhelper.domain.model.GameMetadataSearchResult
import com.marcogn.thepatientgamerhelper.domain.model.HowLongToBeatEstimate
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject

private const val LOG_TAG = "GameMetadataSearch"

/**
 * Shared "cerca online" logic (Tappa 2) used by both the review form and the backlog item form:
 * resolves the configured API key, calls [TheGamesDbApiClient], and turns every failure (missing
 * key, no network, no results) into a plain user-facing [Outcome.Message] instead of throwing —
 * per spec this feature always falls back silently to the existing manual flow, never crashes.
 *
 * The message includes the underlying exception's text (and it's also logged in full via
 * [Log.w]): a purely generic "search failed" with the real cause thrown away made the very first
 * real-device failure impossible to diagnose — see docs/implementation-decisions.md.
 */
class GameMetadataSearchCoordinator @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val apiClient: TheGamesDbApiClient,
    private val preferences: TheGamesDbPreferences,
    private val imageStorage: ImageStorage,
    private val howLongToBeatApiClient: HowLongToBeatApiClient,
) {
    sealed interface Outcome {
        data class Results(val results: List<GameMetadataSearchResult>) : Outcome
        data class Message(val text: String) : Outcome
    }

    /** Unlike [Outcome], this is surfaced to the UI even on success — see [searchHowLongToBeat]. */
    sealed interface HltbOutcome {
        data class Found(val estimate: HowLongToBeatEstimate) : HltbOutcome
        data object NotFound : HltbOutcome
        data class Error(val message: String) : HltbOutcome
    }

    suspend fun search(title: String, platformHint: String?): Outcome {
        val apiKey = preferences.apiKey
        if (apiKey.isNullOrBlank()) {
            return Outcome.Message(appContext.getString(R.string.game_search_missing_api_key))
        }
        return runCatching { apiClient.search(apiKey, title, platformHint) }.fold(
            onSuccess = { results ->
                if (results.isEmpty()) {
                    Outcome.Message(appContext.getString(R.string.game_search_no_results))
                } else {
                    Outcome.Results(results)
                }
            },
            onFailure = { throwable ->
                Log.w(LOG_TAG, "TheGamesDB search failed for \"$title\"", throwable)
                val detail = throwable.message?.takeIf { it.isNotBlank() }
                val base = appContext.getString(R.string.game_search_failed)
                Outcome.Message(if (detail != null) "$base\n$detail" else base)
            },
        )
    }

    /** Downloads and persists the cover locally; returns null silently if there's no cover or the download fails. */
    suspend fun downloadCoverLocally(result: GameMetadataSearchResult): String? {
        val url = result.coverImageUrl ?: return null
        return runCatching {
            imageStorage.writeBytes("${UUID.randomUUID()}.jpg", apiClient.downloadCoverBytes(url))
        }.getOrNull()
    }

    /**
     * Best-effort HowLongToBeat lookup (Fase 8), backlog-only (see `BacklogItemFormViewModel`) —
     * never blocks or crashes the "cerca online" flow it rides on top of a TheGamesDB pick. Unlike
     * [search] it never *surfaces a dialog*, but the [HltbOutcome] it returns is still shown to the
     * user as a small status line in the form (see `BacklogItemFormUiState.hltbMessage`): the first
     * cut of this integration failed completely on real devices and swallowed every error into a
     * `Log.w` the user had no way to read without `adb logcat` — same lesson as [search]'s own
     * "generic message swallowed the real error" fix, applied here too.
     */
    suspend fun searchHowLongToBeat(title: String): HltbOutcome =
        runCatching { howLongToBeatApiClient.search(title) }.fold(
            onSuccess = { estimate -> if (estimate != null) HltbOutcome.Found(estimate) else HltbOutcome.NotFound },
            onFailure = { throwable ->
                Log.w(LOG_TAG, "HowLongToBeat search failed for \"$title\"", throwable)
                HltbOutcome.Error(throwable.message?.takeIf { it.isNotBlank() } ?: throwable::class.simpleName ?: "?")
            },
        )
}
