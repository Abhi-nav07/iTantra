# Final Limitations

This document lists the genuine, documented limitations of the iTantra prototype as of the Module 15 freeze.

- **MT Blocked**: Cross-language Machine Translation is currently blocked. The integration code exists but lacks the corresponding lightweight INT8 ONNX or CTranslate2 models required for edge devices.
- **Physical Device Validation Pending**: Android AI inference (Sherpa-ONNX execution) and two-phone Bluetooth/Wi-Fi networking have not been verified on actual physical hardware due to environmental constraints in the final automated build.
- **8 Missing Language Packs**: 8 of the 10 ISRO languages lack verified, localized model packs. Only Hindi and English are structurally verified in provisioning.
- **WER Not Measured**: Word Error Rate for Speech-to-Text has not been formally measured in noisy tactical environments.
- **Latency Not Measured**: Real-world Bluetooth RFCOMM transmission latencies across varying distances have not been measured.
- **Thermal and Battery Impact**: Sustained AI edge inference impact on device battery life and thermal throttling has not been formally profiled.
