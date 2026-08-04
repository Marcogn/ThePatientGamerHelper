package com.marcogn.gamereviewer.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.marcogn.gamereviewer.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_prefs")
private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")

/** Preferenza di tema persistita con Preferences DataStore, osservabile come [Flow]. */
@Singleton
class ThemePreferences @Inject constructor(@ApplicationContext private val context: Context) {

    val themeMode: Flow<ThemeMode> = context.themeDataStore.data.map { preferences ->
        preferences[THEME_MODE_KEY]?.let { stored ->
            runCatching { ThemeMode.valueOf(stored) }.getOrNull()
        } ?: ThemeMode.SISTEMA
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.themeDataStore.edit { preferences -> preferences[THEME_MODE_KEY] = mode.name }
    }
}
