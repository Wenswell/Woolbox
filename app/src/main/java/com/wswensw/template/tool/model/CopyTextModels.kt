package com.wswensw.template.tool.model

import java.text.Normalizer
import kotlin.math.abs

data class ScreenBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val height: Int
        get() = bottom - top

    val centerY: Double
        get() = (top + bottom) / 2.0
}

enum class TextSource {
    TEXT,
    CONTENT_DESCRIPTION,
    HINT,
    STATE_DESCRIPTION,
    ERROR,
    TOOLTIP,
    PANE_TITLE,
    CONTAINER_TITLE,
}

data class CopyTextBlock(
    val id: String,
    val text: String,
    val source: TextSource,
    val bounds: ScreenBounds,
    val windowId: Int,
    val captureOrder: Int,
)

data class TextSnapshot(
    val packageName: String,
    val blocks: List<CopyTextBlock>,
)

enum class CopyTextOrder {
    CAPTURE,
    READING,
}

object CopyTextSelection {
    fun build(
        blocks: List<CopyTextBlock>,
        order: CopyTextOrder,
    ): String {
        val uniqueBlocks = when (order) {
            CopyTextOrder.CAPTURE -> blocks.sortedBy(CopyTextBlock::captureOrder)
            CopyTextOrder.READING -> blocks.sortedForReadingOrder()
        }.distinctBy(CopyTextBlock::copyDedupeKey)

        return when (order) {
            CopyTextOrder.CAPTURE -> uniqueBlocks.joinToString("\n") { it.text.trim() }
            CopyTextOrder.READING -> uniqueBlocks
                .groupIntoVisualRows()
                .joinToString("\n") { row -> row.joinToString(" ") { it.text.trim() } }
        }.trim()
    }

    fun selected(
        blocks: List<CopyTextBlock>,
        selectedIds: Set<String>,
        order: CopyTextOrder,
    ): String = build(blocks.filter { it.id in selectedIds }, order)
}

fun String.normalizedCopyText(): String =
    Normalizer.normalize(trim(), Normalizer.Form.NFKC).replace(WhitespaceRegex, " ")

private fun CopyTextBlock.copyDedupeKey(): String =
    "$source|${text.normalizedCopyText()}"

private fun List<CopyTextBlock>.sortedForReadingOrder(): List<CopyTextBlock> =
    sortedWith(
        compareBy<CopyTextBlock> { it.bounds.top }
            .thenBy { it.bounds.left }
            .thenBy { it.bounds.bottom }
            .thenBy { it.bounds.right }
            .thenBy { it.text.normalizedCopyText() }
            .thenBy(CopyTextBlock::captureOrder),
    )

private fun List<CopyTextBlock>.groupIntoVisualRows(): List<List<CopyTextBlock>> {
    if (isEmpty()) return emptyList()

    val rows = mutableListOf<MutableList<CopyTextBlock>>()
    forEach { block ->
        val currentRow = rows.lastOrNull()
        if (currentRow != null && currentRow.belongsToVisualRow(block)) {
            currentRow += block
        } else {
            rows += mutableListOf(block)
        }
    }

    return rows.map { row ->
        row.sortedWith(
            compareBy<CopyTextBlock> { it.bounds.left }
                .thenBy { it.bounds.top }
                .thenBy { it.bounds.right }
                .thenBy { it.text.normalizedCopyText() }
                .thenBy(CopyTextBlock::captureOrder),
        )
    }
}

private fun List<CopyTextBlock>.belongsToVisualRow(block: CopyTextBlock): Boolean {
    val rowCenter = sumOf { it.bounds.top + it.bounds.bottom }.toDouble() / (size * 2)
    val rowMinHeight = minOf { it.bounds.height }
    val threshold = maxOf(32.0, minOf(rowMinHeight, block.bounds.height) * 0.8)
    return abs(rowCenter - block.bounds.centerY) <= threshold
}

private val WhitespaceRegex = Regex("\\s+")
