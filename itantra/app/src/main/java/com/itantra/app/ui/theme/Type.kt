package com.itantra.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ═══════════════════════════════════════════════════════════════════
// iTANTRA DESIGN SYSTEM — TYPOGRAPHY
// Monospace for metrics/diagnostics (instrument-panel feel).
// SansSerif for content, labels, and Indic script rendering.
// ═══════════════════════════════════════════════════════════════════

private val MonoFamily = FontFamily.Monospace
private val DefaultFamily = FontFamily.SansSerif

val ITantraTypography = Typography(
    // Product title: "iTANTRA"
    headlineLarge = TextStyle(
        fontFamily = DefaultFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = 1.5.sp,
    ),
    // Screen titles
    headlineMedium = TextStyle(
        fontFamily = DefaultFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        letterSpacing = 0.5.sp,
    ),
    // PTT state label, section headers
    titleLarge = TextStyle(
        fontFamily = DefaultFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
    ),
    // Card titles, language names
    titleMedium = TextStyle(
        fontFamily = DefaultFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
    ),
    // Compact titles
    titleSmall = TextStyle(
        fontFamily = DefaultFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    ),
    // Message text, body content
    bodyLarge = TextStyle(
        fontFamily = DefaultFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    // Secondary body
    bodyMedium = TextStyle(
        fontFamily = DefaultFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    // Compact body / captions
    bodySmall = TextStyle(
        fontFamily = DefaultFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    // Metric values (monospace)
    labelLarge = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 0.5.sp,
    ),
    // Metric labels, status chips (monospace)
    labelMedium = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.3.sp,
    ),
    // Smallest metric/diagnostic text (monospace)
    labelSmall = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        letterSpacing = 0.2.sp,
    ),
    // SAS code, PTT button display text
    displayMedium = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        letterSpacing = 4.sp,
    ),
    // Large metric display
    displaySmall = TextStyle(
        fontFamily = DefaultFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
    ),
)
