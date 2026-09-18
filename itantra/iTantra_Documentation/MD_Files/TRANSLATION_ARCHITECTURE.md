# Module 7A: Offline Cross-Language Translation Architecture

## Overview
This document defines the architectural boundary for the optional offline translation subsystem in iTantra. The primary constraint is that the translation engine must be strictly isolated from the core Speech-to-Text (STT), secure transport, and Text-to-Speech (TTS) pipeline.

The currently working single-language transceiver (Module 6) is the authoritative foundation. Translation acts as an optional interceptor layer.

## Architectural Principles

1. **Non-Destructive Routing**: The original transcript must always be preserved. If translation fails, is unsupported, or the model is unavailable, the system must gracefully fall back to same-language communication.
2. **Receiver-Side Translation Preference**: To support future multi-node topologies and reduce sender processing overhead, translation should occur on the receiving device.
    - The Sender runs STT and transmits the original text securely.
    - The Receiver decrypts the text, notes the `languageCode` of the packet, compares it against its active TTS language, and requests translation if they differ.
3. **Decoupled Engines**: The `TranslationEngine` interface abstracts the underlying model (e.g., IndicTrans2) from the `TransceiverCoordinator`.
4. **Compile-Safe Boundary**: The router (`TranslationRouter`) handles the async complexity and error states, ensuring the main transceiver loop never blocks indefinitely on heavy NMT (Neural Machine Translation) tasks.

## Data Flow (Receiver-Side)

```
[Peer Device]
      |
 (Bluetooth Secure Payload: "मेरा नाम...")
      |
[TransportEngine] -> [SecureSessionManager (Decrypt)]
      |
      v
[TransceiverCoordinator] -> PacketType.TEXT (LanguageCode.HINDI)
      |
      v
(Does Packet Language == Active Local Language?)
   |-- NO -> [TranslationRouter] -> [TranslationEngine]
   |             |-- SUCCESS -> Returns "My name is..."
   |             |-- FAIL -> Returns original "मेरा नाम..."
   |
   |-- YES -> Bypass translation
      |
      v
[Active TTS Engine] -> Audio Output
```

## Security Implications
Translation occurs strictly at the Application Layer **after** decryption. The Bluetooth transport protocol, AEAD encryption (AES-256-GCM), and HKDF derivation remain completely unaffected. The `ItantraPacket` already contains the source `languageCode`.

## Error Handling
If `TranslationEngine` throws an exception, runs out of memory, or encounters a vocabulary error, the `TranslationRouter` logs the failure and immediately returns the original text to ensure communication continuity.
