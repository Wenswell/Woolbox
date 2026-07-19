package com.wswensw.template.tool.service

import com.wswensw.template.tool.model.OledShieldConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCommandPolicyTest {
    @Test
    fun preparingAndActiveStatesMatchTheirCommand() {
        val profileCommand = profileCommand("com.example.reader")

        assertTrue(ToolActivity.COPY_PREPARING.matches(ExternalToolCommand.GLOBAL_COPY))
        assertTrue(ToolActivity.COPY_ACTIVE.matches(ExternalToolCommand.GLOBAL_COPY))
        assertTrue(ToolActivity.OLED_PREPARING.matches(ExternalToolCommand.OLED_SHIELD))
        assertTrue(ToolActivity.OLED_ACTIVE.matches(ExternalToolCommand.OLED_SHIELD))
        assertTrue(ToolActivity.OLED_PREPARING.matches(profileCommand))
        assertTrue(ToolActivity.OLED_ACTIVE.matches(profileCommand))
    }

    @Test
    fun idleAndOtherToolStatesDoNotMatchCommand() {
        val profileCommand = profileCommand("com.example.reader")

        assertFalse(ToolActivity.NONE.matches(ExternalToolCommand.GLOBAL_COPY))
        assertFalse(ToolActivity.NONE.matches(ExternalToolCommand.OLED_SHIELD))
        assertFalse(ToolActivity.NONE.matches(profileCommand))
        assertFalse(ToolActivity.COPY_PREPARING.matches(ExternalToolCommand.OLED_SHIELD))
        assertFalse(ToolActivity.COPY_ACTIVE.matches(ExternalToolCommand.OLED_SHIELD))
        assertFalse(ToolActivity.COPY_PREPARING.matches(profileCommand))
        assertFalse(ToolActivity.COPY_ACTIVE.matches(profileCommand))
        assertFalse(ToolActivity.OLED_PREPARING.matches(ExternalToolCommand.GLOBAL_COPY))
        assertFalse(ToolActivity.OLED_ACTIVE.matches(ExternalToolCommand.GLOBAL_COPY))
    }

    @Test
    fun profileOnlyAcceptsItsConfiguredPackage() {
        val command = profileCommand("com.example.reader")

        assertTrue(command.acceptsTarget("com.example.reader"))
        assertFalse(command.acceptsTarget("com.example.other"))
        assertTrue(ExternalToolCommand.GLOBAL_COPY.acceptsTarget("com.example.other"))
        assertTrue(ExternalToolCommand.OLED_SHIELD.acceptsTarget("com.example.other"))
    }

    @Test
    fun profileUsesIndependentFiveSecondTimeout() {
        assertEquals(ToolActivity.OLED_PREPARING, profileCommand("com.example.reader").preparingActivity())
        assertEquals(5_000L, profileCommand("com.example.reader").timeoutMillis())
        assertEquals(ToolActivity.COPY_PREPARING, ExternalToolCommand.GLOBAL_COPY.preparingActivity())
        assertEquals(ToolActivity.OLED_PREPARING, ExternalToolCommand.OLED_SHIELD.preparingActivity())
        assertEquals(2_000L, ExternalToolCommand.GLOBAL_COPY.timeoutMillis())
        assertEquals(2_000L, ExternalToolCommand.OLED_SHIELD.timeoutMillis())
    }

    private fun profileCommand(packageName: String) = ExternalToolCommand.OledShieldProfile(
        packageName = packageName,
        config = OledShieldConfig.Default,
    )
}
