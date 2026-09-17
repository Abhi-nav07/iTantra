# Continuous Mode (Module 5C)

## Overview
Continuous Mode enables hands-free voice communication. It listens to the microphone continuously, uses a local Voice Activity Detection (VAD) model to segment speech, and automatically transmits recognized semantic text to a secure peer.

## Path: SEGMENT_READY → STT
1. **Detection**: `ContinuousListenEngine` processes incoming microphone frames using Silero VAD.
2. **Segmentation**: A pre-roll buffer (~400ms) prevents clipping the first syllable. Trailing silence (~700ms) protects the last word.
3. **SEGMENT_READY**: A fully formed PCM float array is yielded to `TransceiverCoordinator`.
4. **Suspension**: VAD listening is immediately suspended to ensure exactly **one active STT job**.
5. **Recognition**: The PCM buffer is passed to the *existing* loaded STT engine (`SpeechRecognizerEngine.feed(audio)`).
6. **Validation**: If STT yields a blank or empty string, the pipeline aborts, no message is created, and VAD resumes listening.

## Model Reuse
The STT model instance is strictly shared with Push-To-Talk (PTT). Continuous mode queries `sessionManager.currentSttEngine` which remains hot in memory. The model is NOT reloaded per utterance.

## Secure Auto-Send
If recognition yields valid text:
1. **Verification**: Checks if `isConnected` and `secureSessionManager` state is `SECURE_VERIFIED`.
2. **Failure fallback**: If unverified or disconnected, the text is kept in the UI with an `ERROR` state ("Secure Link Required" or "Peer Disconnected"). **No plaintext fallback exists.**
3. **Transmission**: The recognized text is encoded via the standard `PacketEncoder` (Priority: `NORMAL`), encrypted with AES-256-GCM, and sent over the active RFCOMM Bluetooth socket.

## Priority Rule
All voice detected by Continuous Mode is rigidly marked as `NORMAL` priority. It does not auto-generate `CRITICAL` or `SOS` packets.

## Receiver TTS & Echo Prevention
The remote device (`Phone B`) receives the AES-GCM packet, verifies the authentication tag and replay counters, decrypts the semantic text, displays it in the UI, and automatically passes it to the pre-loaded offline TTS engine (e.g. `SherpaOnnxSpeechSynthesizer`). If TTS fails (e.g. engine crashed), the decrypted semantic text remains visible with an error state.

**Half-Duplex Turn Taking & Ping-Pong Prevention:**
To prevent infinite echo loops (where Phone B's microphone hears its own TTS and sends it back to Phone A):
1. **Suspension**: Before local TTS begins, Continuous VAD is explicitly suspended (`suspendListening()`).
2. **Buffer Flush**: Any stale pre-roll audio or partially captured utterances are immediately discarded.
3. **Cooldown**: After TTS finishes playing, a 300ms cooldown is enforced.
4. **Queue Integrity**: If multiple messages arrive back-to-back, or if a critical alert overrides, the cooldown is repeatedly canceled and extended until the playback queue is completely idle.
5. **Resumption**: Only after the cooldown completes does the VAD reset and resume listening, cleanly handing the turn back to the human speaker.

## Known Limitations
- Background noise exceeding 150ms may trigger a segment. If STT mistakenly transcribes the noise (e.g., as "ah"), it will be transmitted. (Lack of Deep Noise Suppression).
- **Half-duplex only**: There is no acoustic echo cancellation (AEC) running during playback. True full-duplex communication (interrupting while the peer is speaking) is not supported by this architecture.

## Final Hardened Configuration (Module 5E)
- **VAD threshold**: 0.5f
- **Pre-roll duration**: ~400ms (dynamic)
- **Minimum speech duration**: 0.15f (150ms)
- **Trailing endpoint silence**: 0.7f (700ms)
- **Max utterance**: 20.0f (20s)
- **Post-TTS Cooldown**: 300ms
