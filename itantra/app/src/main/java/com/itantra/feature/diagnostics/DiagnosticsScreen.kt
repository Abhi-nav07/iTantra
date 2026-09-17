package com.itantra.feature.diagnostics

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itantra.app.ui.components.SectionHeader
import com.itantra.app.ui.theme.*

@Composable
fun DiagnosticsScreen(
    viewModel: DiagnosticsViewModel,
    onBack: () -> Unit,
    onLaunchBenchmark: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.fillMaxHeight().widthIn(max = 600.dp)) {
            // ── Top Bar ─────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Navigate back")
            }
            Text("DIAGNOSTICS", style = MaterialTheme.typography.headlineMedium)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // ── DEVICE ──────────────────────────────────────────
            DiagnosticsSection(title = "DEVICE") {
                DiagnosticRow("Manufacturer", Build.MANUFACTURER.uppercase())
                DiagnosticRow("Model", Build.MODEL)
                DiagnosticRow("Android API", "${Build.VERSION.SDK_INT}")
                DiagnosticRow("ABI", Build.SUPPORTED_ABIS.firstOrNull() ?: "N/A")
            }

            // ── LANGUAGE ────────────────────────────────────────
            DiagnosticsSection(title = "LANGUAGE") {
                DiagnosticRow("Active language", state.activeLanguage?.displayName ?: "None")
                DiagnosticRow("Installed packs", "${state.installedPackCount} / 10")
            }

            // ── VAD SEGMENTATION (Module 5B) ──────────────────────────
            DiagnosticsSection(title = "VAD SEGMENTATION (Module 5B)") {
                DiagnosticRow("Pre-roll", "${state.metrics.vad.preRollMs} ms")
                DiagnosticRow("Endpoint silence", "${state.metrics.vad.endpointSilenceMs} ms")
                DiagnosticRow("Minimum speech", "${state.metrics.vad.minSpeechMs} ms")
                DiagnosticRow("Maximum utterance", "${state.metrics.vad.maxUtteranceS} s")
                DiagnosticRow("Last segment", state.metrics.vad.lastSegmentDurationS.display(" s") { String.format("%.2f", it) })
                DiagnosticRow("Last segment samples", state.metrics.vad.lastSegmentSamples.display(""))
            }

            // ── STT ─────────────────────────────────────────────
            DiagnosticsSection(title = "STT — Speech to Text") {
                DiagnosticRow("Model load time", state.metrics.stt.modelLoadTimeMillis.display(" ms"))
                DiagnosticRow("Inference time", state.metrics.stt.inferenceTimeMillis.display(" ms"))
                DiagnosticRow("Endpoint → final text", state.metrics.stt.endpointToFinalTextMillis.display(" ms"))
            }

            // ── TTS ─────────────────────────────────────────────
            DiagnosticsSection(title = "TTS — Text to Speech") {
                DiagnosticRow("Model load time", state.metrics.tts.modelLoadTimeMillis.display(" ms"))
                DiagnosticRow("Time to first audio", state.metrics.tts.timeToFirstAudioMillis.display(" ms"))
                DiagnosticRow("Synthesis duration", state.metrics.tts.synthesisDurationMillis.display(" ms"))
                DiagnosticRow("Real-Time Factor", state.metrics.tts.realTimeFactor.display("x"))
            }

            // ── TRANSPORT ───────────────────────────────────────
            DiagnosticsSection(title = "TRANSPORT — Bluetooth RFCOMM") {
                DiagnosticRow("Payload bytes", state.metrics.transport.payloadBytes.display(" B"))
                DiagnosticRow("Framed packet bytes", state.metrics.transport.packetBytes.display(" B"))
                DiagnosticRow("Round Trip Time (RTT)", state.metrics.transport.transmissionLatencyMillis.display(" ms"))
            }

            // ── SECURITY ────────────────────────────────────────
            DiagnosticsSection(title = "SECURITY — AES-256-GCM / ECDH / HKDF") {
                DiagnosticRow("Cipher", "AES-256-GCM")
                DiagnosticRow("Key exchange", "ECDH (NIST P-256)")
                DiagnosticRow("Key derivation", "HKDF-SHA256")
                DiagnosticRow("Handshake time", state.metrics.crypto.handshakeDurationMillis.display(" ms"))
                DiagnosticRow("Verification time", state.metrics.crypto.verificationDurationMillis.display(" ms"))
                DiagnosticRow("Avg encrypt", state.metrics.crypto.avgEncryptUs.display(" µs"))
                DiagnosticRow("Avg decrypt", state.metrics.crypto.avgDecryptUs.display(" µs"))
                DiagnosticRow("Auth failures", "${state.metrics.crypto.authFailures}", if (state.metrics.crypto.authFailures > 0) CriticalRed else SignalGreenDim)
                DiagnosticRow("Replay rejections", "${state.metrics.crypto.replayRejections}", if (state.metrics.crypto.replayRejections > 0) WarningAmber else SignalGreenDim)
            }

            // ── SYSTEM ──────────────────────────────────────────
            DiagnosticsSection(title = "SYSTEM") {
                DiagnosticRow(
                    "Process memory (PSS)",
                    state.metrics.system.approximateProcessMemoryBytes.display(" MB") { (it / (1024 * 1024)).toString() },
                )
                DiagnosticRow(
                    "Pack storage",
                    state.metrics.system.packStorageBytes.display(" MB") { (it / (1024 * 1024)).toString() },
                )
            }

            // ── END-TO-END ──────────────────────────────────────
            DiagnosticsSection(title = "END-TO-END") {
                DiagnosticRow("Speech-end → receive audio start", state.metrics.endToEndMillis.display(" ms"))
            }

            // ── LINK TRACE ──────────────────────────────────────
            DiagnosticsSection(title = "LINK TRACE — Last Message Pipeline") {
                LinkTraceRow("Speech Input", "—")
                LinkTraceRow("↓ STT", state.metrics.stt.inferenceTimeMillis.display(" ms"))
                LinkTraceRow("↓ Text Payload", state.metrics.transport.payloadBytes.display(" B"))
                LinkTraceRow("↓ Secure Packet", state.metrics.transport.packetBytes.display(" B"))
                LinkTraceRow("↓ Bluetooth RTT", state.metrics.transport.transmissionLatencyMillis.display(" ms"))
                LinkTraceRow("↓ TTS", state.metrics.tts.timeToFirstAudioMillis.display(" ms"))
                LinkTraceRow("Speaker Output", "—")
            }

            // ── PRIVACY SUMMARY ─────────────────────────────────
            DiagnosticsSection(title = "PRIVACY SUMMARY") {
                Text(
                    text = "Raw voice stays on device. Only encrypted semantic text is transmitted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SignalGreenDim,
                )
            }

            // ── LOW-BITRATE VISUALIZATION ───────────────────────
            DiagnosticsSection(title = "LOW-BITRATE VISUALIZATION") {
                val pcmBytes = state.metrics.stt.audioDurationMs * 32L // 16kHz 16-bit = 32 bytes/ms
                val pktBytes = (state.metrics.transport.packetBytes as? com.itantra.domain.model.Measurement.Measured)?.value ?: 0
                
                if (pcmBytes > 0 && pktBytes > 0) {
                    val pcmKb = String.format("%.1f KB", pcmBytes / 1024f)
                    val reduction = String.format("%.2f%%", 100f * (1f - (pktBytes.toFloat() / pcmBytes.toFloat())))
                    
                    Text("Raw PCM Equivalent", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Box(modifier = Modifier.fillMaxWidth().height(16.dp).background(TextDisabled))
                    Text(pcmKb, style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                    
                    Spacer(Modifier.height(Spacing.sm))
                    
                    Text("iTantra Secure Packet", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Box(modifier = Modifier.fillMaxWidth(if (pcmBytes > 0) kotlin.math.max(0.01f, pktBytes.toFloat() / pcmBytes.toFloat()) else 0.01f).height(16.dp).background(SignalGreen))
                    Text("$pktBytes B", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                    
                    Spacer(Modifier.height(Spacing.sm))
                    Text("Reduction: $reduction", style = MaterialTheme.typography.labelMedium, color = SignalGreen, fontWeight = FontWeight.Bold)
                } else {
                    Text("No message measured yet. Send a message to populate telemetry.", style = MaterialTheme.typography.bodyMedium, color = TextDisabled)
                }
            }

            // ── Benchmark Button ────────────────────────────────
            Spacer(Modifier.height(Spacing.lg))
            Button(
                onClick = onLaunchBenchmark,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.xxl),
                shape = ITantraShapes.button,
            ) {
                Text("RUN HINDI WER BENCHMARK", fontWeight = FontWeight.Bold)
            }
        }
        }
    }
}

// ═════════════════════════════════════════════════════════════════
// REUSABLE DIAGNOSTIC COMPONENTS
// ═════════════════════════════════════════════════════════════════

@Composable
private fun DiagnosticsSection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs)
            .background(SurfaceDarkElevated, ITantraShapes.card)
            .padding(14.dp),
    ) {
        SectionHeader(title = title)
        Spacer(Modifier.height(Spacing.sm))
        content()
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = TextPrimary,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = if (value == "N/A") TextDisabled else valueColor,
        )
    }
}

/**
 * A row in the Link Trace visualization showing the pipeline stage
 * and the metric at that stage.
 */
@Composable
private fun LinkTraceRow(stage: String, value: String) {
    val isArrow = stage.startsWith("↓")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stage,
            style = MaterialTheme.typography.labelMedium,
            color = if (isArrow) SignalGreenDim else TextPrimary,
            fontWeight = if (!isArrow) FontWeight.Bold else FontWeight.Normal,
        )
        if (value != "—") {
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = if (value == "N/A") TextDisabled else TextPrimary,
            )
        }
    }
}
