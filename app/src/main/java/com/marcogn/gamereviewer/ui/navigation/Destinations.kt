package com.marcogn.gamereviewer.ui.navigation

import kotlinx.serialization.Serializable

sealed interface Destination {

    @Serializable
    data object Library : Destination

    @Serializable
    data object Stats : Destination

    @Serializable
    data class Detail(val reviewId: String) : Destination

    /** [reviewId] null creates a new review, non-null edits the existing one. */
    @Serializable
    data class Form(val reviewId: String? = null) : Destination
}
