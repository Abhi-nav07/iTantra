# Module 6 Final Validation & 10-Language Integration Report

## 1. Executive Summary
This document constitutes the final audit and validation for **Module 6 (Multilingual Real Neural STT + TTS Offline Foundation)** of the iTantra mission-critical offline communication system.

All 10 mandatory languages have been integrated into the unified, manifest-driven offline neural architecture:
1. **Hindi (`hi`)** — Wire ID: 1
2. **English (`en`)** — Wire ID: 2
3. **Bengali (`bn`)** — Wire ID: 3
4. **Gujarati (`gu`)** — Wire ID: 4
5. **Marathi (`mr`)** — Wire ID: 5
6. **Kannada (`kn`)** — Wire ID: 6
7. **Malayalam (`ml`)** — Wire ID: 7
8. **Tamil (`ta`)** — Wire ID: 8
9. **Telugu (`te`)** — Wire ID: 9
10. **Odia (`or`)** — Wire ID: 10

> **CRITICAL ARCHITECTURAL DISTINCTION**:
> **Architecture completion does not mean all 10 languages achieved 70–80% real-world accuracy. Only languages with measured unseen real-human test results may claim that target.**
>
> All 10 languages have valid manifests, genuine neural pre-trained models selected, and structured domain training scaffolds (`training/<language>/`). However, real-world domain field training and unseen human evaluation require the physical collection of the iTantra field speech dataset.

---

## 2. 10-Language Readiness Matrix

| Language | Code | Wire ID | STT Model | STT Format | TTS Model | TTS Format | Config | Storage | Init | Real Mic | Arbitrary TTS | Baseline WER | Domain Trained | >=70% Target | >=80% Target | Android Device | Offline Cold-Start | Current Blocker |
| :--- | :---: | :---: | :--- | :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| **Hindi** | `hi` | 1 | `indicconformer-sherpa-onnx-hi` | INT8 ONNX | `vits-piper-hi_IN` | ONNX | PASS | APP-PRIVATE | PASS | PASS | PASS | NOT MEASURED | PIPELINE READY | NOT MEASURED | NOT MEASURED | PASS | NOT TESTED | DATASET REQUIRED |
| **English** | `en` | 2 | `sherpa-onnx-streaming-zipformer-en` | INT8 ONNX | `vits-piper-en_US` | ONNX | PASS | APP-PRIVATE | PASS | PASS | PASS | NOT MEASURED | PIPELINE READY | NOT MEASURED | NOT MEASURED | PASS | NOT TESTED | DATASET REQUIRED |
| **Bengali** | `bn` | 3 | `indicconformer-sherpa-onnx-bn` | INT8 ONNX | `vits-mms-ben` | ONNX | PASS | APP-PRIVATE | PASS | PASS | PASS | NOT MEASURED | PIPELINE READY | NOT MEASURED | NOT MEASURED | PASS | NOT TESTED | DATASET REQUIRED |
| **Gujarati** | `gu` | 4 | `indicconformer-sherpa-onnx-gu` | INT8 ONNX | `vits-mms-guj` | ONNX | PASS | APP-PRIVATE | PASS | PASS | PASS | NOT MEASURED | PIPELINE READY | NOT MEASURED | NOT MEASURED | PASS | NOT TESTED | DATASET REQUIRED |
| **Marathi** | `mr` | 5 | `indicconformer-sherpa-onnx-mr` | INT8 ONNX | `vits-mms-mar` | ONNX | PASS | APP-PRIVATE | PASS | PASS | PASS | NOT MEASURED | PIPELINE READY | NOT MEASURED | NOT MEASURED | PASS | NOT TESTED | DATASET REQUIRED |
| **Kannada** | `kn` | 6 | `indicconformer-sherpa-onnx-kn` | INT8 ONNX | `vits-mms-kan` | ONNX | PASS | APP-PRIVATE | PASS | PASS | PASS | NOT MEASURED | PIPELINE READY | NOT MEASURED | NOT MEASURED | PASS | NOT TESTED | DATASET REQUIRED |
| **Malayalam** | `ml` | 7 | `indicconformer-sherpa-onnx-ml` | INT8 ONNX | `vits-mms-mal` | ONNX | PASS | APP-PRIVATE | PASS | PASS | PASS | NOT MEASURED | PIPELINE READY | NOT MEASURED | NOT MEASURED | PASS | NOT TESTED | DATASET REQUIRED |
| **Tamil** | `ta` | 8 | `indicconformer-sherpa-onnx-ta` | INT8 ONNX | `vits-mms-tam` | ONNX | PASS | APP-PRIVATE | PASS | PASS | PASS | NOT MEASURED | PIPELINE READY | NOT MEASURED | NOT MEASURED | PASS | NOT TESTED | DATASET REQUIRED |
| **Telugu** | `te` | 9 | `indicconformer-sherpa-onnx-te` | INT8 ONNX | `vits-mms-tel` | ONNX | PASS | APP-PRIVATE | PASS | PASS | PASS | NOT MEASURED | PIPELINE READY | NOT MEASURED | NOT MEASURED | PASS | NOT TESTED | DATASET REQUIRED |
| **Odia** | `or` | 10 | `indicconformer-sherpa-onnx-or` | INT8 ONNX | `vits-mms-ory` | ONNX | PASS | APP-PRIVATE | PASS | PASS | PASS | NOT MEASURED | PIPELINE READY | NOT MEASURED | NOT MEASURED | PASS | NOT TESTED | DATASET REQUIRED |

---

## 3. Real Neural Model Provenance & Licensing Matrix

| Language | STT Model Lineage | STT Base License | STT Runtime | TTS Model Lineage | TTS Base License | TTS Runtime | Restriction Flags |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Hindi** | IndicConformer (AI4Bharat) | MIT | Sherpa-ONNX (Apache 2.0) | Piper VITS (rhasspy) | MIT | Sherpa-ONNX (Apache 2.0) | None |
| **English** | Zipformer (k2-fsa) | Apache 2.0 | Sherpa-ONNX (Apache 2.0) | Piper VITS (rhasspy) | MIT | Sherpa-ONNX (Apache 2.0) | None |
| **Bengali** | IndicConformer (AI4Bharat) | MIT | Sherpa-ONNX (Apache 2.0) | Meta MMS VITS | CC-BY-NC 4.0 | Sherpa-ONNX (Apache 2.0) | Non-commercial base weights |
| **Gujarati** | IndicConformer (AI4Bharat) | MIT | Sherpa-ONNX (Apache 2.0) | Meta MMS VITS | CC-BY-NC 4.0 | Sherpa-ONNX (Apache 2.0) | Non-commercial base weights |
| **Marathi** | IndicConformer (AI4Bharat) | MIT | Sherpa-ONNX (Apache 2.0) | Meta MMS VITS | CC-BY-NC 4.0 | Sherpa-ONNX (Apache 2.0) | Non-commercial base weights |
| **Kannada** | IndicConformer (AI4Bharat) | MIT | Sherpa-ONNX (Apache 2.0) | Meta MMS VITS | CC-BY-NC 4.0 | Sherpa-ONNX (Apache 2.0) | Non-commercial base weights |
| **Malayalam** | IndicConformer (AI4Bharat) | MIT | Sherpa-ONNX (Apache 2.0) | Meta MMS VITS | CC-BY-NC 4.0 | Sherpa-ONNX (Apache 2.0) | Non-commercial base weights |
| **Tamil** | IndicConformer (AI4Bharat) | MIT | Sherpa-ONNX (Apache 2.0) | Meta MMS VITS | CC-BY-NC 4.0 | Sherpa-ONNX (Apache 2.0) | Non-commercial base weights |
| **Telugu** | IndicConformer (AI4Bharat) | MIT | Sherpa-ONNX (Apache 2.0) | Meta MMS VITS | CC-BY-NC 4.0 | Sherpa-ONNX (Apache 2.0) | Non-commercial base weights |
| **Odia** | IndicConformer (AI4Bharat) | MIT | Sherpa-ONNX (Apache 2.0) | Meta MMS VITS | CC-BY-NC 4.0 | Sherpa-ONNX (Apache 2.0) | Non-commercial base weights |

---

## 4. Architecture & Security Invariants Verification
1. **Single-Active In-Memory Rule**:
   - Only the currently selected language's heavy STT/TTS assets are loaded into RAM.
   - Enforced by `ActiveLanguageSessionManager` with mutex-serialized transitions (`switchTo`).
   - Unloads previous engines prior to loading target engines.
2. **Audio Pipeline Consistency**:
   - `MicrophoneAudioSource(scope: CoroutineScope)` emits `SharedFlow<FloatArray>`.
   - Stale `recordStream()` removed across all codebase.
3. **Transport Security & Protocol**:
   - AES-256-GCM authenticated encryption preserved.
   - Stable Wire IDs (1 to 10) mapped via `ProtocolLanguageMapper`.
   - Unicode text surviving full round-trip for Devanagari, Bengali, Gujarati, Kannada, Malayalam, Tamil, Telugu, Odia, and Latin scripts.
4. **Fallback Handling**:
   - If receiver lacks local TTS for the transmitted language, Unicode text remains displayed on-screen without message loss or dropped packets.
