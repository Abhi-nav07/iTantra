package com.itantra.core.inference

import com.itantra.domain.model.LanguageCode
import com.itantra.domain.model.SpeechRecognitionResult
import kotlinx.coroutines.flow.Flow

/**
 * Contract for an offline speech-to-text engine bound to one loaded
 * language pack.
 *
 * NO IMPLEMENTATION EXISTS YET. This interface exists purely so that:
 *  - [ActiveLanguageSessionManager] can be written and tested against a
 *    stable lifecycle contract before a real engine (sherpa-onnx,
 *    ONNX Runtime Mobile, etc.) is chosen and wired in, and
 *  - the choice of concrete STT runtime stays an implementation detail
 *    swappable behind this interface, per the "replaceable model/runtime
 *    components" requirement.
 *
 * Threading: implementations must document which methods are safe to call
 * from which threads/dispatchers; callers in this task always invoke
 * these from a coroutine, never assuming a specific dispatcher.
 *
 * Lifecycle contract: [load] must be called before [recognizeStream] /
 * [reset], and [unload] must fully release native/heap resources so a
 * different language's engine can be loaded without exceeding memory
 * budget (see the single-active-language rule in ActiveLanguageSessionManager).
 */
interface SpeechRecognizerEngine {

    val languageCode: LanguageCode

    /** True once [load] has completed successfully and [unload] has not
     *  since been called. */
    val isLoaded: Boolean

    /**
     * Loads model weights/graph into memory. Must be idempotent: calling
     * [load] while already loaded is a no-op, not an error.
     */
    suspend fun load()

    /**
     * Feeds PCM audio samples (FloatArray, -1.0 to 1.0, 16kHz) to the engine.
     */
    suspend fun feed(samples: FloatArray)

    /**
     * Finalizes the current utterance and returns the transcribed result.
     */
    suspend fun finalizeUtterance(): SpeechRecognitionResult

    /** Resets any internal streaming state (e.g. between utterances)
     *  without unloading the model. */
    suspend fun reset()

    /** Releases all native/heap resources held by this engine instance. */
    suspend fun unload()
}
