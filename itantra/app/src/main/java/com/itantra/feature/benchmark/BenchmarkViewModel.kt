package com.itantra.feature.benchmark

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itantra.core.inference.ActiveLanguageSessionManager
import com.itantra.core.inference.MicrophoneAudioSource
import com.itantra.core.metrics.TextNormalizer
import com.itantra.core.metrics.WerResult
import com.itantra.core.metrics.WordErrorRateCalculator
import com.itantra.data.benchmark.LocalBenchmarkRepository
import com.itantra.domain.model.BenchmarkResult
import com.itantra.domain.model.BenchmarkSession
import com.itantra.domain.model.EvidenceLevel
import com.itantra.domain.model.NoiseCondition
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BenchmarkSentenceDef(
    val id: String,
    val category: String,
    val referenceText: String
)

data class BenchmarkState(
    val selectedLanguage: String = "hi",
    val availableLanguages: List<String> = listOf("hi", "en", "bn", "gu", "mr", "kn", "ml", "ta", "te", "or"),
    val noiseCondition: NoiseCondition = NoiseCondition.QUIET,
    val sentences: List<BenchmarkSentenceDef> = emptyList(),
    val currentIndex: Int = 0,
    val results: List<BenchmarkResult> = emptyList(),
    val isRecording: Boolean = false,
    val currentTranscription: String = "",
    val sessionFinished: Boolean = false,
    val currentReferenceText: String = "",
    val currentSentenceId: String = "",
    val currentCategory: String = "",
    val lastResult: BenchmarkResult? = null,
    val savedSession: BenchmarkSession? = null
)

@SuppressLint("StaticFieldLeak")
class BenchmarkViewModel(
    private val context: Context,
    private val activeLanguageSessionManager: ActiveLanguageSessionManager,
    private val benchmarkRepository: LocalBenchmarkRepository
) : ViewModel() {

    private val _state = MutableStateFlow(BenchmarkState())
    val state: StateFlow<BenchmarkState> = _state

    private var startTimeMs = 0L
    private val audioSource = MicrophoneAudioSource(viewModelScope)
    private var recordingJob: Job? = null

    init {
        loadBenchmarkSentences(_state.value.selectedLanguage)
    }

    fun selectLanguage(lang: String) {
        if (lang == _state.value.selectedLanguage && _state.value.sentences.isNotEmpty()) return
        _state.update {
            it.copy(
                selectedLanguage = lang,
                currentIndex = 0,
                results = emptyList(),
                isRecording = false,
                currentTranscription = "",
                sessionFinished = false,
                lastResult = null,
                savedSession = null
            )
        }
        loadBenchmarkSentences(lang)
    }

    fun selectNoiseCondition(condition: NoiseCondition) {
        _state.update { it.copy(noiseCondition = condition) }
    }

    fun resetBenchmark() {
        val sentences = _state.value.sentences
        _state.update {
            it.copy(
                currentIndex = 0,
                results = emptyList(),
                isRecording = false,
                currentTranscription = "",
                sessionFinished = false,
                currentReferenceText = sentences.firstOrNull()?.referenceText ?: "",
                currentSentenceId = sentences.firstOrNull()?.id ?: "",
                currentCategory = sentences.firstOrNull()?.category ?: "",
                lastResult = null,
                savedSession = null
            )
        }
    }

    private fun loadBenchmarkSentences(lang: String) {
        viewModelScope.launch {
            try {
                val assetPath = "benchmark/${lang}_benchmark.json"
                val jsonString = context.assets.open(assetPath).bufferedReader().use { it.readText() }
                val json = Json { ignoreUnknownKeys = true }
                val sentences = json.decodeFromString<List<BenchmarkSentenceDef>>(jsonString)
                if (sentences.isNotEmpty()) {
                    _state.update {
                        it.copy(
                            sentences = sentences,
                            currentIndex = 0,
                            currentReferenceText = sentences[0].referenceText,
                            currentSentenceId = sentences[0].id,
                            currentCategory = sentences[0].category
                        )
                    }
                }
            } catch (e: Exception) {
                // Fallback to hindi_benchmark.json if specific not found
                try {
                    val fallback = context.assets.open("benchmark/hindi_benchmark.json").bufferedReader().use { it.readText() }
                    val json = Json { ignoreUnknownKeys = true }
                    val sentences = json.decodeFromString<List<BenchmarkSentenceDef>>(fallback)
                    _state.update {
                        it.copy(
                            sentences = sentences,
                            currentIndex = 0,
                            currentReferenceText = sentences[0].referenceText,
                            currentSentenceId = sentences[0].id,
                            currentCategory = sentences[0].category
                        )
                    }
                } catch (fallbackEx: Exception) {
                    fallbackEx.printStackTrace()
                }
            }
        }
    }

    fun startRecording() {
        if (_state.value.sessionFinished || _state.value.isRecording) return

        val engine = activeLanguageSessionManager.currentSttEngine
        if (engine == null || !engine.isLoaded) return

        _state.update { it.copy(isRecording = true, currentTranscription = "Listening...") }
        startTimeMs = SystemClock.elapsedRealtime()

        recordingJob = viewModelScope.launch {
            launch {
                delay(60_000L)
                if (_state.value.isRecording) {
                    stopRecording()
                }
            }
            try {
                audioSource.stream.collect { samples ->
                    engine.feed(samples)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopRecording() {
        if (!_state.value.isRecording) return
        _state.update { it.copy(isRecording = false) }
        recordingJob?.cancel()
        recordingJob = null

        val engine = activeLanguageSessionManager.currentSttEngine ?: return
        val t0 = SystemClock.elapsedRealtimeNanos()
        val durationMillis = SystemClock.elapsedRealtime() - startTimeMs

        if (durationMillis < 300) {
            viewModelScope.launch {
                _state.update { it.copy(currentTranscription = "Recording too short") }
                delay(2000)
                _state.update { it.copy(currentTranscription = "") }
                engine.reset()
            }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(currentTranscription = "Finalizing...") }
            try {
                val result = engine.finalizeUtterance()
                val t1 = SystemClock.elapsedRealtimeNanos()
                val latencyMillis = (t1 - t0) / 1_000_000

                val finalString = result.text
                val refText = _state.value.currentReferenceText
                val currentCategory = _state.value.currentCategory
                val isCritical = currentCategory.contains("critical", ignoreCase = true)
                val isMatch = isCritical && TextNormalizer.isExactMatch(refText, finalString)

                val werResult = WordErrorRateCalculator.calculate(refText, finalString)

                val benchResult = BenchmarkResult(
                    sentenceId = _state.value.currentSentenceId,
                    category = currentCategory,
                    isCritical = isCritical,
                    referenceText = refText,
                    recognizedText = finalString,
                    audioDurationMs = durationMillis,
                    processingMs = latencyMillis,
                    finalizationLatencyMs = latencyMillis,
                    referenceWordCount = werResult.referenceWordCount,
                    wer = werResult.wer,
                    substitutions = werResult.substitutions,
                    deletions = werResult.deletions,
                    insertions = werResult.insertions,
                    isCriticalMatch = isMatch,
                    isSuccess = true
                )

                val newResults = _state.value.results + benchResult
                val nextIdx = _state.value.currentIndex + 1

                if (nextIdx < _state.value.sentences.size) {
                    val nextSentence = _state.value.sentences[nextIdx]
                    _state.update {
                        it.copy(
                            results = newResults,
                            currentIndex = nextIdx,
                            currentReferenceText = nextSentence.referenceText,
                            currentSentenceId = nextSentence.id,
                            currentCategory = nextSentence.category,
                            currentTranscription = finalString,
                            lastResult = benchResult
                        )
                    }
                } else {
                    // Session finished
                    _state.update {
                        it.copy(
                            results = newResults,
                            sessionFinished = true,
                            currentTranscription = finalString,
                            lastResult = benchResult
                        )
                    }
                    saveSession(newResults)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _state.update { it.copy(currentTranscription = "Error: ${e.message}") }
            }
        }
    }

    private fun saveSession(results: List<BenchmarkResult>) {
        viewModelScope.launch {
            val werResults = results.map {
                WerResult(
                    referenceWordCount = it.referenceWordCount,
                    substitutions = it.substitutions,
                    deletions = it.deletions,
                    insertions = it.insertions
                )
            }
            val corpusMetrics = WordErrorRateCalculator.calculateCorpusWer(werResults)

            val latencies = results.map { it.finalizationLatencyMs }.sorted()
            val medianLatency = if (latencies.isNotEmpty()) latencies[latencies.size / 2] else 0L
            val meanLatency = if (latencies.isNotEmpty()) latencies.average().toLong() else 0L
            val totalAudioDuration = results.sumOf { it.audioDurationMs }

            val criticalCount = results.count { it.isCritical }
            val criticalMatches = results.count { it.isCritical && it.isCriticalMatch }
            val criticalRate = if (criticalCount > 0) criticalMatches.toFloat() / criticalCount else 1.0f

            val session = BenchmarkSession(
                timestampMs = System.currentTimeMillis(),
                language = _state.value.selectedLanguage,
                deviceManufacturer = Build.MANUFACTURER ?: "",
                deviceModel = Build.MODEL ?: "",
                androidVersion = Build.VERSION.RELEASE ?: "",
                abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "",
                threadCount = 1,
                modelVersion = "Whisper Tiny Multilingual INT8 ONNX",
                noiseCondition = _state.value.noiseCondition,
                totalUtterances = results.size,
                totalReferenceWords = corpusMetrics.totalReferenceWords,
                substitutions = corpusMetrics.totalSubstitutions,
                deletions = corpusMetrics.totalDeletions,
                insertions = corpusMetrics.totalInsertions,
                corpusWer = corpusMetrics.corpusWer,
                meanSentenceWer = corpusMetrics.meanSentenceWer,
                medianFinalizationLatencyMs = medianLatency,
                meanFinalizationLatencyMs = meanLatency,
                totalAudioDurationMs = totalAudioDuration,
                criticalPhraseCount = criticalCount,
                criticalPhraseExactMatches = criticalMatches,
                criticalPhraseExactMatchRate = criticalRate,
                evidenceLevel = EvidenceLevel.DEVICE_TESTED,
                results = results
            )
            benchmarkRepository.saveSession(session)
            _state.update { it.copy(savedSession = session) }
        }
    }
}
