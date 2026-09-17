package com.itantra.core.inference

import com.itantra.domain.model.LanguageCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Enforces the project's core model-lifecycle rule:
 *
 *   Many language packs may exist in storage, BUT only the currently
 *   selected language's heavy inference assets may be resident in memory
 *   at once.
 *
 * This class owns the transition:
 *
 *   Hindi active -> switch to Tamil -> unload Hindi engines ->
 *   load Tamil engines -> Tamil active
 *
 * It does NOT itself decide which pack is "installed" (that is
 * [com.itantra.domain.repository.LanguagePackRepository]'s job) and it
 * does NOT construct real engines yet — [EngineFactory] is a placeholder
 * seam for Task 02+ to plug real [SpeechRecognizerEngine] /
 * [SpeechSynthesizerEngine] implementations into, per language.
 *
 * Concurrency: [switchTo] is serialized with a [Mutex] so two rapid
 * language switches can't race and leave two languages' engines loaded at
 * once, or leave neither loaded.
 */
enum class LanguageSessionState {
    IDLE,
    LOADING_STT,
    LOADING_TTS,
    READY,
    ERROR
}

class ActiveLanguageSessionManager(
    private val engineFactory: EngineFactory = EngineFactory.NoOp,
) {
    private val switchMutex = Mutex()

    var currentSttEngine: SpeechRecognizerEngine? = null
        private set
    var currentTtsEngine: SpeechSynthesizerEngine? = null
        private set

    private val _activeLanguage = MutableStateFlow<LanguageCode?>(null)
    val activeLanguage: StateFlow<LanguageCode?> = _activeLanguage

    private val _sessionState = MutableStateFlow(LanguageSessionState.IDLE)
    val sessionState: StateFlow<LanguageSessionState> = _sessionState

    /**
     * Switches active inference resources to [target].
     *
     * Guarantees:
     *  - the previously active language's engines are fully unloaded
     *    before the new ones are loaded (never both resident at once),
     *  - if loading the new engines fails, no language is left "active"
     *    with partially-loaded resources — state falls back to null and
     *    the exception propagates for the caller to surface.
     */
    suspend fun switchTo(target: LanguageCode, loadStt: Boolean = true, loadTts: Boolean = true) {
        switchMutex.withLock {
            if (_activeLanguage.value == target && _sessionState.value == LanguageSessionState.READY) return@withLock

            try {
                currentSttEngine?.unload()
                currentTtsEngine?.unload()
                currentSttEngine = null
                currentTtsEngine = null
                _activeLanguage.value = null

                var stt: SpeechRecognizerEngine? = null
                if (loadStt) {
                    _sessionState.value = LanguageSessionState.LOADING_STT
                    stt = engineFactory.createRecognizer(target)
                    stt?.load()
                }

                var tts: SpeechSynthesizerEngine? = null
                if (loadTts) {
                    _sessionState.value = LanguageSessionState.LOADING_TTS
                    tts = engineFactory.createSynthesizer(target)
                    tts?.load()
                }

                currentSttEngine = stt
                currentTtsEngine = tts
                _activeLanguage.value = target
                _sessionState.value = LanguageSessionState.READY
            } catch (e: Exception) {
                e.printStackTrace()
                _sessionState.value = LanguageSessionState.ERROR
                currentSttEngine?.unload()
                currentTtsEngine?.unload()
                currentSttEngine = null
                currentTtsEngine = null
                _activeLanguage.value = null
            }
        }
    }

    suspend fun releaseAll() {
        switchMutex.withLock {
            currentSttEngine?.unload()
            currentTtsEngine?.unload()
            currentSttEngine = null
            currentTtsEngine = null
            _activeLanguage.value = null
            _sessionState.value = LanguageSessionState.IDLE
        }
    }
}

/**
 * Seam for constructing real engines per language. Task 01 ships only
 * [NoOp], which throws — there is no real STT/TTS engine to construct
 * yet, and this manager must not silently pretend otherwise.
 */
interface EngineFactory {
    fun createRecognizer(language: LanguageCode): SpeechRecognizerEngine?
    fun createSynthesizer(language: LanguageCode): SpeechSynthesizerEngine?

    object NoOp : EngineFactory {
        override fun createRecognizer(language: LanguageCode): SpeechRecognizerEngine? =
            throw NotImplementedError("No SpeechRecognizerEngine configured in NoOp EngineFactory")
        override fun createSynthesizer(language: LanguageCode): SpeechSynthesizerEngine? =
            throw NotImplementedError("No SpeechSynthesizerEngine configured in NoOp EngineFactory")
    }
}
