package com.itantra.feature.benchmark

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchmarkScreen(
    viewModel: BenchmarkViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hindi STT Benchmark") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            if (state.sentences.isEmpty()) {
                Text("Loading benchmark dataset...")
                return@Column
            }

            if (state.sessionFinished) {
                BenchmarkSummaryView(state)
            } else {
                BenchmarkActiveView(
                    state = state,
                    hasPermission = hasPermission,
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    onStartRecording = { viewModel.startRecording() },
                    onStopRecording = { viewModel.stopRecording() }
                )
            }
        }
    }
}

@Composable
fun BenchmarkActiveView(
    state: BenchmarkState,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    Text(
        text = "Sentence ${state.currentIndex + 1} / ${state.sentences.size}",
        style = MaterialTheme.typography.titleMedium
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Category: ${state.currentCategory}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.secondary
    )
    Spacer(modifier = Modifier.height(32.dp))

    Text("Reference:", style = MaterialTheme.typography.labelLarge)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Text(
            text = state.currentReferenceText,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    }

    Spacer(modifier = Modifier.height(32.dp))

    if (!hasPermission) {
        Button(onClick = onRequestPermission) {
            Text("Grant Microphone Permission")
        }
    } else {
        val buttonColor = if (state.isRecording) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
        val contentColor = if (state.isRecording) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
        
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(buttonColor)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            onStartRecording()
                            tryAwaitRelease()
                            onStopRecording()
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Hold to record", tint = contentColor, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text(if (state.isRecording) "RECORDING" else "HOLD TO TALK", color = contentColor, style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    Spacer(modifier = Modifier.height(32.dp))

    Text("Recognized:", style = MaterialTheme.typography.labelLarge)
    Text(
        text = state.currentTranscription.ifEmpty { "..." },
        modifier = Modifier.padding(vertical = 8.dp),
        style = MaterialTheme.typography.bodyLarge
    )

    state.lastResult?.let { result ->
        Spacer(modifier = Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Last Result Metrics", style = MaterialTheme.typography.titleSmall)
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                Text("WER: ${String.format("%.1f", result.wer * 100)}%")
                Text("Audio: ${result.audioDurationMs} ms")
                Text("STT Final: ${result.processingMs} ms")
                Text("RTF: ${String.format("%.2f", result.rtf)}")
            }
        }
    }
}

@Composable
fun BenchmarkSummaryView(state: BenchmarkState) {
    Text(
        text = "Benchmark Complete",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(16.dp))

    val validResults = state.results.filter { it.isSuccess && it.referenceWordCount > 0 }
    
    if (validResults.isEmpty()) {
        Text("No valid results recorded.")
        return
    }

    val avgWer = validResults.map { it.wer }.average()
    val medWer = validResults.map { it.wer }.sorted().let {
        if (it.isEmpty()) 0f else it[it.size / 2]
    }
    
    val latencies = validResults.map { it.finalizationLatencyMs }.sorted()
    val medLatency = if (latencies.isEmpty()) 0L else latencies[latencies.size / 2]
    val p95Latency = if (latencies.isEmpty()) 0L else latencies[(latencies.size * 0.95).toInt().coerceAtMost(latencies.size - 1)]

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Sessions: ${state.results.size}", style = MaterialTheme.typography.bodyLarge)
            Text("Valid: ${validResults.size}", style = MaterialTheme.typography.bodyLarge)
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Mean WER: ${String.format("%.1f", avgWer * 100)}%")
            Text("Median WER: ${String.format("%.1f", medWer * 100)}%")
            Text("Median Final Latency: $medLatency ms")
            Text("P95 Final Latency: $p95Latency ms")
        }
    }
    
    Spacer(modifier = Modifier.height(16.dp))
    Text("Results have been saved to local JSON.", style = MaterialTheme.typography.bodyMedium)
}
