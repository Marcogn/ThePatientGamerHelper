package com.marcogn.thepatientgamerhelper.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.marcogn.thepatientgamerhelper.R
import com.marcogn.thepatientgamerhelper.domain.model.ReviewStatus

/**
 * Etichetta localizzata per la UI. Distinta da [com.marcogn.thepatientgamerhelper.domain.model.label],
 * che resta in italiano fisso: è usata anche da domain/export (Markdown), il cui output non
 * segue la lingua dell'app.
 */
@Composable
fun ReviewStatus.displayName(): String = stringResource(
    when (this) {
        ReviewStatus.IN_CORSO -> R.string.review_status_in_progress
        ReviewStatus.COMPLETATO -> R.string.review_status_completed
        ReviewStatus.ABBANDONATO -> R.string.review_status_abandoned
    },
)
