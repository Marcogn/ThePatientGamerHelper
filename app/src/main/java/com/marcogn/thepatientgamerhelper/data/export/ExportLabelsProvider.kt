package com.marcogn.thepatientgamerhelper.data.export

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import com.marcogn.thepatientgamerhelper.R
import com.marcogn.thepatientgamerhelper.domain.export.ExportLabels
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds [ExportLabels] from the string resources of the language the user picked in the app (the
 * in-app selector, `AppCompatDelegate.setApplicationLocales`), so exported files follow the app
 * language like the screens do. Resolved per call, not cached: the language can change at any time.
 */
@Singleton
class ExportLabelsProvider @Inject constructor(@ApplicationContext private val context: Context) {

    fun current(): ExportLabels = context.toExportLabels()
}

/**
 * [ExportLabels] in the language of [this] context. When the user chose a language in the app, that
 * language is applied explicitly: below API 33 an application context does not follow
 * `setApplicationLocales` (only activities do), so a plain `getString` there could answer in the
 * system language instead.
 */
fun Context.toExportLabels(): ExportLabels {
    val appLocales = AppCompatDelegate.getApplicationLocales()
    val localized = if (appLocales.toLanguageTags().isEmpty()) {
        this
    } else {
        createConfigurationContext(Configuration(resources.configuration).apply { setLocale(appLocales.get(0)) })
    }
    return ExportLabels(
        csvColumns = listOf(
            R.string.export_csv_id, R.string.export_csv_title, R.string.export_csv_platforms, R.string.export_csv_genres,
            R.string.export_csv_tags, R.string.export_csv_score, R.string.export_csv_start_date, R.string.export_csv_end_date,
            R.string.export_csv_hours_played, R.string.export_csv_status, R.string.export_csv_pros, R.string.export_csv_cons,
            R.string.export_csv_review_text, R.string.export_csv_cover, R.string.export_csv_created_at, R.string.export_csv_updated_at,
        ).map(localized::getString),
        statusInProgress = localized.getString(R.string.export_status_in_progress),
        statusCompleted = localized.getString(R.string.export_status_completed),
        statusAbandoned = localized.getString(R.string.export_status_abandoned),
        pdfScore = localized.getString(R.string.export_pdf_score),
        pdfStatus = localized.getString(R.string.export_pdf_status),
        pdfPlatforms = localized.getString(R.string.export_pdf_platforms),
        pdfGenres = localized.getString(R.string.export_pdf_genres),
        pdfTags = localized.getString(R.string.export_pdf_tags),
        pdfStarted = localized.getString(R.string.export_pdf_started),
        pdfFinished = localized.getString(R.string.export_pdf_finished),
        pdfHoursPlayed = localized.getString(R.string.export_pdf_hours_played),
        pdfPros = localized.getString(R.string.export_pdf_pros),
        pdfCons = localized.getString(R.string.export_pdf_cons),
        pdfReview = localized.getString(R.string.export_pdf_review),
        fallbackReviewFileName = localized.getString(R.string.export_file_fallback_review),
        libraryFilePrefix = localized.getString(R.string.export_file_library_prefix),
        backlogFilePrefix = localized.getString(R.string.export_file_backlog_prefix),
    )
}
