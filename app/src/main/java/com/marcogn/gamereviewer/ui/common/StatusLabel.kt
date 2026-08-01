package com.marcogn.gamereviewer.ui.common

import com.marcogn.gamereviewer.domain.model.ReviewStatus

fun ReviewStatus.label(): String = when (this) {
    ReviewStatus.IN_CORSO -> "In corso"
    ReviewStatus.COMPLETATO -> "Completato"
    ReviewStatus.ABBANDONATO -> "Abbandonato"
}
