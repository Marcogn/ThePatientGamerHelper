package com.marcogn.thepatientgamerhelper.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.marcogn.thepatientgamerhelper.R
import com.marcogn.thepatientgamerhelper.domain.model.ViewMode

/** List/grid toggle (Fase 8) shared by the library and the backlog list detail screen — shows the icon for the mode a tap would switch *to*. */
@Composable
fun ViewModeToggle(viewMode: ViewMode, onViewModeChange: (ViewMode) -> Unit) {
    when (viewMode) {
        ViewMode.LIST -> IconButton(onClick = { onViewModeChange(ViewMode.GRID) }) {
            Icon(Icons.Filled.GridView, contentDescription = stringResource(R.string.cd_view_mode_grid))
        }
        ViewMode.GRID -> IconButton(onClick = { onViewModeChange(ViewMode.LIST) }) {
            Icon(Icons.Filled.ViewList, contentDescription = stringResource(R.string.cd_view_mode_list))
        }
    }
}
