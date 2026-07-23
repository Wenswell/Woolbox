package com.wswensw.woolbox.tool.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CopyTextSelectionTest {
    @Test
    fun captureOrderNormalizesAndDeduplicatesText() {
        val blocks = listOf(
            block(id = "first", text = "  Full\u3000width  ", left = 0, top = 0, order = 0),
            block(id = "second", text = "Full width", left = 100, top = 0, order = 1),
            block(id = "third", text = "Later", left = 0, top = 40, order = 2),
        )

        assertEquals(
            "Full　width\nLater",
            CopyTextSelection.build(blocks, CopyTextOrder.CAPTURE),
        )
    }

    @Test
    fun differentSemanticSourcesRemainSeparate() {
        val blocks = listOf(
            block(id = "text", text = "Ready", left = 0, top = 0, order = 0),
            block(
                id = "state",
                text = "Ready",
                left = 0,
                top = 40,
                order = 1,
                source = TextSource.STATE_DESCRIPTION,
            ),
        )

        assertEquals(
            "Ready\nReady",
            CopyTextSelection.build(blocks, CopyTextOrder.CAPTURE),
        )
    }

    @Test
    fun readingOrderGroupsRowsAndSortsLeftToRight() {
        val blocks = listOf(
            block(id = "right", text = "right", left = 180, top = 8, order = 0),
            block(id = "next", text = "next", left = 0, top = 70, order = 1),
            block(id = "left", text = "left", left = 0, top = 4, order = 2),
        )

        assertEquals(
            "left right\nnext",
            CopyTextSelection.build(blocks, CopyTextOrder.READING),
        )
    }

    @Test
    fun captureOrderKeepsFirstOccurrenceBeforeReadingSort() {
        val blocks = listOf(
            block(id = "beta-first", text = "Beta", left = 0, top = 80, order = 0),
            block(id = "alpha", text = "Alpha", left = 0, top = 20, order = 1),
            block(id = "beta-second", text = "Beta", left = 0, top = 120, order = 2),
        )

        assertEquals(
            "Beta\nAlpha",
            CopyTextSelection.build(blocks, CopyTextOrder.CAPTURE),
        )
    }

    @Test
    fun readingOrderSortsBeforeDroppingDuplicates() {
        val blocks = listOf(
            block(id = "beta-first", text = "Beta", left = 0, top = 80, order = 0),
            block(id = "alpha", text = "Alpha", left = 0, top = 20, order = 1),
            block(id = "beta-second", text = "Beta", left = 0, top = 120, order = 2),
        )

        assertEquals(
            "Alpha\nBeta",
            CopyTextSelection.build(blocks, CopyTextOrder.READING),
        )
    }

    private fun block(
        id: String,
        text: String,
        left: Int,
        top: Int,
        order: Int,
        source: TextSource = TextSource.TEXT,
    ) = CopyTextBlock(
        id = id,
        text = text,
        source = source,
        bounds = ScreenBounds(left, top, left + 80, top + 24),
        windowId = 1,
        captureOrder = order,
    )
}
