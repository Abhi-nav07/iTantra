package com.itantra.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class BenchmarkSession(
    val timestampMs: Long,
    val deviceModel: String,
    val androidVersion: String,
    val threadCount: Int,
    val modelVersion: String,
    val results: List<BenchmarkResult>
)

@Serializable
data class BenchmarkResult(
    val sentenceId: String,
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
    val isSuccess: Boolean
) {
    val rtf: Float
        get() = if (audioDurationMs > 0) processingMs.toFloat() / audioDurationMs else 0f
}
