package com.itantra.app

import android.content.Context
import com.itantra.core.inference.ActiveLanguageSessionManager
import com.itantra.core.inference.EngineFactory
import com.itantra.core.inference.SherpaOnnxSpeechRecognizer
import com.itantra.core.inference.SpeechRecognizerEngine
import com.itantra.core.inference.SpeechSynthesizerEngine
import com.itantra.core.metrics.InMemoryMetricsRecorder
import com.itantra.core.metrics.MetricsRecorder
import com.itantra.data.languagepack.FileLanguagePackStorage
import com.itantra.data.languagepack.LanguagePackManifestParser
import com.itantra.data.languagepack.RealLanguagePackRepository
import com.itantra.domain.model.LanguageCode
import com.itantra.domain.model.SpeechSynthesisResult
import com.itantra.domain.repository.LanguagePackRepository
import com.itantra.data.benchmark.LocalBenchmarkRepository
import com.itantra.core.transport.BluetoothTransportEngine
import android.bluetooth.BluetoothManager
import com.itantra.core.crypto.SecureSessionManager
import com.itantra.core.transceiver.TransceiverCoordinator

object AppGraph {
    private var appContext: Context? = null
    
    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    private val context: Context
        get() = requireNotNull(appContext) { "AppGraph not initialized" }

    val metricsRecorder: MetricsRecorder by lazy { InMemoryMetricsRecorder() }

    val languagePackRepository: LanguagePackRepository by lazy {
        RealLanguagePackRepository(
            context,
            FileLanguagePackStorage(context),
            LanguagePackManifestParser
        )
    }

    val localBenchmarkRepository: LocalBenchmarkRepository by lazy {
        LocalBenchmarkRepository(context)
    }

    val activeLanguageSessionManager: ActiveLanguageSessionManager by lazy {
        val factory = object : EngineFactory {
            override fun createRecognizer(language: LanguageCode): SpeechRecognizerEngine {
                return SherpaOnnxSpeechRecognizer(context, language, FileLanguagePackStorage(context), metricsRecorder)
            }

            override fun createSynthesizer(language: LanguageCode): SpeechSynthesizerEngine {
                return com.itantra.core.inference.SherpaOnnxSpeechSynthesizer(
                    context, language, FileLanguagePackStorage(context), metricsRecorder
                )
            }
        }
        ActiveLanguageSessionManager(factory)
    }

    val transportEngine: BluetoothTransportEngine by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        BluetoothTransportEngine(context, bluetoothManager.adapter)
    }

    val secureSessionManager: SecureSessionManager by lazy {
        SecureSessionManager()
    }

    val transceiverCoordinator: TransceiverCoordinator by lazy {
        TransceiverCoordinator(context, activeLanguageSessionManager, transportEngine, metricsRecorder, secureSessionManager)
    }
}
