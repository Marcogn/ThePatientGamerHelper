package com.marcogn.gamereviewer.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
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
 * necessario. [context] serve per richiedere un `recreate()` esplicito dell'activity corrente,
 * ma **solo sotto API 33**: da Android 13 in su `setApplicationLocales()` è un vero cambio di
 * configurazione gestito dal sistema, che ricrea da sé le activity in primo piano (vale anche per
 * `ComponentActivity`, non solo `AppCompatActivity`). Chiamare comunque `recreate()` anche lì
 * produce due ricreazioni in corsa sulla stessa activity — quella innescata dal sistema e quella
 * manuale — e nella pratica manda la UI in stallo (schermo bloccato, nessuna risposta al tocco).
 * Sotto API 33 invece il ricalcolo automatico non è garantito per un'activity che non estende
 * AppCompatActivity, quindi la `recreate()` esplicita resta necessaria.
 */
fun applyAppLanguage(context: Context, language: AppLanguage) {
    val locales = language.tag?.let(LocaleListCompat::forLanguageTags) ?: LocaleListCompat.getEmptyLocaleList()
    AppCompatDelegate.setApplicationLocales(locales)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        context.findActivity()?.recreate()
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
