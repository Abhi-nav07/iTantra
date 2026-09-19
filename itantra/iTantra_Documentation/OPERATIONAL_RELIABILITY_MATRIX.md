# iTantra Operational Reliability & Resilience Matrix

This matrix documents the operational hardening, emergency safety pathways, audio behaviors, background execution, and offline guarantees for iTantra Pass E.

> **Evidence Level Legend**:
> - **`UNIT_TESTED`**: Verified via deterministic JVM unit/loopback test harness.
> - **`SOURCE_READY`**: Implemented and statically validated in Android production source.
> - **`DEVICE_TESTED`**: Verified on physical Android hardware.
> - **`HUMAN_REVIEWED`**: Verified by fluent certified native speaker.
> - **`NOT_TESTED`**: Physical device or human verification not yet performed.

---

## 1. Operational Reliability Matrix

| Operational Dimension | Source Status | Unit Test | Device Test | Evidence Level | Limitations & Nuances |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Receiver-Language Emergency** | PASS | PASS | NOT_TESTED | `UNIT_TESTED` | Emergency codes resolve directly into receiver's active local language; sender packet language does not dictate local speech. |
| **MT Bypass Emergency** | PASS | PASS | NOT_TESTED | `UNIT_TESTED` | Emergency semantic codes strictly bypass dynamic MT to eliminate hallucination risks. |
| **Transport ACK Separation** | PASS | PASS | NOT_TESTED | `UNIT_TESTED` | Wire ACK confirms packet reception only; never clears human response requirement. |
| **HUMAN_ACK Integrity** | PASS | PASS | NOT_TESTED | `UNIT_TESTED` | Only explicit operator confirmation transmits `HUMAN_ACK` and clears unresolved alert status. |
| **Emergency State Persistence** | PASS | PASS | NOT_TESTED | `UNIT_TESTED` | Unresolved emergency states stored in durable storage (`emergency_records.json`), surviving activity/process recreation. |
| **Retry After Reconnect** | PASS | PASS | NOT_TESTED | `UNIT_TESTED` | Bounded retries (max 3-5) triggered on transport reconnection for unacknowledged critical SOS messages. |
| **Foreground Continuous Mode** | PASS | PASS_SOURCE | NOT_TESTED | `SOURCE_READY` | `OperationalForegroundService` runs with `FOREGROUND_SERVICE_MICROPHONE` and persistent notification. |
| **Screen-Off Continuous Mode** | PASS | PASS_SOURCE | NOT_TESTED | `SOURCE_READY` | Holds partial `WakeLock` in foreground service; physical OEM background limits remain subject to device battery optimization. |
| **Foreground Emergency Alert** | PASS | PASS_SOURCE | NOT_TESTED | `SOURCE_READY` | Unresolved SOS alerts trigger high-priority notification with DND bypass channel. |
| **Audio Focus Handling** | PASS | PASS | NOT_TESTED | `UNIT_TESTED` | Transient ducking for normal speech; exclusive transient gain requested for critical alarms. |
| **Audio Alarm Usage** | PASS | PASS | NOT_TESTED | `UNIT_TESTED` | Emergency alerts use `AudioAttributes.USAGE_ALARM` and sonification content type rather than standard media stream. |
| **Volume Handling** | PASS | PASS | NOT_TESTED | `SOURCE_READY` | Maximum-volume intent requested for alarms; previous system alarm volume saved and restored. OEM/DND policies apply. |
| **Acoustic Echo Cancellation** | PASS | PASS | NOT_TESTED | `UNIT_TESTED` | Probes `AcousticEchoCanceler.isAvailable()`; graceful fallback to half-duplex suppression when unsupported. |
| **TTS Playback Completion** | PASS | PASS | NOT_TESTED | `UNIT_TESTED` | `SpeakerAudioSink.flushAndStop` tracks `AudioTrack.playbackHeadPosition` to prevent end-of-sentence clipping. |
| **Offline Runtime Guarantee** | PASS | PASS | NOT_TESTED | `SOURCE_READY` | STT, MT, TTS, RFCOMM Bluetooth, and Local Wi-Fi TCP execute 100% air-gapped without internet access after provisioning. |

---

## 2. Audio & Safety Semantics Clarifications

1. **Application-Level Non-Interruptible**:
   - An incoming normal voice message will never dismiss, interrupt, or preempt an active safety-critical emergency alert.
   - The emergency alert remains prominently displayed and active until an explicit `HUMAN_ACK` is dispatched by the operator.
   - This guarantee is enforced at the application state level; it does not attempt to subvert Android OS emergency/call preemption.

2. **Volume Intent Policy**:
   - The app requests maximum volume intent for emergency alarms, restoring previous volume levels upon completion.
   - Absolute volume override depends on OEM firmware, Do Not Disturb (DND) bypass permissions, and Android device volume policies.

3. **Continuous Mode Half-Duplex Operation**:
   - Speech detection is temporarily paused during active local TTS synthesis and resumes 300 ms post-playback to eliminate acoustic echo feedback without relying strictly on hardware AEC.
