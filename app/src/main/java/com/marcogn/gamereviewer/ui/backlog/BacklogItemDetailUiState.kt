package com.marcogn.gamereviewer.ui.backlog

import com.marcogn.gamereviewer.domain.model.BacklogItem
import com.marcogn.gamereviewer.domain.model.BacklogList

data class BacklogItemDetailUiState(
    val isLoading: Boolean = true,
    val item: BacklogItem? = null,
    val lists: List<BacklogList> = emptyList(),
    val newCommentText: String = "",
)
