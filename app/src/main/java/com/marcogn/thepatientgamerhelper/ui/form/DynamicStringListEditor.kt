package com.marcogn.thepatientgamerhelper.ui.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.marcogn.thepatientgamerhelper.R

/** Editable list of free-text items (used for Pro/Contro), each row removable inline. */
@Composable
fun DynamicStringListEditor(
    label: String,
    items: List<String>,
    onItemsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(text = label, style = MaterialTheme.typography.titleSmall)
        items.forEachIndexed { index, value ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { newValue ->
                        onItemsChange(items.toMutableList().apply { set(index, newValue) })
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                IconButton(onClick = { onItemsChange(items.toMutableList().apply { removeAt(index) }) }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.list_item_remove_cd))
                }
            }
        }
        TextButton(onClick = { onItemsChange(items + "") }) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(stringResource(R.string.dynamic_list_add_label))
        }
    }
}
