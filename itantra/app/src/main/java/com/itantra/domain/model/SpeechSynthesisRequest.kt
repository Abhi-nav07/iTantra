package com.itantra.domain.model

/**
 * Input to one text-to-speech synthesis call.
 *
 * Deliberately minimal for Task 01 — no voice/style controls (pitch, rate,
 * emotion, etc.) are modeled yet, since no TTS engine is integrated. When
 * a concrete engine is chosen, extend this rather than letting engine SDKs
 * leak into the rest of the app.
 */
data class SpeechSynthesisRequest(
    val languageCode: LanguageCode,
    val text: String,

    /** Correlates this request back to the [SpeechRecognitionResult] (or
     *  received packet) that produced it, for end-to-end latency tracking. */
    val correlationId: String,
)

/**
 * Result of a synthesis call: raw PCM audio plus enough metadata for
 * playback and for the metrics layer to compute Real-Time Factor without
 * re-deriving audio duration from the byte buffer downstream.
 */
data class SpeechSynthesisResult(
    val correlationId: String,
    val pcmAudio: FloatArray,
    val sampleRateHz: Int,
    val channelCount: Int,
    val durationMillis: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SpeechSynthesisResult) return false
        return correlationId == other.correlationId &&
            pcmAudio.contentEquals(other.pcmAudio) &&
            sampleRateHz == other.sampleRateHz &&
            channelCount == other.channelCount &&
            durationMillis == other.durationMillis
    }

    override fun hashCode(): Int {
        var result = correlationId.hashCode()
        result = 31 * result + pcmAudio.contentHashCode()
        result = 31 * result + sampleRateHz
        result = 31 * result + channelCount
        result = 31 * result + durationMillis.hashCode()
        return result
    }
}
