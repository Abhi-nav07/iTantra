package com.itantra.core.inference

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    ERROR
}

data class AudioSegment(
    val samples: FloatArray,
    val durationMs: Long,
    val sampleRate: Int = 16000
)

class ContinuousListenEngine(private val context: Context) {
    private val _state = MutableStateFlow(ContinuousListenState.OFF)
    val state: StateFlow<ContinuousListenState> = _state.asStateFlow()

    private val _lastSegment = MutableStateFlow<AudioSegment?>(null)
    val lastSegment: StateFlow<AudioSegment?> = _lastSegment.asStateFlow()

    private var vad: Vad? = null

    // Config values
    private val sampleRate = 16000

    // Fallback manual tracker if VAD doesn't output segment
    private var isSpeechActive = false
    private val activeUtterance = mutableListOf<FloatArray>()

    fun start() {
        if (_state.value != ContinuousListenState.OFF && _state.value != ContinuousListenState.ERROR && _state.value != ContinuousListenState.SEGMENT_READY) {
            return
        }
        _state.value = ContinuousListenState.STARTING

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

            val config = VadModelConfig().apply {
                sileroVadModelConfig = SileroVadModelConfig().apply {
                    model = modelFile.absolutePath
                    threshold = 0.5f
                    minSilenceDuration = 0.7f
                    minSpeechDuration = 0.15f
                    windowSize = 512
                    maxSpeechDuration = 20.0f
                }
                sampleRate = this@ContinuousListenEngine.sampleRate
                numThreads = 1
                debug = false
            }

            // Re-initialize VAD
            vad?.release()
            vad = Vad(config = config)

            activeUtterance.clear()
            isSpeechActive = false

            _state.value = ContinuousListenState.LISTENING
        } catch (e: Exception) {
            Log.e("ContinuousListenEngine", "Failed to start VAD", e)
            _state.value = ContinuousListenState.ERROR
        }
    }

    fun stop() {
        _state.value = ContinuousListenState.OFF
        vad?.release()
        vad = null
        activeUtterance.clear()
        isSpeechActive = false
    }

    fun resetAndResume() {
        // Like stop/start but faster, meant for TTS interruption recovery
        vad?.reset()
        activeUtterance.clear()
        isSpeechActive = false
        if (_state.value != ContinuousListenState.OFF && _state.value != ContinuousListenState.ERROR) {
            _state.value = ContinuousListenState.LISTENING
        }
    }

    fun feedAudio(samples: FloatArray) {
        if (_state.value == ContinuousListenState.OFF || _state.value == ContinuousListenState.ERROR) return

        val currentVad = vad ?: return

        // Feed to VAD. Note: Silero VAD requires chunks of 512 samples.
        // MicrophoneAudioSource emits in 2048 or 4096 chunks.
        // sherpa-onnx `acceptWaveform` handles buffering internally for VAD inference.
        currentVad.acceptWaveform(samples)

        if (currentVad.isSpeechDetected()) {
            if (!isSpeechActive) {
                isSpeechActive = true
                _state.value = ContinuousListenState.SPEECH_DETECTED
            }
        }

        // If the VAD has completed a segment internally (sherpa-onnx logic: when speech turns into silence or max duration)
        while (!currentVad.empty()) {
            // A segment is ready!
            val segment = currentVad.front()
            currentVad.pop()

            // The segment.samples from Sherpa-Onnx already contains the utterance.
            // We can just use it directly! Sherpa-ONNX's Silero implementation maintains its own buffer.
            val segmentSamples = segment.samples

            val durationMs = (segmentSamples.size.toLong() * 1000) / sampleRate

            Log.d("ContinuousListenEngine", "VAD produced segment: ${segmentSamples.size} samples, ${durationMs}ms")

            _lastSegment.value = AudioSegment(
                samples = segmentSamples,
                durationMs = durationMs,
                sampleRate = sampleRate
            )

            // Reset our manual tracking since Sherpa-ONNX handled it
            isSpeechActive = false
            activeUtterance.clear()

            _state.value = ContinuousListenState.SEGMENT_READY
        }

        // If we were in SEGMENT_READY and no new segment popped, we revert to LISTENING
        // to show we are waiting again (unless we manually stopped).
        if (_state.value == ContinuousListenState.SEGMENT_READY && currentVad.empty()) {
            _state.value = ContinuousListenState.LISTENING
        } else if (isSpeechActive && _state.value != ContinuousListenState.SPEECH_DETECTED) {
             _state.value = ContinuousListenState.SPEECH_DETECTED
        } else if (!isSpeechActive && _state.value != ContinuousListenState.LISTENING && _state.value != ContinuousListenState.SEGMENT_READY) {
             _state.value = ContinuousListenState.LISTENING
        }
    }
}
