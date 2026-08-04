package com.marcogn.gamereviewer.ui.backlog

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.marcogn.gamereviewer.R
import com.marcogn.gamereviewer.domain.model.BacklogHistoryEntry
import com.marcogn.gamereviewer.domain.model.BacklogHistoryEventType
import com.marcogn.gamereviewer.domain.model.BacklogItemStatus
import com.marcogn.gamereviewer.ui.common.displayName

/** Human-readable line for a timeline entry, resolving [BacklogHistoryEntry.detail] per event type. */
@Composable
fun BacklogHistoryEntry.describe(): String = when (type) {
    BacklogHistoryEventType.CREATO -> stringResource(R.string.backlog_history_created)
    BacklogHistoryEventType.CAMBIO_STATO -> {
        val status = detail?.let { raw -> runCatching { BacklogItemStatus.valueOf(raw) }.getOrNull() }
        stringResource(R.string.backlog_history_status_changed, status?.displayName() ?: detail.orEmpty())
    }
    BacklogHistoryEventType.CAMBIO_LISTA -> stringResource(R.string.backlog_history_moved, detail.orEmpty())
    BacklogHistoryEventType.COMMENTO -> stringResource(R.string.backlog_history_comment_added)
    BacklogHistoryEventType.RECENSIONE_COLLEGATA -> stringResource(R.string.backlog_history_review_linked)
}
