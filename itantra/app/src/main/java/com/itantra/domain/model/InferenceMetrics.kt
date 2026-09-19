package com.itantra.domain.model

/**
 * Measurable performance of the speech-to-text stage for one session/utterance.
 * Every field starts life as [Measurement.NotMeasured] and is only ever
 * filled in by an actual timed measurement recorded through
 * [com.itantra.core.metrics.MetricsRecorder] — never synthesized.
 */
data class SttMetrics(
    val modelLoadTimeMillis: Measurement<Long> = Measurement.NotMeasured,
    val inferenceTimeMillis: Measurement<Long> = Measurement.NotMeasured,
    /** Endpoint (end of speech detected) to final text available. */
    val endpointToFinalTextMillis: Measurement<Long> = Measurement.NotMeasured,
    val audioDurationMs: Long = 0L
)

/**
 * Measurable performance of the text-to-speech stage for one session/utterance.
 */
data class TtsMetrics(
    val modelLoadTimeMillis: Measurement<Long> = Measurement.NotMeasured,
    /**
     * TTFA proxy = full batch generation latency.
     * Note: Current batch VITS generation produces the entire waveform before playback;
     * this is batch synthesis latency, not true streaming first-audio latency.
     */
    val timeToFirstAudioMillis: Measurement<Long> = Measurement.NotMeasured,
    /** Actual compute time in ms to synthesize audio. */
    val synthesisDurationMillis: Measurement<Long> = Measurement.NotMeasured,
    /** Real-Time Factor = synthesisComputeTime / generatedAudioDuration. <1.0 is faster
     *  than real-time playback. */
    val realTimeFactor: Measurement<Double> = Measurement.NotMeasured,
) {
    /** Direct alias for batch synthesis latency */
    val batchGenerationLatencyMillis: Measurement<Long>
        get() = timeToFirstAudioMillis
}

/**
 * Coarse system-level figures shown on the diagnostics screen.
 */
data class SystemMetrics(
    val approximateProcessMemoryBytes: Measurement<Long> = Measurement.NotMeasured,
    val activeLanguage: LanguageCode? = null,
    val packStorageBytes: Measurement<Long> = Measurement.NotMeasured,
)

/**
 * Performance metrics for the secure session layer.
 */
data class CryptoMetrics(
    val handshakeDurationMillis: Measurement<Long> = Measurement.NotMeasured,
    val verificationDurationMillis: Measurement<Long> = Measurement.NotMeasured,
    val avgEncryptUs: Measurement<Long> = Measurement.NotMeasured,
    val avgDecryptUs: Measurement<Long> = Measurement.NotMeasured,
    val authFailures: Int = 0,
    val replayRejections: Int = 0
)

/**
 * Aggregate inference metrics for one end-to-end utterance, combining STT
 * and TTS sides. [endToEndMillis] is speech-end (on the sending device) to
 * receiving-device audio start, and can only be non-N/A once both a real
 * transport and a real TTS engine exist.
 */
data class InferenceMetrics(
    val stt: SttMetrics = SttMetrics(),
    val tts: TtsMetrics = TtsMetrics(),
    val system: SystemMetrics = SystemMetrics(),
    val transport: TransmissionMetrics = TransmissionMetrics(),
    val crypto: CryptoMetrics = CryptoMetrics(),
    val endToEndMillis: Measurement<Long> = Measurement.NotMeasured,
    val vad: VadMetrics = VadMetrics(),
)

/**
 * Measurable values from the Continuous Listen VAD engine for Module 5A.
 */
data class VadMetrics(
    val preRollMs: Int = 400,
    val endpointSilenceMs: Int = 700,
    val minSpeechMs: Int = 150,
    val maxUtteranceS: Int = 20,
    val lastSegmentDurationS: Measurement<Double> = Measurement.NotMeasured,
    val lastSegmentSamples: Measurement<Int> = Measurement.NotMeasured
)
