package com.itantra.core.inference

/**
 * Contract for voice activity / endpoint detection sitting between the
 * microphone and the STT engine.
 *
 * NO IMPLEMENTATION EXISTS YET. Left deliberately small: a real
 * implementation (energy-based, or a small dedicated VAD model such as
 * Silero VAD via ONNX Runtime Mobile) will feed PCM frames in and receive
 * speech/silence + endpoint events out. The exact frame size and event
 * shape are left to be finalized when the audio-capture task lands, so
 * this interface is intentionally not over-specified yet.
 */
interface VoiceActivityDetector {

    val isLoaded: Boolean

    suspend fun load()

    /**
     * Feeds one frame of 16-bit PCM audio and returns whether it was
     * classified as containing speech. A real implementation will also
     * need to expose endpoint (end-of-utterance) events; that signal
     * shape is deferred to the task that implements this interface, to
     * avoid guessing an API this task cannot validate.
     */
    suspend fun processFrame(pcm16: ShortArray): Boolean

    suspend fun reset()

    suspend fun unload()
}
