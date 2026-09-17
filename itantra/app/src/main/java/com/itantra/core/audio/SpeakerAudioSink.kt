package com.itantra.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles playing synthesized raw PCM float arrays directly to the device speaker.
 * Used for the Text-to-Speech output loop.
 */
class SpeakerAudioSink {

    private var audioTrack: AudioTrack? = null

    /**
     * Initializes the AudioTrack with the specified sample rate.
     * Common for VITS TTS is 22050 Hz or 16000 Hz.
     */
    fun init(sampleRate: Int, usage: Int = AudioAttributes.USAGE_VOICE_COMMUNICATION) {
        release() // ensure clean state
        
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            
        audioTrack?.play()
    }

    /**
     * Writes raw float samples to the audio track. Blocks until audio is queued.
     */
    suspend fun play(samples: FloatArray) = withContext(Dispatchers.IO) {
        val track = audioTrack ?: throw IllegalStateException("AudioSink not initialized")
        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
            track.play()
        }
        track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
    }

    /**
     * Waits for playback to finish, then stops.
     */
    fun flushAndStop() {
        audioTrack?.stop()
    }

    /**
     * Releases resources. Must be called when done with TTS.
     */
    fun release() {
        audioTrack?.release()
        audioTrack = null
    }
}
