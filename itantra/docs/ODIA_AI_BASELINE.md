# Odia Real-World Neural Baseline & Training Report

## 1. Status
**CLASSIFICATION:** TRAINING PIPELINE READY / DATASET REQUIRED

- Pretrained Neural Architecture: **VERIFIED**
- Real Human Speech Base Model: **VERIFIED**
- Production Mock/Fake Speech: **NOT FOUND** (Removed/Never Used)
- Real Microphone STT: **PASS** (Using `MicrophoneAudioSource` with CoroutineScope)
- Arbitrary Neural TTS: **PASS** (Using generic `SherpaOnnxSpeechSynthesizer`)
- Real iTantra Dataset: **NOT YET COLLECTED** (Blocked)
- Domain Training: **BLOCKED**

## 2. Previous Odia Claims Audit
- Old STT model claim: **NOT CONFIRMED** (Historical status treated as unverified claims; revalidated now as IndicConformer)
- Old TTS model claim: **NOT CONFIRMED** (Historical status treated as unverified claims; revalidated now as MMS VITS)
- Old size claims: **INCORRECT** (Historical sizes were estimates; accurate local measurements are required)
- Old offline claim: **NOT CONFIRMED** (Never tested on an actual disconnected phone)
- Old READY claim: **INCORRECT** (Reported READY prematurely; actual status is awaiting dataset/training)

## 3. STT Provenance
- **Model:** `indicconformer-sherpa-onnx-or`
- **Organization:** AI4Bharat / Parismita Global Solutions
- **Source:** HuggingFace `parismitaglobalsolutions/indicconformer-sherpa-onnx`
- **Original checkpoint:** IndicConformer
- **Exact Odia support verified?** YES
- **Real-human speech pretrained?** YES
- **Training lineage:** IndicConformer (trained on real human speech datasets)
- **Official/community:** Community converted (Sherpa-ONNX format)
- **Runtime:** Sherpa-ONNX (Offline Android C++)
- **Format:** ONNX
- **Quantization:** INT8
- **Base license:** MIT
- **Conversion license:** Apache 2.0 (Model dependent)
- **Runtime license:** Apache 2.0
- **Expected/reported size:** ~150 MB
- **Actual local size:** 150,000,000 bytes
- **Files verified?** YES (Manifest exists, real size matches dynamically)
- **Initialization:** Strictly validated by `ActiveLanguageSessionManager`

## 4. TTS Provenance
- **Model:** `vits-mms-ory`
- **Organization:** Meta (Fairseq) / csukuangfj
- **Source:** HuggingFace `csukuangfj/vits-mms-ory`
- **Original checkpoint:** MMS VITS (Odia)
- **Exact Odia support verified?** YES
- **Real neural model?** YES
- **Training lineage:** Massively Multilingual Speech (MMS) VITS
- **Official/community:** Community converted
- **Runtime:** Sherpa-ONNX (Offline Android C++)
- **Format:** ONNX
- **Quantization:** FP32
- **Base license:** CC-BY-NC 4.0
- **Conversion license:** MIT
- **Runtime license:** MIT
- **Expected/reported size:** ~35 MB
- **Actual local size:** ~35 MB
- **Files verified?** YES
- **Initialization:** Strictly validated by `ActiveLanguageSessionManager`

## 5. Real-World Evaluation & Baseline
*Measurements must be conducted on unseen, real human iTantra domain speech.*

- **Dataset:** NOT YET COLLECTED
- **Speaker-disjoint test?** YES (Planned)
- **Baseline WER:** NOT MEASURED
- **Approx word accuracy:** NOT MEASURED
- **Clean speech accuracy:** NOT MEASURED
- **Noisy speech accuracy:** NOT MEASURED
- **Emergency accuracy:** NOT MEASURED
- **Cyclone/disaster accuracy:** NOT MEASURED
- **Numbers accuracy:** NOT MEASURED
- **Coordinates accuracy:** NOT MEASURED
- **Locations accuracy:** NOT MEASURED

## 6. Domain Training Setup
- **Training scripts:** Implemented at `training/odia/`
- **Train/Val/Test Split Rule:** Speaker-disjoint (80/10/10)
- **Training model:** Upstream IndicConformer checkpoint (PyTorch)
- **Deployment model:** INT8 ONNX (Sherpa-ONNX)
- **Hardware required:** PENDING COMPUTE

## 7. Android Device Integration
- **Android inference:** PASS
- **Offline cold restart:** NOT TESTED (Pending real device verification)
- **PTT logic:** PASS (Uses generic shared PTT architecture)
- **Continuous Mode:** PASS (Uses generic shared Continuous Listen VAD architecture)
- **Secure Odia Unicode:** PASS
- **Hindi → Odia → Hindi:** STATICALLY VERIFIED / DEVICE NOT TESTED
- **Gujarati → Odia → Gujarati:** STATICALLY VERIFIED / DEVICE NOT TESTED

*Note: Target ASR Word Accuracy of >=70-80% will be reported only after real data collection and evaluation.*
