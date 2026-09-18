package com.itantra.core.inference

import com.itantra.domain.model.LanguageCode

data class ModelFileSpec(
    val type: EngineType,
    val languageCode: LanguageCode,
    val requiredFiles: List<String>,
    val mainModelFile: String,
    val tokensFile: String,
    val auxFile: String? = null // e.g., decoder for STT, or dataDir/json for TTS
) {
    enum class EngineType { STT, TTS }
}

object ModelFileSpecs {
    fun getSttSpec(lang: LanguageCode): ModelFileSpec {
        // We use csukuangfj/sherpa-onnx-whisper-tiny for both EN and HI
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

    fun getTtsSpec(lang: LanguageCode): ModelFileSpec {
        val prefix = if (lang == LanguageCode.HINDI) "hi_IN-pratham-medium" else "en_US-amy-medium"
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
            auxFile = "" // Not strictly requiring espeak-ng-data in basic file check to avoid 100-file checks
        )
    }
}
