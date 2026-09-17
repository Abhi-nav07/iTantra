package com.itantra.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.itantra.app.ui.theme.TextSecondary

/**
 * Compact metric display: label on top, value below.
 * Used in the diagnostic strip and diagnostics screen.
 *
 * Shows "N/A" in [TextSecondary] when value is null or zero.
 */
@Composable
fun MetricItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.White,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = if (value == "N/A") TextSecondary else valueColor,
        )
    }
}
