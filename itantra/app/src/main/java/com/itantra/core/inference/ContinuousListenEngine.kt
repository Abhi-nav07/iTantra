package com.itantra.core.inference

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream

enum class ContinuousListenState {
    OFF,
    STARTING,
    LISTENING,
    SPEECH_DETECTED,
    FINALIZING,
    SEGMENT_READY,
    PAUSED,
    ERROR
}

data class AudioSegment(
    val samples: FloatArray,
    val durationMs: Long,
    val sampleRate: Int = 16000
)

/**
 * Configurable sentence-boundary parameters for speech endpointing.
 *
 * @property speechStartThreshold VAD probability threshold to detect speech onset (0.0 .. 1.0).
 * @property minSilenceDurationSec Silence pause duration required to finalize an utterance (seconds).
 *                                  Short pauses below this threshold are treated as intra-sentence hesitations.
 * @property minSpeechDurationSec Minimum speech duration required to consider an utterance valid (seconds).
 * @property maxSpeechDurationSec Maximum utterance duration before forced finalization (seconds).
 * @property windowSize Samples per evaluation window (typically 512 for Silero at 16kHz).
 */
data class SentenceBoundaryConfig(
    val speechStartThreshold: Float = 0.5f,
    val minSilenceDurationSec: Float = 0.7f,
    val minSpeechDurationSec: Float = 0.25f,
    val maxSpeechDurationSec: Float = 20.0f,
    val windowSize: Int = 512
)

class ContinuousListenEngine(
    private val context: Context,
    initialConfig: SentenceBoundaryConfig = SentenceBoundaryConfig()
) {
    private val _state = MutableStateFlow(ContinuousListenState.OFF)
    val state: StateFlow<ContinuousListenState> = _state.asStateFlow()

    private val _lastSegment = MutableStateFlow<AudioSegment?>(null)
    val lastSegment: StateFlow<AudioSegment?> = _lastSegment.asStateFlow()

    private val _segmentEvents = MutableSharedFlow<AudioSegment>(extraBufferCapacity = 16)
    val segmentEvents: SharedFlow<AudioSegment> = _segmentEvents.asSharedFlow()

    var sentenceBoundaryConfig: SentenceBoundaryConfig = initialConfig
        private set

    private var vad: Vad? = null

    // Config values
    val sampleRate = 16000

    @Volatile
    private var isPaused = false

    // Fallback manual tracker if VAD doesn't output segment
    private var isSpeechActive = false
    private val activeUtterance = mutableListOf<FloatArray>()
    private var useEnergyFallback = false
    private var fallbackSilenceFrames = 0

    fun updateConfig(config: SentenceBoundaryConfig) {
        sentenceBoundaryConfig = config
        if (_state.value == ContinuousListenState.LISTENING || _state.value == ContinuousListenState.PAUSED) {
            start()
        }
    }

    fun start() {
        if (_state.value != ContinuousListenState.OFF &&
            _state.value != ContinuousListenState.ERROR &&
            _state.value != ContinuousListenState.SEGMENT_READY &&
            _state.value != ContinuousListenState.PAUSED
        ) {
            return
        }
        _state.value = ContinuousListenState.STARTING
        isPaused = false

        try {
            // Copy silero_vad.onnx to cache if not exists, as asset cannot be accessed via path directly by C++
            val cacheDir = context.cacheDir
            val modelFile = File(cacheDir, "silero_vad.onnx")
            if (!modelFile.exists()) {
                context.assets.open("silero_vad.onnx").use { input ->
                    FileOutputStream(modelFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            val cfg = sentenceBoundaryConfig
            val config = VadModelConfig().apply {
                sileroVadModelConfig = SileroVadModelConfig().apply {
                    model = modelFile.absolutePath
                    threshold = cfg.speechStartThreshold
                    minSilenceDuration = cfg.minSilenceDurationSec
                    minSpeechDuration = cfg.minSpeechDurationSec
                    windowSize = cfg.windowSize
                    maxSpeechDuration = cfg.maxSpeechDurationSec
                }
                sampleRate = this@ContinuousListenEngine.sampleRate
                numThreads = 1
                debug = false
            }

            // Re-initialize VAD
            vad?.release()
            vad = Vad(config = config)
            useEnergyFallback = false
            activeUtterance.clear()
            fallbackSilenceFrames = 0
            isSpeechActive = false

            _state.value = ContinuousListenState.LISTENING
        } catch (e: Throwable) {
            Log.w("ContinuousListenEngine", "Failed to init Silero VAD, falling back to defensive RMS energy gating", e)
            vad?.release()
            vad = null
            useEnergyFallback = true
            activeUtterance.clear()
            fallbackSilenceFrames = 0
            isSpeechActive = false
            _state.value = ContinuousListenState.LISTENING
        }
    }

    fun stop() {
        _state.value = ContinuousListenState.OFF
        isPaused = false
        vad?.release()
        vad = null
        useEnergyFallback = false
        activeUtterance.clear()
        fallbackSilenceFrames = 0
        isSpeechActive = false
    }

    /**
     * Pauses listening without tearing down native resources.
     * Used when an utterance is handed to STT or while local audio is playing.
     */
    fun pauseListening() {
        isPaused = true
        vad?.reset()
        vad?.clear()
        activeUtterance.clear()
        fallbackSilenceFrames = 0
        isSpeechActive = false
        if (_state.value != ContinuousListenState.OFF && _state.value != ContinuousListenState.ERROR) {
            _state.value = ContinuousListenState.PAUSED
        }
    }

    /**
     * Resumes listening after STT or playback completes.
     */
    fun resumeListening() {
        isPaused = false
        vad?.reset()
        vad?.clear()
        activeUtterance.clear()
        fallbackSilenceFrames = 0
        isSpeechActive = false
        if (_state.value != ContinuousListenState.OFF && _state.value != ContinuousListenState.ERROR) {
            _state.value = ContinuousListenState.LISTENING
        } else {
            start()
        }
    }

    fun resetAndResume() {
        resumeListening()
    }

    fun feedAudio(samples: FloatArray) {
        if (isPaused || _state.value == ContinuousListenState.OFF || _state.value == ContinuousListenState.ERROR || _state.value == ContinuousListenState.PAUSED) {
            return
        }

        if (useEnergyFallback || vad == null) {
            var sumSq = 0.0
            for (s in samples) {
                sumSq += (s * s)
            }
            val rms = Math.sqrt(sumSq / samples.size).toFloat()

            val cfg = sentenceBoundaryConfig
            val minSilenceFrames = ((cfg.minSilenceDurationSec * sampleRate) / samples.size).toInt().coerceAtLeast(1)
            val minSpeechSamples = (cfg.minSpeechDurationSec * sampleRate).toInt()
            val maxSpeechSamples = (cfg.maxSpeechDurationSec * sampleRate).toInt()

            if (rms > 0.02f) {
                isSpeechActive = true
                fallbackSilenceFrames = 0
                activeUtterance.add(samples.clone())
                _state.value = ContinuousListenState.SPEECH_DETECTED
            } else if (isSpeechActive) {
                fallbackSilenceFrames++
                activeUtterance.add(samples.clone())
                val totalSamples = activeUtterance.sumOf { it.size }
                if (fallbackSilenceFrames >= minSilenceFrames || totalSamples >= maxSpeechSamples) {
                    if (totalSamples >= minSpeechSamples) {
                        val merged = FloatArray(totalSamples)
                        var offset = 0
                        for (chunk in activeUtterance) {
                            System.arraycopy(chunk, 0, merged, offset, chunk.size)
                            offset += chunk.size
                        }
                        val durationMs = (totalSamples.toLong() * 1000) / sampleRate
                        Log.d("ContinuousListenEngine", "RMS fallback produced segment: $totalSamples samples, ${durationMs}ms")
                        val seg = AudioSegment(
                            samples = merged,
                            durationMs = durationMs,
                            sampleRate = sampleRate
                        )
                        _lastSegment.value = seg
                        _segmentEvents.tryEmit(seg)
                        _state.value = ContinuousListenState.SEGMENT_READY
                    }
                    activeUtterance.clear()
                    isSpeechActive = false
                    fallbackSilenceFrames = 0
                }
            }
            return
        }

        val currentVad = vad ?: return

        // Feed to VAD. Note: Silero VAD requires chunks of 512 samples.
        // sherpa-onnx `acceptWaveform` handles buffering internally for VAD inference.
        currentVad.acceptWaveform(samples)

        if (currentVad.isSpeechDetected()) {
            if (!isSpeechActive) {
                isSpeechActive = true
                _state.value = ContinuousListenState.SPEECH_DETECTED
            }
        }

        // If the VAD has completed a segment internally
        while (!currentVad.empty()) {
            val segment = currentVad.front()
            currentVad.pop()

            val segmentSamples = segment.samples
            val durationMs = (segmentSamples.size.toLong() * 1000) / sampleRate

            Log.d("ContinuousListenEngine", "VAD produced segment: ${segmentSamples.size} samples, ${durationMs}ms")

            val seg = AudioSegment(
                samples = segmentSamples,
                durationMs = durationMs,
                sampleRate = sampleRate
            )
            _lastSegment.value = seg
            _segmentEvents.tryEmit(seg)

            isSpeechActive = false
            activeUtterance.clear()

            _state.value = ContinuousListenState.SEGMENT_READY
        }

        // State settlement
        if (_state.value == ContinuousListenState.SEGMENT_READY && currentVad.empty()) {
            _state.value = ContinuousListenState.LISTENING
        } else if (isSpeechActive && _state.value != ContinuousListenState.SPEECH_DETECTED) {
            _state.value = ContinuousListenState.SPEECH_DETECTED
        } else if (!isSpeechActive && _state.value != ContinuousListenState.LISTENING && _state.value != ContinuousListenState.SEGMENT_READY) {
            _state.value = ContinuousListenState.LISTENING
        }
    }
}
