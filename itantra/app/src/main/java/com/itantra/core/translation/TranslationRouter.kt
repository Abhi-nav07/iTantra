package com.itantra.core.translation

import android.annotation.SuppressLint
import com.itantra.domain.model.LanguageCode

/**
 * The TranslationRouter sits between the Secure Transport layer and the Text-To-Speech engine.
 * It is responsible for deciding whether a translation is required and handling fallbacks.
 */
class TranslationRouter(private val engine: TranslationEngine?) {

    /**
     * Routes the incoming text for translation if the source language differs from the target language.
     * If the translation engine is unavailable, fails, or the languages are the same,
     * it immediately returns the original text to guarantee uninterrupted communication.
     */
    suspend fun routeAndTranslate(
        text: String,
        source: LanguageCode,
        target: LanguageCode
    ): TranslationResult {
        if (source == target) {
            return TranslationResult(text, text, true, source, target)
        }

        if (engine == null) {
            return TranslationResult(text, "", false, source, target, error = "MODEL_NOT_INSTALLED")
        }

        if (!engine.isLoaded) {
            try {
                // Determine model dir. In a real app, this path comes from provisioning.
                @SuppressLint("SdCardPath")
                val modelsDir = java.io.File("/data/user/0/com.itantra.app/files/translation_models")
                engine.init(modelsDir)
            } catch (e: Exception) {
                // Models unavailable or not provisioned
                return TranslationResult(text, "", false, source, target, error = "MODEL_LOAD_FAILED")
            }
        }

        if (!engine.supportedSourceLanguages.contains(source) ||
            !engine.supportedTargetLanguages.contains(target)) {
            return TranslationResult(text, "", false, source, target, error = "UNSUPPORTED_ROUTE")
        }

        return try {
            val result = engine.translate(text, source, target)
            if (result.isSuccessful && result.translatedText.isNotBlank()) {
                result
            } else {
                TranslationResult(text, "", false, source, target, error = result.error ?: "TRANSLATION_FAILED")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // In case of any inference error (OOM, timeout, vocabulary error), fallback immediately
            TranslationResult(text, "", false, source, target, error = "INFERENCE_FAILED")
        }
    }
}
