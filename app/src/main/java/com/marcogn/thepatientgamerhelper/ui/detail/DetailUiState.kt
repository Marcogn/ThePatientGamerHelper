package com.marcogn.thepatientgamerhelper.ui.detail

import com.marcogn.thepatientgamerhelper.domain.model.Review

data class DetailUiState(
    val isLoading: Boolean = true,
    val review: Review? = null,
)
