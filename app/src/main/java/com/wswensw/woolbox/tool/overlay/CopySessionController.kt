package com.wswensw.woolbox.tool.overlay

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wswensw.woolbox.R
import com.wswensw.woolbox.tool.model.CopyTextBlock
import com.wswensw.woolbox.tool.model.CopySessionScreen
import com.wswensw.woolbox.tool.model.CopySessionUiState
import com.wswensw.woolbox.tool.model.TextSnapshot
import com.wswensw.woolbox.tool.model.initialCopySessionUiState
import com.wswensw.woolbox.ui.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class CopySessionController(
    private val service: AccessibilityService,
    private val saveSession: (Long, CopySessionUiState) -> Unit,
    private val onActiveChanged: (active: Boolean, packageName: String?) -> Unit,
) {
    private val mutableUiState = MutableStateFlow(CopySessionUiState())
    private val uiState = mutableUiState.asStateFlow()
    private var overlayWindow: ComposeOverlayWindow? = null

    private var historyId = 0L
    private var session = ""
    private var capture: com.wswensw.woolbox.tool.model.ScrollCapture? = null
    var isCapturing = false
        private set
    val packageName: String get() = mutableUiState.value.packageName

    private fun log(action: String, details: String = "") =
        com.wswensw.woolbox.log.ToolLogs.record(service, "copy", session, action, details)

    private var lastCaptureEvent = ""
    fun captureEvent(type: Int, from: Int, to: Int, scrollY: Int) {
        lastCaptureEvent = "type=$type from=$from to=$to scrollY=$scrollY"
    }

    fun sample(snapshot: TextSnapshot) {
        if (!isCapturing || snapshot.packageName != packageName) return
        log("settled", lastCaptureEvent)
        val decisions = requireNotNull(capture).append(snapshot.blocks)
        log("sample", org.json.JSONArray().apply {
            decisions.forEach { decision -> put(org.json.JSONObject()
                .put("text", decision.block.text).put("source", decision.block.source)
                .put("bounds", decision.block.bounds.toString()).put("key", decision.key)
                .put("decision", if (decision.added) "added" else "duplicate_source_normalized_text")) }
        }.toString())
        val blocks = requireNotNull(capture).blocks
        if (decisions.any { it.added }) {
            mutableUiState.value = TextSnapshot(packageName, blocks).initialCopySessionUiState()
            saveSession(historyId, mutableUiState.value)
            log("merged", mutableUiState.value.currentDraftText)
        }
    }

    private fun setCapture(enabled: Boolean) {
        isCapturing = enabled
        log(if (enabled) "scroll_start" else "scroll_end")
        overlayWindow?.dismiss()
        showWindow()
    }

    fun show(snapshot: TextSnapshot, historyId: Long) {
        dismissOverlay(notify = false)
        this.historyId = historyId
        session = com.wswensw.woolbox.log.ToolLogs.session()
        capture = com.wswensw.woolbox.tool.model.ScrollCapture(snapshot.blocks)
        mutableUiState.value = TextSnapshot(snapshot.packageName, requireNotNull(capture).blocks).initialCopySessionUiState()
        saveSession(historyId, mutableUiState.value)
        log("start", snapshot.toString())
        showWindow()
        onActiveChanged(true, snapshot.packageName)
    }

    private fun showWindow() {

        val host = ComposeOverlayWindow(service)
        overlayWindow = host
        host.show(copyOverlayLayoutParams()) {
            AppTheme(useDynamicColor = false) {
                val state by uiState.collectAsState()
                if (isCapturing) {
                    Surface { Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.copy_scroll_count, state.blocks.size), Modifier.weight(1f))
                        OutlinedButton(onClick = { setCapture(false) }) { Text(stringResource(R.string.copy_finish_scroll)) }
                        IconButton(onClick = { close() }) { Icon(painterResource(R.drawable.ic_close_24), stringResource(R.string.action_close)) }
                    } }
                } else CopySessionPanel(
                    onScrollCapture = { setCapture(true) },
                    state = state,
                    onToggle = ::toggle,
                    onToggleAll = ::toggleAll,
                    onCopy = ::copy,
                    onOpenTextSelection = ::openTextSelection,
                    onDraftChange = ::updateDraft,
                    onReadingOrderChange = ::setReadingOrder,
                    onBack = ::backToEditor,
                    onClose = { close() },
                )
            }
        }
    }

    fun close(notify: Boolean = true, reason: String = "user_close") {
        if (packageName.isNotBlank()) {
            log("end", reason)
            saveSession(historyId, mutableUiState.value)
        }
        dismissOverlay(notify = notify)
    }

    private fun dismissOverlay(notify: Boolean) {
        val packageName = mutableUiState.value.packageName.takeIf(String::isNotBlank)
        overlayWindow?.dismiss()
        overlayWindow = null
        isCapturing = false
        capture = null
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
        clipboard.setPrimaryClip(ClipData.newPlainText(service.getString(R.string.global_copy_tile_label), text))
        Toast.makeText(service, service.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
        log("copy", text)
        close(reason = "copied")
    }

    @Suppress("DEPRECATION")
    private fun copyOverlayLayoutParams(): WindowManager.LayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        if (isCapturing) WindowManager.LayoutParams.WRAP_CONTENT else WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        if (isCapturing) WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL else WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = (if (isCapturing) Gravity.BOTTOM else Gravity.TOP) or Gravity.START
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
    onScrollCapture: (() -> Unit)? = null,
    onSave: (() -> Unit)? = null,
    saveEnabled: Boolean = true,
    onDelete: (() -> Unit)? = null,
    title: String? = null,
) {
    val selectedCount = state.selectedIds.size
    val isSelectingText = state.screen == CopySessionScreen.TEXT_SELECTION

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OverlayScrim)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 24.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    title = {
                        Text(
                            text = if (isSelectingText) {
                                stringResource(R.string.copy_select_text)
                            } else {
                                title ?: stringResource(R.string.global_copy_tile_label)
                            },
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
                                contentDescription = stringResource(
                                    if (isSelectingText) R.string.navigation_back else R.string.action_close,
                                ),
                            )
                        }
                    },
                    actions = {
                        if (isSelectingText) {
                            val allSelected =
                                selectedCount == state.blocks.size && state.blocks.isNotEmpty()
                            IconButton(onClick = onToggleAll) {
                                Icon(
                                    imageVector = if (allSelected) {
                                        Icons.Outlined.Deselect
                                    } else {
                                        Icons.Outlined.SelectAll
                                    },
                                    contentDescription = stringResource(
                                        if (allSelected) R.string.action_clear else R.string.action_select_all,
                                    ),
                                )
                            }
                        } else {
                            if (onDelete != null) {
                                IconButton(onClick = onDelete) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_delete_24),
                                        contentDescription = stringResource(R.string.action_delete),
                                    )
                                }
                            }
                            if (onSave != null) {
                                IconButton(
                                    enabled = saveEnabled,
                                    onClick = onSave,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = stringResource(R.string.action_save),
                                    )
                                }
                            }
                        }
                    },
                )

                if (isSelectingText) {
                    CopyTextSelectionList(
                        blocks = state.blocks,
                        selectedIds = state.selectedIds,
                        onToggle = onToggle,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                } else {
                    // Stable field height + scroll; only the middle region reflows with IME.
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = state.useReadingOrder,
                                    role = Role.Switch,
                                    onValueChange = onReadingOrderChange,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.copy_reading_order),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Switch(
                                checked = state.useReadingOrder,
                                onCheckedChange = null,
                            )
                        }
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onOpenTextSelection,
                        ) {
                            Text(stringResource(R.string.copy_select_text_count, selectedCount))
                        }
                        if (onScrollCapture != null) OutlinedButton(onClick = onScrollCapture, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.copy_scroll)) }
                        OutlinedTextField(
                            value = state.currentDraftText,
                            onValueChange = onDraftChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 180.dp),
                            label = { Text(stringResource(R.string.copy_text_label)) },
                            minLines = 6,
                        )
                    }
                    CopyBottomBar(
                        enabled = state.currentDraftText.isNotBlank(),
                        onCopy = onCopy,
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
            Text(stringResource(R.string.action_copy))
        }
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
                    Text(stringResource(R.string.copy_no_accessible_text))
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
