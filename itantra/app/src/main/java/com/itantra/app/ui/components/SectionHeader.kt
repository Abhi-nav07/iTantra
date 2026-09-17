package com.itantra.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itantra.app.ui.theme.OutlineDim
import com.itantra.app.ui.theme.TextSecondary

/**
 * Section header with optional divider for diagnostics and settings screens.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = if (showDivider) 6.dp else 0.dp),
        )
        if (showDivider) {
            HorizontalDivider(color = OutlineDim)
        }
    }
}
