package com.itantra.feature.benchmark

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itantra.core.audio.SpeakerAudioSink
import com.itantra.core.inference.ActiveLanguageSessionManager
import com.itantra.data.benchmark.LocalBenchmarkRepository
import com.itantra.domain.model.EvidenceLevel
import com.itantra.domain.model.LanguageCode
import com.itantra.domain.model.SpeechSynthesisRequest
import com.itantra.domain.model.TtsEvaluationSession
import com.itantra.domain.model.TtsSentenceEvaluation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class TtsJsonSentence(
    val id: String,
    val text: String
)

data class TtsEvaluationState(
    val selectedLanguage: String = "hi",
    val availableLanguages: List<String> = listOf("hi", "en", "bn", "gu", "mr", "kn", "ml", "ta", "te", "or"),
    val evaluatorId: String = "Evaluator 1",
    val sentences: List<TtsJsonSentence> = emptyList(),
    val currentIndex: Int = 0,
    val isSynthesizing: Boolean = false,
    val isPlaying: Boolean = false,
    val statusMessage: String = "",
    // Current sentence evaluation form
    val intelligible: Boolean = true,
    val naturalness: Int = 4,
    val pronunciation: Int = 4,
    val comments: String = "",
    // Last synthesis telemetry
    val lastComputeTimeMs: Long = 0L,
    val lastAudioDurationMs: Long = 0L,
    val lastSampleRate: Int = 0,
    val lastSampleCount: Int = 0,
    val lastRtf: Float = 0f,
    val hasSynthesizedCurrent: Boolean = false,
    // Session results
    val recordedEvaluations: List<TtsSentenceEvaluation> = emptyList(),
    val sessionFinished: Boolean = false,
    val savedSession: TtsEvaluationSession? = null
)

@SuppressLint("StaticFieldLeak")
class TtsEvaluationViewModel(
    private val context: Context,
    private val activeLanguageSessionManager: ActiveLanguageSessionManager,
    private val benchmarkRepository: LocalBenchmarkRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TtsEvaluationState())
    val state: StateFlow<TtsEvaluationState> = _state

    private var audioSink: SpeakerAudioSink? = null
    private var lastGeneratedSamples: FloatArray? = null

    init {
        loadSentences(_state.value.selectedLanguage)
    }

    fun selectLanguage(lang: String) {
        if (lang == _state.value.selectedLanguage && _state.value.sentences.isNotEmpty()) return
        _state.update {
            it.copy(
                selectedLanguage = lang,
                currentIndex = 0,
                recordedEvaluations = emptyList(),
                sessionFinished = false,
                hasSynthesizedCurrent = false,
                statusMessage = "",
                savedSession = null
            )
        }
        loadSentences(lang)
    }

    fun setEvaluatorId(id: String) {
        _state.update { it.copy(evaluatorId = id) }
    }

    fun setIntelligible(value: Boolean) {
        _state.update { it.copy(intelligible = value) }
    }

    fun setNaturalness(value: Int) {
        _state.update { it.copy(naturalness = value.coerceIn(1, 5)) }
    }

    fun setPronunciation(value: Int) {
        _state.update { it.copy(pronunciation = value.coerceIn(1, 5)) }
    }

    fun setComments(value: String) {
        _state.update { it.copy(comments = value) }
    }

    private fun loadSentences(lang: String) {
        viewModelScope.launch {
            try {
                val jsonString = context.assets.open("benchmark/tts_sentences.json").bufferedReader().use { it.readText() }
                val json = Json { ignoreUnknownKeys = true }
                val map = json.decodeFromString<Map<String, List<TtsJsonSentence>>>(jsonString)
                val list = map[lang] ?: emptyList()
                _state.update {
                    it.copy(
                        sentences = list,
                        currentIndex = 0,
                        hasSynthesizedCurrent = false,
                        intelligible = true,
                        naturalness = 4,
                        pronunciation = 4,
                        comments = "",
                        statusMessage = if (list.isEmpty()) "No sentences found for $lang" else ""
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(statusMessage = "Error loading sentences: ${e.message}") }
            }
        }
    }

    fun synthesizeAndPlay() {
        val currentSentences = _state.value.sentences
        if (currentSentences.isEmpty()) return
        val currentSentence = currentSentences[_state.value.currentIndex]

        val ttsEngine = activeLanguageSessionManager.currentTtsEngine
        if (ttsEngine == null || !ttsEngine.isLoaded) {
            _state.update { it.copy(statusMessage = "TTS engine not loaded for ${_state.value.selectedLanguage.uppercase()}. DEVICE_REQUIRED or pack not installed.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSynthesizing = true, statusMessage = "Synthesizing...") }
            try {
                val t0 = SystemClock.elapsedRealtimeNanos()
                val langCode = LanguageCode.entries.firstOrNull { it.wireCode == _state.value.selectedLanguage } ?: LanguageCode.HINDI
                val req = SpeechSynthesisRequest(
                    correlationId = currentSentence.id,
                    text = currentSentence.text,
                    languageCode = langCode
                )
                val result = ttsEngine.synthesize(req)
                val t1 = SystemClock.elapsedRealtimeNanos()
                val computeTimeMs = (t1 - t0) / 1_000_000

                val samples = result.pcmAudio
                val sampleRate = result.sampleRateHz
                val audioDurationMs = if (sampleRate > 0) (samples.size.toFloat() / sampleRate * 1000).toLong() else 0L
                val rtf = if (audioDurationMs > 0) computeTimeMs.toFloat() / audioDurationMs else 0f

                lastGeneratedSamples = samples

                _state.update {
                    it.copy(
                        isSynthesizing = false,
                        isPlaying = true,
                        hasSynthesizedCurrent = true,
                        lastComputeTimeMs = computeTimeMs,
                        lastAudioDurationMs = audioDurationMs,
                        lastSampleRate = sampleRate,
                        lastSampleCount = samples.size,
                        lastRtf = rtf,
                        statusMessage = "Playing audio ($audioDurationMs ms, compute: $computeTimeMs ms)..."
                    )
                }

                // Play PCM audio
                withContext(Dispatchers.IO) {
                    if (audioSink == null) {
                        audioSink = SpeakerAudioSink(context)
                    }
                    audioSink?.init(sampleRate)
                    audioSink?.play(samples)
                    audioSink?.flushAndStop()
                }

                _state.update { it.copy(isPlaying = false, statusMessage = "Playback completed. Please rate intelligibility.") }

            } catch (e: Exception) {
                e.printStackTrace()
                _state.update { it.copy(isSynthesizing = false, isPlaying = false, statusMessage = "Error: ${e.message}") }
            }
        }
    }

    fun playAgain() {
        val samples = lastGeneratedSamples ?: return
        val rate = _state.value.lastSampleRate
        if (rate <= 0) return

        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isPlaying = true, statusMessage = "Playing audio again...") }
            try {
                if (audioSink == null) {
                    audioSink = SpeakerAudioSink(context)
                }
                audioSink?.init(rate)
                audioSink?.play(samples)
                audioSink?.flushAndStop()
            } finally {
                _state.update { it.copy(isPlaying = false, statusMessage = "Playback completed.") }
            }
        }
    }

    fun recordAndNext() {
        val currentSentences = _state.value.sentences
        if (currentSentences.isEmpty()) return
        val currentSentence = currentSentences[_state.value.currentIndex]

        val evaluation = TtsSentenceEvaluation(
            sentenceId = currentSentence.id,
            text = currentSentence.text,
            computeTimeMs = _state.value.lastComputeTimeMs,
            audioDurationMs = _state.value.lastAudioDurationMs,
            sampleRateHz = _state.value.lastSampleRate,
            pcmSampleCount = _state.value.lastSampleCount,
            isNonEmptyPcm = _state.value.lastSampleCount > 0,
            intelligible = _state.value.intelligible,
            naturalness = _state.value.naturalness,
            pronunciation = _state.value.pronunciation,
            comments = _state.value.comments
        )

        val newEvaluations = _state.value.recordedEvaluations + evaluation
        val nextIdx = _state.value.currentIndex + 1

        if (nextIdx < currentSentences.size) {
            _state.update {
                it.copy(
                    recordedEvaluations = newEvaluations,
                    currentIndex = nextIdx,
                    hasSynthesizedCurrent = false,
                    intelligible = true,
                    naturalness = 4,
                    pronunciation = 4,
                    comments = "",
                    statusMessage = "Sentence ${nextIdx + 1}/${currentSentences.size}"
                )
            }
        } else {
            // Completed all sentences for this language
            _state.update {
                it.copy(
                    recordedEvaluations = newEvaluations,
                    sessionFinished = true,
                    statusMessage = "Evaluation Complete!"
                )
            }
            saveSession(newEvaluations)
        }
    }

    private fun saveSession(evaluations: List<TtsSentenceEvaluation>) {
        viewModelScope.launch {
            val intelligibleCount = evaluations.count { it.intelligible }
            val total = evaluations.size
            val rate = if (total > 0) intelligibleCount.toFloat() / total else 0f
            val meanNat = if (total > 0) evaluations.map { it.naturalness }.average().toFloat() else 0f
            val meanPron = if (total > 0) evaluations.map { it.pronunciation }.average().toFloat() else 0f
            val meanCompute = if (total > 0) evaluations.map { it.computeTimeMs }.average().toLong() else 0L
            val meanAudio = if (total > 0) evaluations.map { it.audioDurationMs }.average().toLong() else 0L
            val meanRtf = if (meanAudio > 0) meanCompute.toFloat() / meanAudio else 0f

            val session = TtsEvaluationSession(
                timestampMs = System.currentTimeMillis(),
                language = _state.value.selectedLanguage,
                evaluatorId = _state.value.evaluatorId,
                modelIdentity = "Meta MMS TTS VITS ONNX",
                sentencesTested = total,
                intelligibleCount = intelligibleCount,
                intelligibilityPercent = rate,
                meanNaturalness = meanNat,
                meanPronunciation = meanPron,
                meanComputeTimeMs = meanCompute,
                meanAudioDurationMs = meanAudio,
                meanRtf = meanRtf,
                evidenceLevel = EvidenceLevel.HUMAN_REVIEWED,
                evaluations = evaluations
            )

            benchmarkRepository.saveTtsEvaluation(session)
            _state.update { it.copy(savedSession = session) }
        }
    }

    fun resetEvaluation() {
        _state.update {
            it.copy(
                currentIndex = 0,
                recordedEvaluations = emptyList(),
                sessionFinished = false,
                hasSynthesizedCurrent = false,
                statusMessage = "",
                savedSession = null
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioSink?.release()
        audioSink = null
    }
}
