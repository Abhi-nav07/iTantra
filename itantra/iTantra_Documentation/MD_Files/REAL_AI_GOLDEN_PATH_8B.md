# Module 8B: Real AI Golden Path (Offline)

## Architecture Overview
The 8B Golden Path confirms the transition from model-loading scaffolds to genuine native inference. The codebase natively executes IndicTrans2 via CTranslate2 JNI for auto-regressive generation, Whisper Tiny via Sherpa-ONNX for STT, and MMS/VITS via Sherpa-ONNX for TTS. All 10 ISRO languages are provisioned.

## Execution Matrix

| Requirement | Status | Notes |
|---|---|---|
| **Same-Language MT Bypass** | VERIFIED | CTranslate2TranslationEngine.kt bypasses MT when sourceLang == targetLang. |
| **Cross-Language MT Logic** | VERIFIED | CTranslate2TranslationEngine.kt uses native CTranslate2 JNI with SentencePiece tokenization and FLORES language tags. |
| **Indic-Indic Pivot** | VERIFIED | CTranslate2TranslationEngine.kt chains indic-en then en-indic for cross-Indic translation. |
| **Structured Failure** | VERIFIED | Missing model files trigger MODEL_NOT_INSTALLED, preventing packetization of fake/empty strings. |
| **No Sentinel Strings** | VERIFIED | Codebase audited to remove any remaining identity translations or faux successful outputs. |
| **Host MT Inference** | VERIFIED | tools/host_mt_test.py confirmed real CTranslate2 inference on provisioned models. |

## Physical Device & Emulator Limitations
The automated validation environment lacks physical Android devices. Therefore:

- **Hardware Tests:** NOT_TESTED (requires physical device).
- **Microphone E2E:** NOT_TESTED (requires physical device microphone).
- **Model Inference on Device:** SOURCE VERIFIED, DEVICE NOT_TESTED.

## Reproducible Provisioning Strategy
Exact model provisioning is automated via `tools/provision_models.py` with SHA256 verification.

This script fetches the verified models:
- **STT:** Whisper Tiny int8 via Sherpa-ONNX (shared multilingual, all 10 languages)
- **MT:** IndicTrans2 200M Distilled CT2 (indic-en + en-indic)
- **TTS:** MMS/VITS ONNX (per-language, all 10 languages)
- **VAD:** Silero VAD ONNX
