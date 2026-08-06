package com.marcogn.thepatientgamerhelper.ui.backlog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.marcogn.thepatientgamerhelper.R
import com.marcogn.thepatientgamerhelper.domain.model.BacklogHistoryEntry
import com.marcogn.thepatientgamerhelper.domain.model.BacklogItemStatus
import com.marcogn.thepatientgamerhelper.ui.common.CoverThumbnail
import com.marcogn.thepatientgamerhelper.ui.common.displayName
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val historyTimestampFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BacklogItemDetailScreen(
    onBack: () -> Unit,
    onEdit: (String, Long) -> Unit,
    onDeleted: () -> Unit,
    onWriteReview: (String) -> Unit,
    viewModel: BacklogItemDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val showReviewPrompt by viewModel.showReviewPrompt.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMoveMenu by remember { mutableStateOf(false) }

    val item = uiState.item

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item?.title.orEmpty(), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    item?.let {
                        Box {
                            IconButton(onClick = { showMoveMenu = true }) {
                                Icon(Icons.Filled.DriveFileMove, contentDescription = stringResource(R.string.backlog_move_to_list))
                            }
                            DropdownMenu(expanded = showMoveMenu, onDismissRequest = { showMoveMenu = false }) {
                                uiState.lists.filter { list -> list.id != it.listId }.forEach { list ->
                                    DropdownMenuItem(
                                        text = { Text(list.name) },
                                        onClick = {
                                            viewModel.onMoveToList(list.id)
                                            showMoveMenu = false
                                        },
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { onEdit(it.id, it.listId) }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
                        }
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                    }
                },
            )
        },
    ) { padding ->
        if (uiState.isLoading || item == null) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CoverThumbnail(item.coverImagePath, size = 80.dp)
                Column {
                    if (item.platforms.isNotEmpty()) Text(item.platforms.joinToString { it.name }, style = MaterialTheme.typography.bodyMedium)
                    if (item.genres.isNotEmpty()) {
                        Text(
                            item.genres.joinToString { it.name },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val metadata = listOfNotNull(item.developer, item.releaseYear?.toString())
                    if (metadata.isNotEmpty()) {
                        Text(
                            text = metadata.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (item.reviewId != null) {
                        Text(
                            stringResource(R.string.backlog_review_linked),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            HowLongToBeatSection(
                mainStoryHours = item.hltbMainStoryHours,
                mainExtraHours = item.hltbMainExtraHours,
                completionistHours = item.hltbCompletionistHours,
            )

            StatusSelector(status = item.status, onStatusChange = viewModel::onStatusChange)

            if (item.status == BacklogItemStatus.ABBANDONATO) {
                OutlinedTextField(
                    value = item.abandonNote.orEmpty(),
                    onValueChange = viewModel::onAbandonNoteChange,
                    label = { Text(stringResource(R.string.backlog_abandon_note_label)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            HorizontalDivider()

            CommentsSection(
                comments = item.comments.map { it.text to it.timestamp },
                newCommentText = uiState.newCommentText,
                onTextChange = viewModel::onCommentTextChange,
                onAdd = viewModel::onAddComment,
            )

            HorizontalDivider()

            HistorySection(history = item.history)
        }
    }

    if (showReviewPrompt && item != null) {
        AlertDialog(
            onDismissRequest = viewModel::onReviewPromptConsumed,
            title = { Text(stringResource(R.string.backlog_write_review_prompt_title)) },
            text = { Text(stringResource(R.string.backlog_write_review_prompt_message, item.title)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onReviewPromptConsumed()
                    onWriteReview(item.id)
                }) { Text(stringResource(R.string.action_yes)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onReviewPromptConsumed) { Text(stringResource(R.string.action_no)) }
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.backlog_delete_item_confirm_title)) },
            text = { Text(stringResource(R.string.detail_delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.onDelete(onDeleted)
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun HowLongToBeatSection(mainStoryHours: Double?, mainExtraHours: Double?, completionistHours: Double?) {
    if (mainStoryHours == null && mainExtraHours == null && completionistHours == null) return

    val parts = listOfNotNull(
        mainStoryHours?.let { stringResource(R.string.backlog_hltb_main_story_short, it) },
        mainExtraHours?.let { stringResource(R.string.backlog_hltb_main_extra_short, it) },
        completionistHours?.let { stringResource(R.string.backlog_hltb_completionist_short, it) },
    )
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = stringResource(R.string.backlog_hltb_title), style = MaterialTheme.typography.labelSmall)
            Text(text = parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StatusSelector(status: BacklogItemStatus, onStatusChange: (BacklogItemStatus) -> Unit) {
    Column {
        Text(text = stringResource(R.string.label_status), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BacklogItemStatus.entries.forEach { candidate ->
                FilterChip(
                    selected = status == candidate,
                    onClick = { onStatusChange(candidate) },
                    label = { Text(candidate.displayName()) },
                )
            }
        }
    }
}

@Composable
private fun CommentsSection(
    comments: List<Pair<String, java.time.Instant>>,
    newCommentText: String,
    onTextChange: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.backlog_comments_title), style = MaterialTheme.typography.titleSmall)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = newCommentText,
                onValueChange = onTextChange,
                label = { Text(stringResource(R.string.backlog_comment_field_label)) },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onAdd) {
                Icon(Icons.Filled.Send, contentDescription = stringResource(R.string.backlog_add_comment))
            }
        }

        if (comments.isEmpty()) {
            Text(
                text = stringResource(R.string.backlog_no_comments),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            comments.forEach { (text, timestamp) ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = text, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = historyTimestampFormatter.format(timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistorySection(history: List<BacklogHistoryEntry>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.backlog_history_title), style = MaterialTheme.typography.titleSmall)
        history.forEach { entry ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = historyTimestampFormatter.format(entry.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = entry.describe(), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
