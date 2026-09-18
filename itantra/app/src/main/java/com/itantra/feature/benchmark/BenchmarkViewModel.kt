package com.itantra.feature.benchmark

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itantra.core.inference.ActiveLanguageSessionManager
import com.itantra.core.metrics.WordErrorRateCalculator
import com.itantra.data.benchmark.LocalBenchmarkRepository
import com.itantra.domain.model.BenchmarkResult
import com.itantra.domain.model.BenchmarkSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import android.os.SystemClock
import android.annotation.SuppressLint

import com.itantra.core.inference.MicrophoneAudioSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

@Serializable
data class BenchmarkSentenceDef(
    val id: String,
    val category: String,
    val referenceText: String
)

data class BenchmarkState(
    val sentences: List<BenchmarkSentenceDef> = emptyList(),
    val currentIndex: Int = 0,
    val results: List<BenchmarkResult> = emptyList(),
    val isRecording: Boolean = false,
    val currentTranscription: String = "",
    val sessionFinished: Boolean = false,
    val currentReferenceText: String = "",
    val currentSentenceId: String = "",
    val currentCategory: String = "",
    val lastResult: BenchmarkResult? = null
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
        loadBenchmarkSentences()
    }

    private fun loadBenchmarkSentences() {
        viewModelScope.launch {
            try {
                val jsonString = context.assets.open("benchmark/hindi_benchmark.json").bufferedReader().use { it.readText() }
                val json = Json { ignoreUnknownKeys = true }
                val sentences = json.decodeFromString<List<BenchmarkSentenceDef>>(jsonString)
                if (sentences.isNotEmpty()) {
                    _state.update { 
                        it.copy(
                            sentences = sentences,
                            currentReferenceText = sentences[0].referenceText,
                            currentSentenceId = sentences[0].id,
                            currentCategory = sentences[0].category
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
                val werResult = WordErrorRateCalculator.calculate(_state.value.currentReferenceText, finalString)
                
                val benchResult = BenchmarkResult(
                    sentenceId = _state.value.currentSentenceId,
                    referenceText = _state.value.currentReferenceText,
                    recognizedText = finalString,
                    audioDurationMs = durationMillis,
                    processingMs = latencyMillis,
                    finalizationLatencyMs = latencyMillis,
                    referenceWordCount = werResult.referenceWordCount,
                    wer = werResult.wer,
                    substitutions = werResult.substitutions,
                    deletions = werResult.deletions,
                    insertions = werResult.insertions,
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
            val session = BenchmarkSession(
                timestampMs = System.currentTimeMillis(),
                deviceModel = android.os.Build.MODEL,
                androidVersion = android.os.Build.VERSION.RELEASE,
                threadCount = 1, // Will be made dynamic later
                modelVersion = "indicconformer-int8",
                results = results
            )
            benchmarkRepository.saveSession(session)
        }
    }
}
