package com.marcogn.thepatientgamerhelper.data.thegamesdb

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_NAME = "thegamesdb_prefs"
private const val KEY_API_KEY = "api_key"

/**
 * Runtime-editable TheGamesDB API key (Tappa 2): entered by the user in Impostazioni, not baked
 * into the build. TheGamesDB requires a key for every request since their 2026-02-17 policy
 * change (verified against the forum/changelog at implementation time — no more anonymous
 * access), so unlike Drive's build-time placeholder (Fase 4), there is nothing to fall back to
 * at build time here — the field starts empty and the search feature stays disabled until the
 * user fills it in. Plain SharedPreferences, same "no DataStore for one simple value" reasoning
 * as `BackupPreferences`.
 */
@Singleton
class TheGamesDbPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var apiKey: String?
        get() = prefs.getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit {
            if (value.isNullOrBlank()) remove(KEY_API_KEY) else putString(KEY_API_KEY, value.trim())
        }
}
