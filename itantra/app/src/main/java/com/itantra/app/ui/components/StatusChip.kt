package com.itantra.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itantra.app.ui.theme.*

/**
 * Compact, colored status indicator chip.
 * Used throughout the app for operational state labels like
 * "SECURE", "OFFLINE READY", "CONNECTED", etc.
 */
@Composable
fun StatusChip(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    backgroundColor: Color = color.copy(alpha = 0.15f),
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = modifier
            .background(backgroundColor, ITantraShapes.chip)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
