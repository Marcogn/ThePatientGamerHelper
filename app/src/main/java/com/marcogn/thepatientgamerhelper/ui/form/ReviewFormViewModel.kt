package com.marcogn.thepatientgamerhelper.ui.form

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.marcogn.thepatientgamerhelper.R
import com.marcogn.thepatientgamerhelper.data.image.ImageStorage
import com.marcogn.thepatientgamerhelper.data.thegamesdb.GameMetadataSearchCoordinator
import com.marcogn.thepatientgamerhelper.domain.model.BacklogListKind
import com.marcogn.thepatientgamerhelper.domain.model.GameMetadataSearchResult
import com.marcogn.thepatientgamerhelper.domain.model.Genre
import com.marcogn.thepatientgamerhelper.domain.model.Platform
import com.marcogn.thepatientgamerhelper.domain.model.PendingListMove
import com.marcogn.thepatientgamerhelper.domain.model.ReviewDraft
import com.marcogn.thepatientgamerhelper.domain.model.ReviewStatus
import com.marcogn.thepatientgamerhelper.domain.model.Tag
import com.marcogn.thepatientgamerhelper.domain.model.toDraft
import com.marcogn.thepatientgamerhelper.domain.repository.BacklogRepository
import com.marcogn.thepatientgamerhelper.domain.repository.LookupRepository
import com.marcogn.thepatientgamerhelper.domain.repository.ReviewRepository
import com.marcogn.thepatientgamerhelper.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class LookupNames(
    val platforms: List<String>,
    val genres: List<String>,
    val tags: List<String>,
)

private data class SearchState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<GameMetadataSearchResult> = emptyList(),
    val message: String? = null,
)

@HiltViewModel
class ReviewFormViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle,
    private val reviewRepository: ReviewRepository,
    private val backlogRepository: BacklogRepository,
    private val searchCoordinator: GameMetadataSearchCoordinator,
    lookupRepository: LookupRepository,
    private val imageStorage: ImageStorage,
) : ViewModel() {

    private val formRoute = savedStateHandle.toRoute<Destination.Form>()
    private val editingId: String? = formRoute.reviewId
    private val prefillBacklogItemId: String? = formRoute.backlogItemId

    private val draft = MutableStateFlow(ReviewDraft.empty())
    private val isLoading = MutableStateFlow(editingId != null || prefillBacklogItemId != null)
    private val isSaving = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val search = MutableStateFlow(SearchState())

    /**
     * One-shot "move it to 'Completed with review'?" confirmation, offered right after
     * the *first* save of a review created from the backlog flow (explicit checkmark or the
     * implicit draft-save-on-back, see [onBackPressed]) — never on a later edit of an already-linked
     * review, which would re-ask on every single edit. The actual navigation callback the screen
     * passed in is deferred until the user answers, so "move it?" is asked *before* leaving.
     */
    private val _pendingMove = MutableStateFlow<PendingListMove?>(null)
    val pendingMove: StateFlow<PendingListMove?> = _pendingMove.asStateFlow()
    private var pendingMoveContinuation: (() -> Unit)? = null

    private val lookupNames = combine(
        lookupRepository.observePlatforms().map { it.map(Platform::name) },
        lookupRepository.observeGenres().map { it.map(Genre::name) },
        lookupRepository.observeTags().map { it.map(Tag::name) },
    ) { platforms, genres, tags -> LookupNames(platforms, genres, tags) }

    init {
        val id = editingId
        val backlogItemId = prefillBacklogItemId
        if (id != null) {
            viewModelScope.launch {
                val existing = reviewRepository.observeById(id).first()
                if (existing != null) {
                    draft.value = existing.toDraft()
                } else {
                    errorMessage.value = appContext.getString(R.string.review_not_found)
                }
                isLoading.value = false
            }
        } else if (backlogItemId != null) {
            viewModelScope.launch {
                val backlogItem = backlogRepository.observeItem(backlogItemId).first()
                if (backlogItem != null) {
                    // Reaching this screen at all only ever happens right after the backlog item was
                    // marked COMPLETATO (see BacklogItemDetailScreen's review prompt) — the review
                    // should open already reflecting that, not the form's default IN_CORSO.
                    draft.value = ReviewDraft.empty(startDate = backlogItem.startDate ?: LocalDate.now()).copy(
                        title = backlogItem.title,
                        platformNames = backlogItem.platforms.map { it.name },
                        genreNames = backlogItem.genres.map { it.name },
                        tagNames = backlogItem.tags.map { it.name },
                        endDate = backlogItem.completedDate ?: LocalDate.now(),
                        status = ReviewStatus.COMPLETATO,
                        coverImagePath = backlogItem.coverImagePath?.let { imageStorage.duplicate(it) },
                    )
                }
                isLoading.value = false
            }
        }
    }

    private val formCore = combine(draft, isLoading, isSaving, errorMessage, lookupNames) { d, loading, saving, error, lookup ->
        ReviewFormUiState(
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

    val uiState: StateFlow<ReviewFormUiState> = combine(formCore, search) { core, search ->
        core.copy(
            searchQuery = search.query,
            isSearchingOnline = search.isSearching,
            searchResults = search.results,
            searchMessage = search.message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReviewFormUiState(isLoading = editingId != null || prefillBacklogItemId != null),
    )

    fun onTitleChange(value: String) = updateDraft { it.copy(title = value) }
    fun onPlatformsChange(value: List<String>) = updateDraft { it.copy(platformNames = value) }
    fun onGenresChange(value: List<String>) = updateDraft { it.copy(genreNames = value) }
    fun onTagsChange(value: List<String>) = updateDraft { it.copy(tagNames = value) }
    fun onRatingChange(value: Double) = updateDraft { it.copy(rating = value) }
    fun onStartDateChange(value: LocalDate) = updateDraft { it.copy(startDate = value) }
    fun onEndDateChange(value: LocalDate?) = updateDraft { it.copy(endDate = value) }
    fun onHoursPlayedChange(value: Double?) = updateDraft { it.copy(hoursPlayed = value) }
    fun onStatusChange(value: ReviewStatus) = updateDraft { it.copy(status = value) }
    fun onProsChange(value: List<String>) = updateDraft { it.copy(pros = value) }
    fun onConsChange(value: List<String>) = updateDraft { it.copy(cons = value) }
    fun onReviewTextChange(value: String) = updateDraft { it.copy(reviewText = value) }

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
            updateDraft {
                it.copy(
                    title = result.title,
                    platformNames = listOfNotNull(result.platformName).ifEmpty { it.platformNames },
                    genreNames = result.genreNames.ifEmpty { it.genreNames },
                    coverImagePath = coverPath ?: it.coverImagePath,
                )
            }
            if (coverPath != null && previousPath != null) imageStorage.delete(previousPath)
            search.value = SearchState()
        }
    }

    fun onSearchDialogDismissed() {
        search.update { it.copy(results = emptyList(), message = null) }
    }

    /**
     * Back/cancel handler. For a brand new review pre-filled from a backlog item, treat it as a
     * draft worth keeping (matches the caller's expectation that leaving mid-write doesn't lose
     * the game/platform/cover data already pulled in) — silently saves whatever's there as long as
     * there's at least a title, then links it back to the backlog item exactly like a normal save.
     * No validation is enforced here: this is an implicit save-on-exit, not a deliberate confirm,
     * so an incomplete-but-titled draft is still worth keeping rather than blocking navigation.
     * Every other case (editing an existing review, or a brand new review started from the
     * library) is an ordinary cancel — nothing is saved. Like [save], the very first successful
     * save of a backlog-originated review also offers to move the backlog item into
     * [BacklogListKind.COMPLETED_WITH_REVIEW] (see [offerMoveToCompletedWithReview]) — [onDone]
     * only fires once that offer has been answered.
     */
    fun onBackPressed(onDone: (savedReviewId: String?) -> Unit) {
        val backlogItemId = prefillBacklogItemId
        if (editingId != null || backlogItemId == null || draft.value.title.isBlank()) {
            onDone(null)
            return
        }
        viewModelScope.launch {
            isSaving.value = true
            val id = reviewRepository.save(id = null, draft = draft.value)
            backlogRepository.linkReview(backlogItemId, id)
            isSaving.value = false
            offerMoveToCompletedWithReview(backlogItemId) { onDone(id) }
        }
    }

    fun save(onSaved: (String) -> Unit) {
        val current = draft.value
        val validationError = validate(current)
        if (validationError != null) {
            errorMessage.value = validationError
            return
        }
        viewModelScope.launch {
            isSaving.value = true
            val id = reviewRepository.save(editingId, current)
            val backlogItemId = prefillBacklogItemId
            if (editingId == null && backlogItemId != null) {
                backlogRepository.linkReview(backlogItemId, id)
                isSaving.value = false
                offerMoveToCompletedWithReview(backlogItemId) { onSaved(id) }
            } else {
                isSaving.value = false
                onSaved(id)
            }
        }
    }

    /** Offers to move [backlogItemId] into [BacklogListKind.COMPLETED_WITH_REVIEW]; [onDone] fires either way, after the user answers. */
    private suspend fun offerMoveToCompletedWithReview(backlogItemId: String, onDone: () -> Unit) {
        val item = backlogRepository.observeItem(backlogItemId).first()
        if (item == null) {
            onDone()
            return
        }
        pendingMoveContinuation = onDone
        val name = appContext.getString(R.string.backlog_list_completed_with_review)
        _pendingMove.value = PendingListMove(item.title, BacklogListKind.COMPLETED_WITH_REVIEW, name)
    }

    fun onConfirmMove() {
        val backlogItemId = prefillBacklogItemId ?: return
        val target = _pendingMove.value ?: return
        viewModelScope.launch {
            val listId = backlogRepository.getOrCreateSystemList(target.targetListKind, target.targetListName)
            backlogRepository.moveItem(backlogItemId, listId)
            _pendingMove.value = null
            pendingMoveContinuation?.invoke()
            pendingMoveContinuation = null
        }
    }

    fun onDeclineMove() {
        _pendingMove.value = null
        pendingMoveContinuation?.invoke()
        pendingMoveContinuation = null
    }

    private fun validate(draft: ReviewDraft): String? = when {
        draft.title.isBlank() -> appContext.getString(R.string.form_validation_title_required)
        draft.rating !in 0.0..10.0 -> appContext.getString(R.string.form_validation_rating_range)
        draft.endDate != null && draft.endDate.isBefore(draft.startDate) ->
            appContext.getString(R.string.form_validation_date_order)
        else -> null
    }

    private fun updateDraft(transform: (ReviewDraft) -> ReviewDraft) {
        draft.update(transform)
        errorMessage.value = null
    }
}
