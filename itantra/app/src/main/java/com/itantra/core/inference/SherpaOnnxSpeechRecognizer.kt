package com.itantra.core.inference

import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import com.itantra.core.metrics.MetricsRecorder
import com.itantra.core.storage.LanguagePackStorage
import com.itantra.domain.model.LanguageCode
import com.itantra.domain.model.SpeechRecognitionResult
import com.k2fsa.sherpa.onnx.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SherpaOnnxSpeechRecognizer(
    private val context: Context,
    override val languageCode: LanguageCode,
    private val storage: LanguagePackStorage,
    private val metricsRecorder: MetricsRecorder
) : SpeechRecognizerEngine {

    private var recognizer: OfflineRecognizer? = null
    private var currentStream: OfflineStream? = null

    override var isLoaded: Boolean = false
        private set

    override suspend fun load() = withContext(Dispatchers.IO) {
        if (isLoaded) return@withContext

        val packDir = storage.packDirectory(languageCode)
        val modelFile = File(packDir, "model.int8.onnx")
        val tokensFile = File(packDir, "tokens.txt")

        if (!modelFile.exists() || !tokensFile.exists()) {
            throw IllegalStateException("Model files not found for language ${languageCode.wireCode}")
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

        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(
                sampleRate = 16000,
                featureDim = 80
            ),
            modelConfig = OfflineModelConfig(
                nemo = OfflineNemoEncDecCtcModelConfig(
                    model = modelFile.absolutePath
                ),
                tokens = tokensFile.absolutePath,
                numThreads = 2,
                debug = false
            )
        )

        recognizer = OfflineRecognizer(config = config)
        
        val t1 = SystemClock.elapsedRealtimeNanos()
        val memoryAfter = getProcessPssBytes()
        
        metricsRecorder.recordSystemMemory(memoryAfter - memoryBefore)
        metricsRecorder.recordSttModelLoadTime((t1 - t0) / 1_000_000)

        isLoaded = true
    }

    override suspend fun feed(samples: FloatArray) {
        if (currentStream == null) {
            currentStream = recognizer?.createStream()
        }
        currentStream?.acceptWaveform(samples, sampleRate = 16000)
    }

    override suspend fun finalizeUtterance(): SpeechRecognitionResult = withContext(Dispatchers.Default) {
        val stream = currentStream ?: return@withContext SpeechRecognitionResult(
            text = "",
            isFinal = true,
            languageCode = languageCode,
            confidence = 0f,
            timestampMillis = 0L
        )

        val rec = recognizer ?: throw IllegalStateException("Recognizer not loaded")

        rec.decode(stream)
        val resultText = rec.getResult(stream).text

        // Clean up stream for next utterance
        stream.release()
        currentStream = null

        SpeechRecognitionResult(
            text = resultText,
            isFinal = true,
            languageCode = languageCode,
            confidence = 0f,
            timestampMillis = 0L
        )
    }

    override suspend fun reset() {
        currentStream?.release()
        currentStream = null
    }

    override suspend fun unload() = withContext(Dispatchers.IO) {
        currentStream?.release()
        currentStream = null
        
        recognizer?.release()
        recognizer = null
        isLoaded = false
    }
}
