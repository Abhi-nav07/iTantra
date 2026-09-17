package com.itantra.feature.languages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itantra.app.ui.components.StatusChip
import com.itantra.app.ui.theme.*
import com.itantra.core.util.toHumanReadableBytes
import com.itantra.domain.model.LanguagePackAvailability
import com.itantra.domain.model.LanguagePackSummary

@Composable
fun LanguagePacksScreen(
    viewModel: LanguagePacksViewModel,
    onBack: () -> Unit,
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
                Column {
                    Text("LANGUAGE PACKS", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Download once. Use offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(state.packs, key = { it.language.code }) { summary ->
                    LanguagePackRow(
                        summary = summary,
                        onClick = {
                            if (summary.isDownloaded) {
                                viewModel.activateLanguage(summary.language.code)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguagePackRow(summary: LanguagePackSummary, onClick: () -> Unit) {
    val isActive = summary.availability == LanguagePackAvailability.ACTIVE
    val isInstalled = summary.isDownloaded

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isActive) SurfaceCard else SurfaceDarkElevated,
                ITantraShapes.card,
            )
            .clickable(enabled = isInstalled, onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = 14.dp)
            .semantics {
                contentDescription = "${summary.language.displayName}. " +
                    "${if (isActive) "Active" else if (isInstalled) "Installed" else "Not installed"}."
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = summary.language.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                )
                if (isActive) {
                    StatusChip(label = "ACTIVE", color = SignalGreen)
                }
            }

            // Native name
            Text(
                text = summary.language.nativeDisplayName,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )

            // Availability details row
            Spacer(Modifier.height(Spacing.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                // STT availability
                val isSttInstalled = summary.isSttDownloaded
                val sttLabel = if (isSttInstalled) "STT ✓" else "STT ✗"
                val sttColor = if (isSttInstalled) SignalGreenDim else TextDisabled
                Text(sttLabel, style = MaterialTheme.typography.labelSmall, color = sttColor)

                // TTS availability
                val isTtsInstalled = summary.isTtsDownloaded
                val ttsLabel = if (isTtsInstalled) "TTS ✓" else "TTS ✗"
                val ttsColor = if (isTtsInstalled) SignalGreenDim else TextDisabled
                Text(ttsLabel, style = MaterialTheme.typography.labelSmall, color = ttsColor)

                // Size
                val totalSize = (summary.sttSizeBytes ?: 0L) + (summary.ttsSizeBytes ?: 0L)
                if (totalSize > 0) {
                    Text(
                        text = totalSize.toHumanReadableBytes(),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
            }
        }

        // Status badge for non-active packs
        if (!isActive) {
            val (statusLabel, statusColor) = when (summary.availability) {
                LanguagePackAvailability.DOWNLOADED -> "Installed" to WarningAmber
                LanguagePackAvailability.AVAILABLE -> "Not Installed" to TextDisabled
                else -> "" to TextSecondary
            }
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelMedium,
                color = statusColor,
            )
        }
    }
}
