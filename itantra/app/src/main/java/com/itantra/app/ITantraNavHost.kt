package com.itantra.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.itantra.feature.diagnostics.DiagnosticsScreen
import com.itantra.feature.diagnostics.DiagnosticsViewModel
import com.itantra.feature.languages.LanguagePacksScreen
import com.itantra.feature.languages.LanguagePacksViewModel
import com.itantra.feature.transceiver.ConnectScreen
import com.itantra.feature.transceiver.ConnectViewModel
import com.itantra.feature.transceiver.TransceiverScreen
import com.itantra.feature.transceiver.TransceiverViewModel

/** Route constants — the single source of truth for navigation destinations. */
object ITantraDestinations {
    const val TRANSCEIVER = "transceiver"
    const val LANGUAGE_PACKS = "language_packs"
    const val DIAGNOSTICS = "diagnostics"
    const val BENCHMARK = "benchmark"
    const val TTS_EVALUATION = "tts_evaluation"
    const val PERFORMANCE_BENCHMARK = "performance_benchmark"
    const val CONNECT = "connect"
}

@Composable
fun ITantraNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = ITantraDestinations.TRANSCEIVER,
    ) {
        composable(ITantraDestinations.TRANSCEIVER) {
            val viewModel = viewModelWithFactory {
                TransceiverViewModel(
                    languagePackRepository = AppGraph.languagePackRepository,
                    metricsRecorder = AppGraph.metricsRecorder,
                    transportEngine = AppGraph.transportEngine,
                    coordinator = AppGraph.transceiverCoordinator
                )
            }
            TransceiverScreen(
                viewModel = viewModel,
                onOpenLanguagePacks = { navController.navigate(ITantraDestinations.LANGUAGE_PACKS) },
                onOpenDiagnostics = { navController.navigate(ITantraDestinations.DIAGNOSTICS) },
                onOpenConnect = { navController.navigate(ITantraDestinations.CONNECT) }
            )
        }
        composable(ITantraDestinations.CONNECT) {
            val context = LocalContext.current
            val viewModel = viewModelWithFactory {
                val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
                ConnectViewModel(
                    transportCoordinator = AppGraph.transportEngine,
                    bluetoothTransport = AppGraph.bluetoothPeerTransport,
                    wifiTransport = AppGraph.wifiPeerTransport,
                    bluetoothAdapter = bluetoothManager.adapter
                )
            }
            ConnectScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(ITantraDestinations.LANGUAGE_PACKS) {
            val viewModel = viewModelWithFactory {
                LanguagePacksViewModel(
                    repository = AppGraph.languagePackRepository,
                    sessionManager = AppGraph.activeLanguageSessionManager
                )
            }
            LanguagePacksScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(ITantraDestinations.DIAGNOSTICS) {
            val viewModel = viewModelWithFactory {
                DiagnosticsViewModel(
                    metricsRecorder = AppGraph.metricsRecorder,
                    languagePackRepository = AppGraph.languagePackRepository,
                )
            }
            DiagnosticsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onLaunchBenchmark = { navController.navigate(ITantraDestinations.BENCHMARK) },
                onLaunchTtsEvaluation = { navController.navigate(ITantraDestinations.TTS_EVALUATION) },
                onLaunchPerformanceBenchmark = { navController.navigate(ITantraDestinations.PERFORMANCE_BENCHMARK) }
            )
        }
        composable(ITantraDestinations.BENCHMARK) {
            val context = LocalContext.current
            val viewModel = viewModelWithFactory {
                com.itantra.feature.benchmark.BenchmarkViewModel(
                    context = context,
                    activeLanguageSessionManager = AppGraph.activeLanguageSessionManager,
                    benchmarkRepository = AppGraph.localBenchmarkRepository
                )
            }
            com.itantra.feature.benchmark.BenchmarkScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(ITantraDestinations.TTS_EVALUATION) {
            val context = LocalContext.current
            val viewModel = viewModelWithFactory {
                com.itantra.feature.benchmark.TtsEvaluationViewModel(
                    context = context,
                    activeLanguageSessionManager = AppGraph.activeLanguageSessionManager,
                    benchmarkRepository = AppGraph.localBenchmarkRepository
                )
            }
            com.itantra.feature.benchmark.TtsEvaluationScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(ITantraDestinations.PERFORMANCE_BENCHMARK) {
            val metrics by AppGraph.metricsRecorder.latest.collectAsState()
            com.itantra.feature.diagnostics.PerformanceBenchmarkScreen(
                metrics = metrics,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
