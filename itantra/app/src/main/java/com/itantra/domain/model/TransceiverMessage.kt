package com.itantra.domain.model

enum class MessageState {
    IDLE,
    RECORDING,
    STT_PROCESSING,
    STT_COMPLETE,
    PACKET_ENCODING,
    TRANSMITTING,
    SENT,
    DELIVERED,
    REMOTE_TTS_READY,
    REMOTE_PLAYING,
    REMOTE_PLAYBACK_CONFIRMED,
    WAITING_ACK,
    ACKNOWLEDGED,
    WAITING_USER_CONFIRMATION,
    ERROR
}

enum class TranslationStatus {
    NONE,
    BYPASSED,
    TRANSLATING,
    SUCCESS,
    FAILED,
    UNSUPPORTED,
    MODEL_MISSING
}

object MessagePriority {
    const val NORMAL = 0
    const val HIGH = 1
    const val CRITICAL = 2
}

enum class MessageSource {
    LOCAL,
    REMOTE
}

data class TransceiverMessage(
    val messageId: Long,
    val language: LanguageCode?,
    val targetLanguage: LanguageCode? = null,
    val priority: Int,
    val text: String,
    val originalText: String? = null,
    val translationStatus: TranslationStatus = TranslationStatus.NONE,
    val source: MessageSource,
    val createdAtLocal: Long,
    val state: MessageState,
    
    // Metrics per message for E2E traceability
    val sttLatencyMillis: Long = 0,
    val payloadBytes: Int = 0,
    val semanticBytes: Int = 0,
    val secureBytes: Int = 0,
    val finalFrameBytes: Int = 0,
    val packetBytes: Int = 0,
    val rttMillis: Long = 0,
    val peerTtfaMillis: Long = 0,
    val estimatedE2eMillis: Long = 0,
    val remoteAudioStartConfMillis: Long = 0,
    val rawPcmEquivalentBytes: Int = 0,
    val speechDurationMillis: Long = 0
) {
    val semanticReductionPercent: Float
        get() {
            if (rawPcmEquivalentBytes == 0) return 0f
            return 100f * (1.0f - (finalFrameBytes.toFloat() / rawPcmEquivalentBytes.toFloat()))
        }
        
    val semanticBitrateBps: Float
        get() {
            if (speechDurationMillis == 0L) return 0f
            return (finalFrameBytes * 8f) / (speechDurationMillis / 1000f)
        }
}
