package com.marcogn.gamereviewer.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.marcogn.gamereviewer.R
import com.marcogn.gamereviewer.domain.filter.SortDirection
import com.marcogn.gamereviewer.domain.filter.SortField
import com.marcogn.gamereviewer.domain.filter.SortOption

private fun SortField.labelRes(): Int = when (this) {
    SortField.DATE -> R.string.sort_field_date
    SortField.RATING -> R.string.sort_field_rating
    SortField.TITLE -> R.string.sort_field_title
    SortField.HOURS_PLAYED -> R.string.sort_field_hours_played
}

@Composable
fun SortMenu(sort: SortOption, onSortChange: (SortOption) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.cd_sort))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortField.entries.forEach { field ->
                DropdownMenuItem(
                    text = { Text(stringResource(field.labelRes())) },
                    trailingIcon = {
                        if (sort.field == field) {
                            Icon(
                                imageVector = if (sort.direction == SortDirection.ASC) {
                                    Icons.Filled.ArrowUpward
                                } else {
                                    Icons.Filled.ArrowDownward
                                },
                                contentDescription = null,
                            )
                        }
                    },
                    onClick = {
                        val newDirection = if (sort.field == field) {
                            if (sort.direction == SortDirection.ASC) SortDirection.DESC else SortDirection.ASC
                        } else {
                            SortDirection.DESC
                        }
                        onSortChange(SortOption(field, newDirection))
                        expanded = false
                    },
                )
            }
        }
    }
}
