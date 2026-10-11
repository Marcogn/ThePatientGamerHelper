package com.marcogn.thepatientgamerhelper.data.update

import android.content.Context
import androidx.core.content.edit
import com.marcogn.thepatientgamerhelper.domain.update.DEFAULT_UPDATE_FREQUENCY
import com.marcogn.thepatientgamerhelper.domain.update.UpdateFrequency
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_NAME = "update_prefs"
private const val KEY_FREQUENCY = "frequency"
private const val KEY_LAST_CHECK_AT = "last_check_at"

/** Update check settings — plain SharedPreferences like `BackupPreferences`, two values don't need DataStore. */
@Singleton
class UpdatePreferences @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Stored by enum name; an unknown name (renamed constant, hand-edited file) falls back to the default. */
    var frequency: UpdateFrequency
        get() = prefs.getString(KEY_FREQUENCY, null)
            ?.let { name -> UpdateFrequency.entries.firstOrNull { it.name == name } }
            ?: DEFAULT_UPDATE_FREQUENCY
        set(value) = prefs.edit { putString(KEY_FREQUENCY, value.name) }

    /** Last check that got an answer from GitHub (update or not); `null` = never. */
    var lastCheckAt: Instant?
        get() = prefs.getLong(KEY_LAST_CHECK_AT, -1L).takeIf { it >= 0 }?.let(Instant::ofEpochMilli)
        set(value) = prefs.edit {
            if (value != null) putLong(KEY_LAST_CHECK_AT, value.toEpochMilli()) else remove(KEY_LAST_CHECK_AT)
        }
}
