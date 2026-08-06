package com.marcogn.thepatientgamerhelper.domain.model

/** Lightweight aggregate view over the whole backlog (Tappa 1): counts only, no charting. */
data class BacklogStatistics(
    val totalItems: Int,
    val countByStatus: Map<BacklogItemStatus, Int>,
    val countByList: Map<Long, Int>,
    val timeEstimate: BacklogTimeEstimateStatistics,
) {
    companion object {
        val EMPTY = BacklogStatistics(
            totalItems = 0,
            countByStatus = emptyMap(),
            countByList = emptyMap(),
            timeEstimate = BacklogTimeEstimateStatistics.EMPTY,
        )
    }
}

/**
 * Sum of HowLongToBeat estimates (Fase 8) across every backlog item that has one — regardless of
 * status, so it reads as "total time these games are estimated to take", not just what's left to
 * start. Items without an estimate (HLTB lookup failed/no match) are simply not counted, tracked
 * separately via [itemsWithEstimate] so the UI can show "X of Y items have an estimate".
 */
data class BacklogTimeEstimateStatistics(
    val itemsWithEstimate: Int,
    val totalMainStoryHours: Double,
    val totalMainExtraHours: Double,
    val totalCompletionistHours: Double,
) {
    companion object {
        val EMPTY = BacklogTimeEstimateStatistics(
            itemsWithEstimate = 0,
            totalMainStoryHours = 0.0,
            totalMainExtraHours = 0.0,
            totalCompletionistHours = 0.0,
        )
    }
}
