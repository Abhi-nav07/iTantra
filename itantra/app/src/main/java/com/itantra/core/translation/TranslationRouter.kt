package com.itantra.core.translation

import com.itantra.domain.model.LanguageCode

class TranslationRouter(
    private val engine: TranslationEngine
) {
    suspend fun routeAndTranslate(
        text: String,
        source: LanguageCode,
        target: LanguageCode
    ): TranslationResult {
        if (source == target) {
            return TranslationResult(text, text, true, source, target)
        }

        if (!engine.supportedSourceLanguages.contains(source) || !engine.supportedTargetLanguages.contains(target)) {
            return TranslationResult(text, "", false, source, target, error = "UNSUPPORTED_ROUTE")
        }

        return try {
            engine.translate(text, source, target)
        } catch (e: Exception) {
            e.printStackTrace()
            TranslationResult(text, "", false, source, target, error = "INFERENCE_FAILED")
        }
    }
}
