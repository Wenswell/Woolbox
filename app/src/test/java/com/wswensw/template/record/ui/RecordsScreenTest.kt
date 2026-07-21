package com.wswensw.template.record.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordsScreenTest {
    @Test
    fun firstRecordCharacterKeepsTextCharacter() {
        assertEquals("阅", firstRecordCharacter("阅读"))
    }

    @Test
    fun firstRecordCharacterKeepsEmojiCharacter() {
        assertEquals("💧", firstRecordCharacter("💧 Water"))
    }

    @Test
    fun firstRecordCharacterKeepsCombiningCharacterSequence() {
        assertEquals("e\u0301", firstRecordCharacter("e\u0301 Accent"))
    }

    @Test
    fun firstRecordCharacterHandlesEmptyName() {
        assertEquals("", firstRecordCharacter(""))
    }
}
