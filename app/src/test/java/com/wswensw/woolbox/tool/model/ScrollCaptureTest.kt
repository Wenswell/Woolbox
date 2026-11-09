package com.wswensw.woolbox.tool.model

import org.junit.Assert.*
import org.junit.Test

class ScrollCaptureTest {
    private fun block(text: String, y: Int = 0, source: TextSource = TextSource.TEXT) =
        CopyTextBlock("node", text, source, ScreenBounds(0, y, 100, y + 20), 1, y)

    @Test fun overlappingScreensAndBacktrackingOnlyAppendNewText() {
        val capture = ScrollCapture(listOf(block("A"), block("B", 50)))
        val decisions = capture.append(listOf(block("B"), block("C", 50)))
        assertEquals(listOf(false, true), decisions.map { it.added })
        assertEquals(listOf("A", "B", "C"), capture.blocks.map { it.text })
        assertTrue(capture.append(listOf(block("A"), block("B", 50))).none { it.added })
        assertEquals("A\nB\nC", CopyTextSelection.build(capture.blocks, CopyTextOrder.READING))
        assertEquals(3, capture.blocks.map { it.id }.distinct().size)
    }

    @Test fun normalizationAndSourceMatchCopyRules() {
        val capture = ScrollCapture(listOf(block("Ａ  B")))
        assertFalse(capture.append(listOf(block("A\nB"))).single().added)
        assertTrue(capture.append(listOf(block("A B", source = TextSource.CONTENT_DESCRIPTION))).single().added)
        assertEquals(2, capture.blocks.size)
    }

    @Test fun emptySampleKeepsSession() {
        val capture = ScrollCapture(listOf(block("A")))
        assertTrue(capture.append(emptyList()).isEmpty())
        assertEquals("A", capture.blocks.single().text)
    }
}
