package com.wswensw.template.tool.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wswensw.template.R
import com.wswensw.template.tool.model.CopyHistoryEntry
import com.wswensw.template.tool.model.CopySessionScreen
import com.wswensw.template.tool.model.CopySessionUiState
import com.wswensw.template.tool.overlay.CopySessionPanel
import com.wswensw.template.ui.TemplateAppIcon
import com.wswensw.template.ui.TemplateLazyRouteScaffold
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun CopyHistoryScreen(
    history: List<CopyHistorySummaryUi>,
    selectedHistory: CopyHistoryEntry?,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onCloseEditor: () -> Unit,
    onSave: (Long, CopySessionUiState, (String?) -> Unit) -> Unit,
    onCopy: (String) -> Unit,
    onDelete: (Long, (String?) -> Unit) -> Unit,
    onError: (String) -> Unit,
) {
    var pendingDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }

    TemplateLazyRouteScaffold(
        title = "Copy history",
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
                        text = "No copy history yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(history, key = CopyHistorySummaryUi::id) { summary ->
                CopyHistoryRow(
                    summary = summary,
                    onOpen = { onOpen(summary.id) },
                    onDelete = { pendingDeleteId = summary.id },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }

    pendingDeleteId?.let { historyId ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete copy history?") },
            text = { Text("This captured text and its saved edits will be removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(historyId) { error ->
                            if (error != null) onError(error)
                        }
                        pendingDeleteId = null
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    selectedHistory?.let { entry ->
        CopyHistoryEditorDialog(
            entry = entry,
            onDismiss = onCloseEditor,
            onSave = onSave,
            onCopy = onCopy,
            onError = onError,
        )
    }
}

@Composable
private fun CopyHistoryRow(
    summary: CopyHistorySummaryUi,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = summary.appName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "${summary.blockCount} blocks | ${formatHistoryTime(summary.capturedAt)}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = summary.textPreview.ifBlank { "Empty draft" },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        leadingContent = {
            TemplateAppIcon(
                bitmap = summary.appIcon,
                fallbackIconRes = R.drawable.ic_app_icon,
                contentDescription = null,
            )
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete_24),
                    contentDescription = "Delete",
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun CopyHistoryEditorDialog(
    entry: CopyHistoryEntry,
    onDismiss: () -> Unit,
    onSave: (Long, CopySessionUiState, (String?) -> Unit) -> Unit,
    onCopy: (String) -> Unit,
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
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
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
            onClose = onDismiss,
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
            title = "Copy history",
        )
    }
}

private fun CopySessionUiState.persistedState(): CopySessionUiState = copy(screen = CopySessionScreen.EDITOR)

private fun formatHistoryTime(epochMillis: Long): String = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    .withLocale(Locale.getDefault())
    .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
