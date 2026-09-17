package com.itantra.feature.transceiver

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.itantra.app.ui.components.*
import com.itantra.app.ui.theme.*
import com.itantra.core.crypto.SecureSessionState
import com.itantra.domain.model.*

@Composable
fun TransceiverScreen(
    viewModel: TransceiverViewModel,
    onOpenLanguagePacks: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenConnect: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxHeight()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        ) {
            // ── Product Header ──────────────────────────────────────
            ProductHeader(
                onOpenDiagnostics = onOpenDiagnostics,
                onOpenConnect = onOpenConnect,
                onOpenLanguagePacks = onOpenLanguagePacks,
            )

            Spacer(Modifier.height(Spacing.sm))

            // ── System Status Bar ───────────────────────────────────
            SystemStatusBar(
                connectionState = state.connectionState,
                secureState = state.secureState,
                activeLanguage = state.activeLanguage?.displayName,
            )

            androidx.compose.animation.AnimatedVisibility(visible = state.connectionState == com.itantra.core.transport.ConnectionState.DISCONNECTED || state.connectionState == com.itantra.core.transport.ConnectionState.ERROR) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.sm)
                        .background(CriticalSurface, ITantraShapes.card)
                        .padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = "Connection Lost", tint = CriticalRed)
                    Spacer(Modifier.width(Spacing.sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Connection Lost", style = MaterialTheme.typography.titleSmall, color = TextOnCritical)
                        Text("Peer device is unreachable.", style = MaterialTheme.typography.bodySmall, color = TextOnCritical)
                    }
                    TextButton(onClick = onOpenConnect) {
                        Text("RECONNECT", color = CriticalRed, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(Spacing.sm))

            // ── Mode Selector ───────────────────────────────────────
            ModeSelector(
                selectedMode = state.mode,
                onModeSelected = viewModel::selectMode,
            )

            Spacer(Modifier.height(Spacing.md))

            // ── Message List ────────────────────────────────────────
            MessageList(
                messages = state.messages,
                onAck = { msgId -> viewModel.sendHumanAck(msgId) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            Spacer(Modifier.height(Spacing.md))

            // ── Primary Action Area ─────────────────────────────────
            if (state.mode == TransceiverMode.SOS) {
                EmergencyQuickPanel(
                    onSendCode = { code -> viewModel.sendEmergencyCode(code) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                PttArea(
                    state = state,
                    onPressed = viewModel::onHoldToTalkPressed,
                    onReleased = viewModel::onHoldToTalkReleased,
                )
            }

            Spacer(Modifier.height(Spacing.md))

            // ── Metric Strip ────────────────────────────────────────
            MetricStrip(
                messages = state.messages,
                secureState = state.secureState,
            )
        }
    }

    // ── SAS Verification Dialog ─────────────────────────────────
    SasVerificationDialog(
        secureState = state.secureState,
        sasCode = state.sasCode,
        onConfirm = viewModel::confirmPeerVerification,
        onReject = viewModel::rejectPeerVerification,
    )
}

// ═════════════════════════════════════════════════════════════════
// COMPOSABLE BUILDING BLOCKS
// ═════════════════════════════════════════════════════════════════

@Composable
private fun ProductHeader(
    onOpenDiagnostics: () -> Unit,
    onOpenConnect: () -> Unit,
    onOpenLanguagePacks: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "iTANTRA",
                style = MaterialTheme.typography.headlineLarge,
                color = SignalGreen,
            )
            Text(
                text = "Offline Neural Transceiver",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SmallActionButton("LINK", onOpenConnect, "Open peer connection screen")
            SmallActionButton("LANG", onOpenLanguagePacks, "Open language packs")
            SmallActionButton("DIAG", onOpenDiagnostics, "Open diagnostics")
        }
    }
}

@Composable
private fun SmallActionButton(label: String, onClick: () -> Unit, accessibilityLabel: String) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        modifier = Modifier.semantics { contentDescription = accessibilityLabel },
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ModeSelector(
    selectedMode: TransceiverMode,
    onModeSelected: (TransceiverMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        ModeButton("PTT", TransceiverMode.PTT, selectedMode, Modifier.weight(1f), onModeSelected)
        ModeButton("CONTINUOUS", TransceiverMode.CONTINUOUS, selectedMode, Modifier.weight(1f), onModeSelected)
        ModeButton("SOS", TransceiverMode.SOS, selectedMode, Modifier.weight(1f), onModeSelected, isDanger = true)
    }
}

@Composable
private fun ModeButton(
    label: String,
    mode: TransceiverMode,
    selectedMode: TransceiverMode,
    modifier: Modifier = Modifier,
    onSelected: (TransceiverMode) -> Unit,
    isDanger: Boolean = false,
) {
    val isSelected = mode == selectedMode
    val accent = if (isDanger) CriticalRed else SignalGreen
    if (isSelected) {
        Button(
            onClick = { onSelected(mode) },
            modifier = modifier,
            shape = ITantraShapes.button,
            colors = ButtonDefaults.buttonColors(containerColor = accent),
        ) {
            Text(label, color = Color.Black, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        }
    } else {
        OutlinedButton(
            onClick = { onSelected(mode) },
            modifier = modifier,
            shape = ITantraShapes.button,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun PttArea(
    state: TransceiverUiState,
    onPressed: () -> Unit,
    onReleased: () -> Unit,
) {
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
        if (isGranted) onPressed()
    }

    // Derive PTT visual state from actual message pipeline
    val isRecording = state.messages.any { it.source == MessageSource.LOCAL && it.state == MessageState.RECORDING }
    val lastLocalState = state.messages.lastOrNull { it.source == MessageSource.LOCAL }?.state
    val pttState = if (!hasPermission && state.activeLanguage == null) {
        PttVisualState.DISABLED
    } else {
        derivePttState(isRecording, lastLocalState)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        PttButton(
            visualState = pttState,
            onPressed = {
                if (hasPermission) onPressed()
                else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            onReleased = onReleased,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── Message List ────────────────────────────────────────────────

@Composable
private fun MessageList(
    messages: List<TransceiverMessage>,
    onAck: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (messages.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "No messages yet.\nHold to talk to send your first message.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextDisabled,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            reverseLayout = true,
        ) {
            items(messages.reversed()) { msg ->
                MessageItem(msg = msg, onAck = onAck)
            }
        }
    }
}

@Composable
private fun MessageItem(msg: TransceiverMessage, onAck: (Long) -> Unit) {
    val isLocal = msg.source == MessageSource.LOCAL
    val isCritical = msg.priority == MessagePriority.CRITICAL
    val alignment = if (isLocal) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
    ) {
        if (isCritical && !isLocal) {
            // Use EmergencyBanner for incoming critical messages
            EmergencyBanner(
                text = msg.text,
                stateLabel = messageStateLabel(msg.state),
                isAcknowledged = msg.state == MessageState.ACKNOWLEDGED,
                onAcknowledge = { onAck(msg.messageId) },
            )
        } else {
            // Normal message bubble
            val bubbleColor = if (isLocal) SurfaceDarkElevated else RemoteBubble
            Box(
                modifier = Modifier
                    .background(bubbleColor, ITantraShapes.card)
                    .padding(Spacing.md),
            ) {
                Column {
                    // Source label
                    Text(
                        text = if (isLocal) "YOU" else "PEER",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isLocal) SignalGreenDim else TextSecondary,
                    )
                    Spacer(Modifier.height(Spacing.xs))

                    // Message text
                    Text(
                        text = msg.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                    )
                    Spacer(Modifier.height(Spacing.xs))

                    // State label
                    Text(
                        text = messageStateLabel(msg.state),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (msg.state == MessageState.ERROR) CriticalRed else SignalGreenDim,
                    )
                }
            }
        }

        // Per-message metrics
        val showMetrics = msg.state in listOf(
            MessageState.REMOTE_PLAYBACK_CONFIRMED, MessageState.SENT,
            MessageState.DELIVERED, MessageState.ACKNOWLEDGED
        )
        if (showMetrics) {
            Spacer(Modifier.height(2.dp))
            val metrics = buildList {
                if (msg.sttLatencyMillis > 0) add("STT: ${msg.sttLatencyMillis}ms")
                if (msg.packetBytes > 0) add("Pkt: ${msg.packetBytes}B")
                if (msg.rttMillis > 0) add("RTT: ${msg.rttMillis}ms")
                if (msg.peerTtfaMillis > 0) add("TTFA: ${msg.peerTtfaMillis}ms")
                if (msg.estimatedE2eMillis > 0) add("E2E: ${msg.estimatedE2eMillis}ms")
            }
            if (metrics.isNotEmpty()) {
                Text(
                    text = metrics.joinToString(" │ "),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = Spacing.xs),
                )
            }
        }
    }
}

private fun messageStateLabel(state: MessageState): String = when (state) {
    MessageState.IDLE -> "Ready"
    MessageState.RECORDING -> "Recording…"
    MessageState.STT_PROCESSING -> "Processing STT…"
    MessageState.STT_COMPLETE -> "Recognized"
    MessageState.PACKET_ENCODING -> "Encoding…"
    MessageState.TRANSMITTING -> "Transmitting…"
    MessageState.SENT -> "Sent"
    MessageState.DELIVERED -> "Delivered"
    MessageState.REMOTE_TTS_READY -> "Peer Ready"
    MessageState.REMOTE_PLAYING -> "Peer Speaking…"
    MessageState.REMOTE_PLAYBACK_CONFIRMED -> "Played on Peer"
    MessageState.WAITING_ACK -> "Awaiting ACK…"
    MessageState.ACKNOWLEDGED -> "Acknowledged"
    MessageState.ERROR -> "Failed"
}

// ── Metric Strip ────────────────────────────────────────────────

@Composable
private fun MetricStrip(
    messages: List<TransceiverMessage>,
    secureState: SecureSessionState,
) {
    val lastMsg = messages.lastOrNull {
        it.state in listOf(
            MessageState.REMOTE_PLAYBACK_CONFIRMED,
            MessageState.SENT,
            MessageState.DELIVERED,
            MessageState.ACKNOWLEDGED,
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDarkElevated, ITantraShapes.card)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MetricItem("STT", lastMsg?.sttLatencyMillis?.let { if (it > 0) "${it}ms" else "N/A" } ?: "N/A")
        MetricItem("RTT", lastMsg?.rttMillis?.let { if (it > 0) "${it}ms" else "N/A" } ?: "N/A")
        MetricItem("E2E", lastMsg?.estimatedE2eMillis?.let { if (it > 0) "${it}ms" else "N/A" } ?: "N/A")
        MetricItem(
            "SECURE",
            if (secureState == SecureSessionState.SECURE_VERIFIED) "ON" else "OFF",
            valueColor = if (secureState == SecureSessionState.SECURE_VERIFIED) SecureBlue else TextSecondary,
        )
    }
}

// ── Emergency Quick Panel ───────────────────────────────────────

@Composable
private fun EmergencyQuickPanel(
    onSendCode: (EmergencyCode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmCode by remember { mutableStateOf<EmergencyCode?>(null) }
    val codes = EmergencyCode.entries

    Column(
        modifier = modifier
            .background(SurfaceDarkElevated, ITantraShapes.card)
            .padding(Spacing.lg),
    ) {
        Text(
            text = "⚠ EMERGENCY QUICK CODES",
            style = MaterialTheme.typography.labelMedium,
            color = CriticalRed,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = Spacing.md),
        )

        val chunked = codes.chunked(2)
        chunked.forEach { rowCodes ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                rowCodes.forEach { code ->
                    OutlinedButton(
                        onClick = { confirmCode = code },
                        modifier = Modifier.weight(1f),
                        shape = ITantraShapes.button,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    ) {
                        Text(
                            code.name.replace("_", " "),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                if (rowCodes.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(Spacing.sm))
        }
    }

    if (confirmCode != null) {
        AlertDialog(
            onDismissRequest = { confirmCode = null },
            title = { Text("Send Critical Alert", color = CriticalRed) },
            text = { Text("Are you sure you want to send the CRITICAL alert for:\n\n${confirmCode?.name}?") },
            confirmButton = {
                val haptic = LocalHapticFeedback.current
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        confirmCode?.let { onSendCode(it) }
                        confirmCode = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CriticalRed),
                ) {
                    Text("SEND", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmCode = null }) { Text("CANCEL") }
            },
            containerColor = SurfaceDarkElevated,
        )
    }
}

// ── SAS Verification Dialog ─────────────────────────────────────

@Composable
private fun SasVerificationDialog(
    secureState: SecureSessionState,
    sasCode: String?,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
) {
    if (secureState != SecureSessionState.WAITING_USER_VERIFICATION || sasCode == null) return

    AlertDialog(
        onDismissRequest = onReject,
        title = {
            Text("VERIFY DEVICE", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Make sure this code appears on both phones.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Spacing.xl))
                Text(
                    text = sasCode,
                    style = MaterialTheme.typography.displayMedium,
                    color = SecureBlue,
                    modifier = Modifier.semantics { contentDescription = "Verification code: $sasCode" },
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("CODES MATCH", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onReject) {
                Text("MISMATCH", color = CriticalRed)
            }
        },
        containerColor = SurfaceDarkElevated,
    )
}
