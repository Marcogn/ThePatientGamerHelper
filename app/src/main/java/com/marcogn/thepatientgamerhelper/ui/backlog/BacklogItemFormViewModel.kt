package com.marcogn.thepatientgamerhelper.ui.backlog

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.marcogn.thepatientgamerhelper.R
import com.marcogn.thepatientgamerhelper.data.image.ImageStorage
import com.marcogn.thepatientgamerhelper.data.thegamesdb.GameMetadataSearchCoordinator
import com.marcogn.thepatientgamerhelper.domain.model.BacklogItemDraft
import com.marcogn.thepatientgamerhelper.domain.model.GameMetadataSearchResult
import com.marcogn.thepatientgamerhelper.domain.model.Genre
import com.marcogn.thepatientgamerhelper.domain.model.Platform
import com.marcogn.thepatientgamerhelper.domain.model.Tag
import com.marcogn.thepatientgamerhelper.domain.model.toDraft
import com.marcogn.thepatientgamerhelper.domain.repository.BacklogRepository
import com.marcogn.thepatientgamerhelper.domain.repository.LookupRepository
import com.marcogn.thepatientgamerhelper.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class LookupNames(val platforms: List<String>, val genres: List<String>, val tags: List<String>)

private data class SearchState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<GameMetadataSearchResult> = emptyList(),
    val message: String? = null,
)

@HiltViewModel
class BacklogItemFormViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle,
    private val backlogRepository: BacklogRepository,
    private val imageStorage: ImageStorage,
    private val searchCoordinator: GameMetadataSearchCoordinator,
    lookupRepository: LookupRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Destination.BacklogItemForm>()
    private val listId: Long = route.listId
    private val editingId: String? = route.itemId

    private val draft = MutableStateFlow(BacklogItemDraft.empty())
    private val isLoading = MutableStateFlow(editingId != null)
    private val isSaving = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val search = MutableStateFlow(SearchState())

    private val lookupNames = combine(
        lookupRepository.observePlatforms().map { it.map(Platform::name) },
        lookupRepository.observeGenres().map { it.map(Genre::name) },
        lookupRepository.observeTags().map { it.map(Tag::name) },
    ) { platforms, genres, tags -> LookupNames(platforms, genres, tags) }

    init {
        val id = editingId
        if (id != null) {
            viewModelScope.launch {
                val existing = backlogRepository.observeItem(id).first()
                if (existing != null) {
                    draft.value = existing.toDraft()
                } else {
                    errorMessage.value = appContext.getString(R.string.backlog_item_not_found)
                }
                isLoading.value = false
            }
        }
    }

    private val formCore = combine(draft, isLoading, isSaving, errorMessage, lookupNames) { d, loading, saving, error, lookup ->
        BacklogItemFormUiState(
            draft = d,
            isEditMode = editingId != null,
            isLoading = loading,
            isSaving = saving,
            errorMessage = error,
            availablePlatformNames = lookup.platforms,
            availableGenreNames = lookup.genres,
            availableTagNames = lookup.tags,
        )
    }

    val uiState: StateFlow<BacklogItemFormUiState> = combine(formCore, search) { core, search ->
        core.copy(
            searchQuery = search.query,
            isSearchingOnline = search.isSearching,
            searchResults = search.results,
            searchMessage = search.message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BacklogItemFormUiState(isLoading = editingId != null),
    )

    fun onTitleChange(value: String) = updateDraft { it.copy(title = value) }
    fun onPlatformsChange(value: List<String>) = updateDraft { it.copy(platformNames = value) }
    fun onGenresChange(value: List<String>) = updateDraft { it.copy(genreNames = value) }
    fun onTagsChange(value: List<String>) = updateDraft { it.copy(tagNames = value) }

    fun onCoverImagePicked(uri: Uri) {
        viewModelScope.launch {
            val previousPath = draft.value.coverImagePath
            val newPath = imageStorage.persist(uri)
            updateDraft { it.copy(coverImagePath = newPath) }
            imageStorage.delete(previousPath)
        }
    }

    fun onRemoveCoverImage() {
        viewModelScope.launch {
            imageStorage.delete(draft.value.coverImagePath)
            updateDraft { it.copy(coverImagePath = null) }
        }
    }

    fun onSearchQueryChange(value: String) {
        search.update { it.copy(query = value) }
    }

    fun onSearchOnlineOpened() {
        search.update { it.copy(query = draft.value.title, results = emptyList(), message = null) }
    }

    fun onSearchOnline() {
        val query = search.value.query
        if (query.isBlank()) return
        viewModelScope.launch {
            search.update { it.copy(isSearching = true, message = null) }
            val platformHint = draft.value.platformNames.firstOrNull()
            when (val outcome = searchCoordinator.search(query, platformHint)) {
                is GameMetadataSearchCoordinator.Outcome.Results ->
                    search.update { it.copy(isSearching = false, results = outcome.results, message = null) }
                is GameMetadataSearchCoordinator.Outcome.Message ->
                    search.update { it.copy(isSearching = false, results = emptyList(), message = outcome.text) }
            }
        }
    }

    fun onSearchResultSelected(result: GameMetadataSearchResult) {
        viewModelScope.launch {
            val coverPath = searchCoordinator.downloadCoverLocally(result)
            val previousPath = draft.value.coverImagePath
            val newDraft = draft.value.copy(
                title = result.title,
                platformNames = listOfNotNull(result.platformName).ifEmpty { draft.value.platformNames },
                genreNames = result.genreNames.ifEmpty { draft.value.genreNames },
                coverImagePath = coverPath ?: draft.value.coverImagePath,
                releaseYear = result.releaseYear,
                developer = result.developerName,
            )
            onDraftReplaced(newDraft)
            if (coverPath != null && previousPath != null) imageStorage.delete(previousPath)
            search.value = SearchState()
        }
    }

    fun onSearchDialogDismissed() {
        search.update { it.copy(results = emptyList(), message = null) }
    }

    /** Replaces the draft wholesale, used when the user picks a TheGamesDB search result. */
    fun onDraftReplaced(newDraft: BacklogItemDraft) {
        draft.value = newDraft
        errorMessage.value = null
    }

    fun save(onSaved: (String) -> Unit) {
        val current = draft.value
        if (current.title.isBlank()) {
            errorMessage.value = appContext.getString(R.string.form_validation_title_required)
            return
        }
        viewModelScope.launch {
            isSaving.value = true
            val id = backlogRepository.saveItem(editingId, listId, current)
            isSaving.value = false
            onSaved(id)
        }
    }

    private fun updateDraft(transform: (BacklogItemDraft) -> BacklogItemDraft) {
        draft.update(transform)
        errorMessage.value = null
    }
}
