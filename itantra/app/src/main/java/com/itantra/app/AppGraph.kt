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
import com.itantra.domain.repository.LanguagePackRepository
import com.itantra.data.benchmark.LocalBenchmarkRepository

import android.bluetooth.BluetoothManager
import com.itantra.core.crypto.SecureSessionManager
import com.itantra.core.transceiver.TransceiverCoordinator
import com.itantra.core.translation.TranslationRouter

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

    val bluetoothPeerTransport: com.itantra.core.transport.peer.BluetoothPeerTransport by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        com.itantra.core.transport.peer.BluetoothPeerTransport(context, bluetoothManager.adapter)
    }

    val wifiPeerTransport: com.itantra.core.transport.peer.WifiPeerTransport by lazy {
        com.itantra.core.transport.peer.WifiPeerTransport()
    }

    val transportEngine: com.itantra.core.transport.TransportCoordinator by lazy {
        // Default to Bluetooth initially
        com.itantra.core.transport.TransportCoordinator(bluetoothPeerTransport)
    }

    val secureSessionManager: SecureSessionManager by lazy {
        SecureSessionManager()
    }

    val translationEngine: com.itantra.core.translation.TranslationEngine by lazy {
        com.itantra.core.translation.CTranslate2TranslationEngine().apply {
            init(java.io.File(context.filesDir, "translation_models"))
        }
    }

    val translationRouter: TranslationRouter by lazy {
        TranslationRouter(translationEngine)
    }

    val transceiverCoordinator: TransceiverCoordinator by lazy {
        TransceiverCoordinator(context, activeLanguageSessionManager, languagePackRepository, transportEngine, metricsRecorder, secureSessionManager, translationRouter)
    }
}
