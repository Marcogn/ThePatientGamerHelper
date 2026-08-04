package com.marcogn.gamereviewer.domain.stats

import com.marcogn.gamereviewer.domain.model.BacklogItem
import com.marcogn.gamereviewer.domain.model.BacklogItemStatus
import com.marcogn.gamereviewer.domain.model.BacklogStatistics

/**
 * Pure, Android-free aggregation for the backlog's "vista aggregata leggera" — counts per
 * status/list only, no percentages or charting, mirrors `domain/stats/LibraryStatisticsCalculator`.
 */
fun computeBacklogStatistics(items: List<BacklogItem>): BacklogStatistics {
    if (items.isEmpty()) return BacklogStatistics.EMPTY

    val countByStatus = BacklogItemStatus.entries.associateWith { status -> items.count { it.status == status } }
    val countByList = items.groupingBy { it.listId }.eachCount()

    return BacklogStatistics(
        totalItems = items.size,
        countByStatus = countByStatus,
        countByList = countByList,
    )
}
