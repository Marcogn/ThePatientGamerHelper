package com.marcogn.thepatientgamerhelper.data.backup

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_NAME = "backup_prefs"
private const val KEY_AUTO_ENABLED = "auto_backup_enabled"
private const val KEY_LAST_AT = "last_backup_at"
private const val KEY_LAST_ERROR = "last_backup_error"

/** Small persisted state for the backup feature — no need for DataStore for three flags. */
@Singleton
class BackupPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var autoBackupEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_ENABLED, value) }

    var lastBackupAt: Instant?
        get() = prefs.getLong(KEY_LAST_AT, -1L).takeIf { it >= 0 }?.let(Instant::ofEpochMilli)
        set(value) = prefs.edit {
            if (value != null) putLong(KEY_LAST_AT, value.toEpochMilli()) else remove(KEY_LAST_AT)
        }

    var lastBackupError: String?
        get() = prefs.getString(KEY_LAST_ERROR, null)
        set(value) = prefs.edit {
            if (value != null) putString(KEY_LAST_ERROR, value) else remove(KEY_LAST_ERROR)
        }
}
