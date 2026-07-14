package com.wswensw.template.tool.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundTargetSelectorTest {
    @Test
    fun activeWindowWinsOverHigherInactiveWindow() {
        val selected = ForegroundTargetSelector.select(
            listOf(
                candidate("inactive", layer = 20),
                candidate("active", isActive = true, layer = 2),
            ),
        )

        assertEquals("active", selected)
    }

    @Test
    fun highestLayerWinsWhenPlatformReportsNoActiveFlags() {
        val selected = ForegroundTargetSelector.select(
            listOf(
                candidate("recent-one", layer = 10),
                candidate("recent-two", layer = 20),
            ),
        )

        assertEquals("recent-two", selected)
    }

    @Test
    fun focusedWindowIsAcceptedWhenActiveFlagIsTemporarilyMissing() {
        val selected = ForegroundTargetSelector.select(
            listOf(
                candidate("focused", isFocused = true, layer = 4),
            ),
        )

        assertEquals("focused", selected)
    }

    @Test
    fun focusedPopupDoesNotReplaceActiveApplication() {
        val selected = ForegroundTargetSelector.select(
            listOf(
                candidate("active", isActive = true, layer = 2),
                candidate("popup", isFocused = true, layer = 8),
            ),
        )

        assertEquals("active", selected)
    }

    @Test
    fun noApplicationCandidateReturnsNoTarget() {
        assertNull(ForegroundTargetSelector.select(emptyList()))
    }

    private fun candidate(
        packageName: String,
        isActive: Boolean = false,
        isFocused: Boolean = false,
        layer: Int,
    ) = ForegroundTargetCandidate(
        packageName = packageName,
        isActive = isActive,
        isFocused = isFocused,
        layer = layer,
    )
}
