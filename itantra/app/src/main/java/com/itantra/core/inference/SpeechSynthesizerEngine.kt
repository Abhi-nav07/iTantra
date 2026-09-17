package com.itantra.core.inference

import com.itantra.domain.model.LanguageCode
import com.itantra.domain.model.SpeechSynthesisRequest
import com.itantra.domain.model.SpeechSynthesisResult

/**
 * Contract for an offline text-to-speech engine bound to one loaded
 * language pack.
 *
 * NO IMPLEMENTATION EXISTS YET — same rationale as [SpeechRecognizerEngine]:
 * this lets the lifecycle/session management and UI be built against a
 * stable contract before a concrete TTS runtime (e.g. a converted/quantized
 * FastPitch+HiFi-GAN pair, or a sherpa-onnx TTS model) is chosen.
 *
 * Lifecycle mirrors [SpeechRecognizerEngine]: [load] before [synthesize],
 * [unload] to fully release resources when this language is no longer
 * active.
 */
interface SpeechSynthesizerEngine {

    val languageCode: LanguageCode

    val isLoaded: Boolean

    suspend fun load()

    /**
     * Synthesizes [request] to PCM audio. Suspends until synthesis is
     * complete; a later task may add a streaming variant for lower
     * time-to-first-audio, but Task 01 only declares the simple contract.
     */
    suspend fun synthesize(request: SpeechSynthesisRequest): SpeechSynthesisResult

    suspend fun unload()
}
