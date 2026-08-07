package com.marcogn.thepatientgamerhelper.domain.model

/**
 * Identifies the two backlog lists the app itself creates and moves completed items into (see
 * `BacklogItemDetailViewModel`/`ReviewFormViewModel`). Stored as `BacklogListEntity.systemKind`
 * (nullable — regular, user-created lists have none) and used for matching instead of the list's
 * `name`: a list *name* is UI text that should read in the app's current language, but the app
 * still needs a **stable, language-independent identity** to find "the same list" again later —
 * otherwise switching the app's language would make the next automatic move create a second,
 * differently-named list instead of reusing the existing one. The display name itself is resolved
 * from a string resource at the moment a list is first created (see call sites) and, like any other
 * list name, doesn't retroactively change if the app's language changes afterwards — same as a
 * user-typed list name would.
 */
enum class BacklogListKind {
    COMPLETED_WITH_REVIEW,
    COMPLETED_AWAITING_REVIEW,
}

/** One-shot "vuoi spostarlo in questa lista?" confirmation, shared by the backlog and review form ViewModels. */
data class PendingListMove(val itemTitle: String, val targetListKind: BacklogListKind, val targetListName: String)
