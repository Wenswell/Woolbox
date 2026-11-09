package com.wswensw.woolbox.tool.model

/** Session order is first appearance; geometry stays ordered between successive screens. */
class ScrollCapture(initial: List<CopyTextBlock>) {
    private val keys = linkedSetOf<String>()
    private val collected = mutableListOf<CopyTextBlock>()
    val blocks: List<CopyTextBlock> get() = collected.toList()
    init { append(initial) }

    fun append(sample: List<CopyTextBlock>): List<CaptureDecision> {
        val offset = if (collected.isEmpty()) 0 else collected.maxOf { it.bounds.bottom } + 64
        val origin = sample.minOfOrNull { it.bounds.top } ?: 0
        return sample.sortedBy { it.captureOrder }.map { block ->
            val key = "${block.source}|${block.text.normalizedCopyText()}"
            val added = keys.add(key)
            if (added) {
                val position = collected.size
                collected += block.copy(id = "capture-$position", captureOrder = position,
                    bounds = block.bounds.copy(top = block.bounds.top - origin + offset,
                        bottom = block.bounds.bottom - origin + offset))
            }
            CaptureDecision(block, key, added)
        }
    }
}

data class CaptureDecision(val block: CopyTextBlock, val key: String, val added: Boolean)
