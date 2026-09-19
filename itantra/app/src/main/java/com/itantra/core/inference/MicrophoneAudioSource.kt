package com.itantra.core.inference

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive

enum class AecStatus {
    AEC_SUPPORTED,
    AEC_ENABLED,
    AEC_DISABLED,
    AEC_UNAVAILABLE
}

class MicrophoneAudioSource(
    scope: CoroutineScope,
    private val enableAecIfAvailable: Boolean = true
) {

    var currentAecStatus: AecStatus = AecStatus.AEC_UNAVAILABLE
        private set

    @SuppressLint("MissingPermission")
    val stream: SharedFlow<FloatArray> = callbackFlow {
        val sampleRate = 16000
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ) * 2

        val audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (e: SecurityException) {
            close(SecurityException("Permission denied for RECORD_AUDIO", e))
            return@callbackFlow
        }

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            close(Exception("AudioRecord initialization failed (state uninitialized)"))
            return@callbackFlow
        }

        var echoCanceler: AcousticEchoCanceler? = null
        try {
            if (enableAecIfAvailable && AcousticEchoCanceler.isAvailable()) {
                currentAecStatus = AecStatus.AEC_SUPPORTED
                echoCanceler = AcousticEchoCanceler.create(audioRecord.audioSessionId)
                if (echoCanceler != null) {
                    val res = echoCanceler.setEnabled(true)
                    currentAecStatus = if (res == AudioEffect.SUCCESS && echoCanceler.enabled) {
                        AecStatus.AEC_ENABLED
                    } else {
                        AecStatus.AEC_DISABLED
                    }
                } else {
                    currentAecStatus = AecStatus.AEC_UNAVAILABLE
                }
            } else {
                currentAecStatus = AecStatus.AEC_UNAVAILABLE
            }
        } catch (t: Throwable) {
            // Devices with buggy HAL or missing effects fallback gracefully
            currentAecStatus = AecStatus.AEC_UNAVAILABLE
            t.printStackTrace()
        }

        audioRecord.startRecording()
        val buffer = ShortArray(bufferSize / 2)

        try {
            while (isActive) {
                val readResult = audioRecord.read(buffer, 0, buffer.size)
                if (readResult > 0) {
                    val floatArray = FloatArray(readResult)
                    for (i in 0 until readResult) {
                        floatArray[i] = buffer[i] / 32768.0f
                    }
                    trySend(floatArray)
                }
            }
        } finally {
            try {
                echoCanceler?.release()
            } catch (t: Throwable) {
                t.printStackTrace()
            }
            audioRecord.stop()
            audioRecord.release()
        }

        awaitClose {
            // Releasing is handled in finally block
        }
    }.flowOn(Dispatchers.IO)
     .shareIn(
         scope = scope,
         started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 500),
         replay = 0
     )
}
