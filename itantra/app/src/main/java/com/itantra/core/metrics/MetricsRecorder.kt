package com.itantra.core.metrics

import com.itantra.domain.model.InferenceMetrics
import com.itantra.domain.model.LanguageCode
import com.itantra.domain.model.Measurement
import com.itantra.domain.model.TransmissionMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Records real, timed measurements and exposes the latest aggregate
 * snapshot for the diagnostics screen.
 *
 * Hard rule (see docs/ARCHITECTURE.md "Metrics architecture"): a
 * [MetricsRecorder] implementation must NEVER invent a value. Every
 * `record*` method is only ever called by code that just performed the
 * actual timed operation. Until a real STT/TTS/transport implementation
 * calls these, [latest] stays at all-[Measurement.NotMeasured] defaults —
 * which is the correct, honest state for Task 01.
 */
interface MetricsRecorder {

    val latest: StateFlow<InferenceMetrics>

    fun recordSttModelLoadTime(millis: Long)
    fun recordSttInferenceTime(millis: Long)
    fun recordSttEndpointToFinalText(millis: Long)
    fun recordSttAudioDuration(millis: Long)

    fun recordTtsModelLoadTime(millis: Long)
    fun recordTtsTimeToFirstAudio(millis: Long)
    fun recordTtsSynthesisDuration(millis: Long)
    fun recordTtsRealTimeFactor(rtf: Double)

    fun recordEndToEndLatency(millis: Long)

    fun recordVadSegment(durationS: Double, samples: Int)

    fun recordSystemMemory(bytes: Long)
    fun recordActiveLanguage(code: LanguageCode?)
    fun recordPackStorage(bytes: Long)

    fun recordTransmission(metrics: TransmissionMetrics)
    fun recordCryptoMetrics(metrics: com.itantra.domain.model.CryptoMetrics)

    /** Resets all metrics back to NotMeasured, e.g. when switching active
     *  language, so stale numbers from a different language are never
     *  shown as if they apply to the new one. */
    fun reset()
}

/**
 * Simple thread-safe in-memory [MetricsRecorder]. No persistence, no
 * network, no averaging/windowing yet — Task 01 only needs a truthful
 * single latest-snapshot for the diagnostics screen.
 */
class InMemoryMetricsRecorder : MetricsRecorder {

    private val _latest = MutableStateFlow(InferenceMetrics())
    override val latest: StateFlow<InferenceMetrics> = _latest

    @Synchronized
    override fun recordSttModelLoadTime(millis: Long) {
        _latest.value = _latest.value.copy(
            stt = _latest.value.stt.copy(modelLoadTimeMillis = Measurement.Measured(millis))
        )
    }

    @Synchronized
    override fun recordSttInferenceTime(millis: Long) {
        _latest.value = _latest.value.copy(
            stt = _latest.value.stt.copy(inferenceTimeMillis = Measurement.Measured(millis))
        )
    }

    @Synchronized
    override fun recordSttEndpointToFinalText(millis: Long) {
        _latest.value = _latest.value.copy(
            stt = _latest.value.stt.copy(endpointToFinalTextMillis = Measurement.Measured(millis))
        )
    }

    @Synchronized
    override fun recordSttAudioDuration(millis: Long) {
        _latest.value = _latest.value.copy(
            stt = _latest.value.stt.copy(audioDurationMs = millis)
        )
    }

    @Synchronized
    override fun recordTtsModelLoadTime(millis: Long) {
        _latest.value = _latest.value.copy(
            tts = _latest.value.tts.copy(modelLoadTimeMillis = Measurement.Measured(millis))
        )
    }

    @Synchronized
    override fun recordTtsTimeToFirstAudio(millis: Long) {
        _latest.value = _latest.value.copy(
            tts = _latest.value.tts.copy(timeToFirstAudioMillis = Measurement.Measured(millis))
        )
    }

    @Synchronized
    override fun recordTtsSynthesisDuration(millis: Long) {
        _latest.value = _latest.value.copy(
            tts = _latest.value.tts.copy(synthesisDurationMillis = Measurement.Measured(millis))
        )
    }

    @Synchronized
    override fun recordTtsRealTimeFactor(rtf: Double) {
        _latest.value = _latest.value.copy(
            tts = _latest.value.tts.copy(realTimeFactor = Measurement.Measured(rtf))
        )
    }

    @Synchronized
    override fun recordEndToEndLatency(millis: Long) {
        _latest.value = _latest.value.copy(endToEndMillis = Measurement.Measured(millis))
    }

    @Synchronized
    override fun recordVadSegment(durationS: Double, samples: Int) {
        _latest.value = _latest.value.copy(
            vad = _latest.value.vad.copy(
                lastSegmentDurationS = Measurement.Measured(durationS),
                lastSegmentSamples = Measurement.Measured(samples)
            )
        )
    }

    @Synchronized
    override fun recordSystemMemory(bytes: Long) {
        _latest.value = _latest.value.copy(
            system = _latest.value.system.copy(approximateProcessMemoryBytes = Measurement.Measured(bytes))
        )
    }

    @Synchronized
    override fun recordActiveLanguage(code: LanguageCode?) {
        _latest.value = _latest.value.copy(
            system = _latest.value.system.copy(activeLanguage = code)
        )
    }

    @Synchronized
    override fun recordPackStorage(bytes: Long) {
        _latest.value = _latest.value.copy(
            system = _latest.value.system.copy(packStorageBytes = Measurement.Measured(bytes))
        )
    }

    @Synchronized
    override fun recordTransmission(metrics: TransmissionMetrics) {
        _latest.value = _latest.value.copy(transport = metrics)
    }

    @Synchronized
    override fun recordCryptoMetrics(metrics: com.itantra.domain.model.CryptoMetrics) {
        _latest.value = _latest.value.copy(crypto = metrics)
    }

    @Synchronized
    override fun reset() {
        _latest.value = InferenceMetrics()
    }
}
