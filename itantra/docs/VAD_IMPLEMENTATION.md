# VAD Implementation (Module 5B)

## Starting Values
- VAD threshold: 0.5f
- Pre-roll duration: ~400ms (Bounded array, ~6400 samples)
- Minimum speech duration: 0.25f (250ms)
- Trailing silence / endpoint duration: 0.7f (700ms)
- Maximum utterance duration: 20.0f (20s)
- Audio sample rate: 16000

## Final Values
- VAD threshold: 0.5f
- Pre-roll duration: ~400ms (Bounded array, ~6400 samples)
- Minimum speech duration: 0.15f (150ms)
- Trailing silence / endpoint duration: 0.7f (700ms)
- Maximum utterance duration: 20.0f (20s)
- Audio sample rate: 16000

## Reason for each changed value
- **Minimum speech duration**: Reduced from 0.25s to 0.15s because 250ms might filter out legitimate short words like "हाँ" (yes) or "रुको" (stop). The manual validation layer also enforces a 150ms minimum limit.

## First-word behavior
Protected by a rolling pre-roll buffer of ~400ms. When speech is detected, the `CAPTURING` phase prepends these samples to the final utterance, ensuring the first syllable is not lost due to VAD detection latency.

## Last-word behavior
Protected by the 0.7f (700ms) endpoint silence duration. The engine keeps capturing audio until a sustained silence is observed, meaning the trailing syllables are retained instead of being chopped off the moment silence begins.

## Natural-pause behavior
The 700ms trailing silence is balanced to tolerate natural short pauses within a sentence. A speaker can pause for half a second without the VAD splitting the utterance into two segments.

## Short-speech behavior
Short authentic speech (e.g., "हाँ", "आओ") is captured accurately by lowering the `minSpeechDuration` parameter to 150ms. Additionally, the engine checks `totalSize > 0 && durationInSeconds >= 0.15f` before emitting a segment to enforce validity and reject microscopic transients.

## Known limitations
- Background transient noises longer than 150ms might still trigger a segment.
- Module 5C integrated STT and Network Transmission natively. VAD is suspended during local TTS playback to prevent echo.
