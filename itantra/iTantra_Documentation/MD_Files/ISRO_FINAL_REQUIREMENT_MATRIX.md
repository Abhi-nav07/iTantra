# ISRO FINAL REQUIREMENT MATRIX

| Requirement | Implementation | Exact Evidence | Evidence Level | Status | Notes |
|---|---|---|---|---|---|
| 10 languages | All 10 provisioned (hi, en, bn, gu, mr, kn, ml, ta, te, or) | `CTranslate2TranslationEngine.kt`, `provision_models.py`, per-language manifests | L1 BUILD/UNIT VERIFIED | VERIFIED | Shared STT + per-lang TTS + shared MT |
| Offline STT | `SherpaOnnxSpeechRecognizer` with Whisper Tiny int8 | `tiny-encoder.int8.onnx`, `tiny-decoder.int8.onnx`, `tiny-tokens.txt` | L1 BUILD/UNIT VERIFIED | PROVISIONED | Shared multilingual model |
| Offline TTS | `SherpaOnnxSpeechSynthesizer` with MMS/VITS | Per-language `model.onnx`, `tokens.txt` | L1 BUILD/UNIT VERIFIED | PROVISIONED | 10 separate VITS models |
| Cross-language MT | `CTranslate2TranslationEngine` via native JNI | `itantra_mt_jni.cpp`, `model.bin`, SentencePiece vocab files | L1 BUILD/UNIT VERIFIED | PROVISIONED | IndicTrans2 200M Distilled, indic-en + en-indic |
| PTT | Press/release in `TransceiverCoordinator` | PttButton UI + flow | L1 BUILD/UNIT VERIFIED | VERIFIED | None |
| Continuous mode | `ContinuousListenEngine` + `OperationalForegroundService` | Foreground service keeps STT active in background | L1 BUILD/UNIT VERIFIED | VERIFIED | None |
| Pause/VAD segmentation | Silero VAD via Sherpa-ONNX native | `silero_vad.onnx` provisioned | L1 BUILD/UNIT VERIFIED | PROVISIONED | Native Sherpa VAD integration |
| Bluetooth | `BluetoothPeerTransport` RFCOMM | Point-to-point stream transport | SOURCE/UNIT VERIFIED | SOURCE/UNIT VERIFIED | NOT mesh — direct 1:1 RFCOMM. Physical: NOT_TESTED |
| Wi-Fi | `WifiPeerTransport` TCP | Local LAN peer transport | SOURCE/LOOPBACK VERIFIED | SOURCE/LOOPBACK VERIFIED | Physical: NOT_TESTED |
| Low bitrate | Semantic text via `ItantraPacket` | `PacketEncoder`/`PacketDecoder` tests passing | SOURCE_VERIFIED | SOURCE_VERIFIED | Typical semantic messages are designed to remain small; actual wire size is measured from encodedFrame.size. |
| Latency | `InferenceMetricsRecorder` + `LATENCY_MATRIX.md` | Code for TTFA/RTF + simulated loopback | L1 BUILD/UNIT VERIFIED | SIMULATED | Physical device NOT_TESTED |
| RAM/storage | `DeviceCapabilityDetector` + `ActiveLanguageSessionManager` | Dynamic unloading + RAM profiling | L1 BUILD/UNIT VERIFIED | VERIFIED | Physical PSS NOT_TESTED |
| WER | `WordErrorRateCalculator` + benchmark framework | Per-language benchmark JSON + test suite | L1 BUILD/UNIT VERIFIED | FRAMEWORK_READY | Physical WER NOT_TESTED |
| TTS intelligibility | TTS evaluation screen + `tts_sentences.json` | Evaluation UI built | L1 BUILD/UNIT VERIFIED | FRAMEWORK_READY | Human evaluation NOT_TESTED |
| Emergency/alert | `EmergencyPersistenceStore` + semantic IDs | `EmergencyAndOperationalHardeningTest` — 100/100 combos | L1 BUILD/UNIT VERIFIED | VERIFIED | Persists across restarts |
| Security | ECDH, AES-256-GCM, HKDF, Nonce, SAS | `SecureSessionManager` + automated tests | L1 BUILD/UNIT VERIFIED | VERIFIED | None |
| Physical 2-Device | NOT_TESTED | No hardware available during automated validation | NOT_TESTED | NOT_TESTED | Requires two Android phones |
| Hardware Metrics | NOT_TESTED | Benchmark harness built but no device | NOT_TESTED | NOT_TESTED | Requires physical device |
