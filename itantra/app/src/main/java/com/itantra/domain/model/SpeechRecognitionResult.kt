package com.itantra.domain.model

/**
 * Output of one speech-to-text pass over an utterance.
 *
 * This type is engine-agnostic: whatever STT runtime is chosen later
 * (sherpa-onnx, ONNX Runtime Mobile, etc.) must adapt its output into this
 * shape so the rest of the app (transport, UI, metrics) never depends on a
 * specific engine's SDK types.
 *
 * No engine implements this yet — see [com.itantra.core.inference.SpeechRecognizerEngine].
 */
data class SpeechRecognitionResult(
    val languageCode: LanguageCode,

    /** Final recognized text. Empty string is valid (e.g. silence/no speech). */
    val text: String,

    /** Engine-reported confidence in [0.0, 1.0], or null if the engine
     *  doesn't expose one. */
    val confidence: Float?,

    /** Whether this is a final result or an intermediate/partial hypothesis
     *  from a streaming recognizer. Partial results may be shown in the UI
     *  but must not be transmitted. */
    val isFinal: Boolean,

    /** Wall-clock timestamp (System.currentTimeMillis) when this result was produced. */
    val timestampMillis: Long,
)
