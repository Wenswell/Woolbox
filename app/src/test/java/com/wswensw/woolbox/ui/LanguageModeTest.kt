package com.wswensw.woolbox.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageModeTest {
    @Test
    fun mapsChineseLocaleToSimplifiedChinese() {
        assertEquals(
            LanguageMode.SIMPLIFIED_CHINESE,
            LanguageMode.fromLanguage("zh"),
        )
    }

    @Test
    fun mapsOtherExplicitLocalesToEnglish() {
        assertEquals(LanguageMode.ENGLISH, LanguageMode.fromLanguage("en"))
        assertEquals(LanguageMode.ENGLISH, LanguageMode.fromLanguage("fr"))
    }
}
