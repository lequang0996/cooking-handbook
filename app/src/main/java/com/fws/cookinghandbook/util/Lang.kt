package com.fws.cookinghandbook.util

import android.content.Context
import java.util.Locale

/** The app's UI languages and how they map to language codes and TTS locales. */
object Lang {
    /** Language tag shown in the picker -> display name written in that language. */
    val options = listOf(
        "en" to "English", "zh-CN" to "简体中文", "es" to "Español", "pt" to "Português",
        "ru" to "Русский", "ja" to "日本語", "ko" to "한국어", "vi" to "Tiếng Việt",
    )

    /** Language code (zh, en, es, pt, ru, ja, ko, vi) actually in use by the UI right now. */
    fun current(context: Context): String {
        val loc = context.resources.configuration.locales[0]
        return when (loc.language) {
            "zh" -> if (loc.country in setOf("TW", "HK", "MO")) "en" else "zh"
            "es", "pt", "ru", "ja", "ko", "vi" -> loc.language
            else -> "en"
        }
    }

    /** Every language a recipe is translated into (the Chinese original needs no translation). */
    val targets = listOf("en", "es", "pt", "ru", "ja", "ko", "vi")

    fun ttsLocale(code: String): Locale = when (code) {
        "zh" -> Locale.SIMPLIFIED_CHINESE
        else -> Locale.forLanguageTag(code)
    }
}
