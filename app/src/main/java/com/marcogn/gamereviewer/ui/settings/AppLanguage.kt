package com.marcogn.gamereviewer.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
 * necessario. [context] serve solo per richiedere un `recreate()` dell'activity corrente: senza
 * AppCompatActivity (qui usiamo ComponentActivity per Compose puro) il ricalcolo delle risorse
 * localizzate non è automatico su tutte le API prima della 33.
 */
fun applyAppLanguage(context: Context, language: AppLanguage) {
    val locales = language.tag?.let(LocaleListCompat::forLanguageTags) ?: LocaleListCompat.getEmptyLocaleList()
    AppCompatDelegate.setApplicationLocales(locales)
    context.findActivity()?.recreate()
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
