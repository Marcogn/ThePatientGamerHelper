package com.marcogn.gamereviewer.ui.detail

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.marcogn.gamereviewer.R
import com.marcogn.gamereviewer.data.export.ReviewExporter
import com.marcogn.gamereviewer.domain.model.Review
import com.marcogn.gamereviewer.domain.repository.ReviewRepository
import com.marcogn.gamereviewer.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DetailViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle,
    private val reviewRepository: ReviewRepository,
    private val reviewExporter: ReviewExporter,
) : ViewModel() {

    private val reviewId: String = savedStateHandle.toRoute<Destination.Detail>().reviewId

    private val _exportMessage = MutableStateFlow<String?>(null)
    val exportMessage: StateFlow<String?> = _exportMessage.asStateFlow()

    val uiState: StateFlow<DetailUiState> = reviewRepository.observeById(reviewId)
        .map { review -> DetailUiState(isLoading = false, review = review) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DetailUiState(),
        )

    fun deleteReview(onDeleted: () -> Unit) {
        viewModelScope.launch {
            reviewRepository.delete(reviewId)
            onDeleted()
        }
    }

    fun exportMarkdown(destination: Uri) = exportReview { review ->
        reviewExporter.exportMarkdown(review, destination)
    }

    fun exportPdf(destination: Uri) = exportReview { review ->
        reviewExporter.exportPdf(listOf(review), destination)
    }

    fun consumeExportMessage() {
        _exportMessage.value = null
    }

    private fun exportReview(export: suspend (Review) -> Unit) {
        val review = uiState.value.review ?: return
        viewModelScope.launch {
            runCatching {
                export(review)
            }.onSuccess {
                _exportMessage.value = appContext.getString(R.string.export_completed)
            }.onFailure {
                _exportMessage.value = appContext.getString(R.string.export_failed, it.message.orEmpty())
            }
        }
    }
}
