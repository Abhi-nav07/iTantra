# ADR 007: SOS Priority and Preemption Mechanism

## Status
Accepted

## Context
iTantra is an offline rescue communication app. In disaster scenarios, a flood of standard traffic must not block critical life-saving alerts. We need a way to ensure that SOS messages (either semantic codes or critical free-speech) take precedence over normal text-to-speech rendering, which can take several seconds and block the audio channel. We also need to prevent malicious priority escalation (e.g., tampering with the priority byte).

## Decision
1. **Authenticated Priority**: We map `MessagePriority` to the `ItantraPacket.flags` byte. Since `flags` is included in the AAD block constructed by `PacketEncoder.extractAad()`, any in-flight modification by an adversary will be rejected by the AES-GCM cipher on the receiving end.
2. **Preemption Architecture**: `TransceiverCoordinator` uses a Mutex-protected priority queue. When a CRITICAL packet is received, the coordinator immediately cancels the ongoing TTS coroutine and calls `flushAndStop()` on the `SpeakerAudioSink`. The critical message is then synthesized with `USAGE_ALARM` attributes to bypass Do Not Disturb restrictions.
3. **Semantic Emergency Codes**: Instead of transmitting heavy text payloads for common emergencies, we introduced `EmergencyCode` (a 1-byte ID). This maximizes offline RFCOMM reliability in extremely degraded environments.
4. **Human Acknowledgement**: A `HUMAN_ACK` packet forces explicit user interaction, ensuring alerts are seen and mitigating "alarm fatigue". Unacknowledged alerts are repeated locally.

## Consequences
- **Positive**: Near-instant delivery of SOS messages, bypassing queue bottlenecks. Preemption ensures the audio channel is immediately yielded. Tamper-proof priority ensures trust.
- **Negative**: Preemption abruptly halts the TTS engine. The TTS engine (`SpeechSynthesizerEngine`) must gracefully handle `CancellationException` and rapid `init()` / `release()` cycles without crashing or leaking native memory.
