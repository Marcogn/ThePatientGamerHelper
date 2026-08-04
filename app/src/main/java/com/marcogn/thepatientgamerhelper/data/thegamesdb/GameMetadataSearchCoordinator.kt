package com.marcogn.thepatientgamerhelper.data.thegamesdb

import android.content.Context
import android.util.Log
import com.marcogn.thepatientgamerhelper.R
import com.marcogn.thepatientgamerhelper.data.image.ImageStorage
import com.marcogn.thepatientgamerhelper.domain.model.GameMetadataSearchResult
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
 * real-device failure impossible to diagnose — see docs/decisioni-implementazione.md.
 */
class GameMetadataSearchCoordinator @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val apiClient: TheGamesDbApiClient,
    private val preferences: TheGamesDbPreferences,
    private val imageStorage: ImageStorage,
) {
    sealed interface Outcome {
        data class Results(val results: List<GameMetadataSearchResult>) : Outcome
        data class Message(val text: String) : Outcome
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
}
