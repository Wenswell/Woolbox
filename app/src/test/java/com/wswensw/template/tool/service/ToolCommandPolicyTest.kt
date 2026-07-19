package com.wswensw.template.tool.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCommandPolicyTest {
    @Test
    fun preparingAndActiveStatesMatchTheirCommand() {
        assertTrue(ToolActivity.COPY_PREPARING.matches(ExternalToolCommand.GLOBAL_COPY))
        assertTrue(ToolActivity.COPY_ACTIVE.matches(ExternalToolCommand.GLOBAL_COPY))
        assertTrue(ToolActivity.OLED_PREPARING.matches(ExternalToolCommand.OLED_SHIELD))
        assertTrue(ToolActivity.OLED_ACTIVE.matches(ExternalToolCommand.OLED_SHIELD))
    }

    @Test
    fun idleAndOtherToolStatesDoNotMatchCommand() {
        assertFalse(ToolActivity.NONE.matches(ExternalToolCommand.GLOBAL_COPY))
        assertFalse(ToolActivity.NONE.matches(ExternalToolCommand.OLED_SHIELD))
        assertFalse(ToolActivity.COPY_PREPARING.matches(ExternalToolCommand.OLED_SHIELD))
        assertFalse(ToolActivity.COPY_ACTIVE.matches(ExternalToolCommand.OLED_SHIELD))
        assertFalse(ToolActivity.OLED_PREPARING.matches(ExternalToolCommand.GLOBAL_COPY))
        assertFalse(ToolActivity.OLED_ACTIVE.matches(ExternalToolCommand.GLOBAL_COPY))
    }
}
