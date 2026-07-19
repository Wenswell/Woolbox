package com.wswensw.template.tool.overlay

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wswensw.template.tool.model.CopyTextBlock
import com.wswensw.template.tool.model.CopyTextOrder
import com.wswensw.template.tool.model.CopyTextSelection
import com.wswensw.template.tool.model.TextSnapshot
import com.wswensw.template.ui.TemplateTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private enum class CopySessionMode {
    SELECTING,
    EDITING,
}

private data class CopySessionUiState(
    val packageName: String = "",
    val blocks: List<CopyTextBlock> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val mode: CopySessionMode = CopySessionMode.SELECTING,
    val draftText: String = "",
    val captureDraftText: String = "",
    val reorderedDraftText: String = "",
    val useReadingOrder: Boolean = false,
)

class CopySessionController(
    private val service: AccessibilityService,
    private val onActiveChanged: (active: Boolean, packageName: String?) -> Unit,
) {
    private val mutableUiState = MutableStateFlow(CopySessionUiState())
    private val uiState = mutableUiState.asStateFlow()
    private var overlayWindow: ComposeOverlayWindow? = null

    fun show(snapshot: TextSnapshot) {
        dismissOverlay(notify = false)
        mutableUiState.value = CopySessionUiState(
            packageName = snapshot.packageName,
            blocks = snapshot.blocks,
            selectedIds = snapshot.blocks.mapTo(linkedSetOf(), CopyTextBlock::id),
        )

        val host = ComposeOverlayWindow(service)
        overlayWindow = host
        host.show(copyOverlayLayoutParams()) {
            TemplateTheme(useDynamicColor = false) {
                CopySessionOverlay(
                    state = uiState,
                    onToggle = ::toggle,
                    onToggleAll = ::toggleAll,
                    onCopy = ::copy,
                    onEdit = ::edit,
                    onDraftChange = ::updateDraft,
                    onReadingOrderChange = ::setReadingOrder,
                    onBack = ::back,
                )
            }
        }
        onActiveChanged(true, snapshot.packageName)
    }

    fun close(notify: Boolean = true) {
        dismissOverlay(notify = notify)
    }

    private fun dismissOverlay(notify: Boolean) {
        val packageName = mutableUiState.value.packageName.takeIf(String::isNotBlank)
        overlayWindow?.dismiss()
        overlayWindow = null
        mutableUiState.value = CopySessionUiState()
        if (notify && packageName != null) {
            onActiveChanged(false, packageName)
        }
    }

    private fun toggle(id: String) {
        mutableUiState.update { state ->
            state.copy(
                selectedIds = state.selectedIds.toMutableSet().also { selected ->
                    if (!selected.add(id)) selected.remove(id)
                },
            )
        }
    }

    private fun toggleAll() {
        mutableUiState.update { state ->
            val allIds = state.blocks.mapTo(linkedSetOf(), CopyTextBlock::id)
            state.copy(selectedIds = if (state.selectedIds.size == allIds.size) emptySet() else allIds)
        }
    }

    private fun copy(order: CopyTextOrder) {
        val state = mutableUiState.value
        val text = when (state.mode) {
            CopySessionMode.SELECTING -> CopyTextSelection.selected(
                blocks = state.blocks,
                selectedIds = state.selectedIds,
                order = order,
            )

            CopySessionMode.EDITING -> state.draftText.trim()
        }
        if (text.isBlank()) return

        val clipboard = service.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Global Copy", text))
        Toast.makeText(
            service,
            "Copied to clipboard.",
            Toast.LENGTH_SHORT,
        ).show()
        close()
    }

    private fun edit() {
        mutableUiState.update { state ->
            val captureText = CopyTextSelection.selected(
                blocks = state.blocks,
                selectedIds = state.selectedIds,
                order = CopyTextOrder.CAPTURE,
            )
            val reorderedText = CopyTextSelection.selected(
                blocks = state.blocks,
                selectedIds = state.selectedIds,
                order = CopyTextOrder.READING,
            )
            state.copy(
                mode = CopySessionMode.EDITING,
                draftText = captureText,
                captureDraftText = captureText,
                reorderedDraftText = reorderedText,
                useReadingOrder = false,
            )
        }
    }

    private fun updateDraft(text: String) {
        mutableUiState.update { it.copy(draftText = text) }
    }

    private fun setReadingOrder(enabled: Boolean) {
        mutableUiState.update { state ->
            state.copy(
                draftText = if (enabled) state.reorderedDraftText else state.captureDraftText,
                useReadingOrder = enabled,
            )
        }
    }

    private fun back() {
        val state = mutableUiState.value
        if (state.mode == CopySessionMode.EDITING) {
            mutableUiState.update { it.copy(mode = CopySessionMode.SELECTING) }
        } else {
            close()
        }
    }

    @Suppress("DEPRECATION")
    private fun copyOverlayLayoutParams(): WindowManager.LayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CopySessionOverlay(
    state: kotlinx.coroutines.flow.StateFlow<CopySessionUiState>,
    onToggle: (String) -> Unit,
    onToggleAll: () -> Unit,
    onCopy: (CopyTextOrder) -> Unit,
    onEdit: () -> Unit,
    onDraftChange: (String) -> Unit,
    onReadingOrderChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val uiState by state.collectAsState()
    val selectedCount = uiState.selectedIds.size
    val isEditing = uiState.mode == CopySessionMode.EDITING

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OverlayScrim)
            .padding(horizontal = 12.dp, vertical = 32.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 8.dp,
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = if (isEditing) "Edit copy" else "Global Copy",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        navigationIcon = {
                            TextButton(onClick = onBack) {
                                Text(if (isEditing) "Back" else "Close")
                            }
                        },
                        actions = {
                            if (!isEditing) {
                                TextButton(onClick = onToggleAll) {
                                    Text(
                                        if (selectedCount == uiState.blocks.size) "Clear" else "Select all",
                                    )
                                }
                            }
                        },
                    )
                },
                bottomBar = {
                    Surface(shadowElevation = 4.dp) {
                        if (isEditing) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                Button(onClick = { onCopy(CopyTextOrder.CAPTURE) }) {
                                    Text("Copy")
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "$selectedCount selected",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Button(
                                        modifier = Modifier.weight(1f),
                                        enabled = selectedCount > 0,
                                        onClick = { onCopy(CopyTextOrder.CAPTURE) },
                                    ) {
                                        Text("Copy capture")
                                    }
                                    FilledTonalButton(
                                        modifier = Modifier.weight(1f),
                                        enabled = selectedCount > 0,
                                        onClick = { onCopy(CopyTextOrder.READING) },
                                    ) {
                                        Text("Copy reordered")
                                    }
                                }
                                TextButton(
                                    modifier = Modifier.align(Alignment.End),
                                    enabled = selectedCount > 0,
                                    onClick = onEdit,
                                ) {
                                    Text("Edit before copy")
                                }
                            }
                        }
                    }
                },
            ) { innerPadding ->
                if (isEditing) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onReadingOrderChange(!uiState.useReadingOrder)
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = uiState.useReadingOrder,
                                onCheckedChange = onReadingOrderChange,
                            )
                            Text("Reading order")
                        }
                        OutlinedTextField(
                            value = uiState.draftText,
                            onValueChange = onDraftChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            label = { Text("Text") },
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        if (uiState.blocks.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillParentMaxSize()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("No accessible text in this window.")
                                }
                            }
                        } else {
                            items(uiState.blocks, key = CopyTextBlock::id) { block ->
                                CopyTextRow(
                                    block = block,
                                    selected = block.id in uiState.selectedIds,
                                    onToggle = { onToggle(block.id) },
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CopyTextRow(
    block: CopyTextBlock,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 8.dp, top = 8.dp, end = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = { onToggle() },
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "${block.captureOrder}. ${block.text.trim()}",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = block.metadataSummary(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private fun CopyTextBlock.metadataSummary(): String =
    "${source.name.lowercase()} · ${bounds.left},${bounds.top}-${bounds.right},${bounds.bottom}"

private val OverlayScrim = Color(0xB40C1018)
