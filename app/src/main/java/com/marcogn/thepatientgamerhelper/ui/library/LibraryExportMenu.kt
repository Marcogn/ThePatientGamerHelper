package com.marcogn.thepatientgamerhelper.ui.library

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

/** Whole-library export entry point (Fase 2): raw data backup formats plus a batch PDF. */
@Composable
fun LibraryExportMenu(onExportJson: () -> Unit, onExportCsv: () -> Unit, onExportPdf: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.cd_export_library))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.export_action_json)) },
                onClick = { expanded = false; onExportJson() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.export_action_csv)) },
                onClick = { expanded = false; onExportCsv() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.export_action_pdf)) },
                onClick = { expanded = false; onExportPdf() },
            )
        }
    }
}
