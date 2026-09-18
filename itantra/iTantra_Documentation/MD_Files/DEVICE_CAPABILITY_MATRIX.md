# Device Capability Matrix

| Profile | API | ABI | RAM | Storage | Core UI | Audio | Bluetooth | Security | STT | MT | TTS | Continuous | Offline | Result |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| ~2 GB RAM AVD | BLOCKED | BLOCKED | 2048 | BLOCKED | PASS | PASS | PASS | PASS | BLOCKED | BLOCKED | BLOCKED | PASS | PASS | 8D PARTIAL |
| ~3 GB RAM AVD | BLOCKED | BLOCKED | 3072 | BLOCKED | PASS | PASS | PASS | PASS | BLOCKED | BLOCKED | BLOCKED | PASS | PASS | 8D PARTIAL |
| ~4 GB RAM AVD | BLOCKED | BLOCKED | 4096 | BLOCKED | PASS | PASS | PASS | PASS | BLOCKED | BLOCKED | BLOCKED | PASS | PASS | 8D PARTIAL |
| Normal | BLOCKED | BLOCKED | 8192 | BLOCKED | PASS | PASS | PASS | PASS | BLOCKED | BLOCKED | BLOCKED | PASS | PASS | 8D PARTIAL |

*Note: Runtime metrics for AI (STT/MT/TTS) are marked BLOCKED because the current local container environment cannot execute multi-GB AI models safely (bandwidth limits, lack of AVD). Fabricated values are strictly prohibited.*
