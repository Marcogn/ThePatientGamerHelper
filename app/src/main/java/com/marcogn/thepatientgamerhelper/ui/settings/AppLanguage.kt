package com.marcogn.thepatientgamerhelper.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * App language, managed through the AndroidX per-app language APIs
 * (work from API 26 thanks to the backport, not just from API 33+).
 * A null [tag] means "follow the system language".
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
 * autoStoreLocales (manifest) persists the choice automatically, no custom storage needed. No
 * manual `recreate()`: `setApplicationLocales()` already triggers it on its own, but **only if
 * the activity extends `AppCompatActivity`** (see `MainActivity`) — on a plain `ComponentActivity`
 * the language change would be silently ignored.
 */
fun applyAppLanguage(language: AppLanguage) {
    val locales = language.tag?.let(LocaleListCompat::forLanguageTags) ?: LocaleListCompat.getEmptyLocaleList()
    AppCompatDelegate.setApplicationLocales(locales)
}
