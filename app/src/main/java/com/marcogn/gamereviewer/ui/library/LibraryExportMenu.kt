package com.marcogn.gamereviewer.ui.library

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

/** Whole-library export entry point (Fase 2): raw data backup formats. */
@Composable
fun LibraryExportMenu(onExportJson: () -> Unit, onExportCsv: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.Download, contentDescription = "Esporta libreria")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Esporta JSON") },
                onClick = { expanded = false; onExportJson() },
            )
            DropdownMenuItem(
                text = { Text("Esporta CSV") },
                onClick = { expanded = false; onExportCsv() },
            )
        }
    }
}
