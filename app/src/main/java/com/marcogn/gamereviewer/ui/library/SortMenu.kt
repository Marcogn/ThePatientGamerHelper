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
import com.marcogn.gamereviewer.domain.filter.SortDirection
import com.marcogn.gamereviewer.domain.filter.SortField
import com.marcogn.gamereviewer.domain.filter.SortOption

private fun SortField.label(): String = when (this) {
    SortField.DATE -> "Data"
    SortField.RATING -> "Voto"
    SortField.TITLE -> "Titolo"
    SortField.HOURS_PLAYED -> "Ore di gioco"
}

@Composable
fun SortMenu(sort: SortOption, onSortChange: (SortOption) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Ordina")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortField.entries.forEach { field ->
                DropdownMenuItem(
                    text = { Text(field.label()) },
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
