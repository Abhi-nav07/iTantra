# Final Limitations

This document lists the genuine, documented limitations of the iTantra prototype as of the Pass F freeze.

- **MT Enabled**: Cross-language Machine Translation is fully implemented via native CTranslate2 JNI with IndicTrans2 200M Distilled models. Supports all 10 ISRO languages (indic→en, en→indic, indic↔indic via English pivot).
- **All 10 Language Packs Provisioned**: STT (shared Whisper Tiny), TTS (per-language MMS/VITS), and MT (shared IndicTrans2 CT2) models are provisioned for all 10 languages. Model download and integrity verification is automated via `tools/provision_models.py`.
- **Physical Device Validation Pending**: Android AI inference (Sherpa-ONNX + CTranslate2 execution) and two-phone Bluetooth/Wi-Fi networking have not been verified on actual physical hardware due to environmental constraints.
- **WER Not Measured**: Word Error Rate for Speech-to-Text has not been formally measured on physical devices in noisy tactical environments. Benchmark framework exists but requires device execution.
- **Latency Not Measured**: Real-world Bluetooth RFCOMM transmission latencies across varying distances have not been measured. Simulated loopback latencies are documented in LATENCY_MATRIX.md.
- **Thermal and Battery Impact**: Sustained AI edge inference impact on device battery life and thermal throttling has not been formally profiled. Performance benchmark harness exists but requires device execution.
- **Transport Architecture**: Bluetooth RFCOMM provides point-to-point (1:1) peer communication. This is NOT a mesh network. Wi-Fi TCP provides an alternative local LAN transport.
- **TTS License**: MMS/VITS models use CC-BY-NC 4.0 license, which restricts commercial use.
