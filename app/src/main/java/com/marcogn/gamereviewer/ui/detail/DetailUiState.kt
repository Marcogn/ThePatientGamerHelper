package com.marcogn.gamereviewer.ui.detail

import com.marcogn.gamereviewer.domain.model.Review

data class DetailUiState(
    val isLoading: Boolean = true,
    val review: Review? = null,
)
