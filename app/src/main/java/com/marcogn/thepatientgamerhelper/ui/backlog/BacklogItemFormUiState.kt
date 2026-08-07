package com.marcogn.thepatientgamerhelper.ui.backlog

import com.marcogn.thepatientgamerhelper.domain.model.BacklogItemDraft
import com.marcogn.thepatientgamerhelper.domain.model.GameMetadataSearchResult

data class BacklogItemFormUiState(
    val draft: BacklogItemDraft = BacklogItemDraft.empty(),
    val isEditMode: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val availablePlatformNames: List<String> = emptyList(),
    val availableGenreNames: List<String> = emptyList(),
    val availableTagNames: List<String> = emptyList(),
    val searchQuery: String = "",
    val isSearchingOnline: Boolean = false,
    val searchResults: List<GameMetadataSearchResult> = emptyList(),
    val searchMessage: String? = null,
    /** Outcome of the HowLongToBeat lookup that follows picking a "cerca online" result (Fase 8) — shown in the form so it's visible without `adb logcat`. */
    val hltbMessage: String? = null,
)
