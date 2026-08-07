package com.marcogn.thepatientgamerhelper.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.marcogn.thepatientgamerhelper.R
import com.marcogn.thepatientgamerhelper.domain.model.ReviewStatus

/**
 * Localized label for the UI. Distinct from [com.marcogn.thepatientgamerhelper.domain.model.label],
 * which stays fixed Italian: it's also used by domain/export (Markdown), whose output doesn't
 * follow the app's language setting.
 */
@Composable
fun ReviewStatus.displayName(): String = stringResource(
    when (this) {
        ReviewStatus.IN_CORSO -> R.string.review_status_in_progress
        ReviewStatus.COMPLETATO -> R.string.review_status_completed
        ReviewStatus.ABBANDONATO -> R.string.review_status_abandoned
    },
)
