# iTantra

Offline, multilingual neural voice transceiver — SIH 2026, Problem Statement 173. 
See `iTantra_Documentation/MD_Files/ARCHITECTURE.md` for the full design, `iTantra_Documentation/MD_Files/MODEL_STRATEGY.md` for model research findings, and `iTantra_Documentation/MD_Files/adr/` for recorded engineering decisions.

**Status: FINAL PROTOTYPE PARTIAL (Module 10 complete)** 
- **STT (Sherpa-ONNX Whisper)**, **TTS (VITS Piper)**, **VAD (Silero)**, **Continuous Mode**, and **Secure Transport** (ECDH/AES-GCM via Bluetooth/Wi-Fi Direct) are **implemented** and integrated.
- **Machine Translation (IndicTrans2)** is currently **BLOCKED** natively on Android without a custom C++ KV-cache inference wrapper.

## Building

Requires Android Studio (or a standalone Android SDK) and JDK 17+.

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## Project layout

```
app/src/main/java/com/itantra/
  app/            entry point, navigation, theme, manual DI (AppGraph)
  domain/model/   pure data types (Language, LanguagePackManifest, Measurement, ...)
  domain/repository/  LanguagePackRepository contract
  core/inference/ SherpaOnnxSpeechRecognizer / KokoroTtsEngine / ContinuousListenEngine / VoiceActivityDetector
  core/transport/ BluetoothPeerTransport / WifiPeerTransport / TransportCoordinator
  core/crypto/    SecureSessionManager (ECDH/AES-GCM)
  core/storage/   LanguagePackStorage 
  core/metrics/   MetricsRecorder — real measurements only
  data/languagepack/  RealLanguagePackRepository 
  feature/transceiver/  main screen
  feature/languages/    language packs screen
  feature/diagnostics/  diagnostics screen
```
