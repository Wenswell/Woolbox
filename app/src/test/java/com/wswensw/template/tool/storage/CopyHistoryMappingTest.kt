package com.wswensw.template.tool.storage

import com.wswensw.template.tool.model.CopySessionScreen
import com.wswensw.template.tool.model.CopySessionUiState
import com.wswensw.template.tool.model.CopyTextBlock
import com.wswensw.template.tool.model.ScreenBounds
import com.wswensw.template.tool.model.TextSource
import org.junit.Assert.assertEquals
import org.junit.Test

class CopyHistoryMappingTest {
    @Test
    fun summaryMappingKeepsListFieldsWithoutSessionBlocks() {
        val summary = CopyHistorySummaryRow(
            id = 12L,
            packageName = "reader.app",
            capturedAt = 9_876L,
            textPreview = "Current edited draft",
            blockCount = 34,
        ).toModel()

        assertEquals(12L, summary.id)
        assertEquals("reader.app", summary.packageName)
        assertEquals(9_876L, summary.capturedAt)
        assertEquals("Current edited draft", summary.textPreview)
        assertEquals(34, summary.blockCount)
    }

    @Test
    fun sessionRoundTripPreservesBlocksSelectionDraftsAndOrder() {
        val state = CopySessionUiState(
            packageName = "example.app",
            blocks = listOf(
                block(
                    id = "description",
                    text = "Accessible description",
                    source = TextSource.CONTENT_DESCRIPTION,
                    bounds = ScreenBounds(left = 12, top = 24, right = 240, bottom = 72),
                    windowId = 7,
                    captureOrder = 1,
                ),
                block(
                    id = "label",
                    text = "Visible label",
                    source = TextSource.TEXT,
                    bounds = ScreenBounds(left = 8, top = 4, right = 180, bottom = 20),
                    windowId = 3,
                    captureOrder = 0,
                ),
            ),
            selectedIds = linkedSetOf("label"),
            screen = CopySessionScreen.TEXT_SELECTION,
            captureDraftText = "Edited capture",
            reorderedDraftText = "Edited reading",
            useReadingOrder = false,
        )
        val entry = state.toEntity(capturedAt = 1_234L, id = 9L)
        val blocks = state.toBlockEntities(historyId = 9L)

        val restored = CopyHistoryWithBlocks(
            entry = entry,
            blocks = blocks.reversed(),
        ).toModel()

        assertEquals(9L, restored.id)
        assertEquals(1_234L, restored.capturedAt)
        assertEquals("example.app", restored.session.packageName)
        assertEquals(state.blocks, restored.session.blocks)
        assertEquals(linkedSetOf("label"), restored.session.selectedIds)
        assertEquals("Edited capture", restored.session.captureDraftText)
        assertEquals("Edited reading", restored.session.reorderedDraftText)
        assertEquals(false, restored.session.useReadingOrder)
        assertEquals(CopySessionScreen.EDITOR, restored.session.screen)
    }

    @Test
    fun blockMappingPersistsEveryBlockFieldAndSelectionState() {
        val block = block(
            id = "tooltip",
            text = "More information",
            source = TextSource.TOOLTIP,
            bounds = ScreenBounds(left = -20, top = 30, right = 400, bottom = 90),
            windowId = 18,
            captureOrder = 6,
        )
        val state = CopySessionUiState(
            packageName = "example.app",
            blocks = listOf(block),
            selectedIds = setOf(block.id),
        )

        val entity = state.toBlockEntities(historyId = 44L).single()

        assertEquals(44L, entity.historyId)
        assertEquals(0, entity.position)
        assertEquals(block.id, entity.blockId)
        assertEquals(block.text, entity.text)
        assertEquals(block.source.name, entity.source)
        assertEquals(block.bounds.left, entity.left)
        assertEquals(block.bounds.top, entity.top)
        assertEquals(block.bounds.right, entity.right)
        assertEquals(block.bounds.bottom, entity.bottom)
        assertEquals(block.windowId, entity.windowId)
        assertEquals(block.captureOrder, entity.captureOrder)
        assertEquals(true, entity.selected)
    }

    private fun block(
        id: String,
        text: String,
        source: TextSource,
        bounds: ScreenBounds,
        windowId: Int,
        captureOrder: Int,
    ) = CopyTextBlock(
        id = id,
        text = text,
        source = source,
        bounds = bounds,
        windowId = windowId,
        captureOrder = captureOrder,
    )
}
