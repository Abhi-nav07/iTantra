package com.itantra.app.ui.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════════
// iTANTRA DESIGN SYSTEM — COLOR TOKENS
// Field-communication aesthetic: near-black background, high-contrast
// signal green for primary actions/status, amber for warnings, red
// reserved solely for SOS. No decorative gradients or pastel accents —
// this is an instrument panel, not a consumer chat app.
// ═══════════════════════════════════════════════════════════════════

// ── Backgrounds ──────────────────────────────────────────────────
val BackgroundBlack      = Color(0xFF0B0F0C)
val SurfaceDark          = Color(0xFF141815)
val SurfaceDarkElevated  = Color(0xFF1C2119)
val SurfaceCard          = Color(0xFF212721)

// ── Operational Greens ───────────────────────────────────────────
val SignalGreen          = Color(0xFF39FF6A)
val SignalGreenDim       = Color(0xFF1E8F3E)
val OfflineReady         = Color(0xFF2A6B40) // Muted green for "offline provisioned"

// ── Warning / Loading ────────────────────────────────────────────
val WarningAmber         = Color(0xFFFFB020)
val WarningAmberDim      = Color(0xFF8A6B20)

// ── Critical / Emergency ─────────────────────────────────────────
val CriticalRed          = Color(0xFFFF3B30)
val CriticalRedDim       = Color(0xFFB22820)
val CriticalSurface      = Color(0xFF3D1515) // Dark red background for CRITICAL cards

// ── Secure Link ──────────────────────────────────────────────────
val SecureBlue           = Color(0xFF5AC8FA) // Verified secure session

// ── Text ─────────────────────────────────────────────────────────
val TextPrimary          = Color(0xFFEAF2EC)
val TextSecondary        = Color(0xFF8FA398)
val TextDisabled         = Color(0xFF4A554D)
val TextOnCritical       = Color(0xFFFFD0CC)

// ── Borders / Outlines ───────────────────────────────────────────
val OutlineDim           = Color(0xFF2A322C)
val OutlineFocused       = Color(0xFF3A4A3E)

// ── Remote Message Bubble ────────────────────────────────────────
val RemoteBubble         = Color(0xFF1E3A2F)
