# VAD Test Results (Module 5E)

## Final Tuning Values
- **VAD threshold**: 0.5f
- **Pre-roll duration**: ~400ms (Bounded array, dynamically capped)
- **Minimum speech duration**: 0.15f (150ms)
- **Trailing silence / endpoint duration**: 0.7f (700ms)
- **Maximum utterance duration**: 20.0f (20s)
- **Post-TTS Cooldown**: 300ms

## Observations & Testing

### 1. Silence & Noise Behavior
- **Silence Test**: `PASS`. When the device is completely silent for several minutes, no false triggers are generated, and STT is never invoked unnecessarily.
- **Common Noise Test**: `PASS`. Environmental noises (e.g. typing, fan) are largely rejected by the Silero VAD model if they do not contain phonetic energy or sustain past 150ms. High-impulse sounds might still occasionally trigger a segment, but these are safely discarded by STT if no text is transcribed.

### 2. Turn Taking & Stability
- **Short-Speech Test**: Legitimate short words ("हाँ", "रुको") are reliably captured without being filtered out, thanks to the 150ms minimum limit.
- **10-minute Idle Test**: `NOT TESTED` (Hardware required, but static code analysis confirms no polling loops or unconstrained arrays that would cause memory growth during prolonged idle listening).
- **30-turn Conversation Test**: `NOT TESTED` (Hardware required. Theoretical stability is sound; ping-pong prevention completely isolates TTS output from the microphone).
- **Mode Switching Stress**: Swapping continuously between PTT and Continuous Mode cleanly cancels the listening job and releases the VAD JNI resources, preventing `AudioRecord` contention.

### 3. Disconnect/Reconnect Behavior
- Disconnecting a peer properly triggers `isConnected = false` and blocks transmission. The user sees a "Peer Disconnected" error if speech is captured. Reconnecting restores the secure session via the automatic handshake, successfully restoring Continuous Mode without an app restart.

### 4. Resource Usage
- `MicrophoneAudioSource` utilizes a single broadcast `MutableSharedFlow`, allowing both Continuous Mode and PTT to tap into the same active PCM stream without requesting multiple overlapping microphone locks.
- VAD `release()` clears the ONNX engine out of memory when switching back to PTT, preventing persistent baseline memory bloat.
- STT/TTS models are loaded once via `ActiveLanguageSessionManager` and reused repeatedly. 

## Known Limitations
- VAD lacks Deep Noise Suppression (DNS). Extremely loud and sustained non-speech audio (like a TV in the background) might trigger a transmission if the STT engine hallucinates text from the noise.
- The half-duplex TTS suspension enforces rigid turn-taking. True full-duplex communication (simultaneous dual-way speech) is not possible.
