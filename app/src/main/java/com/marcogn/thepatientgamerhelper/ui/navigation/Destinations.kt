package com.marcogn.thepatientgamerhelper.ui.navigation

import kotlinx.serialization.Serializable

sealed interface Destination {

    /** Landing "cosa vuoi fare?" chooser between the three main sections. */
    @Serializable
    data object Home : Destination

    @Serializable
    data object Library : Destination

    @Serializable
    data object Stats : Destination

    @Serializable
    data object Settings : Destination

    @Serializable
    data class Detail(val reviewId: String) : Destination

    /**
     * [reviewId] null creates a new review, non-null edits the existing one. [backlogItemId] is
     * only used when creating (pre-populates the draft from that backlog item and links the new
     * review back to it on save) — ignored when [reviewId] is set.
     */
    @Serializable
    data class Form(val reviewId: String? = null, val backlogItemId: String? = null) : Destination

    @Serializable
    data object Backlog : Destination

    @Serializable
    data class BacklogListDetail(val listId: Long) : Destination

    @Serializable
    data class BacklogItemDetail(val itemId: String) : Destination

    /** [itemId] null creates a new item in [listId], non-null edits the existing one. */
    @Serializable
    data class BacklogItemForm(val listId: Long, val itemId: String? = null) : Destination
}
