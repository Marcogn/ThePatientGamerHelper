package com.marcogn.gamereviewer.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.marcogn.gamereviewer.R
import com.marcogn.gamereviewer.domain.model.BacklogItemStatus

@Composable
fun BacklogItemStatus.displayName(): String = stringResource(
    when (this) {
        BacklogItemStatus.DA_INIZIARE -> R.string.backlog_status_to_start
        BacklogItemStatus.IN_CORSO -> R.string.backlog_status_in_progress
        BacklogItemStatus.COMPLETATO -> R.string.backlog_status_completed
        BacklogItemStatus.ABBANDONATO -> R.string.backlog_status_abandoned
        BacklogItemStatus.IN_PAUSA -> R.string.backlog_status_paused
    },
)
