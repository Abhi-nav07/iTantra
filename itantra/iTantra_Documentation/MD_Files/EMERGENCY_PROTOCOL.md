# iTantra Emergency Protocol (v1.0)

## Overview
Phase 04D introduces an offline SOS protocol built into the semantic transceiver layer. The protocol ensures that critical emergency messages bypass normal queues, preempt ongoing audio, and guarantee delivery through human acknowledgement.

## Protocol Mechanics

1. **Priority Domain**:
   - Uses a 3-bit priority field mapped to `ItantraPacket` `flags` (0 = NORMAL, 1 = HIGH, 2 = CRITICAL).
   - `flags` is included in the AES-GCM Additional Authenticated Data (AAD) block (byte 10 of the `PacketEncoder` wire format). Any tampering with the priority bit will cause the AES-GCM MAC validation to fail.

2. **Emergency Codes**:
   - Semantic codes (e.g., `0x05 LANDSLIDE`) are serialized via the `EMERGENCY_CODE (10)` packet type.
   - The payload is a single byte indicating the code ID. This achieves extreme compression (30 bytes total wire size including headers and security overhead).
   - Upon receipt, the code is resolved locally to the recipient's language via `EmergencyPhraseResolver`.

3. **Preemption**:
   - The `TransceiverCoordinator` maintains a priority queue protected by a `Mutex`.
   - When a CRITICAL packet arrives, the coordinator cancels the active TTS job and calls `SpeakerAudioSink.flushAndStop()` to interrupt audio playback instantly.
   - The CRITICAL packet synthesizes with `USAGE_ALARM` audio focus.

4. **Reliability via Human Acknowledgement**:
   - Unacknowledged critical messages replay locally every 10 seconds up to 3 times to grab the user's attention.
   - A `HUMAN_ACK (11)` packet is sent when the user explicitly taps "ACKNOWLEDGE" on the emergency UI.
   - Replay protection (via monotonically increasing 64-bit counters) ensures an attacker cannot replay a `HUMAN_ACK`.

## Usage Guidelines
- SOS messages must only be sent deliberately (via the Emergency Quick Panel).
- All SOS states must persist across app lifecycles until acknowledged (to be implemented in future phase).
