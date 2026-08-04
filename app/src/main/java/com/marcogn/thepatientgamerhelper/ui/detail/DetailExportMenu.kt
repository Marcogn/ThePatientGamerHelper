package com.marcogn.thepatientgamerhelper.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
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
import com.marcogn.thepatientgamerhelper.R

/** Single-review export entry point (Fase 2): Reddit-flavored Markdown and PDF. */
@Composable
fun DetailExportMenu(onExportMarkdown: () -> Unit, onExportPdf: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.cd_export_review))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.export_action_markdown)) },
                onClick = { expanded = false; onExportMarkdown() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.export_action_pdf)) },
                onClick = { expanded = false; onExportPdf() },
            )
        }
    }
}
