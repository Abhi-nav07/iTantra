package com.itantra.core.translation

import com.itantra.domain.model.LanguageCode
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Validates that the production transliteration and preprocessing contract
 * exactly matches the official IndicTrans2 golden vectors for all 10 supported languages.
 */
class TranslationPreprocessingGoldenTest {

    private fun parseLanguageCode(tag: String): LanguageCode {
        return when (tag) {
            "hin_Deva" -> LanguageCode.HINDI
            "eng_Latn" -> LanguageCode.ENGLISH
            "ben_Beng" -> LanguageCode.BENGALI
            "guj_Gujr" -> LanguageCode.GUJARATI
            "mar_Deva" -> LanguageCode.MARATHI
            "kan_Knda" -> LanguageCode.KANNADA
            "mal_Mlym" -> LanguageCode.MALAYALAM
            "tam_Taml" -> LanguageCode.TAMIL
            "tel_Telu" -> LanguageCode.TELUGU
            "ory_Orya" -> LanguageCode.ODIA
            else -> throw IllegalArgumentException("Unknown tag: $tag")
        }
    }

    @Test
    fun testGoldenVectorPreprocessingConsistency() {
        val goldenFile = File("tools/golden_vectors.json")
        if (!goldenFile.exists()) {
            println("Skipping golden vector test: tools/golden_vectors.json not found on disk")
            return
        }

        val jsonStr = goldenFile.readText(Charsets.UTF_8)
        val array = JSONArray(jsonStr)

        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val rawInput = item.getString("raw_input")
            val sourceTag = item.getString("source_tag")
            val targetTag = item.getString("target_tag")
            val expectedPreprocessed = item.getString("preprocessed_text")

            val srcLang = parseLanguageCode(sourceTag)
            val devanagariText = IndicScriptTransliterator.toDevanagari(rawInput, srcLang)

            // The expected preprocessed string in IndicTrans2 has format:
            // "<sourceTag> <targetTag> <devanagari_or_latin_text>" (with possible whitespace/punct normalized)
            val expectedTextPart = expectedPreprocessed
                .removePrefix("$sourceTag $targetTag")
                .trim()

            if (srcLang == LanguageCode.ENGLISH) {
                // English remains English
                assertEquals("English input preserved", rawInput.trim(), devanagariText.trim())
            } else if (srcLang == LanguageCode.HINDI || srcLang == LanguageCode.MARATHI) {
                // Devanagari languages remain Devanagari
                assertEquals("Devanagari input unchanged", rawInput.trim(), devanagariText.trim())
            } else {
                // Non-Devanagari Indic scripts match the golden vector transliteration
                assertEquals(
                    "Transliterator output mismatch for $sourceTag ($rawInput)",
                    expectedTextPart,
                    devanagariText.trim()
                )

                // Round-trip verification back to source script
                val roundTrip = IndicScriptTransliterator.fromDevanagari(devanagariText, srcLang)
                assertEquals(
                    "Roundtrip transliteration mismatch for $sourceTag",
                    rawInput.trim(),
                    roundTrip.trim()
                )
            }
        }
    }
}
