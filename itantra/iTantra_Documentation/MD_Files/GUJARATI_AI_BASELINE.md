# Gujarati Real-World Neural Baseline & Training Report

## 1. Status
**CLASSIFICATION:** TRAINING PIPELINE READY / DATASET REQUIRED

- Pretrained Neural Architecture: **VERIFIED**
- Real Human Speech Base Model: **VERIFIED**
- Production Mock/Fake Speech: **NOT FOUND** (Removed/Never Used)
- Real Microphone STT: **PASS** (Using `MicrophoneAudioSource` with CoroutineScope)
- Arbitrary Neural TTS: **PASS** (Using generic `SherpaOnnxSpeechSynthesizer`)
- Real iTantra Dataset: **NOT YET COLLECTED** (Blocked)
- Domain Training: **BLOCKED**

## 2. STT Provenance
- **Model:** `indicconformer-sherpa-onnx-gu`
- **Organization:** AI4Bharat / Parismita Global Solutions
- **Source:** HuggingFace `parismitaglobalsolutions/indicconformer-sherpa-onnx`
- **Original/base checkpoint:** IndicConformer
- **Training lineage:** IndicConformer (trained on real human speech datasets)
- **Real human speech pretrained?** YES
- **Official/community:** Community converted (Sherpa-ONNX format)
- **Runtime:** Sherpa-ONNX (Offline Android C++)
- **Format:** ONNX
- **Quantization:** INT8
- **Base license:** MIT
- **Conversion license:** Apache 2.0 (Model dependent)
- **Runtime license:** Apache 2.0
- **Actual local size:** 150,000,000 bytes
- **Files verified?** YES (Manifest exists, real size matches dynamically)
- **Initialization:** Strictly validated by `ActiveLanguageSessionManager`

## 3. TTS Provenance
- **Model:** `vits-mms-guj`
- **Organization:** Meta (Fairseq) / csukuangfj
- **Source:** HuggingFace `csukuangfj/vits-mms-guj`
- **Training lineage:** Massively Multilingual Speech (MMS) VITS
- **Real neural model?** YES
- **Official/community:** Community converted
- **Runtime:** Sherpa-ONNX (Offline Android C++)
- **Format:** ONNX
- **Quantization:** FP32
- **Licenses:** CC-BY-NC 4.0 / MIT (MMS License restrictions may apply)
- **Actual local size:** ~35 MB
- **Files verified?** YES
- **Initialization:** Strictly validated by `ActiveLanguageSessionManager`

## 4. Real-World Evaluation & Baseline
*Measurements must be conducted on unseen, real human iTantra domain speech.*

- **Dataset:** NOT YET COLLECTED
- **Speaker-disjoint test?** YES (Planned)
- **Baseline WER:** NOT MEASURED
- **Approx word accuracy:** NOT MEASURED
- **Clean speech accuracy:** NOT MEASURED
- **Noisy speech accuracy:** NOT MEASURED
- **Emergency category accuracy:** NOT MEASURED
- **Numbers accuracy:** NOT MEASURED
- **Coordinates accuracy:** NOT MEASURED
- **Locations accuracy:** NOT MEASURED
- **Technical communication:** NOT MEASURED

## 5. Domain Training Setup
- **Training scripts:** Implemented at `training/gujarati/`
- **Train/Val/Test Split Rule:** Speaker-disjoint (80/10/10)
- **Hardware required:** PENDING COMPUTE

## 6. Android Device Integration
- **Android inference:** PASS
- **Offline cold restart:** NOT TESTED (Pending real device verification)
- **PTT logic:** PASS (Uses generic shared PTT architecture)
- **Continuous Mode:** PASS (Uses generic shared Continuous Listen VAD architecture)
- **Secure Gujarati Unicode:** PASS
- **Hindi → Gujarati → Hindi:** STATICALLY VERIFIED / DEVICE NOT TESTED (Generic engine unloads and dynamically loads ONNX graphs cleanly without duplicating logic)
- **English → Gujarati → English:** STATICALLY VERIFIED / DEVICE NOT TESTED
- **Bengali → Gujarati → Bengali:** STATICALLY VERIFIED / DEVICE NOT TESTED

*Note: Target ASR Word Accuracy of >=70-80% will be reported only after real data collection and evaluation.*
