package com.marcogn.gamereviewer.ui.form

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.marcogn.gamereviewer.R
import com.marcogn.gamereviewer.data.image.ImageStorage
import com.marcogn.gamereviewer.domain.model.Genre
import com.marcogn.gamereviewer.domain.model.Platform
import com.marcogn.gamereviewer.domain.model.ReviewDraft
import com.marcogn.gamereviewer.domain.model.ReviewStatus
import com.marcogn.gamereviewer.domain.model.Tag
import com.marcogn.gamereviewer.domain.model.toDraft
import com.marcogn.gamereviewer.domain.repository.LookupRepository
import com.marcogn.gamereviewer.domain.repository.ReviewRepository
import com.marcogn.gamereviewer.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
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

private data class LookupNames(
    val platforms: List<String>,
    val genres: List<String>,
    val tags: List<String>,
)

@HiltViewModel
class ReviewFormViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle,
    private val reviewRepository: ReviewRepository,
    lookupRepository: LookupRepository,
    private val imageStorage: ImageStorage,
) : ViewModel() {

    private val editingId: String? = savedStateHandle.toRoute<Destination.Form>().reviewId

    private val draft = MutableStateFlow(ReviewDraft.empty())
    private val isLoading = MutableStateFlow(editingId != null)
    private val isSaving = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    private val lookupNames = combine(
        lookupRepository.observePlatforms().map { it.map(Platform::name) },
        lookupRepository.observeGenres().map { it.map(Genre::name) },
        lookupRepository.observeTags().map { it.map(Tag::name) },
    ) { platforms, genres, tags -> LookupNames(platforms, genres, tags) }

    init {
        val id = editingId
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
        }
    }

    val uiState: StateFlow<ReviewFormUiState> = combine(
        draft,
        isLoading,
        isSaving,
        errorMessage,
        lookupNames,
    ) { draft, loading, saving, error, lookup ->
        ReviewFormUiState(
            draft = draft,
            isEditMode = editingId != null,
            isLoading = loading,
            isSaving = saving,
            errorMessage = error,
            availablePlatformNames = lookup.platforms,
            availableGenreNames = lookup.genres,
            availableTagNames = lookup.tags,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReviewFormUiState(isLoading = editingId != null),
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
            isSaving.value = false
            onSaved(id)
        }
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
