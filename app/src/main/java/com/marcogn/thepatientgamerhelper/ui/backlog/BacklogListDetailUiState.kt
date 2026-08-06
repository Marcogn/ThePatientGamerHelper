package com.marcogn.thepatientgamerhelper.ui.backlog

import com.marcogn.thepatientgamerhelper.domain.model.BacklogItem
import com.marcogn.thepatientgamerhelper.domain.model.BacklogList
import com.marcogn.thepatientgamerhelper.domain.model.ViewMode

data class BacklogListDetailUiState(
    val isLoading: Boolean = true,
    val list: BacklogList? = null,
    val items: List<BacklogItem> = emptyList(),
    val viewMode: ViewMode = ViewMode.LIST,
)
