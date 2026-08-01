package com.marcogn.gamereviewer.ui.form

import com.marcogn.gamereviewer.domain.model.ReviewDraft

data class ReviewFormUiState(
    val draft: ReviewDraft = ReviewDraft.empty(),
    val isEditMode: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val availablePlatformNames: List<String> = emptyList(),
    val availableGenreNames: List<String> = emptyList(),
    val availableTagNames: List<String> = emptyList(),
)
