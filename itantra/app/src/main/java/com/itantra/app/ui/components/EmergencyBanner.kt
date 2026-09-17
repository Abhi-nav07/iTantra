package com.itantra.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itantra.app.ui.theme.*

/**
 * High-contrast emergency banner for incoming CRITICAL messages.
 * Visually dominates to demand attention, with an explicit ACKNOWLEDGE button.
 */
@Composable
fun EmergencyBanner(
    text: String,
    stateLabel: String,
    isAcknowledged: Boolean,
    onAcknowledge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CriticalSurface, ITantraShapes.card)
            .padding(14.dp)
            .semantics { contentDescription = "Critical emergency alert: $text" },
    ) {
        // Header badge
        Text(
            text = "⚠ CRITICAL ALERT",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = CriticalRed,
        )

        Spacer(Modifier.height(6.dp))

        // Alert text (may be Indic script)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = TextOnCritical,
        )

        Spacer(Modifier.height(4.dp))

        // State label
        Text(
            text = stateLabel,
            style = MaterialTheme.typography.labelSmall,
            color = if (isAcknowledged) SignalGreen else WarningAmber,
        )

        // ACKNOWLEDGE button
        if (!isAcknowledged) {
            val haptic = LocalHapticFeedback.current
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onAcknowledge()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CriticalRed),
                modifier = Modifier.semantics { contentDescription = "Acknowledge emergency alert" },
            ) {
                Text("ACKNOWLEDGE", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
