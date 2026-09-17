package com.itantra.core.inference

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ContinuousListenState {
    OFF, STARTING, LISTENING, SPEECH_DETECTED, CAPTURING, FINALIZING, SEGMENT_READY, ERROR
}

class ContinuousListenEngine(private val context: Context) {
    private var vad: Vad? = null
    private val TAG = "ContinuousListenEngine"
    
    private val _state = MutableStateFlow(ContinuousListenState.OFF)
    val state: Flow<ContinuousListenState> = _state.asStateFlow()
    
    private val preRollBuffer = ArrayDeque<FloatArray>()
    private val MAX_PREROLL_CHUNKS = 10 // will be calculated dynamically based on chunk size
    private var preRollCapacity = 10
    
    private val capturedChunks = mutableListOf<FloatArray>()

    fun init() {
        if (vad != null) return
        try {
            _state.value = ContinuousListenState.STARTING
            
            val sileroConfig = SileroVadModelConfig(
                model = "silero_vad.onnx",
                minSilenceDuration = 0.7f, // ~700ms endpoint / natural pause handling
                minSpeechDuration = 0.15f, // 150ms minimum speech to capture short words (e.g. "हाँ")
                maxSpeechDuration = 20.0f, // ~20 seconds bounded maximum
                threshold = 0.5f,
                windowSize = 512
            )
            
            val vadConfig = VadModelConfig(
                sileroVadModelConfig = sileroConfig,
                sampleRate = 16000,
                numThreads = 1,
                provider = "cpu",
                debug = false
            )
            
            vad = Vad(context.assets, vadConfig)
            _state.value = ContinuousListenState.LISTENING
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize VAD", e)
            _state.value = ContinuousListenState.ERROR
        }
    }
    
    fun processAudio(samples: FloatArray) {
        if (vad == null) return
        if (_state.value == ContinuousListenState.OFF || _state.value == ContinuousListenState.ERROR) return
        if (_state.value == ContinuousListenState.SEGMENT_READY) return // Don't process while waiting for pop
        
        try {
            // Keep a pre-roll buffer of roughly 400ms
            // 16kHz, 400ms = 6400 samples
            val currentCapacity = 6400 / samples.size.coerceAtLeast(1)
            preRollCapacity = currentCapacity.coerceIn(1, 100)
            
            if (_state.value == ContinuousListenState.LISTENING) {
                preRollBuffer.addLast(samples)
                if (preRollBuffer.size > preRollCapacity) {
                    preRollBuffer.removeFirst()
                }
            }
            
            vad?.acceptWaveform(samples)
            val isSpeech = vad?.isSpeechDetected() == true
            
            if (isSpeech && _state.value == ContinuousListenState.LISTENING) {
                _state.value = ContinuousListenState.CAPTURING
                capturedChunks.clear()
                capturedChunks.addAll(preRollBuffer)
                preRollBuffer.clear()
            }
            
            if (_state.value == ContinuousListenState.CAPTURING) {
                capturedChunks.add(samples)
                
                while (vad?.empty() == false) {
                    val segment = vad?.front()
                    if (segment != null) {
                        vad?.pop()
                        // Ensure one segment per utterance (idempotent finalization)
                        if (_state.value != ContinuousListenState.SEGMENT_READY) {
                            val totalSize = capturedChunks.sumOf { it.size }
                            val durationInSeconds = totalSize / 16000.0f
                            // Segment validation
                            if (totalSize > 0 && durationInSeconds >= 0.15f) {
                                _state.value = ContinuousListenState.SEGMENT_READY
                            } else {
                                // Reject tiny transient noise that made it past the VAD
                                capturedChunks.clear()
                                _state.value = ContinuousListenState.LISTENING
                                vad?.reset()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing audio in VAD", e)
            _state.value = ContinuousListenState.ERROR
        }
    }
    
    fun popCapturedAudio(): FloatArray {
        val totalSize = capturedChunks.sumOf { it.size }
        val result = FloatArray(totalSize)
        var offset = 0
        for (chunk in capturedChunks) {
            System.arraycopy(chunk, 0, result, offset, chunk.size)
            offset += chunk.size
        }
        capturedChunks.clear()
        
        if (_state.value == ContinuousListenState.SEGMENT_READY || _state.value == ContinuousListenState.CAPTURING) {
            _state.value = ContinuousListenState.LISTENING
            vad?.reset()
        }
        return result
    }
    
    fun suspendListening() {
        vad?.reset()
        preRollBuffer.clear()
        capturedChunks.clear()
        _state.value = ContinuousListenState.OFF
    }
    
    fun resumeListening() {
        if (vad != null) {
            vad?.reset()
            _state.value = ContinuousListenState.LISTENING
        } else {
            init()
        }
    }

    fun reset() {
        vad?.reset()
        preRollBuffer.clear()
        capturedChunks.clear()
        if (vad != null) {
            _state.value = ContinuousListenState.LISTENING
        }
    }

    fun release() {
        vad?.release()
        vad = null
        _state.value = ContinuousListenState.OFF
    }
}
