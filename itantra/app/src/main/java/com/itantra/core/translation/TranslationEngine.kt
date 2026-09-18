package com.itantra.core.translation

import com.itantra.domain.model.LanguageCode

data class TranslationResult(
    val originalText: String,
    val translatedText: String,
    val isSuccessful: Boolean,
    val sourceLanguage: LanguageCode,
    val targetLanguage: LanguageCode,
    val error: String? = null
)

/**
 * Interface defining the boundary for the offline neural translation engine.
 * This abstracts away the underlying model (e.g., IndicTrans2) from the core transceiver.
 */
interface TranslationEngine {
    val isLoaded: Boolean
    val supportedSourceLanguages: Set<LanguageCode>
    val supportedTargetLanguages: Set<LanguageCode>

    fun init(modelsDir: java.io.File)
    fun release()

    /**
     * Translates the given text from the source language to the target language.
     * This function should be safe to call from coroutines and should not block the main thread.
     */
    suspend fun translate(
        text: String,
        sourceLang: LanguageCode,
        targetLang: LanguageCode
    ): TranslationResult
}
