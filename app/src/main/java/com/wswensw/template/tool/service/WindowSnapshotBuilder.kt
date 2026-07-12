package com.wswensw.template.tool.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.wswensw.template.tool.model.CopyTextBlock
import com.wswensw.template.tool.model.ScreenBounds
import com.wswensw.template.tool.model.TextSnapshot
import com.wswensw.template.tool.model.TextSource
import com.wswensw.template.tool.model.normalizedCopyText

class WindowSnapshotBuilder(
    private val service: AccessibilityService,
) {
    fun build(target: ForegroundTarget): TextSnapshot {
        val blocks = mutableListOf<CopyTextBlock>()
        service.windows
            .asSequence()
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .forEachIndexed { rootIndex, window ->
                val root = window.root ?: return@forEachIndexed
                if (root.packageName?.toString() == target.packageName) {
                    traverse(
                        node = root,
                        blocks = blocks,
                        rootIndex = rootIndex,
                    )
                }
            }
        val activeRoot = service.getRootInActiveWindow(NODE_PREFETCH_FLAGS)
        if (
            activeRoot?.packageName?.toString() == target.packageName &&
            blocks.none { it.windowId == activeRoot.windowId }
        ) {
            traverse(
                node = activeRoot,
                blocks = blocks,
                rootIndex = service.windows.size,
            )
        }

        val ordered = blocks
            .distinctBy { block ->
                listOf(
                    block.windowId,
                    block.source,
                    block.text.normalizedCopyText(),
                    block.bounds.left,
                    block.bounds.top,
                    block.bounds.right,
                    block.bounds.bottom,
                ).joinToString("|")
            }
            .sortedWith(
                compareBy<CopyTextBlock> { it.bounds.top }
                    .thenBy { it.bounds.left }
                    .thenBy { it.bounds.bottom }
                    .thenBy { it.bounds.right }
                    .thenBy { it.text.normalizedCopyText() },
            )
            .mapIndexed { index, block -> block.copy(captureOrder = index) }

        return TextSnapshot(
            packageName = target.packageName,
            blocks = ordered,
        )
    }

    private fun traverse(
        node: AccessibilityNodeInfo,
        blocks: MutableList<CopyTextBlock>,
        rootIndex: Int,
    ) {
        val bounds = Rect().also(node::getBoundsInScreen)
        val candidate = node.textCandidate()
        if (
            node.isVisibleToUser &&
            !node.isPassword &&
            !bounds.isEmpty &&
            candidate != null
        ) {
            blocks += CopyTextBlock(
                id = "${node.windowId}-$rootIndex-${blocks.size}",
                text = candidate.text,
                source = candidate.source,
                bounds = ScreenBounds(
                    left = bounds.left,
                    top = bounds.top,
                    right = bounds.right,
                    bottom = bounds.bottom,
                ),
                windowId = node.windowId,
                captureOrder = blocks.size,
            )
        }

        repeat(node.childCount) { index ->
            val child = node.getChild(index) ?: return@repeat
            traverse(child, blocks, rootIndex)
        }
    }

    private fun AccessibilityNodeInfo.textCandidate(): TextCandidate? = listOfNotNull(
        candidate(TextSource.TEXT, text),
        candidate(TextSource.CONTENT_DESCRIPTION, contentDescription),
        candidate(TextSource.HINT, hintText),
        candidate(TextSource.STATE_DESCRIPTION, stateDescription),
        candidate(TextSource.ERROR, error),
        candidate(TextSource.TOOLTIP, tooltipText),
        candidate(TextSource.PANE_TITLE, paneTitle),
        candidate(TextSource.CONTAINER_TITLE, containerTitle),
    ).firstOrNull()

    private fun candidate(
        source: TextSource,
        value: CharSequence?,
    ): TextCandidate? = value
        ?.toString()
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.let { TextCandidate(source, it) }

    private data class TextCandidate(
        val source: TextSource,
        val text: String,
    )

    private companion object {
        const val NODE_PREFETCH_FLAGS =
            AccessibilityNodeInfo.FLAG_PREFETCH_ANCESTORS or
                AccessibilityNodeInfo.FLAG_PREFETCH_SIBLINGS or
                AccessibilityNodeInfo.FLAG_PREFETCH_DESCENDANTS_HYBRID
    }
}
