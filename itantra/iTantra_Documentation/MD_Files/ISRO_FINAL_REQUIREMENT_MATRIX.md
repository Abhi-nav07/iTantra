# ISRO FINAL REQUIREMENT MATRIX

| Requirement | Implementation | Exact evidence | Evidence level | Status | Blocker |
|---|---|---|---|---|---|
| 10 languages | Architecture supports 10 languages | Code in `RealTranslationEngine`, `LanguageCode` | L1 BUILD/UNIT VERIFIED | PARTIAL | Missing ONNX models |
| Offline STT | `SherpaOnnxSpeechRecognizer` | Inference engine scaffolded via ONNX Runtime | L1 BUILD/UNIT VERIFIED | PARTIAL | Missing STT model files |
| Offline TTS | `SherpaOnnxSpeechSynthesizer` | Inference engine scaffolded via ONNX Runtime | L1 BUILD/UNIT VERIFIED | PARTIAL | Missing TTS model files |
| PTT | Press/release architecture in `TransceiverCoordinator` | PttButton UI + flow | L1 BUILD/UNIT VERIFIED | VERIFIED | None |
| Continuous mode | `ContinuousListenEngine` structure | Code exists but STT blocked | L1 BUILD/UNIT VERIFIED | SCAFFOLD | Missing STT models |
| Pause/VAD segmentation | Removed unused VAD wrapper | Relies on Sherpa native VAD which is missing | L0 ARCHITECTURE ONLY | MISSING | ONNX files |
| Bluetooth | Not available | Implementation missing/removed | L0 ARCHITECTURE ONLY | MISSING | No RFCOMM transport |
| Wi-Fi | Local peer transport | Transport coordinator skeleton | L1 BUILD/UNIT VERIFIED | SCAFFOLD | Complete network implementation |
| Low bitrate | Semantic binary transmission via `ItantraPacket` | `PacketEncoder`/`PacketDecoder` tests passing | L1 BUILD/UNIT VERIFIED | VERIFIED | None |
| Latency | Measured in metrics recorder | Code for TTFA/RTF exists | L1 BUILD/UNIT VERIFIED | PARTIAL | Missing model to test |
| RAM/storage | Dynamic unloading of language sessions | Code for tracking PSS in inference engine | L1 BUILD/UNIT VERIFIED | PARTIAL | Not tested on low-end device |
| WER | Inference logging | `SpeechRecognitionResult` | L1 BUILD/UNIT VERIFIED | NOT TESTED | Missing MT/STT |
| TTS intelligibility | VITS engine integration | `SpeechSynthesisResult` | L1 BUILD/UNIT VERIFIED | NOT TESTED | Missing TTS models |
| Emergency/alert | Independent semantic IDs sent natively | `EmergencyCode`, AES-GCM encryption | L1 BUILD/UNIT VERIFIED | VERIFIED | None |
| Security | ECDH, AES-256-GCM, HKDF, Nonce | `SecureSessionManager` implementation | L1 BUILD/UNIT VERIFIED | VERIFIED | None |
| Cross-language translation | IndicTrans2 ONNX scaffold | `RealTranslationEngine` | L1 BUILD/UNIT VERIFIED | SCAFFOLD | Blocked on specific ONNX signatures |
