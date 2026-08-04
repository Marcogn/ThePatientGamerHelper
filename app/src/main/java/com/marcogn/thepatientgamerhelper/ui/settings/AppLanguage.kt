package com.marcogn.thepatientgamerhelper.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Lingua dell'app, gestita tramite le API AndroidX per-app language
 * (funzionano da API 26 grazie al backport, non solo da API 33+).
 * [tag] null significa "segui la lingua di sistema".
 */
enum class AppLanguage(val tag: String?) {
    SISTEMA(null),
    ITALIANO("it"),
    ENGLISH("en"),
}

fun currentAppLanguage(): AppLanguage {
    val currentTag = AppCompatDelegate.getApplicationLocales().toLanguageTags().takeIf { it.isNotEmpty() }
    return AppLanguage.entries.firstOrNull { it.tag == currentTag } ?: AppLanguage.SISTEMA
}

/**
 * autoStoreLocales (manifest) persiste automaticamente la scelta, nessuno storage custom
 * necessario. Nessuna `recreate()` manuale: `setApplicationLocales()` la innesca già da sé, ma
 * **solo se l'activity estende `AppCompatActivity`** (vedi `MainActivity`) — su una
 * `ComponentActivity` pura il cambio lingua verrebbe silenziosamente ignorato.
 */
fun applyAppLanguage(language: AppLanguage) {
    val locales = language.tag?.let(LocaleListCompat::forLanguageTags) ?: LocaleListCompat.getEmptyLocaleList()
    AppCompatDelegate.setApplicationLocales(locales)
}
