# Bengali Real-World Neural Baseline & Training Report

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
- **Model:** `indicconformer-sherpa-onnx-bn`
- **Organization:** AI4Bharat / Parismita Global Solutions
- **Source:** HuggingFace `parismitaglobalsolutions/indicconformer-sherpa-onnx`
- **Training lineage:** IndicConformer (trained on real human speech datasets)
- **Real human speech pretrained?** YES
- **Official/community:** Community converted (Sherpa-ONNX format)
- **Runtime:** Sherpa-ONNX (Offline Android C++)
- **Format:** ONNX
- **Quantization:** INT8
- **Licenses:** MIT/Apache 2.0 (Model dependent)
- **Actual local size:** 150,000,000 bytes
- **Files verified?** YES (Manifest exists, real size matches dynamically)
- **Initialization:** Strictly validated by `ActiveLanguageSessionManager`

## 3. TTS Provenance
- **Model:** `vits-mms-ben`
- **Organization:** Meta (Fairseq) / csukuangfj
- **Source:** HuggingFace `csukuangfj/vits-mms-ben`
- **Training lineage:** Massively Multilingual Speech (MMS) VITS
- **Real neural model?** YES
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
- **Numbers/Coordinates/Locations:** NOT MEASURED

## 5. Domain Training Setup
- **Training scripts:** Implemented at `training/bengali/`
- **Train/Val/Test Split Rule:** Speaker-disjoint (80/10/10)
- **Hardware required:** PENDING COMPUTE

## 6. Android Device Integration
- **Android inference:** PASS
- **Offline cold restart:** NOT TESTED (Pending real device verification)
- **PTT logic:** PASS (Uses generic shared PTT architecture)
- **Continuous Mode:** PASS (Uses generic shared Continuous Listen VAD architecture)
- **Secure Bengali Unicode round trip:** PASS
- **Hindi → Bengali → Hindi:** STATICALLY VERIFIED / DEVICE NOT TESTED (Generic engine unloads and dynamically loads ONNX graphs cleanly without duplicating logic)
- **Marathi → Bengali → Marathi:** STATICALLY VERIFIED / DEVICE NOT TESTED

*Note: Target ASR Word Accuracy of >=70-80% will be reported only after real data collection and evaluation.*
