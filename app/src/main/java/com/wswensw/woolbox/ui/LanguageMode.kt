package com.wswensw.woolbox.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

enum class LanguageMode(val languageTag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    SIMPLIFIED_CHINESE("zh-CN"),
    ;

    fun apply() {
        AppCompatDelegate.setApplicationLocales(
            languageTag?.let(LocaleListCompat::forLanguageTags) ?: LocaleListCompat.getEmptyLocaleList(),
        )
    }

    companion object {
        fun current(): LanguageMode {
            val locales = AppCompatDelegate.getApplicationLocales()
            if (locales.isEmpty) return SYSTEM
            return fromLanguage(locales[0]?.language)
        }

        internal fun fromLanguage(language: String?): LanguageMode = when (language) {
            "zh" -> SIMPLIFIED_CHINESE
            else -> ENGLISH
        }
    }
}
