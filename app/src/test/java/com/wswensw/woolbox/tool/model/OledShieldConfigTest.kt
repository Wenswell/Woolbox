package com.wswensw.woolbox.tool.model

import org.junit.Assert.assertEquals
import org.junit.Test

class OledShieldConfigTest {
    @Test
    fun normalizationClampsEveryPersistedValue() {
        val normalized = OledShieldConfig(
            opacityPercent = 0,
            widthPercent = 130,
            heightPercent = -3,
            panelXPercent = -0.2f,
            panelYPercent = 1.5f,
        ).normalized()

        assertEquals(1, normalized.opacityPercent)
        assertEquals(100, normalized.widthPercent)
        assertEquals(1, normalized.heightPercent)
        assertEquals(0f, normalized.panelXPercent)
        assertEquals(1f, normalized.panelYPercent)
    }

    @Test
    fun defaultMatchesFullWidthFifteenPercentShield() {
        assertEquals(100, OledShieldConfig.Default.opacityPercent)
        assertEquals(100, OledShieldConfig.Default.widthPercent)
        assertEquals(15, OledShieldConfig.Default.heightPercent)
    }
}
