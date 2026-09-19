package com.itantra.core.translation

import com.itantra.domain.model.LanguageCode

/**
 * Standard Indic script transliterator for IndicTrans2 architecture.
 *
 * IndicTrans2 models tokenize and process all Indic languages internally in
 * unified Devanagari script. Non-Devanagari Brahmic scripts (Bengali, Gujarati,
 * Odia, Tamil, Telugu, Kannada, Malayalam) share coordinated Unicode block
 * layouts and are transliterated to/from Devanagari.
 */
object IndicScriptTransliterator {

    private val SCRIPT_RANGES = mapOf(
        LanguageCode.HINDI to (0x0900..0x097F),
        LanguageCode.MARATHI to (0x0900..0x097F),
        LanguageCode.BENGALI to (0x0980..0x09FF),
        LanguageCode.GUJARATI to (0x0A80..0x0AFF),
        LanguageCode.ODIA to (0x0B00..0x0B7F),
        LanguageCode.TAMIL to (0x0B80..0x0BFF),
        LanguageCode.TELUGU to (0x0C00..0x0C7F),
        LanguageCode.KANNADA to (0x0C80..0x0CFF),
        LanguageCode.MALAYALAM to (0x0D00..0x0D7F)
    )

    private const val DEVANAGARI_BASE = 0x0900
    private const val COORDINATED_RANGE_START = 0
    private const val COORDINATED_RANGE_END = 111 // 0x6F

    /**
     * Translates a non-Devanagari Indic text into Devanagari script for IndicTrans2 input.
     */
    fun toDevanagari(text: String, srcLang: LanguageCode): String {
        if (srcLang == LanguageCode.ENGLISH || srcLang == LanguageCode.HINDI || srcLang == LanguageCode.MARATHI) {
            return text
        }
        val range = SCRIPT_RANGES[srcLang] ?: return text
        val base = range.first
        val sb = StringBuilder(text.length)
        for (c in text) {
            val code = c.code
            val offset = code - base
            if (offset in COORDINATED_RANGE_START..COORDINATED_RANGE_END && c != '\u0964' && c != '\u0965') {
                sb.append((DEVANAGARI_BASE + offset).toChar())
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    /**
     * Translates Devanagari text back into the target Indic script for display / TTS.
     */
    fun fromDevanagari(text: String, tgtLang: LanguageCode): String {
        if (tgtLang == LanguageCode.ENGLISH || tgtLang == LanguageCode.HINDI || tgtLang == LanguageCode.MARATHI) {
            return text
        }
        val range = SCRIPT_RANGES[tgtLang] ?: return text
        val base = range.first
        val sb = StringBuilder(text.length)
        for (c in text) {
            val code = c.code
            var offset = code - DEVANAGARI_BASE
            if (offset in COORDINATED_RANGE_START..COORDINATED_RANGE_END && c != '\u0964' && c != '\u0965') {
                if (tgtLang == LanguageCode.TAMIL) {
                    offset = correctTamilMapping(offset)
                }
                sb.append((base + offset).toChar())
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun correctTamilMapping(offset: Int): Int {
        var off = offset
        if (off in 0x15..0x28 && off != 0x1C && !((off - 0x15) % 5 == 0 || (off - 0x15) % 5 == 4)) {
            val substChar = (off - 0x15) / 5
            off = 0x15 + 5 * substChar
        }
        if (off in listOf(0x2B, 0x2C, 0x2D)) {
            off = 0x2A
        }
        if (off == 0x36) {
            off = 0x37
        }
        return off
    }
}
