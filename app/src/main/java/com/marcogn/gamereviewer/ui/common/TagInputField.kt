package com.marcogn.gamereviewer.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * Free-text multi-value input with autocomplete suggestions drawn from already-used values
 * (Platform/Genre/Tag) and inline chips for the currently selected values. Typing a brand new
 * value and confirming creates it on save (see ReviewRepositoryImpl.save find-or-create).
 */
@Composable
fun TagInputField(
    label: String,
    selected: List<String>,
    suggestions: List<String>,
    onSelectedChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }

    fun commit(value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return
        if (selected.none { it.equals(trimmed, ignoreCase = true) }) {
            onSelectedChange(selected + trimmed)
        }
        query = ""
    }

    val filteredSuggestions = remember(query, suggestions, selected) {
        if (query.isBlank()) {
            emptyList()
        } else {
            suggestions.filter { candidate ->
                candidate.contains(query, ignoreCase = true) &&
                    selected.none { it.equals(candidate, ignoreCase = true) }
            }.take(5)
        }
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit(query) }),
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { commit(query) }) {
                        Icon(Icons.Default.Add, contentDescription = "Aggiungi $label")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (filteredSuggestions.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                filteredSuggestions.forEach { suggestion ->
                    SuggestionChip(onClick = { commit(suggestion) }, label = { Text(suggestion) })
                }
            }
        }
        if (selected.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                selected.forEach { value ->
                    InputChip(
                        selected = false,
                        onClick = {},
                        label = { Text(value) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Rimuovi $value",
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { onSelectedChange(selected - value) },
                            )
                        },
                    )
                }
            }
        }
    }
}
