package com.wswensw.template.tool.overlay

import com.wswensw.template.tool.model.CopyTextBlock
import com.wswensw.template.tool.model.CopySessionScreen
import com.wswensw.template.tool.model.ScreenBounds
import com.wswensw.template.tool.model.TextSnapshot
import com.wswensw.template.tool.model.TextSource
import com.wswensw.template.tool.model.initialCopySessionUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class CopySessionUiStateTest {
    @Test
    fun sessionStartsInEditorWithEveryBlockAndReadingOrder() {
        val blocks = blocksWithDifferentCaptureAndReadingOrders()

        val state = TextSnapshot(packageName = "example.app", blocks = blocks)
            .initialCopySessionUiState()

        assertEquals(CopySessionScreen.EDITOR, state.screen)
        assertEquals(setOf("later", "earlier"), state.selectedIds)
        assertEquals(true, state.useReadingOrder)
        assertEquals("Earlier\nLater", state.currentDraftText)
        assertEquals("Later\nEarlier", state.captureDraftText)
    }

    @Test
    fun orderSwitchKeepsEditsInBothDrafts() {
        val blocks = blocksWithDifferentCaptureAndReadingOrders()
        val readingDraft = TextSnapshot(packageName = "example.app", blocks = blocks)
            .initialCopySessionUiState()
            .updateCurrentDraft("Edited reading")
        val captureDraft = readingDraft
            .copy(useReadingOrder = false)
            .updateCurrentDraft("Edited capture")

        assertEquals("Edited capture", captureDraft.currentDraftText)
        assertEquals(
            "Edited reading",
            captureDraft.copy(useReadingOrder = true).currentDraftText,
        )
    }

    private fun blocksWithDifferentCaptureAndReadingOrders(): List<CopyTextBlock> = listOf(
        block(
            id = "later",
            text = "Later",
            top = 100,
            captureOrder = 0,
        ),
        block(
            id = "earlier",
            text = "Earlier",
            top = 0,
            captureOrder = 1,
        ),
    )

    private fun block(
        id: String,
        text: String,
        top: Int,
        captureOrder: Int,
    ): CopyTextBlock = CopyTextBlock(
        id = id,
        text = text,
        source = TextSource.TEXT,
        bounds = ScreenBounds(
            left = 0,
            top = top,
            right = 100,
            bottom = top + 20,
        ),
        windowId = 1,
        captureOrder = captureOrder,
    )
}
