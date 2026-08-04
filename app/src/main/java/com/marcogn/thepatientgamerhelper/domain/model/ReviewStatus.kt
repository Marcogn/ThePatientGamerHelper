package com.marcogn.thepatientgamerhelper.domain.model

enum class ReviewStatus {
    IN_CORSO,
    COMPLETATO,
    ABBANDONATO,
}

fun ReviewStatus.label(): String = when (this) {
    ReviewStatus.IN_CORSO -> "In corso"
    ReviewStatus.COMPLETATO -> "Completato"
    ReviewStatus.ABBANDONATO -> "Abbandonato"
}
