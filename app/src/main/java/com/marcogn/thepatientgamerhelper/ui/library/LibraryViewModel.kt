package com.marcogn.thepatientgamerhelper.ui.library

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcogn.thepatientgamerhelper.R
import com.marcogn.thepatientgamerhelper.data.export.ImportFileReader
import com.marcogn.thepatientgamerhelper.data.export.ReviewExporter
import com.marcogn.thepatientgamerhelper.data.settings.ViewModePreferences
import com.marcogn.thepatientgamerhelper.domain.export.parseReviewMarkdown
import com.marcogn.thepatientgamerhelper.domain.filter.LibraryFilters
import com.marcogn.thepatientgamerhelper.domain.filter.SortOption
import com.marcogn.thepatientgamerhelper.domain.filter.applyLibraryFilters
import com.marcogn.thepatientgamerhelper.domain.filter.sortReviews
import com.marcogn.thepatientgamerhelper.domain.model.Genre
import com.marcogn.thepatientgamerhelper.domain.model.Platform
import com.marcogn.thepatientgamerhelper.domain.model.Review
import com.marcogn.thepatientgamerhelper.domain.model.Tag
import com.marcogn.thepatientgamerhelper.domain.model.ViewMode
import com.marcogn.thepatientgamerhelper.domain.repository.LookupRepository
import com.marcogn.thepatientgamerhelper.domain.repository.ReviewRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class LookupOptions(
    val platforms: List<Platform>,
    val genres: List<Genre>,
    val tags: List<Tag>,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val reviewRepository: ReviewRepository,
    private val reviewExporter: ReviewExporter,
    private val importFileReader: ImportFileReader,
    private val viewModePreferences: ViewModePreferences,
    lookupRepository: LookupRepository,
) : ViewModel() {

    private val filters = MutableStateFlow(LibraryFilters())
    private val sort = MutableStateFlow(SortOption.DEFAULT)
    private val viewMode = MutableStateFlow(viewModePreferences.libraryViewMode)

    private val _exportMessage = MutableStateFlow<String?>(null)
    val exportMessage: StateFlow<String?> = _exportMessage.asStateFlow()

    private val lookupOptions = combine(
        lookupRepository.observePlatforms(),
        lookupRepository.observeGenres(),
        lookupRepository.observeTags(),
    ) { platforms, genres, tags -> LookupOptions(platforms, genres, tags) }

    val uiState: StateFlow<LibraryUiState> = combine(
        reviewRepository.observeAll(),
        filters,
        sort,
        lookupOptions,
        viewMode,
    ) { reviews, filters, sort, lookup, viewMode ->
        val filtered = applyLibraryFilters(reviews, filters)
        val sorted = sortReviews(filtered, sort)
        LibraryUiState(
            isLoading = false,
            allReviewsEmpty = reviews.isEmpty(),
            reviews = sorted,
            filters = filters,
            sort = sort,
            availablePlatforms = lookup.platforms,
            availableGenres = lookup.genres,
            availableTags = lookup.tags,
            viewMode = viewMode,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState(),
    )

    fun onSearchQueryChange(query: String) {
        filters.update { it.copy(searchQuery = query) }
    }

    fun onFiltersChange(newFilters: LibraryFilters) {
        filters.value = newFilters
    }

    fun onSortChange(newSort: SortOption) {
        sort.value = newSort
    }

    fun onClearFilters() {
        filters.update { LibraryFilters(searchQuery = it.searchQuery) }
    }

    fun onDeleteReview(id: String) {
        viewModelScope.launch { reviewRepository.delete(id) }
    }

    fun onViewModeChange(mode: ViewMode) {
        viewMode.value = mode
        viewModePreferences.libraryViewMode = mode
    }

    /** Imports a single review from a Reddit-flavored Markdown file (Fase 8) — reverse of the single-review Markdown export. Always creates a new review, never overwrites an existing one. */
    fun importMarkdown(source: Uri) {
        viewModelScope.launch {
            val outcome = runCatching {
                val content = importFileReader.readText(source)
                val draft = parseReviewMarkdown(content).getOrThrow()
                reviewRepository.save(id = null, draft = draft)
            }
            _exportMessage.value = outcome.fold(
                onSuccess = { appContext.getString(R.string.import_completed) },
                onFailure = { appContext.getString(R.string.import_failed, it.message.orEmpty()) },
            )
        }
    }

    /** Exports every review, ignoring active filters — a backup should be complete. */
    fun exportJson(destination: Uri) = exportLibrary { reviews -> reviewExporter.exportJson(reviews, destination) }

    fun exportCsv(destination: Uri) = exportLibrary { reviews -> reviewExporter.exportCsv(reviews, destination) }

    fun exportPdf(destination: Uri) = exportLibrary { reviews -> reviewExporter.exportPdf(reviews, destination) }

    fun consumeExportMessage() {
        _exportMessage.value = null
    }

    private fun exportLibrary(export: suspend (List<Review>) -> Unit) {
        viewModelScope.launch {
            runCatching {
                export(reviewRepository.observeAll().first())
            }.onSuccess {
                _exportMessage.value = appContext.getString(R.string.export_completed)
            }.onFailure {
                _exportMessage.value = appContext.getString(R.string.export_failed, it.message.orEmpty())
            }
        }
    }
}
