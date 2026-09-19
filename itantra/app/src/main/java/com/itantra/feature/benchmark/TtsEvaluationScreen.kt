package com.itantra.feature.benchmark

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsEvaluationScreen(
    viewModel: TtsEvaluationViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TTS Human Intelligibility Evaluation") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.resetEvaluation() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset Evaluation")
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
            // Language and Evaluator Config
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Evaluation Setup", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        var langExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = langExpanded,
                            onExpandedChange = { if (!state.isSynthesizing && !state.isPlaying) langExpanded = !langExpanded },
                            modifier = Modifier.weight(1f).padding(end = 6.dp)
                        ) {
                            OutlinedTextField(
                                value = state.selectedLanguage.uppercase(),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Language") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = langExpanded) },
                                modifier = Modifier.menuAnchor(),
                                enabled = !state.isSynthesizing && !state.isPlaying
                            )
                            ExposedDropdownMenu(
                                expanded = langExpanded,
                                onDismissRequest = { langExpanded = false }
                            ) {
                                state.availableLanguages.forEach { lang ->
                                    DropdownMenuItem(
                                        text = { Text(lang.uppercase()) },
                                        onClick = {
                                            viewModel.selectLanguage(lang)
                                            langExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = state.evaluatorId,
                            onValueChange = { viewModel.setEvaluatorId(it) },
                            label = { Text("Evaluator") },
                            modifier = Modifier.weight(1f).padding(start = 6.dp),
                            singleLine = true
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (state.statusMessage.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Text(
                        text = state.statusMessage,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            if (state.sentences.isEmpty()) {
                Text("No sentences available for ${state.selectedLanguage.uppercase()}")
                return@Column
            }

            if (state.sessionFinished) {
                TtsEvaluationSummaryView(
                    state = state,
                    onRestart = { viewModel.resetEvaluation() }
                )
            } else {
                TtsSentenceEvaluationActiveView(
                    state = state,
                    onSynthesizeAndPlay = { viewModel.synthesizeAndPlay() },
                    onPlayAgain = { viewModel.playAgain() },
                    onIntelligibleChanged = { viewModel.setIntelligible(it) },
                    onNaturalnessChanged = { viewModel.setNaturalness(it) },
                    onPronunciationChanged = { viewModel.setPronunciation(it) },
                    onCommentsChanged = { viewModel.setComments(it) },
                    onRecordAndNext = { viewModel.recordAndNext() }
                )
            }
        }
    }
}

@Composable
private fun TtsSentenceEvaluationActiveView(
    state: TtsEvaluationState,
    onSynthesizeAndPlay: () -> Unit,
    onPlayAgain: () -> Unit,
    onIntelligibleChanged: (Boolean) -> Unit,
    onNaturalnessChanged: (Int) -> Unit,
    onPronunciationChanged: (Int) -> Unit,
    onCommentsChanged: (String) -> Unit,
    onRecordAndNext: () -> Unit
) {
    val currentSentence = state.sentences[state.currentIndex]

    Text(
        text = "Test Sentence ${state.currentIndex + 1} / ${state.sentences.size}",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(modifier = Modifier.height(8.dp))

    // Sentence Box
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Sentence ID: ${currentSentence.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(currentSentence.text, style = MaterialTheme.typography.titleMedium)
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Synthesis & Playback Controls
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Button(
            onClick = onSynthesizeAndPlay,
            enabled = !state.isSynthesizing && !state.isPlaying
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Synthesize & Play")
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (state.isSynthesizing) "Synthesizing..." else "Synthesize & Play")
        }

        if (state.hasSynthesizedCurrent) {
            OutlinedButton(
                onClick = onPlayAgain,
                enabled = !state.isSynthesizing && !state.isPlaying
            ) {
                Text("Replay Audio")
            }
        }
    }

    if (state.hasSynthesizedCurrent) {
        Spacer(modifier = Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Synthesis Telemetry", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Compute: ${state.lastComputeTimeMs} ms", style = MaterialTheme.typography.bodySmall)
                    Text("Audio: ${state.lastAudioDurationMs} ms", style = MaterialTheme.typography.bodySmall)
                    Text("RTF: ${String.format(java.util.Locale.US, "%.2f", state.lastRtf)}", style = MaterialTheme.typography.bodySmall)
                }
                Text("Sample Rate: ${state.lastSampleRate} Hz | Samples: ${state.lastSampleCount}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Human Scoring Form
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Human Listener Scoring", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Intelligible YES / NO
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("INTELLIGIBLE (Speech understood?)", style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = state.intelligible,
                        onClick = { onIntelligibleChanged(true) }
                    )
                    Text("YES", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    RadioButton(
                        selected = !state.intelligible,
                        onClick = { onIntelligibleChanged(false) }
                    )
                    Text("NO", style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Naturalness 1..5
            Text("NATURALNESS (1: Robotic .. 5: Human-like): ${state.naturalness}", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = state.naturalness.toFloat(),
                onValueChange = { onNaturalnessChanged(it.toInt()) },
                valueRange = 1f..5f,
                steps = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Pronunciation 1..5
            Text("PRONUNCIATION (1: Distorted .. 5: Flawless): ${state.pronunciation}", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = state.pronunciation.toFloat(),
                onValueChange = { onPronunciationChanged(it.toInt()) },
                valueRange = 1f..5f,
                steps = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Comments
            OutlinedTextField(
                value = state.comments,
                onValueChange = onCommentsChanged,
                label = { Text("Comments / Mispronunciations (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onRecordAndNext,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.currentIndex + 1 < state.sentences.size) "RECORD & NEXT SENTENCE" else "SAVE & FINISH EVALUATION")
            }
        }
    }
}

@Composable
private fun TtsEvaluationSummaryView(
    state: TtsEvaluationState,
    onRestart: () -> Unit
) {
    val session = state.savedSession

    Text(
        text = "TTS Intelligibility Evaluation Complete",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(16.dp))

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Session Metadata", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("Language: ${state.selectedLanguage.uppercase()}")
            Text("Evaluator: ${session?.evaluatorId ?: state.evaluatorId}")
            Text("Model: ${session?.modelIdentity ?: "Meta MMS TTS VITS ONNX"}")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text("Human Listener Results", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("Sentences Evaluated: ${session?.sentencesTested ?: state.recordedEvaluations.size}")
            Text("Intelligible: ${session?.intelligibleCount ?: 0} / ${session?.sentencesTested ?: 0}")
            Text(
                text = "Intelligibility: ${String.format(java.util.Locale.US, "%.1f", (session?.intelligibilityPercent ?: 0f) * 100)}%",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text("Mean Naturalness: ${String.format(java.util.Locale.US, "%.2f", session?.meanNaturalness ?: 0f)} / 5.0")
            Text("Mean Pronunciation: ${String.format(java.util.Locale.US, "%.2f", session?.meanPronunciation ?: 0f)} / 5.0")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text("Performance Telemetry", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("Mean Compute Time: ${session?.meanComputeTimeMs ?: 0L} ms")
            Text("Mean Audio Duration: ${session?.meanAudioDurationMs ?: 0L} ms")
            Text("Mean RTF: ${String.format(java.util.Locale.US, "%.2f", session?.meanRtf ?: 0f)}")
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
    Button(onClick = onRestart, modifier = Modifier.fillMaxWidth()) {
        Text("START NEW EVALUATION")
    }
}
