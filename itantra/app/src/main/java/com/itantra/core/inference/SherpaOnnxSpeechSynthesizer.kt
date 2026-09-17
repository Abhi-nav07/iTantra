package com.itantra.core.inference

import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import com.itantra.core.metrics.MetricsRecorder
import com.itantra.core.storage.LanguagePackStorage
import com.itantra.domain.model.LanguageCode
import com.itantra.domain.model.SpeechSynthesisRequest
import com.itantra.domain.model.SpeechSynthesisResult
import com.k2fsa.sherpa.onnx.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SherpaOnnxSpeechSynthesizer(
    private val context: Context,
    override val languageCode: LanguageCode,
    private val storage: LanguagePackStorage,
    private val metricsRecorder: MetricsRecorder
) : SpeechSynthesizerEngine {

    private var tts: OfflineTts? = null

    override var isLoaded: Boolean = false
        private set

    override suspend fun load() = withContext(Dispatchers.IO) {
        if (isLoaded) return@withContext

        val packDir = storage.packDirectory(languageCode)
        val modelFile = File(packDir, "tts_model.onnx")
        val lexiconFile = File(packDir, "lexicon.txt")
        val tokensFile = File(packDir, "tts_tokens.txt")

        if (!modelFile.exists() || !lexiconFile.exists() || !tokensFile.exists()) {
            throw IllegalStateException("TTS Model files not found for language ${languageCode.wireCode}")
        }

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val myPid = android.os.Process.myPid()
        
        fun getProcessPssBytes(): Long {
            val memoryInfoArray = activityManager.getProcessMemoryInfo(intArrayOf(myPid))
            return if (memoryInfoArray.isNotEmpty()) {
                memoryInfoArray[0].totalPss * 1024L
            } else 0L
        }

        val memoryBefore = getProcessPssBytes()
        val t0 = SystemClock.elapsedRealtimeNanos()

        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = modelFile.absolutePath,
                    lexicon = lexiconFile.absolutePath,
                    tokens = tokensFile.absolutePath,
                ),
                numThreads = 1,
                debug = false
            )
        )

        tts = OfflineTts(config = config)

        val t1 = SystemClock.elapsedRealtimeNanos()
        val memoryAfter = getProcessPssBytes()
        
        metricsRecorder.recordSystemMemory(memoryAfter - memoryBefore) // Note: this tracks incremental usage
        metricsRecorder.recordTtsModelLoadTime((t1 - t0) / 1_000_000)

        isLoaded = true
    }

    override suspend fun synthesize(request: SpeechSynthesisRequest): SpeechSynthesisResult = withContext(Dispatchers.Default) {
        val t0 = SystemClock.elapsedRealtimeNanos()
        val engine = tts ?: throw IllegalStateException("TTS Engine not loaded")
        
        // Ensure text is not empty
        if (request.text.isBlank()) {
            return@withContext SpeechSynthesisResult(
                correlationId = request.correlationId,
                pcmAudio = FloatArray(0),
                sampleRateHz = 22050,
                channelCount = 1,
                durationMillis = 0L
            )
        }

        val generatedAudio = engine.generate(request.text)
        
        val t1 = SystemClock.elapsedRealtimeNanos()
        val synthesisTimeMs = (t1 - t0) / 1_000_000
        
        val samples = generatedAudio.samples
        val sampleRate = generatedAudio.sampleRate
        
        val audioDurationMs = if (sampleRate > 0) {
            (samples.size.toFloat() / sampleRate * 1000).toLong()
        } else {
            0L
        }

        val rtf = if (audioDurationMs > 0) {
            synthesisTimeMs.toFloat() / audioDurationMs.toFloat()
        } else {
            0f
        }
        
        // Track time to first audio. Here we use batch mode so TTFA = SynthesisTime
        metricsRecorder.recordTtsTimeToFirstAudio(synthesisTimeMs)
        metricsRecorder.recordTtsSynthesisDuration(audioDurationMs)
        metricsRecorder.recordTtsRealTimeFactor(rtf.toDouble())

        SpeechSynthesisResult(
            correlationId = request.correlationId,
            pcmAudio = samples,
            sampleRateHz = sampleRate,
            channelCount = 1,
            durationMillis = audioDurationMs
        )
    }

    override suspend fun unload() = withContext(Dispatchers.IO) {
        tts?.release()
        tts = null
        isLoaded = false
    }
}
