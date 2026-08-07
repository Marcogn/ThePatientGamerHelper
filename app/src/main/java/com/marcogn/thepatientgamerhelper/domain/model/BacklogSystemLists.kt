package com.marcogn.thepatientgamerhelper.domain.model

/**
 * Fixed (non-localized) names for the two backlog lists the app creates on its own, when a
 * COMPLETATO item's "vuoi scrivere una recensione?" prompt is answered. Same "stays fixed
 * regardless of UI language" precedent already applied to `ReviewStatus.label()`/`domain/export`
 * (see CLAUDE.md, Fase 5): a list name is persisted data identifying *the same list* over time,
 * not UI chrome — resolving it from a localized string resource would silently fork it into a
 * second, differently-named list the moment the user switches the app language.
 */
object BacklogSystemLists {
    const val COMPLETED_WITH_REVIEW = "Completati con recensione"
    const val COMPLETED_AWAITING_REVIEW = "Completati in attesa di recensione"
}

/** One-shot "vuoi spostarlo in questa lista?" confirmation, shared by the backlog and review form ViewModels. */
data class PendingListMove(val itemTitle: String, val targetListName: String)
