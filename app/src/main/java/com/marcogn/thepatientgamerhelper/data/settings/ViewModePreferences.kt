package com.marcogn.thepatientgamerhelper.data.settings

import android.content.Context
import com.marcogn.thepatientgamerhelper.domain.model.ViewMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_NAME = "view_mode_prefs"
private const val KEY_LIBRARY = "library_view_mode"
private const val KEY_BACKLOG = "backlog_view_mode"

/**
 * Persists the list/grid choice (Fase 8) per screen. Plain `SharedPreferences`, same minimal
 * pattern as `BackupPreferences`/`TheGamesDbPreferences` — two flags don't need DataStore.
 */
@Singleton
class ViewModePreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var libraryViewMode: ViewMode
        get() = prefs.getString(KEY_LIBRARY, null).toViewMode()
        set(value) = prefs.edit().putString(KEY_LIBRARY, value.name).apply()

    var backlogViewMode: ViewMode
        get() = prefs.getString(KEY_BACKLOG, null).toViewMode()
        set(value) = prefs.edit().putString(KEY_BACKLOG, value.name).apply()

    private fun String?.toViewMode(): ViewMode = this?.let { runCatching { ViewMode.valueOf(it) }.getOrNull() } ?: ViewMode.LIST
}
