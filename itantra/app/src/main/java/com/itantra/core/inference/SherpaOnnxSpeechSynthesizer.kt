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

    private fun isModelAvailable(storage: LanguagePackStorage, code: LanguageCode): Boolean {
        val spec = ModelFileSpecs.getTtsSpec(code) ?: return false
        val ttsDir = File(storage.packDirectory(code), "tts")
        if (!ttsDir.exists()) return false

        return spec.requiredFiles.all { File(ttsDir, it).exists() && File(ttsDir, it).length() > 0 }
    }

    override suspend fun load() = withContext(Dispatchers.IO) {
        if (isLoaded) return@withContext

        val spec = ModelFileSpecs.getTtsSpec(languageCode) ?: throw UnsupportedOperationException("No TTS spec for language $languageCode")
        val packDir = storage.packDirectory(languageCode)
        val ttsDir = File(packDir, "tts")

        for (requiredFile in spec.requiredFiles) {
            val f = File(ttsDir, requiredFile)
            if (!f.exists() || f.length() == 0L) {
                throw IllegalStateException("Missing or zero-byte TTS model file: $requiredFile for language ${languageCode.wireCode}")
            }
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
                    model = File(ttsDir, spec.mainModelFile).absolutePath,
                    tokens = File(ttsDir, spec.tokensFile).absolutePath,
                    dataDir = "" // Piper typically uses dataDir in Sherpa, but passing empty uses built-in or assumes single-speaker no-lexicon.
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

        // TTFA proxy = full batch generation latency (current batch VITS synthesis produces complete waveform before playback)
        metricsRecorder.recordTtsTimeToFirstAudio(synthesisTimeMs)
        metricsRecorder.recordTtsSynthesisDuration(synthesisTimeMs)
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
