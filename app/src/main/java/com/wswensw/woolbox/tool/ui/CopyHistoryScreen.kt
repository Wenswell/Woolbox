package com.wswensw.woolbox.tool.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wswensw.woolbox.R
import com.wswensw.woolbox.tool.model.CopyHistoryEntry
import com.wswensw.woolbox.tool.model.CopySessionScreen
import com.wswensw.woolbox.tool.model.CopySessionUiState
import com.wswensw.woolbox.tool.overlay.CopySessionPanel
import com.wswensw.woolbox.ui.AppIcon
import com.wswensw.woolbox.ui.AppDivider
import com.wswensw.woolbox.ui.AppLazyRouteScaffold
import com.wswensw.woolbox.ui.AppListItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun CopyHistoryScreen(
    history: List<CopyHistorySummaryUi>,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    AppLazyRouteScaffold(
        title = stringResource(R.string.copy_history_title),
        onBack = onBack,
    ) {
        if (history.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillParentMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.copy_history_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        } else {
            items(history, key = CopyHistorySummaryUi::id) { summary ->
                AppListItem(
                    title = summary.appName,
                    subtitle = historySubtitle(summary),
                    leadingContent = {
                        AppIcon(
                            bitmap = summary.appIcon,
                            fallbackIconRes = R.drawable.ic_app_icon,
                            contentDescription = null,
                        )
                    },
                    subtitleMaxLines = 2,
                    onClick = { onOpen(summary.id) },
                )
                AppDivider(start = 72.dp)
            }
        }
    }
}

@Composable
fun CopyHistoryEditorScreen(
    historyId: Long,
    entry: CopyHistoryEntry?,
    onLoad: (Long, (String?) -> Unit) -> Unit,
    onClose: () -> Unit,
    onSave: (Long, CopySessionUiState, (String?) -> Unit) -> Unit,
    onCopy: (String) -> Unit,
    onDelete: (Long, (String?) -> Unit) -> Unit,
    onError: (String) -> Unit,
) {
    var loadError by rememberSaveable(historyId) { mutableStateOf<String?>(null) }
    var pendingDelete by rememberSaveable(historyId) { mutableStateOf(false) }

    LaunchedEffect(historyId) {
        if (entry?.id != historyId) {
            onLoad(historyId) { error ->
                if (error != null) {
                    loadError = error
                    onError(error)
                    onClose()
                }
            }
        }
    }

    when {
        loadError != null -> Unit
        entry == null || entry.id != historyId -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        else -> {
            CopyHistoryEditorContent(
                entry = entry,
                onClose = onClose,
                onSave = onSave,
                onCopy = onCopy,
                onRequestDelete = { pendingDelete = true },
                onError = onError,
            )
        }
    }

    if (pendingDelete) {
        AlertDialog(
            onDismissRequest = { pendingDelete = false },
            title = { Text(stringResource(R.string.copy_history_delete_title)) },
            text = { Text(stringResource(R.string.copy_history_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = false
                        onDelete(historyId) { error ->
                            if (error != null) {
                                onError(error)
                            } else {
                                onClose()
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun CopyHistoryEditorContent(
    entry: CopyHistoryEntry,
    onClose: () -> Unit,
    onSave: (Long, CopySessionUiState, (String?) -> Unit) -> Unit,
    onCopy: (String) -> Unit,
    onRequestDelete: () -> Unit,
    onError: (String) -> Unit,
) {
    var editorState by remember(entry.id, entry.session) { mutableStateOf(entry.session) }
    var savedState by remember(entry.id, entry.session) { mutableStateOf(entry.session) }
    var isSaving by remember(entry.id) { mutableStateOf(false) }
    val hasChanges = editorState.persistedState() != savedState.persistedState()

    BackHandler {
        if (editorState.screen == CopySessionScreen.TEXT_SELECTION) {
            editorState = editorState.copy(screen = CopySessionScreen.EDITOR)
        } else {
            onClose()
        }
    }

    CopySessionPanel(
        state = editorState,
        onToggle = { id -> editorState = editorState.toggleSelection(id) },
        onToggleAll = { editorState = editorState.toggleAll() },
        onCopy = { onCopy(editorState.currentDraftText.trim()) },
        onOpenTextSelection = {
            editorState = editorState.copy(screen = CopySessionScreen.TEXT_SELECTION)
        },
        onDraftChange = { text -> editorState = editorState.updateCurrentDraft(text) },
        onReadingOrderChange = { enabled ->
            editorState = editorState.copy(useReadingOrder = enabled)
        },
        onBack = { editorState = editorState.copy(screen = CopySessionScreen.EDITOR) },
        onClose = onClose,
        onSave = {
            isSaving = true
            onSave(entry.id, editorState) { error ->
                isSaving = false
                if (error == null) {
                    savedState = editorState.persistedState()
                } else {
                    onError(error)
                }
            }
        },
        saveEnabled = hasChanges && !isSaving,
        onDelete = onRequestDelete,
        title = stringResource(R.string.copy_history_title),
    )
}

@Composable
private fun historySubtitle(summary: CopyHistorySummaryUi): String {
    val blocks = pluralStringResource(R.plurals.copy_block_count, summary.blockCount, summary.blockCount)
    val meta = "$blocks · ${formatHistoryTime(summary.capturedAt)}"
    val preview = summary.textPreview.ifBlank { stringResource(R.string.copy_history_empty_draft) }
    return "$meta\n$preview"
}

private fun CopySessionUiState.persistedState(): CopySessionUiState =
    copy(screen = CopySessionScreen.EDITOR)

private fun formatHistoryTime(epochMillis: Long): String = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    .withLocale(Locale.getDefault())
    .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
