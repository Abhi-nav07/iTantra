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
