package com.wswensw.template.tool.overlay

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wswensw.template.R
import com.wswensw.template.tool.model.CopyTextBlock
import com.wswensw.template.tool.model.CopySessionScreen
import com.wswensw.template.tool.model.CopySessionUiState
import com.wswensw.template.tool.model.TextSnapshot
import com.wswensw.template.tool.model.initialCopySessionUiState
import com.wswensw.template.ui.TemplateTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class CopySessionController(
    private val service: AccessibilityService,
    private val onActiveChanged: (active: Boolean, packageName: String?) -> Unit,
) {
    private val mutableUiState = MutableStateFlow(CopySessionUiState())
    private val uiState = mutableUiState.asStateFlow()
    private var overlayWindow: ComposeOverlayWindow? = null

    fun show(snapshot: TextSnapshot) {
        dismissOverlay(notify = false)
        mutableUiState.value = snapshot.initialCopySessionUiState()

        val host = ComposeOverlayWindow(service)
        overlayWindow = host
        host.show(copyOverlayLayoutParams()) {
            TemplateTheme(useDynamicColor = false) {
                val state by uiState.collectAsState()
                CopySessionPanel(
                    state = state,
                    onToggle = ::toggle,
                    onToggleAll = ::toggleAll,
                    onCopy = ::copy,
                    onOpenTextSelection = ::openTextSelection,
                    onDraftChange = ::updateDraft,
                    onReadingOrderChange = ::setReadingOrder,
                    onBack = ::backToEditor,
                    onClose = ::close,
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
        mutableUiState.update { it.toggleSelection(id) }
    }

    private fun toggleAll() {
        mutableUiState.update(CopySessionUiState::toggleAll)
    }

    private fun openTextSelection() {
        mutableUiState.update { it.copy(screen = CopySessionScreen.TEXT_SELECTION) }
    }

    private fun backToEditor() {
        mutableUiState.update { it.copy(screen = CopySessionScreen.EDITOR) }
    }

    private fun updateDraft(text: String) {
        mutableUiState.update { it.updateCurrentDraft(text) }
    }

    private fun setReadingOrder(enabled: Boolean) {
        mutableUiState.update { it.copy(useReadingOrder = enabled) }
    }

    private fun copy() {
        val text = mutableUiState.value.currentDraftText.trim()
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
internal fun CopySessionPanel(
    state: CopySessionUiState,
    onToggle: (String) -> Unit,
    onToggleAll: () -> Unit,
    onCopy: () -> Unit,
    onOpenTextSelection: () -> Unit,
    onDraftChange: (String) -> Unit,
    onReadingOrderChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onSave: (() -> Unit)? = null,
    saveEnabled: Boolean = true,
    title: String = "Global Copy",
) {
    val selectedCount = state.selectedIds.size
    val isSelectingText = state.screen == CopySessionScreen.TEXT_SELECTION

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OverlayScrim)
            .imePadding()
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
                                text = if (isSelectingText) "Select text" else title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = if (isSelectingText) onBack else onClose) {
                                Icon(
                                    painter = painterResource(
                                        if (isSelectingText) {
                                            R.drawable.ic_arrow_back_24
                                        } else {
                                            R.drawable.ic_close_24
                                        },
                                    ),
                                    contentDescription = if (isSelectingText) "Back" else "Close",
                                )
                            }
                        },
                        actions = {
                            if (isSelectingText) {
                                TextButton(onClick = onToggleAll) {
                                    Text(if (selectedCount == state.blocks.size) "Clear" else "Select all")
                                }
                            } else if (onSave != null) {
                                TextButton(
                                    enabled = saveEnabled,
                                    onClick = onSave,
                                ) {
                                    Text("Save")
                                }
                            }
                        },
                    )
                },
                bottomBar = {
                    if (!isSelectingText) {
                        CopyBottomBar(
                            enabled = state.currentDraftText.isNotBlank(),
                            onCopy = onCopy,
                        )
                    }
                },
            ) { innerPadding ->
                if (isSelectingText) {
                    CopyTextSelectionList(
                        blocks = state.blocks,
                        selectedIds = state.selectedIds,
                        onToggle = onToggle,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    )
                } else {
                    CopyTextEditor(
                        draftText = state.currentDraftText,
                        selectedCount = selectedCount,
                        useReadingOrder = state.useReadingOrder,
                        onDraftChange = onDraftChange,
                        onReadingOrderChange = onReadingOrderChange,
                        onOpenTextSelection = onOpenTextSelection,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    )
                }
            }
        }
    }
}

@Composable
private fun CopyBottomBar(
    enabled: Boolean,
    onCopy: () -> Unit,
) {
    Surface(shadowElevation = 4.dp) {
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            enabled = enabled,
            onClick = onCopy,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_content_copy_24),
                contentDescription = null,
            )
            Spacer(Modifier.width(8.dp))
            Text("Copy")
        }
    }
}

@Composable
private fun CopyTextEditor(
    draftText: String,
    selectedCount: Int,
    useReadingOrder: Boolean,
    onDraftChange: (String) -> Unit,
    onReadingOrderChange: (Boolean) -> Unit,
    onOpenTextSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = useReadingOrder,
                    role = Role.Switch,
                    onValueChange = onReadingOrderChange,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Reading order",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
            )
            Switch(
                checked = useReadingOrder,
                onCheckedChange = null,
            )
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenTextSelection,
        ) {
            Text("Select text ($selectedCount)")
        }
        OutlinedTextField(
            value = draftText,
            onValueChange = onDraftChange,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            label = { Text("Text") },
        )
    }
}

@Composable
private fun CopyTextSelectionList(
    blocks: List<CopyTextBlock>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        if (blocks.isEmpty()) {
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
            items(blocks, key = CopyTextBlock::id) { block ->
                CopyTextRow(
                    block = block,
                    selected = block.id in selectedIds,
                    onToggle = { onToggle(block.id) },
                )
                HorizontalDivider()
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
