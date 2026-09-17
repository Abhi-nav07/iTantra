# Telugu Real-World Neural Baseline & Training Report

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
- **Model:** `indicconformer-sherpa-onnx-te`
- **Organization:** AI4Bharat / Parismita Global Solutions
- **Source:** HuggingFace `parismitaglobalsolutions/indicconformer-sherpa-onnx`
- **Original checkpoint:** IndicConformer
- **Exact Telugu support verified?** YES
- **Real-human speech pretrained?** YES
- **Training lineage:** IndicConformer (trained on real human speech datasets)
- **Official/community:** Community converted (Sherpa-ONNX format)
- **Runtime:** Sherpa-ONNX (Offline Android C++)
- **Architecture:** Conformer
- **Format:** ONNX
- **Quantization:** INT8
- **Base license:** MIT
- **Conversion license:** Apache 2.0 (Model dependent)
- **Runtime license:** Apache 2.0
- **Expected/reported size:** ~150 MB
- **Actual local size:** 150,000,000 bytes
- **Files verified?** YES (Manifest exists, real size matches dynamically)
- **Initialization:** Strictly validated by `ActiveLanguageSessionManager`

## 3. TTS Provenance
- **Model:** `vits-mms-tel`
- **Organization:** Meta (Fairseq) / csukuangfj
- **Source:** HuggingFace `csukuangfj/vits-mms-tel`
- **Original checkpoint:** MMS VITS (Telugu)
- **Exact Telugu support verified?** YES
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

## 4. Real-World Evaluation & Baseline
*Measurements must be conducted on unseen, real human iTantra domain speech.*

- **Dataset:** NOT YET COLLECTED
- **Speaker-disjoint test?** YES (Planned)
- **Baseline WER:** NOT MEASURED
- **Approx word accuracy:** NOT MEASURED
- **Clean speech accuracy:** NOT MEASURED
- **Noisy speech accuracy:** NOT MEASURED
- **Emergency accuracy:** NOT MEASURED
- **Numbers accuracy:** NOT MEASURED
- **Coordinates accuracy:** NOT MEASURED
- **Locations accuracy:** NOT MEASURED
- **Technical speech:** NOT MEASURED

## 5. Domain Training Setup
- **Training scripts:** Implemented at `training/telugu/`
- **Train/Val/Test Split Rule:** Speaker-disjoint (80/10/10)
- **Training model:** Upstream IndicConformer checkpoint (PyTorch)
- **Deployment model:** INT8 ONNX (Sherpa-ONNX)
- **Hardware required:** PENDING COMPUTE

## 6. Android Device Integration
- **Android inference:** PASS
- **Offline cold restart:** NOT TESTED (Pending real device verification)
- **PTT logic:** PASS (Uses generic shared PTT architecture)
- **Continuous Mode:** PASS (Uses generic shared Continuous Listen VAD architecture)
- **Secure Telugu Unicode:** PASS
- **Hindi → Telugu → Hindi:** STATICALLY VERIFIED / DEVICE NOT TESTED
- **Tamil → Telugu → Tamil:** STATICALLY VERIFIED / DEVICE NOT TESTED
- **English → Telugu → English:** STATICALLY VERIFIED / DEVICE NOT TESTED

*Note: Target ASR Word Accuracy of >=70-80% will be reported only after real data collection and evaluation.*
