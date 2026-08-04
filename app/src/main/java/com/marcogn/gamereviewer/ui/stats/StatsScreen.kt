package com.marcogn.gamereviewer.ui.stats

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.marcogn.gamereviewer.domain.model.DistributionEntry
import com.marcogn.gamereviewer.domain.model.LibraryStatistics
import com.marcogn.gamereviewer.domain.model.ReviewStatus
import com.marcogn.gamereviewer.domain.model.StatusShare
import com.marcogn.gamereviewer.domain.model.label
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onBack: () -> Unit, viewModel: StatsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistiche") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> Unit
            uiState.statistics.totalReviews == 0 -> EmptyStatsMessage(modifier = Modifier.padding(padding))
            else -> StatsContent(statistics = uiState.statistics, modifier = Modifier.padding(padding))
        }
    }
}

@Composable
private fun EmptyStatsMessage(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Nessuna recensione ancora: aggiungine una per vedere le statistiche",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatsContent(statistics: LibraryStatistics, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                label = "Recensioni",
                value = statistics.totalReviews.toString(),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "Voto medio",
                value = statistics.averageRating?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "—",
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "Ore totali",
                value = String.format(Locale.getDefault(), "%.0f h", statistics.totalHoursPlayed),
                modifier = Modifier.weight(1f),
            )
        }

        HorizontalDivider()
        StatsSection(title = "Stato") { StatusBreakdownChart(shares = statistics.statusBreakdown) }

        if (statistics.platformDistribution.isNotEmpty()) {
            HorizontalDivider()
            StatsSection(title = "Distribuzione per piattaforma") {
                DistributionBarChart(entries = statistics.platformDistribution)
            }
        }

        if (statistics.genreDistribution.isNotEmpty()) {
            HorizontalDivider()
            StatsSection(title = "Distribuzione per genere") {
                DistributionBarChart(entries = statistics.genreDistribution)
            }
        }
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
                Text(text = share.status.label(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    text = String.format(Locale.getDefault(), "%d (%.0f%%)", share.count, share.percentage),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
