package com.itantra.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.itantra.app.ui.theme.*
import com.itantra.domain.model.MessageState

/**
 * Visual states for the PTT button, derived from the current message pipeline state.
 */
enum class PttVisualState {
    READY,
    LISTENING,
    PROCESSING,
    SENDING,
    DELIVERED,
    ERROR,
    DISABLED,
    CRITICAL_READY
}

/**
 * The hero PTT (Push-To-Talk) button.
 * Large, centered, unambiguous. Visual state reflects the actual pipeline state.
 */
@Composable
fun PttButton(
    visualState: PttVisualState,
    onPressed: () -> Unit,
    onReleased: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isActive = visualState == PttVisualState.LISTENING
    val haptic = LocalHapticFeedback.current

    // Subtle scale animation on press
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.05f else 1.0f,
        animationSpec = tween(durationMillis = 150),
        label = "ptt_scale",
    )

    // Pulse animation while listening
    val infiniteTransition = rememberInfiniteTransition(label = "ptt_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )

    val (ringColor, fillColor, labelText, labelColor) = remember(visualState) {
        when (visualState) {
            PttVisualState.READY -> PttStyle(SignalGreenDim, Color.Transparent, "HOLD TO TALK", TextSecondary)
            PttVisualState.LISTENING -> PttStyle(SignalGreen, SignalGreenDim, "LISTENING…", SignalGreen)
            PttVisualState.PROCESSING -> PttStyle(WarningAmber, WarningAmberDim.copy(alpha = 0.2f), "PROCESSING…", WarningAmber)
            PttVisualState.SENDING -> PttStyle(SignalGreen, SignalGreenDim.copy(alpha = 0.2f), "SENDING…", SignalGreen)
            PttVisualState.DELIVERED -> PttStyle(SignalGreen, Color.Transparent, "DELIVERED", SignalGreen)
            PttVisualState.ERROR -> PttStyle(CriticalRed, CriticalSurface, "ERROR", CriticalRed)
            PttVisualState.DISABLED -> PttStyle(TextDisabled, Color.Transparent, "UNAVAILABLE", TextDisabled)
            PttVisualState.CRITICAL_READY -> PttStyle(CriticalRed, CriticalRed.copy(alpha = 0.2f), "HOLD TO TALK\n(CRITICAL)", CriticalRed)
        }
    }

    val actualFill = if (isActive) fillColor.copy(alpha = pulseAlpha) else fillColor

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .aspectRatio(1f)
                .scale(scale)
                .border(3.dp, ringColor, CircleShape)
                .background(actualFill, CircleShape)
                .semantics { contentDescription = "Push to talk button. State: $labelText" }
                .pointerInput(visualState != PttVisualState.DISABLED) {
                    if (visualState != PttVisualState.DISABLED) {
                        detectTapGestures(
                            onPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onPressed()
                                tryAwaitRelease()
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onReleased()
                            },
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = labelText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = labelColor,
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}

private data class PttStyle(
    val ringColor: Color,
    val fillColor: Color,
    val label: String,
    val labelColor: Color,
)

/**
 * Derives the visual state from the current message pipeline.
 */
fun derivePttState(
    isRecording: Boolean,
    lastLocalMessageState: MessageState?,
): PttVisualState = when {
    isRecording -> PttVisualState.LISTENING
    lastLocalMessageState == MessageState.STT_PROCESSING -> PttVisualState.PROCESSING
    lastLocalMessageState == MessageState.PACKET_ENCODING -> PttVisualState.PROCESSING
    lastLocalMessageState == MessageState.TRANSMITTING -> PttVisualState.SENDING
    lastLocalMessageState == MessageState.SENT -> PttVisualState.DELIVERED
    lastLocalMessageState == MessageState.DELIVERED -> PttVisualState.DELIVERED
    lastLocalMessageState == MessageState.REMOTE_PLAYBACK_CONFIRMED -> PttVisualState.DELIVERED
    lastLocalMessageState == MessageState.ERROR -> PttVisualState.ERROR
    lastLocalMessageState == MessageState.WAITING_USER_CONFIRMATION -> PttVisualState.PROCESSING
    else -> PttVisualState.READY
}
