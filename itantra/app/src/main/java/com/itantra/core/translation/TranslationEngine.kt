package com.itantra.core.translation

import com.itantra.domain.model.LanguageCode
import java.io.File

interface TranslationEngine {
    val isLoaded: Boolean
    val supportedSourceLanguages: Set<LanguageCode>
    val supportedTargetLanguages: Set<LanguageCode>

    fun init(modelsDir: File)
    fun release()

    suspend fun translate(
        text: String,
        sourceLang: LanguageCode,
        targetLang: LanguageCode
    ): TranslationResult
}
