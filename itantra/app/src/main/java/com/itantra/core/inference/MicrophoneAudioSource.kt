package com.itantra.core.inference

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive

class MicrophoneAudioSource(scope: CoroutineScope) {

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
