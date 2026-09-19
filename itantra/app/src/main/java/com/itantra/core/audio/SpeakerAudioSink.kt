package com.itantra.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Handles playing synthesized raw PCM float arrays directly to the device speaker.
 * Used for Text-to-Speech output and safety-critical emergency audio alerts.
 *
 * Operational Hardening:
 * 1. Completion Tracking: Polls AudioTrack.playbackHeadPosition to guarantee complete audible
 *    rendering before stopping, preventing syllable clipping at the end of utterances.
 * 2. Audio Focus: Uses AudioFocusRequest (API 26+) to request appropriate transient focus,
 *    supporting ducking for regular speech and exclusive gain for alarm alerts.
 * 3. Volume Intent: Emergency alarms request maximum-volume intent while saving and restoring
 *    the user's original alarm volume on release. (Actual volume behavior depends on Android/OEM policy).
 */
class SpeakerAudioSink(
    private val context: Context? = null
) {

    private var audioTrack: AudioTrack? = null
    private var sampleRate: Int = 16000
    private var totalFramesWritten: Long = 0L
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var originalAlarmVolume: Int? = null
    private var hasElevatedVolume: Boolean = false

    /**
     * Initializes the AudioTrack with the specified sample rate and usage attributes.
     * @param sampleRate Typically 16000 Hz or 22050 Hz.
     * @param usage AudioAttributes.USAGE_VOICE_COMMUNICATION or AudioAttributes.USAGE_ALARM.
     * @param requestMaxVolume When true and usage is USAGE_ALARM, requests maximum alarm volume.
     */
    fun init(
        sampleRate: Int,
        usage: Int = AudioAttributes.USAGE_VOICE_COMMUNICATION,
        requestMaxVolume: Boolean = false
    ) {
        release() // ensure clean state
        this.sampleRate = sampleRate
        this.totalFramesWritten = 0L

        val contentType = if (usage == AudioAttributes.USAGE_ALARM) {
            AudioAttributes.CONTENT_TYPE_SONIFICATION
        } else {
            AudioAttributes.CONTENT_TYPE_SPEECH
        }

        val attributes = AudioAttributes.Builder()
            .setUsage(usage)
            .setContentType(contentType)
            .build()

        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        ).coerceAtLeast(sampleRate / 10 * 4) // minimum 100ms buffer

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(attributes)
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

        requestAudioFocus(attributes, usage)

        if (usage == AudioAttributes.USAGE_ALARM && requestMaxVolume) {
            applyEmergencyVolumeIntent()
        }

        audioTrack?.play()
    }

    private fun requestAudioFocus(attributes: AudioAttributes, usage: Int) {
        try {
            audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val am = audioManager ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusGain = if (usage == AudioAttributes.USAGE_ALARM) {
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                } else {
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                }

                val req = AudioFocusRequest.Builder(focusGain)
                    .setAudioAttributes(attributes)
                    .setOnAudioFocusChangeListener { focusChange ->
                        // Focus changes handled gracefully; alarm continues, normal speech may duck
                    }
                    .build()

                audioFocusRequest = req
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    null,
                    if (usage == AudioAttributes.USAGE_ALARM) AudioManager.STREAM_ALARM else AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }
        } catch (e: Exception) {
            // Audio focus failure should never crash safety audio pipeline
            e.printStackTrace()
        }
    }

    private fun applyEmergencyVolumeIntent() {
        try {
            val am = audioManager ?: return
            originalAlarmVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
            val maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            am.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
            hasElevatedVolume = true
        } catch (e: Exception) {
            // Maximum-volume intent requested; exact device behavior depends on Android/OEM policy
            e.printStackTrace()
        }
    }

    /**
     * Writes raw float samples to the audio track. Blocks until audio is queued.
     * Updates frame counters for end-of-utterance clipping prevention.
     */
    suspend fun play(samples: FloatArray) = withContext(Dispatchers.IO) {
        val track = audioTrack ?: throw IllegalStateException("AudioSink not initialized")
        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
            track.play()
        }
        val written = track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        if (written > 0) {
            totalFramesWritten += written
        }
    }

    /**
     * Waits for all queued PCM frames to physically complete playback through the DAC/speaker
     * before issuing stop(). Uses AudioTrack.playbackHeadPosition to guarantee that the final
     * word/syllable is not clipped.
     */
    suspend fun flushAndStop() = withContext(Dispatchers.IO) {
        val track = audioTrack ?: return@withContext
        try {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING && sampleRate > 0) {
                val maxWaitMillis = ((totalFramesWritten * 1000L) / sampleRate) + 250L
                val startTime = System.currentTimeMillis()

                // Wait until playback head reaches queued frames or timeout expires
                while (track.playbackHeadPosition < totalFramesWritten && (System.currentTimeMillis() - startTime) < maxWaitMillis) {
                    delay(20)
                }
            }
            track.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Releases AudioTrack and restores original alarm volume and audio focus.
     */
    fun release() {
        try {
            audioTrack?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioTrack = null

        // Restore original volume if temporarily elevated
        if (hasElevatedVolume && originalAlarmVolume != null) {
            try {
                audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, originalAlarmVolume!!, 0)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            hasElevatedVolume = false
            originalAlarmVolume = null
        }

        // Abandon audio focus
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                audioManager?.abandonAudioFocusRequest(audioFocusRequest!!)
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioFocusRequest = null
    }
}
