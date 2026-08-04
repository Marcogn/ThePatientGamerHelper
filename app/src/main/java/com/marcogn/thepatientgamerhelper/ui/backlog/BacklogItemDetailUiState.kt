package com.marcogn.thepatientgamerhelper.ui.backlog

import com.marcogn.thepatientgamerhelper.domain.model.BacklogItem
import com.marcogn.thepatientgamerhelper.domain.model.BacklogList

data class BacklogItemDetailUiState(
    val isLoading: Boolean = true,
    val item: BacklogItem? = null,
    val lists: List<BacklogList> = emptyList(),
    val newCommentText: String = "",
)
