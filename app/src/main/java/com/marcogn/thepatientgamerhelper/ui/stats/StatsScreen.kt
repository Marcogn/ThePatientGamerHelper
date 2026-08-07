package com.marcogn.thepatientgamerhelper.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.marcogn.thepatientgamerhelper.R
import com.marcogn.thepatientgamerhelper.domain.model.BacklogTimeEstimateStatistics
import com.marcogn.thepatientgamerhelper.domain.model.DistributionEntry
import com.marcogn.thepatientgamerhelper.domain.model.LibraryStatistics
import com.marcogn.thepatientgamerhelper.domain.model.ReviewStatus
import com.marcogn.thepatientgamerhelper.domain.model.StatusShare
import com.marcogn.thepatientgamerhelper.ui.common.displayName
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onMenuClick: () -> Unit, viewModel: StatsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.cd_menu))
                    }
                },
            )
        },
    ) { padding ->
        val isEmpty = uiState.statistics.totalReviews == 0 && uiState.backlogTimeEstimate.itemsWithEstimate == 0
        when {
            uiState.isLoading -> Unit
            isEmpty -> EmptyStatsMessage(modifier = Modifier.padding(padding))
            else -> StatsContent(
                statistics = uiState.statistics,
                backlogTimeEstimate = uiState.backlogTimeEstimate,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun EmptyStatsMessage(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.stats_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatsContent(
    statistics: LibraryStatistics,
    backlogTimeEstimate: BacklogTimeEstimateStatistics,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (statistics.totalReviews > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    label = stringResource(R.string.stats_reviews_label),
                    value = statistics.totalReviews.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = stringResource(R.string.stats_avg_rating_label),
                    value = statistics.averageRating?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "—",
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = stringResource(R.string.stats_total_hours_label),
                    value = stringResource(R.string.stats_hours_value, statistics.totalHoursPlayed),
                    modifier = Modifier.weight(1f),
                )
            }

            HorizontalDivider()
            StatsSection(title = stringResource(R.string.label_status)) { StatusBreakdownChart(shares = statistics.statusBreakdown) }

            if (statistics.platformDistribution.isNotEmpty()) {
                HorizontalDivider()
                StatsSection(title = stringResource(R.string.stats_platform_distribution)) {
                    DistributionBarChart(entries = statistics.platformDistribution)
                }
            }

            if (statistics.genreDistribution.isNotEmpty()) {
                HorizontalDivider()
                StatsSection(title = stringResource(R.string.stats_genre_distribution)) {
                    DistributionBarChart(entries = statistics.genreDistribution)
                }
            }
        }

        if (backlogTimeEstimate.itemsWithEstimate > 0) {
            if (statistics.totalReviews > 0) HorizontalDivider()
            StatsSection(title = stringResource(R.string.stats_backlog_hltb_title)) {
                BacklogTimeEstimateSection(backlogTimeEstimate)
            }
        }
    }
}

/** HowLongToBeat estimates summed across the backlog (Fase 8) — see `computeBacklogTimeEstimateStatistics`. */
@Composable
private fun BacklogTimeEstimateSection(estimate: BacklogTimeEstimateStatistics) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                label = stringResource(R.string.stats_backlog_hltb_main_story),
                value = stringResource(R.string.stats_hours_value, estimate.totalMainStoryHours),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = stringResource(R.string.stats_backlog_hltb_main_extra),
                value = stringResource(R.string.stats_hours_value, estimate.totalMainExtraHours),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = stringResource(R.string.stats_backlog_hltb_completionist),
                value = stringResource(R.string.stats_hours_value, estimate.totalCompletionistHours),
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = pluralStringResource(R.plurals.stats_backlog_hltb_items_count, estimate.itemsWithEstimate, estimate.itemsWithEstimate),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = value, style = MaterialTheme.typography.titleLarge)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Simple horizontal bar per label, width proportional to the largest count in the set. */
@Composable
private fun DistributionBarChart(entries: List<DistributionEntry>, modifier: Modifier = Modifier) {
    val maxCount = entries.maxOf { it.count }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        entries.forEach { entry -> DistributionBarRow(entry = entry, maxCount = maxCount) }
    }
}

@Composable
private fun DistributionBarRow(entry: DistributionEntry, maxCount: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = entry.label, style = MaterialTheme.typography.bodyMedium)
            Text(text = entry.count.toString(), style = MaterialTheme.typography.bodyMedium)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = entry.count.toFloat() / maxCount)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

private val ReviewStatus.chartColor: Int
    get() = when (this) {
        ReviewStatus.COMPLETATO -> 0
        ReviewStatus.IN_CORSO -> 1
        ReviewStatus.ABBANDONATO -> 2
    }

/** Single stacked bar (share of each status) plus a legend with counts/percentages. */
@Composable
private fun StatusBreakdownChart(shares: List<StatusShare>) {
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.error,
    )
    val nonEmptyShares = shares.filter { it.count > 0 }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (nonEmptyShares.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp)),
            ) {
                nonEmptyShares.forEach { share ->
                    Box(
                        modifier = Modifier
                            .weight(share.percentage.coerceAtLeast(0.01).toFloat())
                            .fillMaxSize()
                            .background(colors[share.status.chartColor]),
                    )
                }
            }
        }

        shares.forEach { share ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors[share.status.chartColor]),
                )
                Text(text = share.status.displayName(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.stats_status_count_percentage, share.count, share.percentage),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
