package com.marcogn.thepatientgamerhelper.domain.stats

import com.marcogn.thepatientgamerhelper.domain.model.BacklogItem
import com.marcogn.thepatientgamerhelper.domain.model.BacklogItemStatus
import com.marcogn.thepatientgamerhelper.domain.model.BacklogStatistics
import com.marcogn.thepatientgamerhelper.domain.model.BacklogTimeEstimateStatistics

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
        timeEstimate = computeBacklogTimeEstimateStatistics(items),
    )
}

/** See [BacklogTimeEstimateStatistics] for what counts as "has an estimate". */
fun computeBacklogTimeEstimateStatistics(items: List<BacklogItem>): BacklogTimeEstimateStatistics {
    val withEstimate = items.filter {
        it.hltbMainStoryHours != null || it.hltbMainExtraHours != null || it.hltbCompletionistHours != null
    }
    if (withEstimate.isEmpty()) return BacklogTimeEstimateStatistics.EMPTY

    return BacklogTimeEstimateStatistics(
        itemsWithEstimate = withEstimate.size,
        totalMainStoryHours = withEstimate.sumOf { it.hltbMainStoryHours ?: 0.0 },
        totalMainExtraHours = withEstimate.sumOf { it.hltbMainExtraHours ?: 0.0 },
        totalCompletionistHours = withEstimate.sumOf { it.hltbCompletionistHours ?: 0.0 },
    )
}
