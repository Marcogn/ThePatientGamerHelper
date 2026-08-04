package com.marcogn.thepatientgamerhelper.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.marcogn.thepatientgamerhelper.R
import com.marcogn.thepatientgamerhelper.domain.filter.LibraryFilters
import com.marcogn.thepatientgamerhelper.domain.model.Genre
import com.marcogn.thepatientgamerhelper.domain.model.Platform
import com.marcogn.thepatientgamerhelper.domain.model.ReviewStatus
import com.marcogn.thepatientgamerhelper.domain.model.Tag
import com.marcogn.thepatientgamerhelper.ui.common.DatePickerField
import com.marcogn.thepatientgamerhelper.ui.common.displayName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    filters: LibraryFilters,
    availablePlatforms: List<Platform>,
    availableGenres: List<Genre>,
    availableTags: List<Tag>,
    onFiltersChange: (LibraryFilters) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Text(stringResource(R.string.filter_sheet_title), style = MaterialTheme.typography.titleLarge)

            FilterSection(title = stringResource(R.string.filter_section_status)) {
                ReviewStatus.entries.forEach { status ->
                    FilterChip(
                        selected = status in filters.statuses,
                        onClick = {
                            val updated = if (status in filters.statuses) {
                                filters.statuses - status
                            } else {
                                filters.statuses + status
                            }
                            onFiltersChange(filters.copy(statuses = updated))
                        },
                        label = { Text(status.displayName()) },
                    )
                }
            }

            if (availablePlatforms.isNotEmpty()) {
                FilterSection(title = stringResource(R.string.filter_section_platform)) {
                    availablePlatforms.forEach { platform ->
                        FilterChip(
                            selected = platform.name in filters.platforms,
                            onClick = { onFiltersChange(filters.copy(platforms = toggle(filters.platforms, platform.name))) },
                            label = { Text(platform.name) },
                        )
                    }
                }
            }

            if (availableGenres.isNotEmpty()) {
                FilterSection(title = stringResource(R.string.filter_section_genre)) {
                    availableGenres.forEach { genre ->
                        FilterChip(
                            selected = genre.name in filters.genres,
                            onClick = { onFiltersChange(filters.copy(genres = toggle(filters.genres, genre.name))) },
                            label = { Text(genre.name) },
                        )
                    }
                }
            }

            if (availableTags.isNotEmpty()) {
                FilterSection(title = stringResource(R.string.filter_section_tags)) {
                    availableTags.forEach { tag ->
                        FilterChip(
                            selected = tag.name in filters.tags,
                            onClick = { onFiltersChange(filters.copy(tags = toggle(filters.tags, tag.name))) },
                            label = { Text(tag.name) },
                        )
                    }
                }
            }

            val minRating = (filters.minRating ?: 0.0).toFloat()
            val maxRating = (filters.maxRating ?: 10.0).toFloat()
            Text(
                text = stringResource(R.string.filter_rating_range, "%.1f".format(minRating), "%.1f".format(maxRating)),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 16.dp),
            )
            RangeSlider(
                value = minRating..maxRating,
                onValueChange = { range ->
                    onFiltersChange(
                        filters.copy(
                            minRating = range.start.toDouble(),
                            maxRating = range.endInclusive.toDouble(),
                        ),
                    )
                },
                valueRange = 0f..10f,
                steps = 99,
            )

            Text(
                text = stringResource(R.string.filter_date_range_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DatePickerField(
                    label = stringResource(R.string.filter_date_from),
                    date = filters.dateFrom,
                    modifier = Modifier.weight(1f),
                    onDateChange = { onFiltersChange(filters.copy(dateFrom = it)) },
                )
                DatePickerField(
                    label = stringResource(R.string.filter_date_to),
                    date = filters.dateTo,
                    modifier = Modifier.weight(1f),
                    onDateChange = { onFiltersChange(filters.copy(dateTo = it)) },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.filter_reset))
                }
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.filter_apply))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(title: String, content: @Composable () -> Unit) {
    Text(text = title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

private fun toggle(set: Set<String>, value: String): Set<String> =
    if (value in set) set - value else set + value
