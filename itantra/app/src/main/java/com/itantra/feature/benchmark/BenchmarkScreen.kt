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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.itantra.domain.model.NoiseCondition

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
                title = { Text("STT Accuracy Benchmark") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.resetBenchmark() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset Benchmark")
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
            // Language and Condition Selection Bar
            LanguageAndConditionSelector(
                selectedLanguage = state.selectedLanguage,
                availableLanguages = state.availableLanguages,
                selectedCondition = state.noiseCondition,
                onLanguageSelected = { viewModel.selectLanguage(it) },
                onConditionSelected = { viewModel.selectNoiseCondition(it) },
                enabled = !state.isRecording
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (state.sentences.isEmpty()) {
                Text("Loading benchmark dataset for ${state.selectedLanguage.uppercase()}...")
                return@Column
            }

            if (state.sessionFinished) {
                BenchmarkSummaryView(
                    state = state,
                    onRestart = { viewModel.resetBenchmark() }
                )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageAndConditionSelector(
    selectedLanguage: String,
    availableLanguages: List<String>,
    selectedCondition: NoiseCondition,
    onLanguageSelected: (String) -> Unit,
    onConditionSelected: (NoiseCondition) -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Benchmark Configuration", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                // Language Dropdown
                var langExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = langExpanded,
                    onExpandedChange = { if (enabled) langExpanded = !langExpanded },
                    modifier = Modifier.weight(1f).padding(end = 6.dp)
                ) {
                    OutlinedTextField(
                        value = selectedLanguage.uppercase(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Language") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = langExpanded) },
                        modifier = Modifier.menuAnchor(),
                        enabled = enabled
                    )
                    ExposedDropdownMenu(
                        expanded = langExpanded,
                        onDismissRequest = { langExpanded = false }
                    ) {
                        availableLanguages.forEach { lang ->
                            DropdownMenuItem(
                                text = { Text(lang.uppercase()) },
                                onClick = {
                                    onLanguageSelected(lang)
                                    langExpanded = false
                                }
                            )
                        }
                    }
                }

                // Noise Condition Dropdown
                var condExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = condExpanded,
                    onExpandedChange = { if (enabled) condExpanded = !condExpanded },
                    modifier = Modifier.weight(1.2f).padding(start = 6.dp)
                ) {
                    OutlinedTextField(
                        value = selectedCondition.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Condition") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = condExpanded) },
                        modifier = Modifier.menuAnchor(),
                        enabled = enabled
                    )
                    ExposedDropdownMenu(
                        expanded = condExpanded,
                        onDismissRequest = { condExpanded = false }
                    ) {
                        NoiseCondition.values().forEach { cond ->
                            DropdownMenuItem(
                                text = { Text(cond.name) },
                                onClick = {
                                    onConditionSelected(cond)
                                    condExpanded = false
                                }
                            )
                        }
                    }
                }
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
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Category: ${state.currentCategory}",
        style = MaterialTheme.typography.labelMedium,
        color = if (state.currentCategory.contains("critical", ignoreCase = true)) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.secondary
        }
    )
    Spacer(modifier = Modifier.height(20.dp))

    Text("Reference Sentence:", style = MaterialTheme.typography.labelLarge)
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

    Spacer(modifier = Modifier.height(24.dp))

    if (!hasPermission) {
        Button(onClick = onRequestPermission) {
            Text("Grant Microphone Permission")
        }
    } else {
        val buttonColor = if (state.isRecording) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
        val contentColor = if (state.isRecording) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer

        Box(
            modifier = Modifier
                .size(110.dp)
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
                Icon(Icons.Default.PlayArrow, contentDescription = "Hold to record", tint = contentColor, modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.height(4.dp))
                Text(if (state.isRecording) "RECORDING" else "HOLD TO TALK", color = contentColor, style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    Text("Recognized:", style = MaterialTheme.typography.labelLarge)
    Text(
        text = state.currentTranscription.ifEmpty { "..." },
        modifier = Modifier.padding(vertical = 4.dp),
        style = MaterialTheme.typography.bodyLarge
    )

    state.lastResult?.let { result ->
        Spacer(modifier = Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Last Utterance Metrics", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("WER: ${String.format(java.util.Locale.US, "%.1f", result.wer * 100)}%")
                    Text("S: ${result.substitutions}  D: ${result.deletions}  I: ${result.insertions}")
                }
                Text("Audio: ${result.audioDurationMs} ms | STT Final: ${result.processingMs} ms")
                Text("RTF: ${String.format(java.util.Locale.US, "%.2f", result.rtf)}")
                if (result.isCritical) {
                    Text(
                        text = if (result.isCriticalMatch) "CRITICAL EXACT MATCH: PASS" else "CRITICAL EXACT MATCH: FAIL",
                        color = if (result.isCriticalMatch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun BenchmarkSummaryView(
    state: BenchmarkState,
    onRestart: () -> Unit
) {
    Text(
        text = "Benchmark Complete (${state.selectedLanguage.uppercase()})",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(16.dp))

    val session = state.savedSession
    val validResults = state.results.filter { it.isSuccess && it.referenceWordCount > 0 }

    if (validResults.isEmpty()) {
        Text("No valid results recorded.")
        return
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Session Metadata", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("Model: ${session?.modelVersion ?: "Whisper Tiny Multilingual INT8 ONNX"}")
            Text("Condition: ${session?.noiseCondition ?: state.noiseCondition}")
            Text("Device: ${session?.deviceManufacturer} ${session?.deviceModel}")
            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Text("Accuracy Metrics", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("Utterances Tested: ${state.results.size}")
            Text("Total Reference Words: ${session?.totalReferenceWords ?: 0}")
            Text("Substitutions: ${session?.substitutions} | Deletions: ${session?.deletions} | Insertions: ${session?.insertions}")
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Corpus WER: ${String.format(java.util.Locale.US, "%.1f", (session?.corpusWer ?: 0f) * 100)}%",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text("Mean Sentence WER: ${String.format(java.util.Locale.US, "%.1f", (session?.meanSentenceWer ?: 0f) * 100)}%")
            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Text("Critical Phrase Evaluation", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("Critical Phrases: ${session?.criticalPhraseCount ?: 0}")
            Text("Exact Matches: ${session?.criticalPhraseExactMatches ?: 0}")
            Text(
                text = "Critical Exact Match Rate: ${String.format(java.util.Locale.US, "%.1f", (session?.criticalPhraseExactMatchRate ?: 0f) * 100)}%",
                fontWeight = FontWeight.Bold
            )
            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Text("Timing Metrics", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("Median Finalization: ${session?.medianFinalizationLatencyMs} ms")
            Text("Mean Finalization: ${session?.meanFinalizationLatencyMs} ms")
            Text("Total Audio: ${session?.totalAudioDurationMs} ms")
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
    Button(onClick = onRestart, modifier = Modifier.fillMaxWidth()) {
        Text("RUN AGAIN")
    }
}
