package com.marcogn.gamereviewer.ui.backlog

import com.marcogn.gamereviewer.domain.model.BacklogItem
import com.marcogn.gamereviewer.domain.model.BacklogList

data class BacklogListDetailUiState(
    val isLoading: Boolean = true,
    val list: BacklogList? = null,
    val items: List<BacklogItem> = emptyList(),
)
