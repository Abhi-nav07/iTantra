package com.itantra.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class NoiseCondition {
    QUIET,
    MODERATE_NOISE,
    HIGH_NOISE
}

@Serializable
enum class EvidenceLevel {
    SOURCE_ONLY,
    HOST_TESTED,
    DEVICE_TESTED,
    HUMAN_REVIEWED
}

@Serializable
data class BenchmarkSession(
    val timestampMs: Long,
    val language: String,
    val deviceManufacturer: String = "",
    val deviceModel: String = "",
    val androidVersion: String = "",
    val abi: String = "",
    val threadCount: Int = 1,
    val modelVersion: String = "Whisper Tiny Multilingual INT8 ONNX",
    val noiseCondition: NoiseCondition = NoiseCondition.QUIET,
    val totalUtterances: Int = 0,
    val totalReferenceWords: Int = 0,
    val substitutions: Int = 0,
    val deletions: Int = 0,
    val insertions: Int = 0,
    val corpusWer: Float = 0f,
    val meanSentenceWer: Float = 0f,
    val medianFinalizationLatencyMs: Long = 0L,
    val meanFinalizationLatencyMs: Long = 0L,
    val totalAudioDurationMs: Long = 0L,
    val criticalPhraseCount: Int = 0,
    val criticalPhraseExactMatches: Int = 0,
    val criticalPhraseExactMatchRate: Float = 0f,
    val evidenceLevel: EvidenceLevel = EvidenceLevel.SOURCE_ONLY,
    val results: List<BenchmarkResult> = emptyList()
)

@Serializable
data class BenchmarkResult(
    val sentenceId: String,
    val category: String = "",
    val isCritical: Boolean = false,
    val referenceText: String,
    val recognizedText: String,
    val audioDurationMs: Long,
    val processingMs: Long,
    val finalizationLatencyMs: Long,
    val referenceWordCount: Int,
    val wer: Float,
    val substitutions: Int,
    val deletions: Int,
    val insertions: Int,
    val isCriticalMatch: Boolean = false,
    val isSuccess: Boolean = true
) {
    val rtf: Float
        get() = if (audioDurationMs > 0) processingMs.toFloat() / audioDurationMs else 0f
}

@Serializable
data class TtsEvaluationSession(
    val timestampMs: Long,
    val language: String,
    val evaluatorId: String = "Human Evaluator",
    val modelIdentity: String = "Meta MMS TTS VITS ONNX",
    val sentencesTested: Int = 0,
    val intelligibleCount: Int = 0,
    val intelligibilityPercent: Float = 0f,
    val meanNaturalness: Float = 0f,
    val meanPronunciation: Float = 0f,
    val meanComputeTimeMs: Long = 0L,
    val meanAudioDurationMs: Long = 0L,
    val meanRtf: Float = 0f,
    val evidenceLevel: EvidenceLevel = EvidenceLevel.HUMAN_REVIEWED,
    val evaluations: List<TtsSentenceEvaluation> = emptyList()
)

@Serializable
data class TtsSentenceEvaluation(
    val sentenceId: String,
    val text: String,
    val computeTimeMs: Long,
    val audioDurationMs: Long,
    val sampleRateHz: Int,
    val pcmSampleCount: Int,
    val isNonEmptyPcm: Boolean,
    val intelligible: Boolean, // YES/NO
    val naturalness: Int,     // 1..5
    val pronunciation: Int,   // 1..5
    val comments: String = ""
) {
    val rtf: Float
        get() = if (audioDurationMs > 0) computeTimeMs.toFloat() / audioDurationMs else 0f
}
