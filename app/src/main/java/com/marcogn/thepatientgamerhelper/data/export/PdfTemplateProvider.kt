package com.marcogn.thepatientgamerhelper.data.export

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extension point for a future custom PDF layout (reviews/backlog import-export spec v2 §2.6):
 * [PdfReviewRenderer] checks [currentTemplate] before rendering. Today [NoOpPdfTemplateProvider] is
 * the only implementation and always returns null, so every render falls back to the existing
 * plain, unstyled layout unchanged — this interface exists so a real template (a persisted
 * user-chosen layout/asset, read the same way `ThemePreferences`/`ViewModePreferences` read a
 * setting) can be wired in later without touching [PdfReviewRenderer]'s call sites, only the
 * `PdfTemplate`-not-null branch inside it.
 */
interface PdfTemplateProvider {
    fun currentTemplate(): PdfTemplate?
}

/** Placeholder shape for a future template — deliberately empty until a real template design exists (no layout/branding data to carry yet). */
interface PdfTemplate

@Singleton
class NoOpPdfTemplateProvider @Inject constructor() : PdfTemplateProvider {
    override fun currentTemplate(): PdfTemplate? = null
}
