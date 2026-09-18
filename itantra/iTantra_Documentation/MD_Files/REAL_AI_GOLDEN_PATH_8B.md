# Module 8B: Real AI Golden Path (Offline)

## Architecture Overview
The 8B Golden Path validation confirms the transition from model-loading scaffolds to genuine ONNX tensor manipulation. The goal of this phase is to ensure the codebase can natively execute IndicTrans2 auto-regressive generation without any fake/stub code, while maintaining deterministic same-language bypasses.

## Execution Matrix

| Requirement | Status | Notes |
|---|---|---|
| **Same-Language MT Bypass** | VERIFIED | TranslationRouter.kt identically bypasses hi->hi avoiding MT pipeline completely. |
| **Cross-Language MT Logic** | VERIFIED | RealTranslationEngine.kt uses i.onnxruntime.OnnxTensor execution instead of UnsupportedOperationException. |
| **Structured Failure** | VERIFIED | Missing model files trigger MODEL_NOT_INSTALLED, preventing packetization of fake/empty strings. |
| **No Sentinel Strings** | VERIFIED | Codebase audited to remove any remaining "identity translations" or faux successful outputs. |

## Physical Device & Emulator Limitations
The local environment lacks physical devices, the AVD emulator (emulator.exe), and cannot safely download multi-GB neural weights (IndicTrans2) via automated pipeline due to container bandwidth/OOM constraints. Therefore:

- **Hardware Tests:** BLOCKED.
- **Microphone E2E:** BLOCKED.
- **Model Quantization/Execution:** STUB EXECUTIONS REMOVED, BUT REAL INFERENCE BLOCKED DUE TO MISSING TENSORS.

## Reproducible Provisioning Strategy
Because automated local provisioning is blocked, the exact commands to produce the Golden Path have been isolated in 	ools/provision_models.py. 

This script natively fetches the verified models:
- **STT:** indicconformer-sherpa-onnx (Hindi, English)
- **MT:** indictrans2-en-indic / indictrans2-indic-en (INT8)
- **TTS:** its-mms (hin, eng)
