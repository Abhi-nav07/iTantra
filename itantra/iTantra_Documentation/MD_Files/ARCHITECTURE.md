# iTantra Architecture

## Core Concepts
iTantra is an offline-first multilingual neural voice transceiver. The architecture is explicitly designed around **Modular Language Packs** rather than a single monolithic application containing all AI models.

### Transceiver Flow
1. **Audio Capture**: `MicrophoneAudioSource` utilizes `AudioRecord` for secure 16kHz PCM streams.
2. **Endpointing**: Voice Activity Detection (VAD) via `ContinuousListenEngine` (Sherpa-ONNX) identifies active speech chunks and automatically triggers processing.
3. **STT Inference**: `SherpaOnnxSpeechRecognizer` digests the PCM stream strictly offline and issues transcripts using the currently active Language Pack model.
4. **Networking**: Transcripts and packets are relayed securely via `BluetoothTransportEngine` and `SecureSessionManager` (AES-256-GCM / ECDH).
5. **TTS Inference**: Received packets are synthesized natively via the local offline TTS model (`KokoroTtsEngine`) and piped to the system speaker.

## 10-Language Architecture
The system centrally represents 10 Indian languages.
- All language metadata is strictly defined in a single `LanguageCatalog` to avoid scattered string constants.
- The lifecycle maintains that exactly **one** language pack can be actively loaded into RAM at any given moment.

### Language Pack Provisioning
A state machine handles the lifecycle of packs:
`AVAILABLE` -> `DOWNLOADING` -> `VERIFYING` -> `INSTALLED` -> `ACTIVE`

Assets strictly live in application private storage (`context.filesDir/language_packs`) and never burden the base APK payload, allowing the application to scale arbitrarily with future dialects.
