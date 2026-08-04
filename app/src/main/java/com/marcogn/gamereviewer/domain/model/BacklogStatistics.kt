package com.marcogn.gamereviewer.domain.model

/** Lightweight aggregate view over the whole backlog (Tappa 1): counts only, no charting. */
data class BacklogStatistics(
    val totalItems: Int,
    val countByStatus: Map<BacklogItemStatus, Int>,
    val countByList: Map<Long, Int>,
) {
    companion object {
        val EMPTY = BacklogStatistics(totalItems = 0, countByStatus = emptyMap(), countByList = emptyMap())
    }
}
