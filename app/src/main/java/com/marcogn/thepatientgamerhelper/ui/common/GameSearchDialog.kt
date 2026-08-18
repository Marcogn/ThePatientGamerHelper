package com.marcogn.thepatientgamerhelper.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.marcogn.thepatientgamerhelper.R
import com.marcogn.thepatientgamerhelper.domain.model.GameMetadataSearchResult

/**
 * Shared "Cerca online" dialog (Tappa 2), used by both the review form and the backlog item form.
 * Search failures/empty results/missing API key are all surfaced as [infoMessage] — never a crash,
 * the caller's manual flow (typing the fields by hand) always stays available underneath.
 */
@Composable
fun GameSearchDialog(
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean,
    results: List<GameMetadataSearchResult>,
    infoMessage: String?,
    onSearch: () -> Unit,
    onResultSelected: (GameMetadataSearchResult) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.game_search_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        label = { Text(stringResource(R.string.form_field_title)) },
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onSearch, enabled = query.isNotBlank() && !isSearching) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.game_search_action))
                    }
                }

                when {
                    isSearching -> CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
                    infoMessage != null -> Text(
                        text = infoMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    results.isNotEmpty() -> LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(results, key = { it.externalId }) { result ->
                            GameSearchResultRow(result = result, onClick = { onResultSelected(result) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun GameSearchResultRow(result: GameMetadataSearchResult, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val previewUrl = result.coverThumbnailUrl ?: result.coverImageUrl
        if (previewUrl != null) {
            AsyncImage(
                model = previewUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small),
            )
        }
        Column {
            Text(text = result.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = listOfNotNull(result.platformName, result.releaseYear?.toString()).joinToString(" · ").ifEmpty { "—" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
