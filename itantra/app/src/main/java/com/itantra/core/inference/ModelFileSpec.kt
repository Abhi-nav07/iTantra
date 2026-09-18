package com.itantra.core.inference

import com.itantra.domain.model.LanguageCode

data class ModelFileSpec(
    val type: EngineType,
    val languageCode: LanguageCode,
    val requiredFiles: List<String>,
    val mainModelFile: String,
    val tokensFile: String,
    val auxFile: String? = null
) {
    enum class EngineType { STT, TTS }
}

object ModelFileSpecs {
    fun getSttSpec(lang: LanguageCode): ModelFileSpec? {
        if (lang != LanguageCode.HINDI && lang != LanguageCode.ENGLISH) return null

        return ModelFileSpec(
            type = ModelFileSpec.EngineType.STT,
            languageCode = lang,
            requiredFiles = listOf(
                "tiny-encoder.int8.onnx",
                "tiny-decoder.int8.onnx",
                "tiny-tokens.txt"
            ),
            mainModelFile = "tiny-encoder.int8.onnx",
            auxFile = "tiny-decoder.int8.onnx",
            tokensFile = "tiny-tokens.txt"
        )
    }

    fun getTtsSpec(lang: LanguageCode): ModelFileSpec? {
        val prefix = when (lang) {
            LanguageCode.HINDI -> "hi_IN-pratham-medium"
            LanguageCode.ENGLISH -> "en_US-amy-medium"
            else -> return null
        }
        val modelFile = "$prefix.onnx"

        return ModelFileSpec(
            type = ModelFileSpec.EngineType.TTS,
            languageCode = lang,
            requiredFiles = listOf(
                modelFile,
                "tokens.txt"
            ),
            mainModelFile = modelFile,
            tokensFile = "tokens.txt",
            auxFile = ""
        )
    }
}
