# iTantra UI/UX Design System

## Design Philosophy

iTantra is a **field communication instrument**, not a consumer messaging app. The UI communicates:

1. **Operational status** — is the system ready to communicate?
2. **Communication state** — what is happening right now?
3. **Engineering depth** — verifiable metrics for judges and developers.

The principle is: **Simple Operation + Deep Verifiable Engineering**.

## Color Semantics

| Token | Hex | Meaning |
|-------|-----|---------|
| `BackgroundBlack` | `#0B0F0C` | App background |
| `SurfaceDark` | `#141815` | Default surface |
| `SurfaceDarkElevated` | `#1C2119` | Elevated cards, dialogs |
| `SurfaceCard` | `#212721` | Active/highlighted cards |
| `SignalGreen` | `#39FF6A` | Ready / Connected / Verified / Primary |
| `SignalGreenDim` | `#1E8F3E` | Dim operational state |
| `OfflineReady` | `#2A6B40` | Offline provisioned indicator |
| `WarningAmber` | `#FFB020` | Warning / Loading / Degraded |
| `CriticalRed` | `#FF3B30` | Emergency / Failure / Critical ONLY |
| `CriticalSurface` | `#3D1515` | Background for critical cards |
| `SecureBlue` | `#5AC8FA` | Verified secure session |
| `TextPrimary` | `#EAF2EC` | Primary text |
| `TextSecondary` | `#8FA398` | Secondary labels |
| `TextDisabled` | `#4A554D` | Disabled / unavailable |

### Color Rules
- **Red is reserved for actual emergencies and failures.** Never decorative.
- **Green means operational.** Connected, verified, ready.
- **Amber means transitional.** Connecting, loading, warning.
- **Blue means secure.** Verified encrypted session.

## Typography

| Role | Font | Weight | Size | Usage |
|------|------|--------|------|-------|
| `headlineLarge` | SansSerif | Bold | 28sp | Product title "iTANTRA" |
| `headlineMedium` | SansSerif | SemiBold | 22sp | Screen titles |
| `titleLarge` | SansSerif | SemiBold | 20sp | Section headers |
| `titleMedium` | SansSerif | Medium | 16sp | Card titles, language names |
| `bodyLarge` | SansSerif | Normal | 16sp | Message text, body |
| `bodyMedium` | SansSerif | Normal | 14sp | Secondary body |
| `bodySmall` | SansSerif | Normal | 12sp | Captions |
| `labelLarge` | **Monospace** | Medium | 14sp | Metric values |
| `labelMedium` | **Monospace** | Normal | 12sp | Status chips |
| `labelSmall` | **Monospace** | Normal | 11sp | Smallest diagnostics |
| `displayMedium` | **Monospace** | Bold | 36sp | SAS verification code |

**Monospace is used only for technical data** (metrics, packet values, latency, codes). All body text and Indic scripts use SansSerif for proper glyph support.

## Spacing Tokens

| Token | Value |
|-------|-------|
| `xs` | 4dp |
| `sm` | 8dp |
| `md` | 12dp |
| `lg` | 16dp |
| `xl` | 24dp |
| `xxl` | 32dp |
| `xxxl` | 48dp |

## Shape Tokens

| Token | Shape |
|-------|-------|
| `card` | RoundedCorner 10dp |
| `chip` | RoundedCorner 6dp |
| `button` | RoundedCorner 8dp |
| `dialog` | RoundedCorner 16dp |

## Components

| Component | File | Purpose |
|-----------|------|---------|
| `StatusChip` | `ui/components/StatusChip.kt` | Compact colored status labels |
| `MetricItem` | `ui/components/MetricItem.kt` | Label + value metric display |
| `SectionHeader` | `ui/components/SectionHeader.kt` | Section divider with title |
| `SystemStatusBar` | `ui/components/SystemStatusBar.kt` | Peer/Security/Language status row |
| `PttButton` | `ui/components/PttButton.kt` | Push-to-talk button with 7 visual states |
| `EmergencyBanner` | `ui/components/EmergencyBanner.kt` | Critical alert card with ACKNOWLEDGE |

## Screen Hierarchy

```
Transceiver (Main)
├── Product Header
├── System Status Bar [Peer, Security, Language]
├── Mode Selector [PTT, CONTINUOUS, SOS]
├── Message List (or Empty State)
├── PTT Button / Emergency Quick Panel
└── Metric Strip [STT, RTT, E2E, Secure]

Connect
├── Connection Status
├── Listen / Disconnect Actions
├── Paired Devices List (or Empty State)
└── SAS Verification Dialog

Language Packs
├── 10 Language Rows
│   ├── Display Name + Native Name
│   ├── STT/TTS Availability
│   ├── Installed Size
│   └── Status Badge
└── "Download once, use offline" subtitle

Diagnostics
├── Device Info
├── Language
├── STT Metrics
├── TTS Metrics
├── Transport Metrics
├── Security (AES-256-GCM details)
├── System (Memory, Storage)
├── End-to-End
├── Link Trace (pipeline visualization)
└── Benchmark Button
```

## Navigation
Stack-based navigation with header action buttons. No bottom nav — the transceiver screen dominates as the primary experience.

## PTT Visual States

| State | Ring | Fill | Label |
|-------|------|------|-------|
| READY | GreenDim | Transparent | "HOLD TO TALK" |
| LISTENING | Green | Pulsing GreenDim | "LISTENING…" |
| PROCESSING | Amber | Dim Amber | "PROCESSING…" |
| SENDING | Green | Dim Green | "SENDING…" |
| DELIVERED | Green | Transparent | "DELIVERED" |
| ERROR | Red | Dark Red | "ERROR" |
| DISABLED | Gray | Transparent | "UNAVAILABLE" |

## Accessibility
- All interactive elements have `contentDescription` semantics
- Important status pairs color with text (never color-only)
- Touch targets meet minimum 48dp
- Indic scripts use SansSerif for proper rendering

## Performance Principles
- Standard Jetpack Compose APIs only
- No Lottie, no video backgrounds, no particle effects
- Minimal animations (PTT pulse, scale on press)
- No custom rendering
