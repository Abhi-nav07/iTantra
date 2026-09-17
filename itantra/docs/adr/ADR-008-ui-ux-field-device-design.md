# ADR-008: Field-Device UI/UX Design

## Status
Accepted

## Date
2026-09-17

## Context
iTantra is a competitive submission for SIH 2026 Problem Statement 173: an offline, multilingual, peer-to-peer neural voice transceiver for disaster/field communication. The UI must communicate complex engineering (STT, TTS, Bluetooth transport, AES-GCM encryption, SOS) to both end-users and hackathon judges within seconds.

## Decision

### Dark, High-Contrast, Industrial Design
- Near-black backgrounds (`#0B0F0C`) with elevated surfaces.
- Designed for field use: outdoor readability, low-light environments, minimal battery drain on OLED.
- Not following system light/dark — iTantra is always dark because it is an instrument.

### PTT as Primary Control
- The Push-to-Talk button is the visual center of the product.
- Users should be able to operate the app with one hand in the lower half of the screen.
- All other controls (navigation, settings, diagnostics) are secondary.

### Operational Status Dominates UI
- The system status bar (Peer, Security, Language) is always visible on the main screen.
- The user never needs to navigate away to answer: "Am I connected? Am I secure? What language?"

### Diagnostics Are Separated
- Main screen: 3–4 key metrics (STT, RTT, E2E, Secure status).
- Full engineering detail (AES-256-GCM parameters, device info, link trace) lives in a dedicated Diagnostics screen.
- This separation keeps the operational UI clean while exposing engineering depth for judges.

### Minimal Animations
- Only state-communicating animations: PTT pulse while listening, scale on press.
- No decorative motion, particles, or continuous background animation.
- Performance on low-end Android devices (minSdk 26) is prioritized.

### Semantic Color System
- Red = Critical/Emergency only. Never decorative.
- Green = Operational/Connected/Verified.
- Amber = Transitional/Warning.
- Blue = Secure/Verified session.
- Colors always paired with text labels for accessibility.

### Accessibility
- Content descriptions on all interactive elements.
- Minimum 48dp touch targets for PTT, SOS, ACK.
- No color-only information — every status has a text label.
- SansSerif for body text ensures proper Indic script rendering.

## Alternatives Considered

### Light theme
Rejected: iTantra is a field instrument, not a consumer app. Consistent dark theme is a product identity choice.

### Bottom navigation
Rejected for 04E-A: The transceiver screen is the primary experience. Header action buttons (LINK, LANG, DIAG) keep secondary navigation compact without stealing vertical space from the PTT button.

### Chat-style UI
Rejected: iTantra is a transceiver, not a messenger. Message history is compact, not a scrolling chat thread. The focus is on the current message and operational state.

### Complex animation library (Lottie)
Rejected: Performance cost and APK size increase not justified. Standard Compose animation APIs are sufficient for the required state feedback.

## Consequences
- All screens must use design-system tokens (Color.kt, Type.kt, Spacing.kt, Shape.kt).
- No raw hex values in composables.
- New components must be added to `ui/components/` and documented in `UI_UX_SYSTEM.md`.
- Visual changes must be tested at multiple screen densities.
