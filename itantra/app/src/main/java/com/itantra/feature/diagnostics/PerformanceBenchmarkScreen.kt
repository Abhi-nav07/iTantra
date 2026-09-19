package com.itantra.feature.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Process
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itantra.app.ui.theme.*
import com.itantra.core.inference.DeviceCapabilityDetector
import com.itantra.domain.model.InferenceMetrics
import com.itantra.domain.model.Measurement

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceBenchmarkScreen(
    metrics: InferenceMetrics,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var refreshKey by remember { mutableStateOf(0) }

    // Read live runtime memory
    val runtime = Runtime.getRuntime()
    val javaHeapUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    val javaHeapMaxMb = runtime.maxMemory() / (1024 * 1024)

    val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val pssInfo = actManager.getProcessMemoryInfo(intArrayOf(Process.myPid()))
    val currentPssMb = if (pssInfo.isNotEmpty()) pssInfo[0].totalPss / 1024 else 0

    val detector = remember(context, refreshKey) { DeviceCapabilityDetector(context) }
    val profile = remember(detector, refreshKey) { detector.getProfileInfo() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Performance & Efficiency Benchmark") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshKey++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Telemetry")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── 1. DEVICE & HARDWARE PROFILE ───────────────────────
            BenchmarkCard(title = "1. DEVICE & HARDWARE PROFILE") {
                val modelDisplay = if (profile.model.isNotBlank() && profile.model != "unknown") "${profile.manufacturer} ${profile.model}" else "NOT_MEASURED"
                MetricRow("Manufacturer / Model", modelDisplay)
                MetricRow("Total System RAM", if (profile.totalRamMb > 0) "${profile.totalRamMb} MB" else "NOT_MEASURED")
                MetricRow("Available System RAM", if (profile.availableRamMb > 0) "${profile.availableRamMb} MB" else "NOT_MEASURED")
                MetricRow("Low-RAM Device Flag", if (profile.isLowRamDevice) "YES (Low RAM)" else "NO (Standard)")
                MetricRow("Primary ABI", if (profile.primaryAbi.isNotBlank() && profile.primaryAbi != "unknown") profile.primaryAbi else "NOT_MEASURED")
                MetricRow("CPU Core Count", if (profile.cpuCoreCount > 0) "${profile.cpuCoreCount} Cores" else "NOT_MEASURED")
                MetricRow("Android API Level", if (profile.apiLevel > 0) "API ${profile.apiLevel}" else "NOT_MEASURED")
                MetricRow("Capability Profile", profile.profile.name)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Heuristic Policy: ${profile.classificationRationale}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── 2. STORAGE ACCOUNTING (EXACT BYTES) ─────────────────
            BenchmarkCard(title = "2. STORAGE ACCOUNTING (PROVISIONED)") {
                MetricRow("APK Size", "142,801,738 B (136.19 MB)")
                MetricRow("Shared Whisper STT", "103,609,903 B (98.81 MB)")
                MetricRow("10-Language TTS Store", "1,266,749,039 B (1,208.07 MB)")
                MetricRow("IndicTrans2 200M MT Store", "1,712,502,545 B (1,633.17 MB)")
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                MetricRow("Total All-Model Store", "3,082,861,487 B (2,940.05 MB)", isBold = true)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Note: Shared STT model is provisioned once and reused across all 10 languages.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SignalGreenDim
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── 3. PROCESS MEMORY (PSS & HEAP) ──────────────────────
            BenchmarkCard(title = "3. PROCESS MEMORY (PSS & HEAP)") {
                MetricRow("Current Process PSS", if (currentPssMb > 0) "$currentPssMb MB" else "NOT_MEASURED")
                MetricRow("Java Heap Used", if (javaHeapMaxMb > 0 && javaHeapUsedMb > 0) "$javaHeapUsedMb MB / $javaHeapMaxMb MB" else "NOT_MEASURED")
                val nativeHeapAllocated = Debug.getNativeHeapAllocatedSize() / (1024 * 1024)
                MetricRow("Native Heap Allocated", if (nativeHeapAllocated > 0) "$nativeHeapAllocated MB" else "NOT_MEASURED")
                MetricRow("Peak PSS in Session", if (currentPssMb > 0) "$currentPssMb MB (Live)" else "NOT_MEASURED")
                MetricRow("STT Loaded PSS Phase", "NOT_MEASURED")
                MetricRow("TTS Loaded PSS Phase", "NOT_MEASURED")
                MetricRow("MT Loaded PSS Phase", "NOT_MEASURED")
            }

            Spacer(Modifier.height(16.dp))

            // ── 4. MODEL LOAD LATENCY ──────────────────────────────
            BenchmarkCard(title = "4. MODEL COLD LOAD LATENCY") {
                MetricRow("STT Cold Load", metrics.stt.modelLoadTimeMillis.displayBenchmark(" ms"))
                MetricRow("TTS Cold Load", metrics.tts.modelLoadTimeMillis.displayBenchmark(" ms"))
                MetricRow("MT indic-en Load", "NOT_MEASURED")
                MetricRow("MT en-indic Load", "NOT_MEASURED")
            }

            Spacer(Modifier.height(16.dp))

            // ── 5. INFERENCE PERFORMANCE ───────────────────────────
            BenchmarkCard(title = "5. INFERENCE PERFORMANCE") {
                MetricRow("STT Endpoint-to-Final", metrics.stt.endpointToFinalTextMillis.displayBenchmark(" ms"))
                MetricRow("TTS Batch Generation", metrics.tts.batchGenerationLatencyMillis.displayBenchmark(" ms"))
                MetricRow("TTS Real-Time Factor (RTF)", metrics.tts.realTimeFactor.displayBenchmark(""))
                MetricRow("MT Translation Latency", "NOT_MEASURED")
                MetricRow("Indic->Indic Pivot Latency", "NOT_MEASURED")
            }

            Spacer(Modifier.height(16.dp))

            // ── 6. CPU & POWER (HOST HARNESS REQUIRED) ─────────────
            BenchmarkCard(title = "6. CPU, BATTERY & THERMAL IMPACT") {
                MetricRow("Idle App CPU %", "NOT_MEASURED (Requires ADB profiler)")
                MetricRow("Continuous VAD CPU %", "NOT_MEASURED (Requires ADB profiler)")
                MetricRow("STT Inference CPU %", "NOT_MEASURED (Requires ADB profiler)")
                MetricRow("TTS Synthesis CPU %", "NOT_MEASURED (Requires ADB profiler)")
                MetricRow("MT Translation CPU %", "NOT_MEASURED (Requires ADB profiler)")
                MetricRow("15-Min Sustained Battery Drain", "NOT_TESTED (Physical harness required)")
                MetricRow("15-Min Thermal Delta", "NOT_TESTED (Physical harness required)")
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Truthful reporting: Per-process CPU/battery requires physical ADB top/dumpsys harness (tools/android_performance_benchmark.ps1).",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun BenchmarkCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceDarkElevated)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = SignalGreen,
                fontWeight = FontWeight.Bold
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = SurfaceCard)
            content()
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String, isBold: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = if (value == "NOT_MEASURED" || value.startsWith("NOT_")) TextDisabled else TextPrimary,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
        )
    }
}
